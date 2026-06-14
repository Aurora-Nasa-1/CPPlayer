use super::database::SharedFileDatabase;
use super::two_phase::TwoPhaseScanner;
use super::types::{ScanDiff, WatchBatch};
use super::watcher::EventDrivenScanner;
use anyhow::Result;
use std::path::{Path, PathBuf};
use std::sync::mpsc::Receiver;

pub struct HybridScanner {
    root_path: PathBuf,
    database: SharedFileDatabase,
    watcher: EventDrivenScanner,
}

impl HybridScanner {
    pub fn bootstrap<P: AsRef<Path>>(
        root_path: P,
        database: SharedFileDatabase,
    ) -> Result<(Self, ScanDiff, Receiver<WatchBatch>)> {
        let root_path = root_path.as_ref().canonicalize()?;
        let diff = TwoPhaseScanner::scan(&root_path, &database)?;
        database.apply_scan_diff(&diff);

        let (watcher, live_updates) = EventDrivenScanner::start(&root_path, database.clone())?;

        Ok((
            Self {
                root_path,
                database,
                watcher,
            },
            diff,
            live_updates,
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

    pub fn watcher(&self) -> &EventDrivenScanner {
        &self.watcher
    }

    pub fn root_path(&self) -> &Path {
        &self.root_path
    }
}
