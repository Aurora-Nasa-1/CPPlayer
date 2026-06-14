#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChangeKind {
    New,
    Modified,
    Deleted,
    Unchanged,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct FileFingerprint {
    pub path: String,
    pub size: u64,
    pub last_modified_ms: i64,
}

impl FileFingerprint {
    pub fn changed_from(&self, other: &Self) -> bool {
        self.size != other.size || self.last_modified_ms != other.last_modified_ms
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct DbWriteBatch {
    pub upserts: Vec<FileFingerprint>,
    pub deletes: Vec<String>,
}

impl DbWriteBatch {
    pub fn is_empty(&self) -> bool {
        self.upserts.is_empty() && self.deletes.is_empty()
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct ScanDiff {
    pub new_files: Vec<FileFingerprint>,
    pub modified_files: Vec<FileFingerprint>,
    pub deleted_files: Vec<String>,
    pub unchanged_files: Vec<FileFingerprint>,
}

impl ScanDiff {
    pub fn is_empty(&self) -> bool {
        self.new_files.is_empty()
            && self.modified_files.is_empty()
            && self.deleted_files.is_empty()
            && self.unchanged_files.is_empty()
    }

    pub fn changed_file_count(&self) -> usize {
        self.new_files.len() + self.modified_files.len() + self.deleted_files.len()
    }

    pub fn total_file_count(&self) -> usize {
        self.changed_file_count() + self.unchanged_files.len()
    }

    pub fn to_write_batch(&self) -> DbWriteBatch {
        let mut upserts = Vec::with_capacity(self.new_files.len() + self.modified_files.len());
        upserts.extend(self.new_files.iter().cloned());
        upserts.extend(self.modified_files.iter().cloned());

        DbWriteBatch {
            upserts,
            deletes: self.deleted_files.clone(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WatchChange {
    pub kind: ChangeKind,
    pub path: String,
    pub fingerprint: Option<FileFingerprint>,
}

impl WatchChange {
    pub fn upsert(kind: ChangeKind, fingerprint: FileFingerprint) -> Self {
        let path = fingerprint.path.clone();
        Self {
            kind,
            path,
            fingerprint: Some(fingerprint),
        }
    }

    pub fn deleted(path: String) -> Self {
        Self {
            kind: ChangeKind::Deleted,
            path,
            fingerprint: None,
        }
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct WatchBatch {
    pub changes: Vec<WatchChange>,
}

impl WatchBatch {
    pub fn is_empty(&self) -> bool {
        self.changes.is_empty()
    }
}
