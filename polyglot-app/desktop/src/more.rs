//! Pages for recurring items, goals, debts, the on-device assistant and the optional bank link.
use crate::assistant;
use crate::bank;
use crate::calc::{money, parse_amount, parse_date};
use crate::db::Db;
use crate::extras::*;
use crate::model::*;
use crate::ui;
use chrono::NaiveDate;
use eframe::egui::{self, Color32, RichText};
use std::sync::{Arc, Mutex};

pub struct Ctx<'a> {
    pub db: &'a Db,
    pub data: &'a Data,
    pub currency: &'a str,
    pub today: NaiveDate,
    pub accent: Color32,
}

/// What a page tells the app after drawing: reload the data, and/or show a message.
#[derive(Default)]
pub struct Out {
    pub changed: bool,
    pub message: Option<(String, bool)>,
}

impl Out {
    fn say(&mut self, text: impl Into<String>, error: bool) {
        self.message = Some((text.into(), error));
    }
}

type Job = Arc<Mutex<Option<Result<String, String>>>>;

pub struct More {
    // recurring
    r_name: String,
    r_amount: String,
    r_income: bool,
    r_account: Option<String>,
    r_category: Option<String>,
    r_freq: String,
    r_next: Option<NaiveDate>,
    r_edit: Option<String>,
    // goals
    g_name: String,
    g_target: String,
    g_saved: String,
    g_due: Option<NaiveDate>,
    g_has_due: bool,
    g_edit: Option<String>,
    // debts
    d_name: String,
    d_balance: String,
    d_apr: String,
    d_min: String,
    d_edit: Option<String>,
    confirm_delete: Option<String>,
    // assistant
    chat: Vec<(bool, String)>,
    ask: String,
    // bank
    token: String,
    job: Option<Job>,
    busy: &'static str,
}

impl Default for More {
    fn default() -> Self {
        More {
            r_name: String::new(), r_amount: String::new(), r_income: false, r_account: None, r_category: None,
            r_freq: "monthly".into(), r_next: None, r_edit: None,
            g_name: String::new(), g_target: String::new(), g_saved: String::new(), g_due: None, g_has_due: false, g_edit: None,
            d_name: String::new(), d_balance: String::new(), d_apr: String::new(), d_min: String::new(), d_edit: None,
            confirm_delete: None,
            chat: vec![(false, "Hi. I run on this computer and only read your own data. Ask me about your balance, spending, budgets, recurring bills, goals or debts.".into())],
            ask: String::new(),
            token: String::new(), job: None, busy: "",
        }
    }
}

fn accounts(c: &Ctx) -> Vec<(String, String)> {
    c.data.accounts.iter().filter(|a| !a.deleted).map(|a| (a.id.clone(), a.name.clone())).collect()
}
fn categories(c: &Ctx) -> Vec<(String, String)> {
    c.data.categories.iter().filter(|a| !a.deleted).map(|a| (a.id.clone(), a.name.clone())).collect()
}

impl More {
    /// Two-click delete: the first click asks, the second removes.
    fn delete_button(&mut self, ui: &mut egui::Ui, id: &str) -> bool {
        let armed = self.confirm_delete.as_deref() == Some(id);
        if ui.button(if armed { "Really delete?" } else { "Delete" }).clicked() {
            if armed {
                self.confirm_delete = None;
                return true;
            }
            self.confirm_delete = Some(id.to_string());
        }
        false
    }

    // ------------------------------------------------------------------ recurring
    pub fn recurring(&mut self, ui: &mut egui::Ui, c: &Ctx) -> Out {
        let mut out = Out::default();
        let items = c.db.recurring();
        let total: f64 = items.iter().filter(|r| r.active).map(monthly_equivalent).sum();
        ui.columns(3, |col| {
            ui::stat(&mut col[0], "Active items", &items.iter().filter(|r| r.active).count().to_string(), None);
            ui::stat(&mut col[1], "Net per month (about)", &money(total, c.currency), Some(ui::money_color(total)));
            let due = items.iter().filter(|r| r.active).filter(|r| !due_dates(r, c.today).is_empty()).count();
            ui::stat(&mut col[2], "Due now", &due.to_string(), None);
        });
        ui.add_space(8.0);
        let (accts, cats) = (accounts(c), categories(c));
        ui::card(ui, |ui| {
            ui::heading(ui, if self.r_edit.is_some() { "Edit recurring item" } else { "Add a recurring bill or income" });
            egui::Grid::new("recform").num_columns(4).spacing([14.0, 8.0]).show(ui, |ui| {
                ui.label("Name");
                ui.add(egui::TextEdit::singleline(&mut self.r_name).desired_width(200.0).hint_text("Rent, Netflix, Paycheck"));
                ui.label("Amount");
                ui.horizontal(|ui| {
                    ui.add(egui::TextEdit::singleline(&mut self.r_amount).desired_width(90.0).hint_text("0.00"));
                    ui.selectable_value(&mut self.r_income, false, "Bill");
                    ui.selectable_value(&mut self.r_income, true, "Income");
                });
                ui.end_row();
                ui::select(ui, "recacct", "Account", &mut self.r_account, &accts, None);
                ui::select(ui, "reccat", "Category", &mut self.r_category, &cats, Some("None"));
                ui.end_row();
                ui.label("Repeats");
                egui::ComboBox::from_id_salt("recfreq").selected_text(self.r_freq.clone()).show_ui(ui, |ui| {
                    for f in FREQUENCIES {
                        ui.selectable_value(&mut self.r_freq, f.to_string(), f);
                    }
                });
                ui.label("Next date");
                let mut d = self.r_next.unwrap_or(c.today);
                ui.add(egui_extras::DatePickerButton::new(&mut d).id_salt("recdate"));
                self.r_next = Some(d);
                ui.end_row();
            });
            ui.horizontal(|ui| {
                if ui.button(if self.r_edit.is_some() { "Save changes" } else { "Add" }).clicked() {
                    match (parse_amount(&self.r_amount), self.r_account.clone(), self.r_name.trim().is_empty()) {
                        (_, _, true) => out.say("Enter a name.", true),
                        (None, _, _) => out.say("Enter the amount as a number.", true),
                        (_, None, _) => out.say("Choose an account.", true),
                        (Some(a), Some(acct), false) => {
                            let magnitude = a.abs();
                            let r = Recurring {
                                id: self.r_edit.clone().unwrap_or_else(new_id),
                                name: self.r_name.trim().to_string(),
                                amount: if self.r_income { magnitude } else { -magnitude },
                                account_id: acct,
                                category_id: self.r_category.clone(),
                                frequency: self.r_freq.clone(),
                                next_date: self.r_next.unwrap_or(c.today).format("%Y-%m-%d").to_string(),
                                active: self.r_edit.as_ref().and_then(|id| items.iter().find(|x| &x.id == id)).map(|x| x.active).unwrap_or(true),
                            };
                            match c.db.save_recurring(&r) {
                                Ok(_) => {
                                    out.say("Saved.", false);
                                    *self = More { chat: std::mem::take(&mut self.chat), token: std::mem::take(&mut self.token), ..More::default() };
                                }
                                Err(e) => out.say(format!("Could not save: {e}"), true),
                            }
                        }
                    }
                }
                if self.r_edit.is_some() && ui.button("Cancel").clicked() {
                    self.r_edit = None;
                    self.r_name.clear();
                    self.r_amount.clear();
                }
                if ui.button("Post what is due now").on_hover_text("Adds a transaction for each occurrence that has come due.").clicked() {
                    let n = c.db.post_due_recurring(c.today);
                    out.changed = true;
                    out.say(if n == 0 { "Nothing was due.".to_string() } else { format!("Posted {n} transaction{}.", if n == 1 { "" } else { "s" }) }, false);
                }
            });
        });
        ui.add_space(8.0);
        for r in &items {
            ui::card(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new(&r.name).strong().size(16.0));
                        ui.label(RichText::new(format!("{} · {} · next {} · {}", r.frequency, c.data.account_name(&r.account_id), r.next_date, c.data.category_name(&r.category_id))).weak());
                    });
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if self.delete_button(ui, &r.id) {
                            c.db.delete_recurring(&r.id);
                            out.say("Deleted.", false);
                        }
                        if ui.button("Edit").clicked() {
                            self.r_edit = Some(r.id.clone());
                            self.r_name = r.name.clone();
                            self.r_amount = format!("{:.2}", r.amount.abs());
                            self.r_income = r.amount > 0.0;
                            self.r_account = Some(r.account_id.clone());
                            self.r_category = r.category_id.clone();
                            self.r_freq = r.frequency.clone();
                            self.r_next = parse_date(&r.next_date);
                        }
                        let mut active = r.active;
                        if ui.checkbox(&mut active, "Active").changed() {
                            let mut n = r.clone();
                            n.active = active;
                            let _ = c.db.save_recurring(&n);
                        }
                        ui.label(RichText::new(money(r.amount, c.currency)).size(18.0).strong().color(ui::money_color(r.amount)));
                    });
                });
            });
        }
        if items.is_empty() {
            ui.label("Nothing recurring yet. Bills and income you add here are posted as transactions when they come due, including when the app starts.");
        }
        out
    }

    // ------------------------------------------------------------------ goals
    pub fn goals(&mut self, ui: &mut egui::Ui, c: &Ctx) -> Out {
        let mut out = Out::default();
        let items = c.db.goals();
        ui::card(ui, |ui| {
            ui::heading(ui, if self.g_edit.is_some() { "Edit goal" } else { "Add a savings goal" });
            egui::Grid::new("goalform").num_columns(4).spacing([14.0, 8.0]).show(ui, |ui| {
                ui.label("Name");
                ui.add(egui::TextEdit::singleline(&mut self.g_name).desired_width(200.0).hint_text("Emergency fund, Vacation"));
                ui.label("Target");
                ui.add(egui::TextEdit::singleline(&mut self.g_target).desired_width(110.0).hint_text("5000.00"));
                ui.end_row();
                ui.label("Saved so far");
                ui.add(egui::TextEdit::singleline(&mut self.g_saved).desired_width(110.0).hint_text("0.00"));
                ui.checkbox(&mut self.g_has_due, "Target date");
                if self.g_has_due {
                    let mut d = self.g_due.unwrap_or(c.today);
                    ui.add(egui_extras::DatePickerButton::new(&mut d).id_salt("goaldate"));
                    self.g_due = Some(d);
                } else {
                    ui.label("");
                }
                ui.end_row();
            });
            ui.horizontal(|ui| {
                if ui.button(if self.g_edit.is_some() { "Save changes" } else { "Add" }).clicked() {
                    let saved = if self.g_saved.trim().is_empty() { Some(0.0) } else { parse_amount(&self.g_saved) };
                    match (self.g_name.trim().is_empty(), parse_amount(&self.g_target), saved) {
                        (true, _, _) => out.say("Enter a name.", true),
                        (_, Some(t), _) if t <= 0.0 => out.say("The target must be more than zero.", true),
                        (_, None, _) => out.say("Enter the target as a number.", true),
                        (_, _, None) => out.say("Enter the saved amount as a number.", true),
                        (false, Some(t), Some(s)) => {
                            let g = Goal {
                                id: self.g_edit.clone().unwrap_or_else(new_id),
                                name: self.g_name.trim().to_string(),
                                target: t,
                                saved: s.max(0.0),
                                due: self.g_has_due.then(|| self.g_due.unwrap_or(c.today).format("%Y-%m-%d").to_string()),
                            };
                            match c.db.save_goal(&g) {
                                Ok(_) => {
                                    out.say("Saved.", false);
                                    self.g_edit = None;
                                    self.g_name.clear();
                                    self.g_target.clear();
                                    self.g_saved.clear();
                                    self.g_has_due = false;
                                }
                                Err(e) => out.say(format!("Could not save: {e}"), true),
                            }
                        }
                    }
                }
                if self.g_edit.is_some() && ui.button("Cancel").clicked() {
                    self.g_edit = None;
                    self.g_name.clear();
                    self.g_target.clear();
                    self.g_saved.clear();
                }
            });
        });
        ui.add_space(8.0);
        for g in &items {
            ui::card(ui, |ui| {
                let frac = if g.target > 0.0 { (g.saved / g.target).clamp(0.0, 1.0) as f32 } else { 0.0 };
                ui.horizontal(|ui| {
                    ui.label(RichText::new(&g.name).strong().size(16.0));
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if self.delete_button(ui, &g.id) {
                            c.db.delete_goal(&g.id);
                            out.say("Deleted.", false);
                        }
                        if ui.button("Edit").clicked() {
                            self.g_edit = Some(g.id.clone());
                            self.g_name = g.name.clone();
                            self.g_target = format!("{:.2}", g.target);
                            self.g_saved = format!("{:.2}", g.saved);
                            self.g_has_due = g.due.is_some();
                            self.g_due = g.due.as_deref().and_then(parse_date);
                        }
                        ui.menu_button("Add funds", |ui| {
                            for step in [25.0, 50.0, 100.0, 500.0] {
                                if ui.button(format!("+ {}", money(step, c.currency))).clicked() {
                                    let mut n = g.clone();
                                    n.saved += step;
                                    let _ = c.db.save_goal(&n);
                                    ui.close_menu();
                                }
                            }
                        });
                    });
                });
                ui.add(egui::ProgressBar::new(frac).fill(c.accent).text(format!("{} of {}  ({}%)", money(g.saved, c.currency), money(g.target, c.currency), (frac * 100.0) as i32)));
                if let Some(due) = &g.due {
                    let need = goal_monthly_need(g, c.today).map(|n| format!(" · save {} a month to make it", money(n, c.currency))).unwrap_or_default();
                    ui.label(RichText::new(format!("Target date {due}{need}")).weak());
                }
                if g.saved >= g.target {
                    ui.colored_label(ui::GOOD, "Goal reached 🎉");
                }
            });
        }
        if items.is_empty() {
            ui.label("No goals yet. Set a target and track what you have put aside.");
        }
        out
    }

    // ------------------------------------------------------------------ debts
    pub fn debts(&mut self, ui: &mut egui::Ui, c: &Ctx) -> Out {
        let mut out = Out::default();
        let items = c.db.debts();
        let owed: f64 = items.iter().map(|d| d.balance).sum();
        let monthly: f64 = items.iter().map(|d| d.min_payment).sum();
        ui.columns(2, |col| {
            ui::stat(&mut col[0], "Total owed", &money(owed, c.currency), Some(ui::BAD));
            ui::stat(&mut col[1], "Payments per month", &money(monthly, c.currency), None);
        });
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, if self.d_edit.is_some() { "Edit debt" } else { "Add a debt" });
            egui::Grid::new("debtform").num_columns(4).spacing([14.0, 8.0]).show(ui, |ui| {
                ui.label("Name");
                ui.add(egui::TextEdit::singleline(&mut self.d_name).desired_width(200.0).hint_text("Credit card, Car loan"));
                ui.label("Balance owed");
                ui.add(egui::TextEdit::singleline(&mut self.d_balance).desired_width(110.0).hint_text("0.00"));
                ui.end_row();
                ui.label("Interest (APR %)");
                ui.add(egui::TextEdit::singleline(&mut self.d_apr).desired_width(110.0).hint_text("0.0"));
                ui.label("Monthly payment");
                ui.add(egui::TextEdit::singleline(&mut self.d_min).desired_width(110.0).hint_text("0.00"));
                ui.end_row();
            });
            ui.horizontal(|ui| {
                if ui.button(if self.d_edit.is_some() { "Save changes" } else { "Add" }).clicked() {
                    let num = |s: &str| if s.trim().is_empty() { Some(0.0) } else { parse_amount(s) };
                    match (self.d_name.trim().is_empty(), parse_amount(&self.d_balance), num(&self.d_apr), num(&self.d_min)) {
                        (true, ..) => out.say("Enter a name.", true),
                        (_, None, ..) => out.say("Enter the balance as a number.", true),
                        (_, _, None, _) | (_, _, _, None) => out.say("Enter the interest and payment as numbers.", true),
                        (false, Some(b), Some(apr), Some(min)) if apr < 0.0 || min < 0.0 || b < 0.0 => {
                            let _ = (b, apr, min);
                            out.say("Amounts cannot be negative.", true)
                        }
                        (false, Some(b), Some(apr), Some(min)) => {
                            let d = Debt { id: self.d_edit.clone().unwrap_or_else(new_id), name: self.d_name.trim().to_string(), balance: b, apr, min_payment: min };
                            match c.db.save_debt(&d) {
                                Ok(_) => {
                                    out.say("Saved.", false);
                                    self.d_edit = None;
                                    self.d_name.clear();
                                    self.d_balance.clear();
                                    self.d_apr.clear();
                                    self.d_min.clear();
                                }
                                Err(e) => out.say(format!("Could not save: {e}"), true),
                            }
                        }
                    }
                }
                if self.d_edit.is_some() && ui.button("Cancel").clicked() {
                    self.d_edit = None;
                    self.d_name.clear();
                    self.d_balance.clear();
                    self.d_apr.clear();
                    self.d_min.clear();
                }
            });
        });
        ui.add_space(8.0);
        for d in &items {
            ui::card(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new(&d.name).strong().size(16.0));
                        ui.label(RichText::new(format!("{:.2}% APR · pays {} a month", d.apr, money(d.min_payment, c.currency))).weak());
                    });
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if self.delete_button(ui, &d.id) {
                            c.db.delete_debt(&d.id);
                            out.say("Deleted.", false);
                        }
                        if ui.button("Edit").clicked() {
                            self.d_edit = Some(d.id.clone());
                            self.d_name = d.name.clone();
                            self.d_balance = format!("{:.2}", d.balance);
                            self.d_apr = format!("{:.2}", d.apr);
                            self.d_min = format!("{:.2}", d.min_payment);
                        }
                        ui.label(RichText::new(money(d.balance, c.currency)).size(18.0).strong().color(ui::BAD));
                    });
                });
                match (payoff_months(d.balance, d.apr, d.min_payment), payoff_interest(d.balance, d.apr, d.min_payment)) {
                    (Some(0), _) => {
                        ui.colored_label(ui::GOOD, "Paid off");
                    }
                    (Some(n), Some(i)) => {
                        ui.label(format!("Paid off in {} year{} {} month{} · total interest {}", n / 12, if n / 12 == 1 { "" } else { "s" }, n % 12, if n % 12 == 1 { "" } else { "s" }, money(i, c.currency)));
                    }
                    _ => {
                        ui.colored_label(ui::BAD, "This payment does not cover the monthly interest, so the balance will not go down.");
                    }
                }
            });
        }
        if items.is_empty() {
            ui.label("No debts recorded. Add loans and cards to see how long they take to pay off.");
        }
        out
    }

    // ------------------------------------------------------------------ assistant
    pub fn assistant(&mut self, ui: &mut egui::Ui, c: &Ctx) {
        ui::card(ui, |ui| {
            ui.label(RichText::new("🔒 Runs entirely on this computer. It reads your data here and sends nothing anywhere.").weak());
        });
        ui.add_space(6.0);
        let (recurring, goals, debts) = (c.db.recurring(), c.db.goals(), c.db.debts());
        let mut send: Option<String> = None;
        ui.horizontal_wrapped(|ui| {
            for s in assistant::SUGGESTIONS {
                if ui.small_button(s).clicked() {
                    send = Some(s.to_string());
                }
            }
        });
        ui.add_space(6.0);
        let height = (ui.available_height() - 52.0).max(120.0);
        egui::ScrollArea::vertical().max_height(height).stick_to_bottom(true).auto_shrink([false, false]).show(ui, |ui| {
            for (mine, text) in &self.chat {
                ui.horizontal(|ui| {
                    if *mine {
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::TOP), |ui| {
                            egui::Frame::none().fill(c.accent).rounding(10.0).inner_margin(10.0).show(ui, |ui| {
                                ui.label(RichText::new(text).color(Color32::WHITE));
                            });
                        });
                    } else {
                        egui::Frame::none().fill(ui.visuals().faint_bg_color).rounding(10.0).inner_margin(10.0).show(ui, |ui| {
                            ui.label(text);
                        });
                    }
                });
                ui.add_space(4.0);
            }
        });
        ui.horizontal(|ui| {
            let r = ui.add(egui::TextEdit::singleline(&mut self.ask).desired_width(ui.available_width() - 80.0).hint_text("Ask about your money…"));
            let enter = r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
            if (ui.button("Ask").clicked() || enter) && !self.ask.trim().is_empty() {
                send = Some(std::mem::take(&mut self.ask));
                r.request_focus();
            }
        });
        if let Some(q) = send {
            let snap = assistant::Snapshot { data: c.data, recurring: &recurring, goals: &goals, debts: &debts, currency: c.currency, today: c.today };
            let a = assistant::answer(&q, &snap);
            self.chat.push((true, q));
            self.chat.push((false, a));
        }
    }

    // ------------------------------------------------------------------ bank link
    fn poll(&mut self, ctx: &egui::Context, out: &mut Out) {
        let Some(job) = self.job.clone() else { return };
        let done = job.lock().ok().and_then(|mut g| g.take());
        match done {
            Some(Ok(m)) => {
                self.job = None;
                self.busy = "";
                out.changed = true;
                out.say(m, false);
            }
            Some(Err(e)) => {
                self.job = None;
                self.busy = "";
                out.say(e, true);
            }
            None => ctx.request_repaint_after(std::time::Duration::from_millis(150)),
        }
    }

    pub fn bank(&mut self, ui: &mut egui::Ui, c: &Ctx, data_dir: &std::path::Path) -> Out {
        let mut out = Out::default();
        self.poll(ui.ctx(), &mut out);
        let connected = bank::load_access_url(data_dir).is_some();
        ui::card(ui, |ui| {
            ui.label(
                "Optional. Link a US bank through SimpleFIN Bridge, a service you subscribe to yourself. The link is read-only: it can fetch \
                 balances and transactions but never move money. Data goes straight from SimpleFIN to this PC. You can disconnect here and disable \
                 access at SimpleFIN at any time. Everything works without this.",
            );
            ui.hyperlink_to("About SimpleFIN Bridge", "https://beta-bridge.simplefin.org/");
        });
        ui.add_space(8.0);
        if !connected {
            ui::card(ui, |ui| {
                ui::heading(ui, "Connect");
                ui.label("At SimpleFIN Bridge create a setup token, then paste it here. It works once.");
                ui.add(egui::TextEdit::singleline(&mut self.token).password(true).desired_width(420.0).hint_text("Setup token"));
                ui.add_enabled_ui(self.job.is_none() && !self.token.trim().is_empty(), |ui| {
                    if ui.button("Connect").clicked() {
                        let token = std::mem::take(&mut self.token);
                        let dir = data_dir.to_path_buf();
                        let job: Job = Arc::new(Mutex::new(None));
                        self.job = Some(job.clone());
                        self.busy = "Connecting…";
                        std::thread::spawn(move || {
                            let r = bank::claim(&token).and_then(|url| bank::save_access_url(&dir, &url).map_err(|_| "Could not save the credential.".to_string())).map(|_| "Connected. Press Sync now to fetch your accounts.".to_string());
                            if let Ok(mut g) = job.lock() {
                                *g = Some(r);
                            }
                        });
                    }
                });
            });
        } else {
            ui::card(ui, |ui| {
                ui::heading(ui, "Connected");
                let last = c.db.get_setting("simplefin_last_sync").and_then(|s| s.parse::<i64>().ok()).and_then(|s| chrono::DateTime::from_timestamp(s, 0)).map(|d| d.with_timezone(&chrono::Local).format("%Y-%m-%d %H:%M").to_string()).unwrap_or_else(|| "never".into());
                ui.label(format!("Last sync: {last}"));
                if let Some(r) = c.db.get_setting("simplefin_last_result").filter(|s| !s.is_empty()) {
                    ui.label(RichText::new(r).weak());
                }
                if let Some(e) = c.db.get_setting("simplefin_last_error").filter(|s| !s.is_empty()) {
                    ui.colored_label(ui::BAD, e);
                }
                ui.horizontal(|ui| {
                    ui.add_enabled_ui(self.job.is_none(), |ui| {
                        if ui.button("Sync now").on_hover_text("SimpleFIN allows about one update an hour.").clicked() {
                            let dir = data_dir.to_path_buf();
                            let job: Job = Arc::new(Mutex::new(None));
                            self.job = Some(job.clone());
                            self.busy = "Syncing…";
                            let file = c.db.file();
                            std::thread::spawn(move || {
                                let r = match file.and_then(|f| Db::open(&f).ok()) {
                                    Some(db) => bank::sync(&db, &dir).map(|(st, msgs)| {
                                        let extra = if msgs.is_empty() { String::new() } else { format!(" ({})", msgs.join("; ")) };
                                        format!("Synced: {} new accounts, {} new transactions{extra}.", st.accounts, st.added)
                                    }),
                                    None => Err("Could not open the database.".into()),
                                };
                                if let Ok(mut g) = job.lock() {
                                    *g = Some(r);
                                }
                            });
                        }
                    });
                    if self.confirm_delete.as_deref() == Some("bank") {
                        if ui.button("Really disconnect?").clicked() {
                            bank::forget(data_dir);
                            self.confirm_delete = None;
                            out.say("Disconnected. Imported accounts and transactions stay. Also disable the access at SimpleFIN Bridge.", false);
                        }
                    } else if ui.button("Disconnect").clicked() {
                        self.confirm_delete = Some("bank".into());
                    }
                });
            });
        }
        if !self.busy.is_empty() {
            ui.horizontal(|ui| {
                ui.spinner();
                ui.label(self.busy);
            });
        }
        ui.add_space(6.0);
        ui.label(RichText::new("The credential is kept in a file in this app's data folder (not in the database, backups or phone sync). Imported transactions are ordinary ones and sync like the rest if you turn phone sync on.").small().weak());
        out
    }
}
