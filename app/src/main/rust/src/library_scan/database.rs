use super::types::{ChangeKind, DbWriteBatch, FileFingerprint, ScanDiff, WatchBatch, WatchChange};
use parking_lot::RwLock;
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Debug, Clone, Default)]
pub struct SharedFileDatabase {
    inner: Arc<RwLock<HashMap<String, FileFingerprint>>>,
}

impl SharedFileDatabase {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn from_files(files: impl IntoIterator<Item = FileFingerprint>) -> Self {
        let map = files
            .into_iter()
            .map(|file| (file.path.clone(), file))
            .collect::<HashMap<_, _>>();

        Self {
            inner: Arc::new(RwLock::new(map)),
        }
    }

    pub fn snapshot(&self) -> HashMap<String, FileFingerprint> {
        self.inner.read().clone()
    }

    pub fn get(&self, path: &str) -> Option<FileFingerprint> {
        self.inner.read().get(path).cloned()
    }

    pub fn contains(&self, path: &str) -> bool {
        self.inner.read().contains_key(path)
    }

    pub fn len(&self) -> usize {
        self.inner.read().len()
    }

    pub fn is_empty(&self) -> bool {
        self.inner.read().is_empty()
    }

    pub fn apply_scan_diff(&self, diff: &ScanDiff) {
        self.apply_write_batch(diff.to_write_batch());
    }

    pub fn apply_write_batch(&self, batch: DbWriteBatch) {
        if batch.is_empty() {
            return;
        }

        let mut guard = self.inner.write();

        for fingerprint in batch.upserts {
            guard.insert(fingerprint.path.clone(), fingerprint);
        }

        for path in batch.deletes {
            guard.remove(&path);
        }
    }

    pub(crate) fn apply_live_batch(&self, batch: DbWriteBatch) -> WatchBatch {
        if batch.is_empty() {
            return WatchBatch::default();
        }

        let mut guard = self.inner.write();
        let mut changes = Vec::with_capacity(batch.upserts.len() + batch.deletes.len());

        for path in batch.deletes {
            if guard.remove(&path).is_some() {
                changes.push(WatchChange::deleted(path));
            }
        }

        for fingerprint in batch.upserts {
            match guard.get(&fingerprint.path) {
                Some(existing) if existing == &fingerprint => {}
                Some(_) => {
                    guard.insert(fingerprint.path.clone(), fingerprint.clone());
                    changes.push(WatchChange::upsert(ChangeKind::Modified, fingerprint));
                }
                None => {
                    guard.insert(fingerprint.path.clone(), fingerprint.clone());
                    changes.push(WatchChange::upsert(ChangeKind::New, fingerprint));
                }
            }
        }

        WatchBatch { changes }
    }
}
