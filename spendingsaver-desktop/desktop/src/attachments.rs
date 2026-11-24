//! Receipts and other files attached to a transaction. Desktop only: the files are copied into an
//! `attachments` folder next to the database and are not part of the Android backup or phone sync.
//! Only common document and image types are accepted, because "Open" hands the file to the operating
//! system, and a program or script must never be launched from here.
use crate::db::Db;
use eframe::egui::{self, RichText};
use rusqlite::params;
use std::collections::HashSet;
use std::path::{Path, PathBuf};

pub const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS attachments(id TEXT PRIMARY KEY, transaction_id TEXT NOT NULL, name TEXT NOT NULL,
  stored TEXT NOT NULL, size INTEGER NOT NULL, created_at TEXT NOT NULL);
CREATE INDEX IF NOT EXISTS attachments_tx ON attachments(transaction_id);
";

pub const MAX_BYTES: u64 = 25 * 1024 * 1024;
pub const ALLOWED: [&str; 21] = [
    "pdf", "png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "heic", "txt", "csv", "md", "rtf", "doc", "docx", "xls", "xlsx", "odt", "ods", "eml",
];

#[derive(Clone, Debug, PartialEq)]
pub struct Attachment {
    pub id: String,
    pub transaction_id: String,
    pub name: String,
    pub stored: String,
    pub size: u64,
}

/// The folder holding the copied files (created on first use).
pub fn dir(db: &Db) -> Option<PathBuf> {
    db.file().and_then(|f| f.parent().map(|p| p.join("attachments")))
}

fn extension(name: &str) -> Option<String> {
    let ext = Path::new(name).extension()?.to_str()?.to_ascii_lowercase();
    ALLOWED.contains(&ext.as_str()).then_some(ext)
}

/// A name that is safe to show: no path parts, no control characters, at most 120 characters.
fn display_name(name: &str) -> String {
    let base = Path::new(name).file_name().and_then(|n| n.to_str()).unwrap_or("file");
    base.chars().filter(|c| !c.is_control()).take(120).collect()
}

/// Copies `src` into the attachments folder and records it against the transaction.
pub fn add(db: &Db, transaction_id: &str, src: &Path) -> Result<Attachment, String> {
    let name = display_name(&src.to_string_lossy());
    let ext = extension(&name).ok_or_else(|| format!("That file type is not supported. Attach one of: {}.", ALLOWED.join(", ")))?;
    let meta = std::fs::metadata(src).map_err(|e| format!("Cannot read that file: {e}"))?;
    if !meta.is_file() {
        return Err("Choose a file, not a folder.".into());
    }
    if meta.len() > MAX_BYTES {
        return Err(format!("That file is larger than {} MB.", MAX_BYTES / 1024 / 1024));
    }
    let folder = dir(db).ok_or("Attachments need the database file, which could not be opened.")?;
    std::fs::create_dir_all(&folder).map_err(|e| format!("Cannot create the attachments folder: {e}"))?;
    let id = uuid::Uuid::new_v4().to_string();
    let stored = format!("{id}.{ext}");
    std::fs::copy(src, folder.join(&stored)).map_err(|e| format!("Could not copy the file: {e}"))?;
    db.conn()
        .execute(
            "INSERT INTO attachments(id,transaction_id,name,stored,size,created_at) VALUES(?1,?2,?3,?4,?5,datetime('now'))",
            params![id, transaction_id, name, stored, meta.len() as i64],
        )
        .map_err(|e| e.to_string())?;
    Ok(Attachment { id, transaction_id: transaction_id.into(), name, stored, size: meta.len() })
}

pub fn list(db: &Db, transaction_id: &str) -> Vec<Attachment> {
    let Ok(mut s) = db.conn().prepare("SELECT id,transaction_id,name,stored,size FROM attachments WHERE transaction_id=?1 ORDER BY created_at, name") else { return vec![] };
    s.query_map(params![transaction_id], |r| {
        Ok(Attachment { id: r.get(0)?, transaction_id: r.get(1)?, name: r.get(2)?, stored: r.get(3)?, size: r.get::<_, i64>(4)? as u64 })
    })
    .map(|m| m.flatten().collect())
    .unwrap_or_default()
}

/// Ids of the transactions that have at least one attachment.
pub fn marked(db: &Db) -> HashSet<String> {
    let Ok(mut s) = db.conn().prepare("SELECT DISTINCT transaction_id FROM attachments") else { return HashSet::new() };
    s.query_map([], |r| r.get::<_, String>(0)).map(|m| m.flatten().collect()).unwrap_or_default()
}

/// Deletes the stored copy and its record. The original file the user picked is never touched.
pub fn remove(db: &Db, id: &str) -> Result<(), String> {
    let stored: Option<String> = db.conn().query_row("SELECT stored FROM attachments WHERE id=?1", params![id], |r| r.get(0)).ok();
    if let (Some(stored), Some(folder)) = (stored, dir(db)) {
        // `stored` is always "<uuid>.<ext>"; refuse anything else so a damaged row cannot reach other files.
        if !stored.contains(['/', '\\']) && !stored.starts_with('.') {
            let _ = std::fs::remove_file(folder.join(stored));
        }
    }
    db.conn().execute("DELETE FROM attachments WHERE id=?1", params![id]).map_err(|e| e.to_string())?;
    Ok(())
}

/// Opens the stored copy in the program the operating system associates with its type.
pub fn open(db: &Db, a: &Attachment) -> Result<(), String> {
    if extension(&a.stored).is_none() || a.stored.contains(['/', '\\']) {
        return Err("This attachment cannot be opened.".into());
    }
    let path = dir(db).ok_or("No attachments folder.")?.join(&a.stored);
    if !path.is_file() {
        return Err("The stored file is missing (it may have been deleted outside the app).".into());
    }
    #[cfg(windows)]
    let mut cmd = std::process::Command::new("explorer.exe");
    #[cfg(target_os = "macos")]
    let mut cmd = std::process::Command::new("open");
    #[cfg(all(unix, not(target_os = "macos")))]
    let mut cmd = std::process::Command::new("xdg-open");
    cmd.arg(&path).spawn().map(|_| ()).map_err(|e| format!("Could not open it: {e}"))
}

fn size_text(n: u64) -> String {
    if n >= 1024 * 1024 {
        format!("{:.1} MB", n as f64 / 1024.0 / 1024.0)
    } else {
        format!("{} KB", (n / 1024).max(1))
    }
}

/// UI state for the attachments shown while a transaction is being edited.
#[derive(Default)]
pub struct Panel {
    for_tx: Option<String>,
    items: Vec<Attachment>,
    confirm_remove: Option<String>,
    message: Option<(String, bool)>,
    pub marks: HashSet<String>,
}

impl Panel {
    pub fn refresh_marks(&mut self, db: &Db) {
        self.marks = marked(db);
        self.for_tx = None; // re-read the list for the transaction being edited
    }

    /// Lists the transaction's files with Attach / Open / Remove. Removing takes two clicks.
    pub fn show(&mut self, ui: &mut egui::Ui, db: &Db, transaction_id: &str) {
        if self.for_tx.as_deref() != Some(transaction_id) {
            self.items = list(db, transaction_id);
            self.for_tx = Some(transaction_id.to_string());
            self.confirm_remove = None;
            self.message = None;
        }
        ui.add_space(4.0);
        ui.separator();
        ui.label(RichText::new("Attachments (receipts, invoices). Kept on this computer only.").strong());
        let mut open: Option<Attachment> = None;
        let mut remove: Option<String> = None;
        for a in &self.items {
            ui.horizontal(|ui| {
                ui.label(&a.name);
                ui.label(RichText::new(size_text(a.size)).weak());
                if ui.small_button("Open").clicked() {
                    open = Some(a.clone());
                }
                let armed = self.confirm_remove.as_deref() == Some(a.id.as_str());
                if ui.small_button(if armed { "Click again to remove" } else { "Remove" }).clicked() {
                    if armed {
                        remove = Some(a.id.clone());
                    } else {
                        self.confirm_remove = Some(a.id.clone());
                    }
                }
            });
        }
        if self.items.is_empty() {
            ui.label(RichText::new("None yet.").weak());
        }
        if ui.button("Attach a file…").clicked() {
            if let Some(path) = rfd::FileDialog::new().add_filter("Receipts and documents", &ALLOWED).pick_file() {
                match add(db, transaction_id, &path) {
                    Ok(_) => {
                        self.message = Some(("Attached.".into(), false));
                        self.marks.insert(transaction_id.to_string());
                    }
                    Err(e) => self.message = Some((e, true)),
                }
                self.for_tx = None;
            }
        }
        if let Some(a) = open {
            if let Err(e) = open_it(db, &a) {
                self.message = Some((e, true));
            }
        }
        if let Some(id) = remove {
            match remove_it(db, &id) {
                Ok(()) => self.message = Some(("Removed.".into(), false)),
                Err(e) => self.message = Some((e, true)),
            }
            self.marks = marked(db);
            self.for_tx = None;
        }
        if let Some((m, bad)) = &self.message {
            ui.colored_label(if *bad { crate::ui::BAD } else { crate::ui::GOOD }, m);
        }
    }
}

fn open_it(db: &Db, a: &Attachment) -> Result<(), String> {
    open(db, a)
}
fn remove_it(db: &Db, id: &str) -> Result<(), String> {
    remove(db, id)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scratch(tag: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("ss-att-{tag}-{}", uuid::Uuid::new_v4().simple()));
        std::fs::create_dir_all(&d).unwrap();
        d
    }

    #[test]
    fn attach_list_and_remove_round_trip() {
        let root = scratch("rt");
        let db = Db::open(&root.join("spendingsaver.db")).unwrap();
        let src = root.join("Lunch receipt.PDF");
        std::fs::write(&src, b"%PDF-1.4 test").unwrap();
        let a = add(&db, "tx1", &src).unwrap();
        assert_eq!(a.name, "Lunch receipt.PDF");
        assert!(a.stored.ends_with(".pdf"));
        assert!(dir(&db).unwrap().join(&a.stored).is_file());
        assert_eq!(list(&db, "tx1").len(), 1);
        assert!(list(&db, "tx2").is_empty());
        assert!(marked(&db).contains("tx1"));
        remove(&db, &a.id).unwrap();
        assert!(!dir(&db).unwrap().join(&a.stored).exists());
        assert!(list(&db, "tx1").is_empty());
        assert!(src.is_file(), "the user's original file is never touched");
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn programs_and_scripts_are_refused() {
        let root = scratch("bad");
        let db = Db::open(&root.join("spendingsaver.db")).unwrap();
        for name in ["setup.exe", "run.bat", "x.ps1", "a.lnk", "noext", "evil.pdf.exe", "page.html", "x.svg"] {
            let p = root.join(name);
            std::fs::write(&p, b"x").unwrap();
            assert!(add(&db, "tx", &p).is_err(), "{name} should be refused");
        }
        assert!(list(&db, "tx").is_empty());
        assert!(!dir(&db).unwrap().exists() || std::fs::read_dir(dir(&db).unwrap()).unwrap().next().is_none());
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn oversized_files_and_folders_are_refused() {
        let root = scratch("big");
        let db = Db::open(&root.join("spendingsaver.db")).unwrap();
        let big = root.join("big.pdf");
        let f = std::fs::File::create(&big).unwrap();
        f.set_len(MAX_BYTES + 1).unwrap();
        assert!(add(&db, "tx", &big).unwrap_err().contains("larger"));
        let folder = root.join("dir.pdf");
        std::fs::create_dir_all(&folder).unwrap();
        assert!(add(&db, "tx", &folder).is_err());
        let _ = std::fs::remove_dir_all(&root);
    }

    #[test]
    fn a_damaged_row_cannot_delete_other_files() {
        let root = scratch("dmg");
        let db = Db::open(&root.join("spendingsaver.db")).unwrap();
        let victim = root.join("keep.txt");
        std::fs::write(&victim, b"keep").unwrap();
        db.conn().execute("INSERT INTO attachments VALUES('x','tx','n','..\\keep.txt',1,'now')", []).unwrap();
        db.conn().execute("INSERT INTO attachments VALUES('y','tx','n','../keep.txt',1,'now')", []).unwrap();
        remove(&db, "x").unwrap();
        remove(&db, "y").unwrap();
        assert!(victim.is_file());
        assert!(open(&db, &Attachment { id: "x".into(), transaction_id: "tx".into(), name: "n".into(), stored: "..\\keep.txt".into(), size: 1 }).is_err());
        let _ = std::fs::remove_dir_all(&root);
    }
}
