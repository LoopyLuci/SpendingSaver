#![cfg_attr(all(not(debug_assertions), target_os = "windows"), windows_subsystem = "windows")]
//! SpendingSaver desktop. The window opens immediately: the only start-up work is opening a local
//! SQLite file and reading its rows, which takes milliseconds. There are no background services.

mod app;
mod assistant;
mod attachments;
mod bank;
mod calc;
mod db;
mod extras;
mod hub;
mod link;
mod model;
mod more;
mod sync_page;
mod tax;
mod ui;

fn main() -> eframe::Result<()> {
    let db = match db::Db::open(&db::default_path()) {
        Ok(d) => d,
        Err(e) => {
            eprintln!("Could not open the database ({e}); using a temporary in-memory one.");
            db::Db::memory().expect("in-memory database")
        }
    };
    let options = eframe::NativeOptions {
        viewport: eframe::egui::ViewportBuilder::default()
            .with_title("SpendingSaver")
            .with_inner_size([1180.0, 760.0])
            .with_min_inner_size([860.0, 560.0]),
        vsync: true,
        ..Default::default()
    };
    eframe::run_native("SpendingSaver", options, Box::new(move |cc| Ok(Box::new(app::App::new(cc, db)))))
}
