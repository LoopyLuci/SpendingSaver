//! Phone Sync page. Sharing is off until the user turns it on, and the hub only starts then.
use crate::db::Db;
use crate::hub::{self, Hub};
use crate::link::{self, Link};
use crate::ui;
use eframe::egui::{self, Color32, RichText};
use std::sync::atomic::Ordering;
use std::sync::{mpsc, Arc};

#[derive(Default)]
pub struct SyncPage {
    hub: Option<Hub>,
    error: Option<String>,
    show_key: bool,
    seen_changes: u64,
    // "Connect from anywhere" (optional, off by default; see link.rs)
    link: Option<Arc<Link>>,
    link_job: Option<mpsc::Receiver<Result<Link, String>>>,
    link_error: Option<String>,
    relays_text: Option<String>,
    code: Option<String>,
    code_job: Option<mpsc::Receiver<Result<String, String>>>,
    peers: Vec<String>,
}

struct Device {
    id: String,
    name: String,
    last_seen: Option<i64>,
}

pub fn lan_ip() -> String {
    std::net::UdpSocket::bind("0.0.0.0:0")
        .and_then(|s| {
            s.connect("8.8.8.8:80")?;
            s.local_addr()
        })
        .map(|a| a.ip().to_string())
        .unwrap_or_else(|_| "127.0.0.1".into())
}

impl SyncPage {
    /// Starts sharing at launch if the user left it on. Spawning a thread and binding a port takes microseconds.
    pub fn autostart(&mut self, db: &Db, ctx: &egui::Context) {
        if db.get_setting("sync_enabled").as_deref() == Some("1") {
            self.start(db, ctx);
            if db.get_setting("link_enabled").as_deref() == Some("1") {
                self.start_link(db, ctx);
            }
        }
    }

    fn link_dir(db: &Db) -> Option<std::path::PathBuf> {
        db.file().and_then(|f| f.parent().map(|p| p.join("link")))
    }

    /// Starts the Transferd agent on a background thread; the app never waits for it.
    fn start_link(&mut self, db: &Db, ctx: &egui::Context) {
        let (Some(port), Some(dir)) = (self.hub.as_ref().map(|h| h.port), Self::link_dir(db)) else { return };
        let relays = link::parse_relays(&db.get_setting("link_relays").unwrap_or_default()).unwrap_or_default();
        let name = db.get_setting("link_name").unwrap_or_else(|| "SpendingSaver Desktop".into());
        let (tx, rx) = mpsc::channel();
        self.link_job = Some(rx);
        self.link_error = None;
        let c = ctx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(link::start(&dir, &name, &relays, port));
            c.request_repaint();
        });
    }

    fn stop_link(&mut self) {
        self.link = None;
        self.link_job = None;
        self.code = None;
        self.code_job = None;
        self.peers.clear();
    }

    fn poll_link(&mut self, db: &Db) {
        if let Some(rx) = &self.link_job {
            match rx.try_recv() {
                Ok(Ok(l)) => {
                    self.link = Some(Arc::new(l));
                    self.link_job = None;
                    self.refresh_peers();
                }
                Ok(Err(e)) => {
                    self.link_error = Some(e);
                    self.link_job = None;
                    db.set_setting("link_enabled", "0");
                }
                Err(mpsc::TryRecvError::Empty) => {}
                Err(mpsc::TryRecvError::Disconnected) => {
                    self.link_job = None;
                    self.link_error = Some("The link component stopped unexpectedly.".into());
                }
            }
        }
        if let Some(rx) = &self.code_job {
            match rx.try_recv() {
                Ok(r) => {
                    match r {
                        Ok(c) => self.code = Some(c),
                        Err(e) => self.link_error = Some(e),
                    }
                    self.code_job = None;
                    self.refresh_peers();
                }
                Err(mpsc::TryRecvError::Empty) => {}
                Err(mpsc::TryRecvError::Disconnected) => self.code_job = None,
            }
        }
    }

    fn refresh_peers(&mut self) {
        if let Some(l) = &self.link {
            self.peers = l.peers();
        }
    }

    fn start(&mut self, db: &Db, ctx: &egui::Context) {
        let Some(file) = db.file() else {
            self.error = Some("Sharing needs the database file, which could not be opened.".into());
            return;
        };
        let c = ctx.clone();
        match hub::start(file, move || c.request_repaint()) {
            Ok(h) => {
                self.hub = Some(h);
                self.error = None;
                db.set_setting("sync_enabled", "1");
            }
            Err(e) => self.error = Some(e),
        }
    }

    /// True once after a phone has changed data, so the app reloads.
    pub fn take_changed(&mut self, db: &Db) -> bool {
        self.poll_link(db);
        match &self.hub {
            Some(h) => {
                let n = h.changed.load(Ordering::SeqCst);
                let changed = n != self.seen_changes;
                self.seen_changes = n;
                changed
            }
            None => false,
        }
    }

    fn devices(db: &Db) -> Vec<Device> {
        let Ok(mut s) = db.conn().prepare("SELECT id, COALESCE(name,''), last_seen FROM sync_devices WHERE revoked=0 ORDER BY created_at") else { return vec![] };
        s.query_map([], |r| Ok(Device { id: r.get(0)?, name: r.get(1)?, last_seen: r.get(2)? })).map(|m| m.flatten().collect()).unwrap_or_default()
    }

    pub fn show(&mut self, ui: &mut egui::Ui, db: &Db, accent: Color32) {
        let ctx = ui.ctx().clone();
        ui::card(ui, |ui| {
            ui.label(
                "Sync is optional. This computer and the phone each keep a full copy and work alone. When you turn sharing on, the phone \
                 can pair and exchange accounts, categories, transactions and budgets over your Wi-Fi. Turn it off and nothing is reachable.",
            );
        });
        ui.add_space(8.0);
        if let Some(e) = &self.error {
            ui.colored_label(ui::BAD, e);
        }
        let Some(port) = self.hub.as_ref().map(|h| h.port) else {
            ui::card(ui, |ui| {
                ui::heading(ui, "Sharing is off");
                if ui.button("Turn on phone sync").clicked() {
                    self.start(db, &ctx);
                }
            });
            return;
        };
        let ip = lan_ip();
        let key = hub::pairing_key(db.conn());
        ui.columns(2, |c| {
            ui::card(&mut c[0], |ui| {
                ui::heading(ui, "Scan with the phone");
                ui.label("In the Android app open Pair and tap Scan QR code. The phone must be on the same Wi-Fi as this computer.");
                let code = format!("sslan:{ip}:{port}:{key}");
                draw_qr(ui, &code, 236.0);
            });
            ui::card(&mut c[1], |ui| {
                ui::heading(ui, "Or type it in");
                egui::Grid::new("pairinfo").num_columns(2).spacing([14.0, 8.0]).show(ui, |ui| {
                    ui.label("Hub address");
                    ui.label(RichText::new(format!("{ip}:{port}")).monospace().strong());
                    ui.end_row();
                    ui.label("Pairing key");
                    ui.label(RichText::new(if self.show_key { key.clone() } else { "••••••".into() }).monospace().strong());
                    ui.end_row();
                });
                ui.horizontal(|ui| {
                    if ui.button(if self.show_key { "Hide key" } else { "Show key" }).clicked() {
                        self.show_key = !self.show_key;
                    }
                    if ui.button("New key").on_hover_text("Changes the key. Phones already paired keep working.").clicked() {
                        hub::new_key(db.conn());
                    }
                });
                ui.add_space(8.0);
                ui.label(RichText::new("The first time, Windows may ask to allow SpendingSaver on private networks. Allow it so the phone can connect.").small().weak());
                ui.add_space(8.0);
                if ui.button("Turn off phone sync").clicked() {
                    self.stop_link();
                    self.hub = None;
                    db.set_setting("sync_enabled", "0");
                }
            });
        });
        ui.add_space(8.0);
        self.link_card(ui, db, &ctx, &key);
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, "Paired phones");
            let devices = Self::devices(db);
            if devices.is_empty() {
                ui.label("No phone has paired yet.");
            }
            let mut revoke: Option<String> = None;
            for d in &devices {
                ui.horizontal(|ui| {
                    ui.label(RichText::new(if d.name.is_empty() { "Phone" } else { &d.name }).strong());
                    ui.label(RichText::new(match d.last_seen {
                        Some(ms) => format!("last seen {}", hub::wire(ms).replace('T', " ").trim_end_matches('Z')),
                        None => "never synced".into(),
                    }).weak());
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui.small_button("Remove").clicked() {
                            revoke = Some(d.id.clone());
                        }
                    });
                });
            }
            if let Some(id) = revoke {
                let _ = db.conn().execute("UPDATE sync_devices SET revoked=1 WHERE id=?1", rusqlite::params![id]);
            }
        });
        let _ = accent;
    }
}

impl SyncPage {
    fn link_card(&mut self, ui: &mut egui::Ui, db: &Db, ctx: &egui::Context, key: &str) {
        let text = self.relays_text.get_or_insert_with(|| db.get_setting("link_relays").unwrap_or_default()).clone();
        let mut relays = text;
        let mut turn_on = false;
        let mut turn_off = false;
        let mut new_code = false;
        ui::card(ui, |ui| {
            ui::heading(ui, "Connect from anywhere (optional)");
            ui.label(
                "Lets a phone sync with this computer when they are not on the same Wi-Fi, over an end-to-end encrypted Transferd connection. \
                 No port is opened on your router. Both apps keep working alone, and Wi-Fi sync is unaffected.",
            );
            ui.add_space(6.0);
            if let Some(e) = &self.link_error {
                ui.colored_label(ui::BAD, e);
            }
            if self.link.is_some() {
                let id = self.link.as_ref().map(|l| l.node_id.clone()).unwrap_or_default();
                ui.label(RichText::new("On").strong().color(ui::GOOD));
                ui.label(RichText::new(format!("This computer's link ID: {}", id.chars().take(16).collect::<String>())).small().weak());
                ui.horizontal(|ui| {
                    if ui.button("New phone code").clicked() {
                        new_code = true;
                    }
                    if ui.button("Turn off").clicked() {
                        turn_off = true;
                    }
                });
                if self.code_job.is_some() {
                    ui.label("Making a code…");
                }
                if let Some(code) = &self.code {
                    ui.add_space(6.0);
                    ui.label("On the phone open Pair, choose From anywhere and scan this. It works once and expires in 10 minutes.");
                    draw_qr(ui, code, 236.0);
                    if ui.button("Copy code").clicked() {
                        ui.output_mut(|o| o.copied_text = code.clone());
                    }
                }
                ui.add_space(6.0);
                if self.peers.is_empty() {
                    ui.label(RichText::new("No phone has paired over the link yet.").weak());
                } else {
                    ui.label(format!("Paired over the link: {}", self.peers.join(", ")));
                }
            } else if self.link_job.is_some() {
                ui.label("Starting…");
            } else {
                egui::Grid::new("linkcfg").num_columns(2).spacing([14.0, 8.0]).show(ui, |ui| {
                    ui.label("Relay address");
                    ui.add(egui::TextEdit::singleline(&mut relays).hint_text("relay.example.com:5901 (blank = direct only)").desired_width(320.0));
                    ui.end_row();
                });
                ui.label(RichText::new("A relay is a small server you run that only passes along encrypted data it cannot read. Without one only a direct connection works.").small().weak());
                if link::find_agent().is_none() {
                    ui.colored_label(ui::BAD, "The link component (ss-link-agent) is not installed here, so this cannot be turned on. The Windows installer includes it.");
                } else if ui.button("Turn on").clicked() {
                    turn_on = true;
                }
            }
        });
        self.relays_text = Some(relays.clone());
        if turn_on {
            match link::parse_relays(&relays) {
                Ok(_) => {
                    db.set_setting("link_relays", relays.trim());
                    db.set_setting("link_enabled", "1");
                    self.start_link(db, ctx);
                }
                Err(e) => self.link_error = Some(e),
            }
        }
        if turn_off {
            db.set_setting("link_enabled", "0");
            self.stop_link();
        }
        if new_code {
            if let Some(l) = self.link.clone() {
                let (tx, rx) = mpsc::channel();
                self.code_job = Some(rx);
                self.code = None;
                let key = key.to_string();
                let c = ctx.clone();
                std::thread::spawn(move || {
                    let _ = tx.send(l.phone_code(&key));
                    c.request_repaint();
                });
            }
        }
        self.poll_link(db);
    }
}

fn draw_qr(ui: &mut egui::Ui, data: &str, size: f32) {
    let Ok(code) = qrcode::QrCode::new(data.as_bytes()) else {
        ui.label("Could not make a QR code.");
        return;
    };
    let n = code.width();
    let quiet = 2usize;
    let total = (n + quiet * 2) as f32;
    let cell = (size / total).floor().max(2.0);
    let side = cell * total;
    let (rect, _) = ui.allocate_exact_size(egui::vec2(side, side), egui::Sense::hover());
    let p = ui.painter();
    p.rect_filled(rect, 6.0, Color32::WHITE);
    let colors = code.to_colors();
    for y in 0..n {
        for x in 0..n {
            if colors[y * n + x] == qrcode::Color::Dark {
                let min = rect.min + egui::vec2((x + quiet) as f32 * cell, (y + quiet) as f32 * cell);
                p.rect_filled(egui::Rect::from_min_size(min, egui::vec2(cell, cell)), 0.0, Color32::BLACK);
            }
        }
    }
}
