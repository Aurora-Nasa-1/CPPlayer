use super::database::SharedFileDatabase;
use super::two_phase::{is_supported_audio_path, read_fingerprint, TwoPhaseScanner};
use super::types::{DbWriteBatch, FileFingerprint, ScanDiff, WatchBatch};
use anyhow::Result;
use notify::event::ModifyKind;
use notify::{Config, Event, EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use std::collections::HashMap;
use std::io::ErrorKind;
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::thread::{self, JoinHandle};
use std::time::Duration;

const BATCH_WINDOW: Duration = Duration::from_millis(200);
const MAX_PENDING_EVENTS: usize = 256;

enum PendingAction {
    Delete,
    Upsert(FileFingerprint),
}

pub struct EventDrivenScanner {
    root_path: PathBuf,
    database: SharedFileDatabase,
    _watcher: RecommendedWatcher,
    stop_tx: Option<Sender<()>>,
    worker_handle: Option<JoinHandle<()>>,
}

impl EventDrivenScanner {
    pub fn start<P: AsRef<Path>>(
        root_path: P,
        database: SharedFileDatabase,
    ) -> Result<(Self, Receiver<WatchBatch>)> {
        let root_path = root_path.as_ref().canonicalize()?;
        let (raw_event_tx, raw_event_rx) = mpsc::channel();
        let (stop_tx, stop_rx) = mpsc::channel();
        let (live_update_tx, live_update_rx) = mpsc::channel();

        let callback_tx = raw_event_tx.clone();
        let mut watcher = RecommendedWatcher::new(
            move |event| {
                let _ = callback_tx.send(event);
            },
            Config::default(),
        )?;
        watcher.watch(&root_path, RecursiveMode::Recursive)?;

        let worker_database = database.clone();
        let worker_handle = thread::Builder::new()
            .name("library-scan-watcher".to_string())
            .spawn(move || {
                watcher_event_loop(worker_database, raw_event_rx, stop_rx, live_update_tx);
            })?;

        Ok((
            Self {
                root_path,
                database,
                _watcher: watcher,
                stop_tx: Some(stop_tx),
                worker_handle: Some(worker_handle),
            },
            live_update_rx,
        ))
    }

    pub fn manual_rescan(&self) -> Result<ScanDiff> {
        let diff = TwoPhaseScanner::scan(&self.root_path, &self.database)?;
        self.database.apply_scan_diff(&diff);
        Ok(diff)
    }

    pub fn database(&self) -> SharedFileDatabase {
        self.database.clone()
    }

    fn shutdown(&mut self) {
        if let Some(stop_tx) = self.stop_tx.take() {
            let _ = stop_tx.send(());
        }

        if let Some(worker_handle) = self.worker_handle.take() {
            let _ = worker_handle.join();
        }
    }
}

impl Drop for EventDrivenScanner {
    fn drop(&mut self) {
        self.shutdown();
    }
}

fn watcher_event_loop(
    database: SharedFileDatabase,
    raw_event_rx: Receiver<notify::Result<Event>>,
    stop_rx: Receiver<()>,
    live_update_tx: Sender<WatchBatch>,
) {
    let mut pending = HashMap::new();

    loop {
        if stop_rx.try_recv().is_ok() {
            flush_pending(&database, &mut pending, &live_update_tx);
            break;
        }

        match raw_event_rx.recv_timeout(BATCH_WINDOW) {
            Ok(Ok(event)) => {
                queue_event(&database, event, &mut pending);

                if pending.len() >= MAX_PENDING_EVENTS {
                    flush_pending(&database, &mut pending, &live_update_tx);
                }
            }
            Ok(Err(_)) => {}
            Err(RecvTimeoutError::Timeout) => {
                flush_pending(&database, &mut pending, &live_update_tx);
            }
            Err(RecvTimeoutError::Disconnected) => {
                flush_pending(&database, &mut pending, &live_update_tx);
                break;
            }
        }
    }
}

fn queue_event(
    database: &SharedFileDatabase,
    event: Event,
    pending: &mut HashMap<String, PendingAction>,
) {
    match event.kind {
        EventKind::Create(_) => {
            for path in event.paths {
                queue_upsert(database, pending, &path);
            }
        }
        EventKind::Modify(ModifyKind::Name(_)) => {
            queue_rename(database, pending, event.paths);
        }
        EventKind::Modify(_) => {
            for path in event.paths {
                queue_upsert(database, pending, &path);
            }
        }
        EventKind::Remove(_) => {
            for path in event.paths {
                queue_delete(database, pending, &path);
            }
        }
        _ => {}
    }
}

fn queue_rename(
    database: &SharedFileDatabase,
    pending: &mut HashMap<String, PendingAction>,
    paths: Vec<PathBuf>,
) {
    match paths.as_slice() {
        [old_path, new_path] => {
            queue_delete(database, pending, old_path);
            queue_upsert(database, pending, new_path);
        }
        [single_path] => {
            if single_path.exists() {
                queue_upsert(database, pending, single_path);
            } else {
                queue_delete(database, pending, single_path);
            }
        }
        _ => {
            for path in paths {
                if path.exists() {
                    queue_upsert(database, pending, &path);
                } else {
                    queue_delete(database, pending, &path);
                }
            }
        }
    }
}

fn queue_upsert(
    database: &SharedFileDatabase,
    pending: &mut HashMap<String, PendingAction>,
    path: &Path,
) {
    if !is_supported_audio_path(path) {
        return;
    }

    match read_fingerprint(path) {
        Ok(fingerprint) => {
            let path = fingerprint.path.clone();

            if database.get(&path).as_ref() == Some(&fingerprint) {
                pending.remove(&path);
            } else {
                pending.insert(path, PendingAction::Upsert(fingerprint));
            }
        }
        Err(error) if error.kind() == ErrorKind::NotFound => {
            queue_delete(database, pending, path);
        }
        Err(_) => {}
    }
}

fn queue_delete(
    database: &SharedFileDatabase,
    pending: &mut HashMap<String, PendingAction>,
    path: &Path,
) {
    let path = path.to_string_lossy().into_owned();

    if database.contains(&path) || pending.contains_key(&path) {
        pending.insert(path, PendingAction::Delete);
    }
}

fn flush_pending(
    database: &SharedFileDatabase,
    pending: &mut HashMap<String, PendingAction>,
    live_update_tx: &Sender<WatchBatch>,
) {
    if pending.is_empty() {
        return;
    }

    let mut batch = DbWriteBatch::default();

    for (path, action) in pending.drain() {
        match action {
            PendingAction::Delete => batch.deletes.push(path),
            PendingAction::Upsert(fingerprint) => batch.upserts.push(fingerprint),
        }
    }

    batch.deletes.sort();
    batch
        .upserts
        .sort_by(|left, right| left.path.cmp(&right.path));

    let live_batch = database.apply_live_batch(batch);
    if !live_batch.is_empty() {
        let _ = live_update_tx.send(live_batch);
    }
}
