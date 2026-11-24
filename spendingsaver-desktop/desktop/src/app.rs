//! The application: navigation, state and every page. All work happens on the UI thread in
//! microseconds (SQLite in WAL mode, in-memory lists), so there is nothing to wait for.
use crate::calc::{self, money, parse_amount, parse_date};
use crate::db::{self, Db};
use crate::model::*;
use crate::tax;
use crate::ui::{self, ThemeMode};
use chrono::{Local, NaiveDate};
use eframe::egui::{self, Align2, Color32, RichText};
use egui_extras::{Column, TableBuilder};
use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant};

#[derive(Clone, Copy, PartialEq, Eq)]
enum Page {
    Dashboard,
    Transactions,
    Accounts,
    Budgets,
    Categories,
    Recurring,
    Goals,
    Debts,
    Assistant,
    Tax,
    Bank,
    Data,
    Sync,
    Settings,
}

const NAV: [(Page, &str, &str); 14] = [
    (Page::Dashboard, "🏠", "Dashboard"),
    (Page::Transactions, "📝", "Transactions"),
    (Page::Accounts, "🏦", "Accounts"),
    (Page::Budgets, "🐷", "Budgets"),
    (Page::Categories, "🏷", "Categories"),
    (Page::Recurring, "🔁", "Recurring"),
    (Page::Goals, "🎯", "Goals"),
    (Page::Debts, "💳", "Debts"),
    (Page::Assistant, "💬", "Assistant"),
    (Page::Tax, "📑", "Tax"),
    (Page::Bank, "🔗", "Bank Link"),
    (Page::Data, "💾", "Import / Export"),
    (Page::Sync, "📱", "Phone Sync"),
    (Page::Settings, "⚙", "Settings"),
];

const ACCOUNT_TYPES: [&str; 9] = ["Checking", "Savings", "Credit card", "Cash", "Investment", "Retirement", "Loan", "Mortgage", "Other"];
const CURRENCIES: [&str; 20] = [
    "USD", "EUR", "GBP", "CAD", "AUD", "NZD", "JPY", "CNY", "INR", "CHF", "SEK", "NOK", "DKK", "PLN", "BRL", "MXN", "ZAR", "SGD", "HKD", "KRW",
];
const CATEGORY_IDEAS: [&str; 17] = [
    "Food", "Groceries", "Dining out", "Housing", "Utilities", "Transportation", "Fuel", "Healthcare", "Insurance",
    "Entertainment", "Subscriptions", "Shopping", "Travel", "Education", "Gifts", "Savings", "Income",
];
const COLORS: [(&str, &str); 10] = [
    ("Teal", "#009688"), ("Blue", "#2196F3"), ("Green", "#4CAF50"), ("Purple", "#9C27B0"), ("Orange", "#FF9800"),
    ("Red", "#F44336"), ("Pink", "#E91E63"), ("Yellow", "#FFC107"), ("Brown", "#795548"), ("Grey", "#9E9E9E"),
];

#[derive(Default)]
struct TxForm {
    editing: Option<Transaction>,
    description: String,
    amount: String,
    expense: bool,
    date: Option<NaiveDate>,
    account: Option<String>,
    category: Option<String>,
    notes: String,
}

impl TxForm {
    fn blank() -> TxForm {
        TxForm { expense: true, ..Default::default() }
    }
}

#[derive(Default)]
struct AcctForm {
    editing: Option<String>,
    name: String,
    kind: String,
    balance: String,
    currency: String,
}

#[derive(Default)]
struct CatForm {
    editing: Option<String>,
    name: String,
    color: String,
}

#[derive(Default)]
struct BudgetForm {
    category: Option<String>,
    amount: String,
    period: String,
}

#[derive(Serialize, Deserialize, Default, Clone)]
#[serde(default)]
struct TaxForm {
    wages: String,
    se: String,
    other: String,
    adjustments: String,
    itemized: String,
    use_itemized: bool,
    children: u32,
    withheld: String,
    estimated: String,
    state: String,
    state_rate: String,
    year: i32,
    filing: u8,
}

enum Act {
    SaveAccount(Account),
    DeleteAccount(String),
    SaveCategory(Category),
    DeleteCategory(String),
    SaveTx(Transaction),
    DeleteTx(String),
    SaveBudget(Budget),
    DeleteBudget(String),
}

struct Confirm {
    text: String,
    act: Act,
}

#[derive(Default)]
struct Derived {
    today: Option<NaiveDate>,
    net_worth: f64,
    month: calc::Totals,
    by_category: Vec<(String, f64)>,
    monthly: Vec<(String, calc::Totals)>,
    descriptions: Vec<String>,
}

pub struct App {
    db: Db,
    data: Data,
    derived: Derived,
    page: Page,
    theme: ThemeMode,
    accent_name: String,
    currency: String,
    applied: Option<(bool, String)>,
    toast: Option<(String, bool, Instant)>,
    confirm: Option<Confirm>,
    tx_form: TxForm,
    tx_search: String,
    tx_filter_account: Option<String>,
    tx_visible: Vec<usize>,
    tx_key: (String, Option<String>, usize),
    acct_form: AcctForm,
    cat_form: CatForm,
    budget_form: BudgetForm,
    tax_form: TaxForm,
    csv_account: Option<String>,
    restore_pending: Option<Backup>,
    sync: crate::sync_page::SyncPage,
    more: crate::more::More,
    attach: crate::attachments::Panel,
}

fn err(msg: &str) -> Option<(String, bool, Instant)> {
    Some((msg.to_string(), true, Instant::now()))
}

impl App {
    pub fn new(cc: &eframe::CreationContext<'_>, db: Db) -> App {
        let theme = ThemeMode::from_str(&db.get_setting("theme").unwrap_or_default());
        let accent_name = db.get_setting("accent").unwrap_or_else(|| "Teal".into());
        let currency = db.get_setting("currency").unwrap_or_else(|| "USD".into());
        let tax_form: TaxForm = db.get_setting("tax_form").and_then(|s| serde_json::from_str(&s).ok()).unwrap_or_default();
        let mut app = App {
            data: Data::default(),
            derived: Derived::default(),
            db,
            page: Page::Dashboard,
            theme,
            accent_name,
            currency: currency.clone(),
            applied: None,
            toast: None,
            confirm: None,
            tx_form: TxForm::blank(),
            tx_search: String::new(),
            tx_filter_account: None,
            tx_visible: Vec::new(),
            tx_key: (String::new(), None, usize::MAX),
            acct_form: AcctForm { kind: "Checking".into(), currency, ..Default::default() },
            cat_form: CatForm::default(),
            budget_form: BudgetForm { period: "monthly".into(), ..Default::default() },
            tax_form,
            csv_account: None,
            restore_pending: None,
            sync: crate::sync_page::SyncPage::default(),
            more: crate::more::More::default(),
            attach: crate::attachments::Panel::default(),
        };
        // Recurring items that came due while the app was closed are posted before the first screen is drawn.
        app.db.post_due_recurring(Local::now().date_naive());
        // Optional: open on a given page (used to take the README screenshots), e.g. SPENDINGSAVER_PAGE=goals.
        if let Ok(name) = std::env::var("SPENDINGSAVER_PAGE") {
            let name = name.to_lowercase();
            if let Some(n) = NAV.iter().find(|n| n.2.to_lowercase().replace([' ', '/'], "").starts_with(&name)) {
                app.page = n.0;
            }
        }
        app.reload();
        app.sync.autostart(&app.db, &cc.egui_ctx);
        app.sync_theme(&cc.egui_ctx);
        app
    }

    fn reload(&mut self) {
        match self.db.load() {
            Ok(d) => self.data = d,
            Err(e) => self.say(format!("Could not read the database: {e}"), true),
        }
        let today = Local::now().date_naive();
        let (from, to) = calc::window("monthly", today);
        let mut descs: Vec<String> = self.data.transactions.iter().map(|t| t.description.clone()).collect();
        descs.sort();
        descs.dedup();
        self.derived = Derived {
            today: Some(today),
            net_worth: calc::net_worth(&self.data),
            month: calc::totals(&self.data.transactions, from, to),
            by_category: calc::by_category(&self.data, from, to),
            monthly: calc::monthly(&self.data.transactions, today, 6),
            descriptions: descs,
        };
        self.tx_key.2 = usize::MAX; // force the transaction list to refilter
        self.attach.refresh_marks(&self.db);
    }

    fn today(&self) -> NaiveDate {
        self.derived.today.unwrap_or_else(|| Local::now().date_naive())
    }

    fn say(&mut self, text: impl Into<String>, error: bool) {
        self.toast = Some((text.into(), error, Instant::now()));
    }

    fn sync_theme(&mut self, ctx: &egui::Context) {
        let dark = match self.theme {
            ThemeMode::Dark => true,
            ThemeMode::Light => false,
            ThemeMode::System => ctx.style().visuals.dark_mode,
        };
        let key = (dark, self.accent_name.clone());
        if self.applied.as_ref() != Some(&key) {
            ui::apply(ctx, dark, ui::accent(&self.accent_name));
            self.applied = Some(key);
        }
    }

    fn accent(&self) -> Color32 {
        ui::accent(&self.accent_name)
    }

    fn fmt(&self, v: f64) -> String {
        money(v, &self.currency)
    }

    fn run(&mut self, act: Act) {
        let r = match act {
            Act::SaveAccount(a) => self.db.save_account(&a),
            Act::DeleteAccount(id) => {
                if self.data.transactions.iter().any(|t| t.account_id == id) {
                    self.say("Delete or move this account's transactions first.", true);
                    return;
                }
                match self.data.accounts.iter().find(|a| a.id == id).cloned() {
                    Some(mut a) => {
                        a.deleted = true;
                        self.db.save_account(&a)
                    }
                    None => Ok(()),
                }
            }
            Act::SaveCategory(c) => self.db.save_category(&c),
            Act::DeleteCategory(id) => match self.data.categories.iter().find(|c| c.id == id).cloned() {
                Some(mut c) => {
                    c.deleted = true;
                    self.db.save_category(&c)
                }
                None => Ok(()),
            },
            Act::SaveTx(t) => self.db.save_transaction(&t),
            Act::DeleteTx(id) => match self.data.transactions.iter().find(|t| t.id == id).cloned() {
                Some(mut t) => {
                    t.deleted = true;
                    self.db.save_transaction(&t)
                }
                None => Ok(()),
            },
            Act::SaveBudget(b) => self.db.save_budget(&b),
            Act::DeleteBudget(id) => match self.data.budgets.iter().find(|b| b.id == id).cloned() {
                Some(mut b) => {
                    b.deleted = true;
                    self.db.save_budget(&b)
                }
                None => Ok(()),
            },
        };
        match r {
            Ok(()) => {
                self.reload();
                self.say("Saved.", false)
            }
            Err(e) => self.say(format!("Could not save: {e}"), true),
        }
    }

    fn account_options(&self) -> Vec<(String, String)> {
        self.data.accounts.iter().map(|a| (a.id.clone(), a.name.clone())).collect()
    }

    fn category_options(&self) -> Vec<(String, String)> {
        self.data.categories.iter().map(|c| (c.id.clone(), c.name.clone())).collect()
    }

    // ------------------------------------------------------------------ pages

    fn dashboard(&mut self, ui: &mut egui::Ui) {
        let acc = self.accent();
        let cur = self.currency.clone();
        ui::hero(
            ui,
            acc,
            "Net worth",
            &money(self.derived.net_worth, &cur),
            &format!("This month: income {} · spending {}", money(self.derived.month.income, &cur), money(self.derived.month.expenses, &cur)),
        );
        ui.add_space(8.0);
        ui.columns(4, |c| {
            ui::stat(&mut c[0], "Income this month", &money(self.derived.month.income, &cur), Some(ui::GOOD));
            ui::stat(&mut c[1], "Spending this month", &money(self.derived.month.expenses, &cur), Some(ui::BAD));
            ui::stat(&mut c[2], "Accounts", &self.data.accounts.len().to_string(), None);
            ui::stat(&mut c[3], "Transactions", &self.data.transactions.len().to_string(), None);
        });
        ui.add_space(8.0);
        ui.columns(2, |c| {
            ui::card(&mut c[0], |ui| {
                ui::heading(ui, "Spending by category (this month)");
                if self.derived.by_category.is_empty() {
                    ui.label("No spending recorded this month.");
                } else {
                    let rows: Vec<_> = self.derived.by_category.iter().take(8).cloned().collect();
                    ui::bars(ui, &rows, acc, |v| money(v, &cur));
                }
            });
            ui::card(&mut c[1], |ui| {
                ui::heading(ui, "Last 6 months");
                let max = self.derived.monthly.iter().map(|m| m.1.income.max(m.1.expenses)).fold(0.0_f64, f64::max).max(1.0);
                ui.horizontal(|ui| {
                    for (name, t) in &self.derived.monthly {
                        ui.vertical(|ui| {
                            ui.set_width(48.0);
                            let h = 110.0_f32;
                            let (rect, resp) = ui.allocate_exact_size(egui::vec2(44.0, h), egui::Sense::hover());
                            let w = 18.0;
                            let hi = (h as f64 * t.income / max) as f32;
                            let he = (h as f64 * t.expenses / max) as f32;
                            ui.painter().rect_filled(
                                egui::Rect::from_min_max(egui::pos2(rect.left(), rect.bottom() - hi), egui::pos2(rect.left() + w, rect.bottom())),
                                3.0,
                                ui::GOOD,
                            );
                            ui.painter().rect_filled(
                                egui::Rect::from_min_max(egui::pos2(rect.left() + w + 4.0, rect.bottom() - he), egui::pos2(rect.left() + 2.0 * w + 4.0, rect.bottom())),
                                3.0,
                                ui::BAD,
                            );
                            resp.on_hover_text(format!("{name}: income {} · spending {}", money(t.income, &cur), money(t.expenses, &cur)));
                            ui.label(RichText::new(name).small());
                        });
                    }
                });
                ui.horizontal(|ui| {
                    ui.colored_label(ui::GOOD, "■ income");
                    ui.colored_label(ui::BAD, "■ spending");
                });
            });
        });
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, "Budgets");
            if self.data.budgets.is_empty() {
                ui.label("No budgets yet. Add one on the Budgets page.");
            }
            for b in &self.data.budgets {
                let spent = calc::spent(&self.data.transactions, &b.category_id, &b.period, self.today());
                let frac = (spent / b.amount.max(0.01)) as f32;
                ui.horizontal(|ui| {
                    ui.add_sized([150.0, 20.0], egui::Label::new(self.data.category_name(&Some(b.category_id.clone()))).truncate());
                    let bar = egui::ProgressBar::new(frac.min(1.0)).desired_width((ui.available_width() - 260.0).max(60.0)).fill(if frac > 1.0 { ui::BAD } else { acc });
                    ui.add(bar);
                    ui.label(format!("{} of {} ({})", money(spent, &cur), money(b.amount, &cur), b.period));
                });
            }
        });
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, "Recent activity");
            if self.data.transactions.is_empty() {
                ui.label("Nothing yet. Add a transaction to get started.");
            }
            for t in self.data.transactions.iter().take(8) {
                ui.horizontal(|ui| {
                    ui.label(RichText::new(&t.date).weak());
                    ui.add_sized([260.0, 20.0], egui::Label::new(&t.description).truncate());
                    ui.label(RichText::new(self.data.category_name(&t.category_id)).weak());
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        ui.label(RichText::new(money(t.amount, &cur)).color(ui::money_color(t.amount)).strong());
                    });
                });
            }
        });
    }

    fn transactions(&mut self, ui: &mut egui::Ui) {
        let mut ask: Option<Confirm> = None;
        let mut submit = false;
        let mut cancel = false;
        let accounts = self.account_options();
        let cats = self.category_options();
        let editing = self.tx_form.editing.is_some();
        let cur = self.currency.clone();
        ui::card(ui, |ui| {
            ui::heading(ui, if editing { "Edit transaction" } else { "Add a transaction" });
            if accounts.is_empty() {
                ui.label("Add an account first (Accounts page).");
                return;
            }
            egui::Grid::new("txform").num_columns(4).spacing([14.0, 8.0]).show(ui, |ui| {
                ui.label("Description");
                ui::combo_edit(ui, "desc", &mut self.tx_form.description, &self.derived.descriptions, 230.0);
                ui.label("Amount");
                ui.horizontal(|ui| {
                    ui.add(egui::TextEdit::singleline(&mut self.tx_form.amount).desired_width(110.0).hint_text("0.00"));
                    ui.selectable_value(&mut self.tx_form.expense, true, "Expense");
                    ui.selectable_value(&mut self.tx_form.expense, false, "Income");
                });
                ui.end_row();
                ui.label("Date");
                let mut d = self.tx_form.date.unwrap_or_else(|| Local::now().date_naive());
                ui.add(egui_extras::DatePickerButton::new(&mut d));
                self.tx_form.date = Some(d);
                ui.label("Account");
                ui::select(ui, "txacct", "", &mut self.tx_form.account, &accounts, None);
                ui.end_row();
                ui.label("Category");
                ui::select(ui, "txcat", "", &mut self.tx_form.category, &cats, Some("Auto categorize"));
                ui.label("Notes");
                ui.add(egui::TextEdit::singleline(&mut self.tx_form.notes).desired_width(230.0));
                ui.end_row();
            });
            ui.horizontal(|ui| {
                if ui.button(if editing { "Save changes" } else { "Add transaction" }).clicked() {
                    submit = true;
                }
                if editing && ui.button("Cancel").clicked() {
                    cancel = true;
                }
            });
            if let Some(t) = self.tx_form.editing.clone() {
                self.attach.show(ui, &self.db, &t.id);
            }
        });
        if cancel {
            self.tx_form = TxForm::blank();
        }
        if submit {
            match self.build_tx() {
                Ok(t) => {
                    self.run(Act::SaveTx(t));
                    self.tx_form = TxForm::blank();
                }
                Err(m) => self.toast = err(&m),
            }
        }
        ui.add_space(8.0);
        ui.horizontal(|ui| {
            ui.label("🔍");
            ui.add(egui::TextEdit::singleline(&mut self.tx_search).hint_text("Search description, notes or category").desired_width(300.0));
            ui::select(ui, "txfilter", "Account", &mut self.tx_filter_account, &accounts, Some("All accounts"));
            if !self.tx_search.is_empty() && ui.button("Clear").clicked() {
                self.tx_search.clear();
            }
        });
        // Refilter only when something relevant changed.
        let key = (self.tx_search.clone(), self.tx_filter_account.clone(), self.data.transactions.len());
        if key != self.tx_key {
            let q = self.tx_search.to_lowercase();
            self.tx_visible = self
                .data
                .transactions
                .iter()
                .enumerate()
                .filter(|(_, t)| {
                    (self.tx_filter_account.is_none() || self.tx_filter_account.as_deref() == Some(t.account_id.as_str()))
                        && (q.is_empty()
                            || t.description.to_lowercase().contains(&q)
                            || t.notes.as_deref().unwrap_or("").to_lowercase().contains(&q)
                            || self.data.category_name(&t.category_id).to_lowercase().contains(&q))
                })
                .map(|(i, _)| i)
                .collect();
            self.tx_key = key;
        }
        ui.label(RichText::new(format!("{} transactions", self.tx_visible.len())).weak());
        let avail = ui.available_height();
        let mut edit: Option<TxForm> = None;
        TableBuilder::new(ui)
            .striped(true)
            .max_scroll_height(avail)
            .column(Column::exact(92.0))
            .column(Column::remainder().at_least(200.0).clip(true))
            .column(Column::exact(140.0))
            .column(Column::exact(130.0))
            .column(Column::exact(110.0))
            .column(Column::exact(120.0))
            .header(24.0, |mut h| {
                for t in ["Date", "Description", "Category", "Account", "Amount", ""] {
                    h.col(|ui| {
                        ui.strong(t);
                    });
                }
            })
            .body(|body| {
                body.rows(28.0, self.tx_visible.len(), |mut row| {
                    let t = &self.data.transactions[self.tx_visible[row.index()]];
                    row.col(|ui| {
                        ui.label(&t.date);
                    });
                    row.col(|ui| {
                        if self.attach.marks.contains(&t.id) {
                            ui.label(format!("{}  (file attached)", t.description));
                        } else {
                            ui.label(&t.description);
                        }
                    });
                    row.col(|ui| {
                        ui.label(RichText::new(self.data.category_name(&t.category_id)).weak());
                    });
                    row.col(|ui| {
                        ui.label(RichText::new(self.data.account_name(&t.account_id)).weak());
                    });
                    row.col(|ui| {
                        ui.label(RichText::new(money(t.amount, &cur)).color(ui::money_color(t.amount)).strong());
                    });
                    row.col(|ui| {
                        ui.horizontal(|ui| {
                            if ui.small_button("Edit").clicked() {
                                edit = Some(TxForm {
                                    editing: Some(t.clone()),
                                    description: t.description.clone(),
                                    amount: format!("{:.2}", t.amount.abs()),
                                    expense: t.amount < 0.0,
                                    date: parse_date(&t.date),
                                    account: Some(t.account_id.clone()),
                                    category: t.category_id.clone(),
                                    notes: t.notes.clone().unwrap_or_default(),
                                });
                            }
                            if ui.small_button("Delete").clicked() {
                                ask = Some(Confirm { text: format!("Delete “{}”?", t.description), act: Act::DeleteTx(t.id.clone()) });
                            }
                        });
                    });
                });
            });
        if let Some(f) = edit {
            self.tx_form = f;
        }
        if let Some(c) = ask {
            self.confirm = Some(c);
        }
    }

    fn build_tx(&self) -> Result<Transaction, String> {
        let f = &self.tx_form;
        let description = f.description.trim().to_string();
        if description.is_empty() {
            return Err("Enter a description.".into());
        }
        let amount = parse_amount(&f.amount).ok_or("Enter an amount like 12.50.")?;
        if amount == 0.0 {
            return Err("The amount cannot be zero.".into());
        }
        let account = f.account.clone().ok_or("Choose an account.")?;
        let date = f.date.unwrap_or_else(|| Local::now().date_naive());
        let category = f.category.clone().or_else(|| calc::auto_category(&description, &self.data.categories).map(|c| c.id.clone()));
        let signed = if f.expense { -amount.abs() } else { amount.abs() };
        let base = f.editing.clone();
        Ok(Transaction {
            id: base.as_ref().map(|b| b.id.clone()).unwrap_or_else(new_id),
            account_id: account,
            amount: signed,
            description,
            category_id: category,
            date: date.format("%Y-%m-%d").to_string(),
            notes: Some(f.notes.trim().to_string()).filter(|n| !n.is_empty()),
            created_at: base.map(|b| b.created_at).unwrap_or_else(now_ms),
            deleted: false,
            updated_at: 0,
        })
    }

    fn accounts(&mut self, ui: &mut egui::Ui) {
        let mut submit = false;
        let mut cancel = false;
        let mut ask: Option<Confirm> = None;
        let mut edit: Option<AcctForm> = None;
        let types: Vec<String> = ACCOUNT_TYPES.iter().map(|s| s.to_string()).collect();
        let currencies: Vec<String> = CURRENCIES.iter().map(|s| s.to_string()).collect();
        let editing = self.acct_form.editing.is_some();
        ui::card(ui, |ui| {
            ui::heading(ui, if editing { "Edit account" } else { "Add an account" });
            egui::Grid::new("acctform").num_columns(4).spacing([14.0, 8.0]).show(ui, |ui| {
                ui.label("Name");
                ui.add(egui::TextEdit::singleline(&mut self.acct_form.name).desired_width(230.0).hint_text("e.g. Everyday checking"));
                ui.label("Type");
                ui::combo_edit(ui, "atype", &mut self.acct_form.kind, &types, 150.0);
                ui.end_row();
                ui.label("Balance");
                ui.add(egui::TextEdit::singleline(&mut self.acct_form.balance).desired_width(150.0).hint_text("0.00"));
                ui.label("Currency");
                ui::combo_edit(ui, "acur", &mut self.acct_form.currency, &currencies, 90.0);
                ui.end_row();
            });
            ui.horizontal(|ui| {
                if ui.button(if editing { "Save changes" } else { "Add account" }).clicked() {
                    submit = true;
                }
                if editing && ui.button("Cancel").clicked() {
                    cancel = true;
                }
            });
        });
        if cancel {
            self.acct_form = AcctForm { kind: "Checking".into(), currency: self.currency.clone(), ..Default::default() };
        }
        if submit {
            let name = self.acct_form.name.trim().to_string();
            let bal = if self.acct_form.balance.trim().is_empty() { Some(0.0) } else { parse_amount(&self.acct_form.balance) };
            if name.is_empty() {
                self.toast = err("Enter an account name.");
            } else if bal.is_none() {
                self.toast = err("Enter the balance as a number.");
            } else {
                let a = Account {
                    id: self.acct_form.editing.clone().unwrap_or_else(new_id),
                    name,
                    kind: self.acct_form.kind.trim().to_lowercase(),
                    balance: bal.unwrap_or(0.0),
                    currency: self.acct_form.currency.trim().to_uppercase(),
                    deleted: false,
                    updated_at: 0,
                };
                self.run(Act::SaveAccount(a));
                self.acct_form = AcctForm { kind: "Checking".into(), currency: self.currency.clone(), ..Default::default() };
            }
        }
        ui.add_space(8.0);
        ui.label(RichText::new(format!("Total: {}", self.fmt(self.derived.net_worth))).strong());
        for a in &self.data.accounts {
            ui::card(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(RichText::new(&a.name).strong().size(16.0));
                        ui.label(RichText::new(format!("{} · {}", a.kind, a.currency)).weak());
                    });
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui.button("Delete").clicked() {
                            ask = Some(Confirm { text: format!("Delete the account “{}”?", a.name), act: Act::DeleteAccount(a.id.clone()) });
                        }
                        if ui.button("Edit").clicked() {
                            edit = Some(AcctForm {
                                editing: Some(a.id.clone()),
                                name: a.name.clone(),
                                kind: a.kind.clone(),
                                balance: format!("{:.2}", a.balance),
                                currency: a.currency.clone(),
                            });
                        }
                        ui.label(RichText::new(money(a.balance, &a.currency)).size(18.0).strong().color(ui::money_color(a.balance)));
                    });
                });
            });
        }
        if self.data.accounts.is_empty() {
            ui.label("No accounts yet. Add your first one above.");
        }
        if let Some(f) = edit {
            self.acct_form = f;
        }
        if let Some(c) = ask {
            self.confirm = Some(c);
        }
    }

    fn budgets(&mut self, ui: &mut egui::Ui) {
        let mut submit = false;
        let mut ask: Option<Confirm> = None;
        let cats = self.category_options();
        ui::card(ui, |ui| {
            ui::heading(ui, "Add a budget");
            if cats.is_empty() {
                ui.label("Create a category first (Categories page).");
                return;
            }
            ui.horizontal(|ui| {
                ui::select(ui, "bcat", "Category", &mut self.budget_form.category, &cats, None);
                ui.label("Limit");
                ui.add(egui::TextEdit::singleline(&mut self.budget_form.amount).desired_width(100.0).hint_text("0.00"));
                egui::ComboBox::from_id_salt("bper").selected_text(self.budget_form.period.clone()).show_ui(ui, |ui| {
                    for p in ["weekly", "monthly", "yearly"] {
                        ui.selectable_value(&mut self.budget_form.period, p.to_string(), p);
                    }
                });
                if ui.button("Add budget").clicked() {
                    submit = true;
                }
            });
        });
        if submit {
            match (self.budget_form.category.clone(), parse_amount(&self.budget_form.amount)) {
                (None, _) => self.toast = err("Choose a category."),
                (_, None) => self.toast = err("Enter the limit as a number."),
                (Some(c), Some(a)) if a > 0.0 => {
                    let b = Budget {
                        id: new_id(),
                        category_id: c,
                        amount: a,
                        period: self.budget_form.period.clone(),
                        start_date: self.today().format("%Y-%m-%d").to_string(),
                        deleted: false,
                        updated_at: 0,
                    };
                    self.run(Act::SaveBudget(b));
                    self.budget_form.amount.clear();
                }
                _ => self.toast = err("The limit must be above zero."),
            }
        }
        ui.add_space(8.0);
        let acc = self.accent();
        let cur = self.currency.clone();
        for b in &self.data.budgets {
            let spent = calc::spent(&self.data.transactions, &b.category_id, &b.period, self.today());
            let left = b.amount - spent;
            ui::card(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.label(RichText::new(self.data.category_name(&Some(b.category_id.clone()))).strong().size(16.0));
                    ui.label(RichText::new(&b.period).weak());
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui.button("Delete").clicked() {
                            ask = Some(Confirm { text: "Delete this budget?".into(), act: Act::DeleteBudget(b.id.clone()) });
                        }
                        ui.label(if left < 0.0 {
                            RichText::new(format!("over by {}", money(-left, &cur))).color(ui::BAD)
                        } else {
                            RichText::new(format!("{} left", money(left, &cur))).color(ui::GOOD)
                        });
                    });
                });
                let frac = (spent / b.amount.max(0.01)) as f32;
                ui.add(egui::ProgressBar::new(frac.min(1.0)).fill(if frac > 1.0 { ui::BAD } else { acc }).text(format!("{} of {}", money(spent, &cur), money(b.amount, &cur))));
            });
        }
        if self.data.budgets.is_empty() {
            ui.label("No budgets yet.");
        }
        if let Some(c) = ask {
            self.confirm = Some(c);
        }
    }

    fn categories(&mut self, ui: &mut egui::Ui) {
        let mut submit = false;
        let mut cancel = false;
        let mut ask: Option<Confirm> = None;
        let mut edit: Option<CatForm> = None;
        let ideas: Vec<String> = CATEGORY_IDEAS.iter().map(|s| s.to_string()).collect();
        let colors: Vec<String> = COLORS.iter().flat_map(|(n, h)| [n.to_string(), h.to_string()]).collect();
        let editing = self.cat_form.editing.is_some();
        ui::card(ui, |ui| {
            ui::heading(ui, if editing { "Edit category" } else { "Add a category" });
            ui.horizontal(|ui| {
                ui.label("Name");
                ui::combo_edit(ui, "cname", &mut self.cat_form.name, &ideas, 200.0);
                ui.label("Colour");
                ui::combo_edit(ui, "ccol", &mut self.cat_form.color, &colors, 100.0);
                if ui.button(if editing { "Save changes" } else { "Add category" }).clicked() {
                    submit = true;
                }
                if editing && ui.button("Cancel").clicked() {
                    cancel = true;
                }
            });
        });
        if cancel {
            self.cat_form = CatForm::default();
        }
        if submit {
            let name = self.cat_form.name.trim().to_string();
            match resolve_color(&self.cat_form.color) {
                _ if name.is_empty() => self.toast = err("Enter a category name."),
                Err(()) => self.toast = err("Colour must be a name from the list or a hex code like #2196F3."),
                Ok(_) if self.data.categories.iter().any(|c| c.name.eq_ignore_ascii_case(&name) && Some(&c.id) != self.cat_form.editing.as_ref()) => {
                    self.toast = err("A category with that name already exists.")
                }
                Ok(color) => {
                    let parent = self.cat_form.editing.as_ref().and_then(|id| self.data.categories.iter().find(|c| &c.id == id)).and_then(|p| p.parent_id.clone());
                    let c = Category { id: self.cat_form.editing.clone().unwrap_or_else(new_id), name, parent_id: parent, color, deleted: false, updated_at: 0 };
                    self.run(Act::SaveCategory(c));
                    self.cat_form = CatForm::default();
                }
            }
        }
        ui.add_space(8.0);
        for c in &self.data.categories {
            let used = self.data.transactions.iter().filter(|t| t.category_id.as_deref() == Some(c.id.as_str())).count();
            ui.horizontal(|ui| {
                let col = c.color.as_deref().and_then(hex_color).unwrap_or(Color32::GRAY);
                let (r, _) = ui.allocate_exact_size(egui::vec2(14.0, 14.0), egui::Sense::hover());
                ui.painter().circle_filled(r.center(), 7.0, col);
                ui.label(RichText::new(&c.name).strong());
                ui.label(RichText::new(format!("{used} transactions")).weak());
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if ui.small_button("Delete").clicked() {
                        ask = Some(Confirm {
                            text: format!("Delete the category “{}”? Its transactions become Uncategorized.", c.name),
                            act: Act::DeleteCategory(c.id.clone()),
                        });
                    }
                    if ui.small_button("Edit").clicked() {
                        edit = Some(CatForm { editing: Some(c.id.clone()), name: c.name.clone(), color: c.color.clone().unwrap_or_default() });
                    }
                });
            });
            ui.separator();
        }
        if self.data.categories.is_empty() {
            ui.label("No categories yet.");
        }
        if let Some(f) = edit {
            self.cat_form = f;
        }
        if let Some(c) = ask {
            self.confirm = Some(c);
        }
    }

    fn tax_profile(&self) -> tax::Profile {
        let f = &self.tax_form;
        let n = |s: &str| parse_amount(s).unwrap_or(0.0).max(0.0);
        tax::Profile {
            year: if f.year == 0 { 2026 } else { f.year },
            filing: tax::Filing::ALL[(f.filing as usize).min(3)],
            wages: n(&f.wages),
            self_employment: n(&f.se),
            other_income: n(&f.other),
            adjustments: n(&f.adjustments),
            use_itemized: f.use_itemized,
            itemized: n(&f.itemized),
            children: f.children,
            withheld: n(&f.withheld),
            estimated_paid: n(&f.estimated),
            state: f.state.clone(),
            state_rate_pct: n(&f.state_rate),
        }
    }

    fn tax_page(&mut self, ui: &mut egui::Ui) {
        if self.tax_form.year == 0 {
            self.tax_form.year = 2026;
        }
        let before = serde_json::to_string(&self.tax_form).unwrap_or_default();
        let cur = self.currency.clone();
        let year_prefix = format!("{}-", self.tax_form.year);
        let year_income: f64 = self.data.transactions.iter().filter(|t| t.amount > 0.0 && t.date.starts_with(&year_prefix)).map(|t| t.amount).sum();
        ui::card(ui, |ui| {
            ui.label(
                RichText::new(
                    "Planning estimate for US federal tax, worked out on this computer with no internet. Not tax advice or a filing. \
                     It leaves out the business-income deduction, AMT, capital-gain rates and most credits. State tax uses the rate you enter.",
                )
                .small(),
            );
        });
        ui.add_space(6.0);
        let p = self.tax_profile();
        let r = tax::estimate(&p);
        let acc = self.accent();
        let owes = r.balance >= 0.0;
        ui::hero(
            ui,
            acc,
            if owes { "Estimated tax still owed" } else { "Estimated refund" },
            &money(r.balance.abs(), &cur),
            &format!("{} · total due {} · effective {:.1}% · marginal {}%", p.year, money(r.due, &cur), r.effective * 100.0, (r.marginal * 100.0).round()),
        );
        ui.add_space(6.0);
        let std_ded = money(tax::standard_deduction(p.year, p.filing), &cur);
        ui.columns(2, |c| {
            ui::card(&mut c[0], |ui| {
                ui::heading(ui, "About you");
                egui::Grid::new("taxa").num_columns(2).spacing([14.0, 8.0]).show(ui, |ui| {
                    ui.label("Tax year");
                    egui::ComboBox::from_id_salt("ty").selected_text(self.tax_form.year.to_string()).show_ui(ui, |ui| {
                        for y in tax::YEARS {
                            ui.selectable_value(&mut self.tax_form.year, y, y.to_string());
                        }
                    });
                    ui.end_row();
                    ui.label("Filing status");
                    egui::ComboBox::from_id_salt("tf").selected_text(tax::Filing::ALL[(self.tax_form.filing as usize).min(3)].label()).show_ui(ui, |ui| {
                        for (i, f) in tax::Filing::ALL.iter().enumerate() {
                            ui.selectable_value(&mut self.tax_form.filing, i as u8, f.label());
                        }
                    });
                    ui.end_row();
                    ui.label("Children under 17");
                    ui.add(egui::DragValue::new(&mut self.tax_form.children).range(0..=12));
                    ui.end_row();
                    ui.label("State");
                    let shown = if self.tax_form.state.is_empty() { "Not set".to_string() } else { self.tax_form.state.clone() };
                    egui::ComboBox::from_id_salt("ts").selected_text(shown).show_ui(ui, |ui| {
                        ui.selectable_value(&mut self.tax_form.state, String::new(), "Not set / outside the US");
                        for (code, name) in tax::STATES {
                            ui.selectable_value(&mut self.tax_form.state, code.to_string(), format!("{name} ({code})"));
                        }
                    });
                    ui.end_row();
                    if tax::NO_TAX_STATES.contains(&self.tax_form.state.as_str()) {
                        ui.label("State rate");
                        ui.label("No tax on wages here");
                    } else {
                        ui.label("State rate (%)");
                        ui.add(egui::TextEdit::singleline(&mut self.tax_form.state_rate).desired_width(90.0).hint_text("e.g. 5"));
                    }
                    ui.end_row();
                });
            });
            ui::card(&mut c[1], |ui| {
                ui::heading(ui, "Income and payments");
                egui::Grid::new("taxb").num_columns(2).spacing([14.0, 8.0]).show(ui, |ui| {
                    ui.label("Wages (W-2)");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.wages).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                    ui.label("Self-employment profit");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.se).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                    ui.label("Other taxable income");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.other).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                    ui.label("Adjustments (IRA, HSA)");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.adjustments).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                    ui.label("Federal tax withheld");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.withheld).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                    ui.label("Estimated payments made");
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.estimated).desired_width(130.0).hint_text("0"));
                    ui.end_row();
                });
                if year_income > 0.0 && ui.button(format!("Use my {} deposits as wages ({})", self.tax_form.year, money(year_income, &cur))).clicked() {
                    self.tax_form.wages = format!("{:.2}", year_income);
                }
                ui.checkbox(&mut self.tax_form.use_itemized, format!("Itemize instead of the {std_ded} standard deduction"));
                if self.tax_form.use_itemized {
                    ui.add(egui::TextEdit::singleline(&mut self.tax_form.itemized).desired_width(130.0).hint_text("Itemized total"));
                }
            });
        });
        ui.add_space(6.0);
        ui.columns(2, |c| {
            ui::card(&mut c[0], |ui| {
                ui::heading(ui, "Where the tax comes from");
                let line = |ui: &mut egui::Ui, l: &str, v: f64| {
                    ui.horizontal(|ui| {
                        ui.label(l);
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            ui.label(RichText::new(money(v, &cur)).strong());
                        });
                    });
                };
                line(ui, "Gross income", r.gross);
                line(ui, "Adjusted gross income", r.agi);
                line(ui, if p.use_itemized { "Itemized deduction" } else { "Standard deduction" }, r.deduction);
                line(ui, "Taxable income", r.taxable);
                ui.separator();
                line(ui, "Federal income tax", r.income_tax);
                if r.child_credit > 0.0 {
                    line(ui, "  after child credit of", r.child_credit);
                }
                if r.se_tax > 0.0 {
                    line(ui, "Self-employment tax", r.se_tax);
                }
                if r.additional_medicare > 0.0 {
                    line(ui, "Additional Medicare tax", r.additional_medicare);
                }
                if r.state_tax > 0.0 {
                    line(ui, "State tax", r.state_tax);
                }
                line(ui, "Payroll tax on wages (withheld)", r.fica);
                ui.separator();
                line(ui, "Paid so far", r.paid);
            });
            ui::card(&mut c[1], |ui| {
                ui::heading(ui, "Brackets");
                for b in r.brackets.iter().filter(|b| b.taxed > 0.0) {
                    ui.horizontal(|ui| {
                        ui.label(format!("{:.0}% on {}", b.rate * 100.0, money(b.taxed, &cur)));
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            ui.label(money(b.tax, &cur));
                        });
                    });
                }
                if r.taxable <= 0.0 {
                    ui.label("No taxable income after the deduction.");
                }
                ui.add_space(8.0);
                ui::heading(ui, "Quarterly payments");
                for (i, d) in tax::quarter_dates(p.year).iter().enumerate() {
                    ui.horizontal(|ui| {
                        ui.label(format!("Payment {} · {d}", i + 1));
                        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                            ui.label(RichText::new(money(r.quarterly, &cur)).strong());
                        });
                    });
                }
                ui.label(RichText::new("Dates move to the next business day when they fall on a weekend or holiday.").small().weak());
            });
        });
        if let Ok(s) = serde_json::to_string(&self.tax_form) {
            if s != before {
                self.db.set_setting("tax_form", &s);
            }
        }
    }

    fn data_page(&mut self, ui: &mut egui::Ui) {
        ui::card(ui, |ui| {
            ui::heading(ui, "Backup");
            ui.label("A backup is one JSON file with your accounts, categories, transactions and budgets. It is the same file the Android app makes, so it moves your data between phone and computer with no server.");
            ui.horizontal(|ui| {
                if ui.button("Export backup…").clicked() {
                    if let Some(path) = rfd::FileDialog::new().add_filter("SpendingSaver backup", &["json"]).set_file_name("spendingsaver-backup.json").save_file() {
                        let doc = Backup {
                            version: BACKUP_VERSION,
                            exported_at: now_ms(),
                            accounts: self.data.accounts.clone(),
                            categories: self.data.categories.clone(),
                            transactions: self.data.transactions.clone(),
                            budgets: self.data.budgets.clone(),
                        };
                        match serde_json::to_string_pretty(&doc).map_err(|e| e.to_string()).and_then(|s| std::fs::write(&path, s).map_err(|e| e.to_string())) {
                            Ok(()) => self.say("Backup saved.", false),
                            Err(e) => self.say(format!("Could not save: {e}"), true),
                        }
                    }
                }
                if ui.button("Restore from backup…").clicked() {
                    if let Some(path) = rfd::FileDialog::new().add_filter("SpendingSaver backup", &["json"]).pick_file() {
                        match std::fs::read_to_string(&path)
                            .map_err(|e| e.to_string())
                            .and_then(|s| serde_json::from_str::<Backup>(&s).map_err(|e| format!("not a SpendingSaver backup ({e})")))
                        {
                            Ok(b) if b.version == BACKUP_VERSION => self.restore_pending = Some(b),
                            Ok(b) => self.say(format!("Unsupported backup version {}.", b.version), true),
                            Err(e) => self.say(format!("Could not read the file: {e}"), true),
                        }
                    }
                }
            });
        });
        ui.add_space(8.0);
        let accounts = self.account_options();
        ui::card(ui, |ui| {
            ui::heading(ui, "Transactions as CSV");
            ui.label("Export every transaction, or import a bank CSV with date, description and amount columns (and optionally category).");
            ui::select(ui, "csvacct", "Import into account", &mut self.csv_account, &accounts, None);
            ui.horizontal(|ui| {
                if ui.button("Export CSV…").clicked() {
                    if let Some(path) = rfd::FileDialog::new().add_filter("CSV", &["csv"]).set_file_name("transactions.csv").save_file() {
                        let mut w = csv::Writer::from_writer(Vec::new());
                        let _ = w.write_record(["date", "description", "amount", "category", "account", "notes"]);
                        for t in &self.data.transactions {
                            let amount = format!("{:.2}", t.amount);
                            let _ = w.write_record([
                                t.date.as_str(),
                                t.description.as_str(),
                                amount.as_str(),
                                self.data.category_name(&t.category_id),
                                self.data.account_name(&t.account_id),
                                t.notes.as_deref().unwrap_or(""),
                            ]);
                        }
                        match w.into_inner().map_err(|e| e.to_string()).and_then(|b| std::fs::write(&path, b).map_err(|e| e.to_string())) {
                            Ok(()) => self.say("CSV saved.", false),
                            Err(e) => self.say(format!("Could not save: {e}"), true),
                        }
                    }
                }
                if ui.button("Import CSV…").clicked() {
                    match self.csv_account.clone() {
                        None => self.say("Choose the account to import into first.", true),
                        Some(acct) => {
                            if let Some(path) = rfd::FileDialog::new().add_filter("CSV", &["csv"]).pick_file() {
                                match self.import_csv(&path, &acct) {
                                    Ok(n) => {
                                        self.reload();
                                        self.say(format!("Imported {n} transactions."), false)
                                    }
                                    Err(e) => self.say(e, true),
                                }
                            }
                        }
                    }
                }
            });
        });
    }

    fn import_csv(&mut self, path: &std::path::Path, account: &str) -> Result<usize, String> {
        let mut rdr = csv::ReaderBuilder::new().flexible(true).from_path(path).map_err(|e| e.to_string())?;
        let headers: Vec<String> = rdr.headers().map_err(|e| e.to_string())?.iter().map(|h| h.trim().to_lowercase()).collect();
        let col = |names: &[&str]| headers.iter().position(|h| names.contains(&h.as_str()));
        let (di, ni, ai) = (col(&["date", "posted", "transaction date"]), col(&["description", "memo", "payee", "name"]), col(&["amount", "value"]));
        let (Some(di), Some(ni), Some(ai)) = (di, ni, ai) else {
            return Err("The CSV needs date, description and amount columns.".into());
        };
        let ci = col(&["category"]);
        let mut n = 0;
        for rec in rdr.records().flatten() {
            let (Some(date), Some(desc), Some(amt)) = (rec.get(di).and_then(|d| parse_date(d.trim())), rec.get(ni), rec.get(ai).and_then(parse_amount)) else {
                continue;
            };
            let cat = ci
                .and_then(|i| rec.get(i))
                .and_then(|name| self.data.categories.iter().find(|c| c.name.eq_ignore_ascii_case(name.trim())).map(|c| c.id.clone()))
                .or_else(|| calc::auto_category(desc, &self.data.categories).map(|c| c.id.clone()));
            let t = Transaction {
                id: new_id(),
                account_id: account.to_string(),
                amount: amt,
                description: desc.trim().to_string(),
                category_id: cat,
                date: date.format("%Y-%m-%d").to_string(),
                notes: None,
                created_at: now_ms(),
                deleted: false,
                updated_at: 0,
            };
            self.db.save_transaction(&t).map_err(|e| e.to_string())?;
            n += 1;
        }
        Ok(n)
    }

    fn settings(&mut self, ui: &mut egui::Ui) {
        let mut changed = false;
        ui::card(ui, |ui| {
            ui::heading(ui, "Appearance");
            ui.horizontal(|ui| {
                ui.label("Theme");
                for (m, l) in [(ThemeMode::System, "System"), (ThemeMode::Light, "Light"), (ThemeMode::Dark, "Dark")] {
                    if ui.selectable_value(&mut self.theme, m, l).changed() {
                        changed = true;
                    }
                }
            });
            ui.horizontal(|ui| {
                ui.label("Accent");
                for (name, color) in ui::ACCENTS {
                    let sel = self.accent_name.eq_ignore_ascii_case(name);
                    let b = egui::Button::new("   ").fill(color).stroke(if sel { egui::Stroke::new(2.5_f32, Color32::WHITE) } else { egui::Stroke::NONE });
                    if ui.add(b).on_hover_text(name).clicked() {
                        self.accent_name = name.to_string();
                        changed = true;
                    }
                }
            });
        });
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, "Currency");
            let list: Vec<String> = CURRENCIES.iter().map(|s| s.to_string()).collect();
            ui.horizontal(|ui| {
                ui.label("Default currency");
                let mut cur = self.currency.clone();
                ui::combo_edit(ui, "defcur", &mut cur, &list, 80.0);
                if cur != self.currency {
                    self.currency = cur.to_uppercase();
                    if self.currency.len() == 3 {
                        self.db.set_setting("currency", &self.currency);
                        self.reload();
                    }
                }
            });
        });
        ui.add_space(8.0);
        ui::card(ui, |ui| {
            ui::heading(ui, "About");
            ui.label(format!("SpendingSaver desktop {}", env!("CARGO_PKG_VERSION")));
            ui.label("A native Rust app: no web view, no background services. Your data is one SQLite file on this computer:");
            ui.label(RichText::new(db::default_path().display().to_string()).monospace());
            ui.label("Nothing leaves this computer unless you pair a phone, link a bank (optional) or export a file.");
        });
        if changed {
            self.db.set_setting("theme", self.theme.as_str());
            self.db.set_setting("accent", &self.accent_name);
        }
    }

    fn more_page(&mut self, ui: &mut egui::Ui) {
        let today = self.today();
        let accent = self.accent();
        let data_dir = crate::db::default_path().parent().map(|p| p.to_path_buf()).unwrap_or_default();
        let ctx = crate::more::Ctx { db: &self.db, data: &self.data, currency: &self.currency, today, accent };
        let out = match self.page {
            Page::Recurring => self.more.recurring(ui, &ctx),
            Page::Goals => self.more.goals(ui, &ctx),
            Page::Debts => self.more.debts(ui, &ctx),
            Page::Bank => self.more.bank(ui, &ctx, &data_dir),
            _ => {
                self.more.assistant(ui, &ctx);
                crate::more::Out::default()
            }
        };
        if out.changed {
            self.reload();
        }
        if let Some((m, e)) = out.message {
            self.say(m, e);
        }
    }

    fn page_body(&mut self, ui: &mut egui::Ui) {
        match self.page {
            Page::Dashboard => self.dashboard(ui),
            Page::Transactions => self.transactions(ui),
            Page::Accounts => self.accounts(ui),
            Page::Budgets => self.budgets(ui),
            Page::Categories => self.categories(ui),
            Page::Recurring | Page::Goals | Page::Debts | Page::Assistant | Page::Bank => self.more_page(ui),
            Page::Tax => self.tax_page(ui),
            Page::Data => self.data_page(ui),
            Page::Sync => {
                let acc = self.accent();
                self.sync.show(ui, &self.db, acc);
            }
            Page::Settings => self.settings(ui),
        }
    }
}

fn hex_color(s: &str) -> Option<Color32> {
    let h = s.trim().trim_start_matches('#');
    if h.len() != 6 {
        return None;
    }
    let v = u32::from_str_radix(h, 16).ok()?;
    Some(Color32::from_rgb((v >> 16) as u8, (v >> 8) as u8, v as u8))
}

/// A named colour from the list, or a #RRGGBB code. Empty means no colour.
fn resolve_color(input: &str) -> Result<Option<String>, ()> {
    let t = input.trim();
    if t.is_empty() {
        return Ok(None);
    }
    if let Some((_, hex)) = COLORS.iter().find(|(n, _)| n.eq_ignore_ascii_case(t)) {
        return Ok(Some(hex.to_string()));
    }
    let h = if t.starts_with('#') { t.to_string() } else { format!("#{t}") };
    if hex_color(&h).is_some() {
        Ok(Some(h.to_uppercase()))
    } else {
        Err(())
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.sync_theme(ctx);
        if self.sync.take_changed(&self.db) {
            self.reload();
            self.say("Synced with your phone.", false);
        }

        egui::SidePanel::left("nav").exact_width(196.0).resizable(false).show(ctx, |ui| {
            ui.add_space(10.0);
            ui.label(RichText::new("💰 SpendingSaver").size(18.0).strong());
            ui.add_space(10.0);
            for (page, icon, label) in NAV {
                let sel = self.page == page;
                let text = RichText::new(format!("{icon}  {label}")).size(15.0);
                let b = egui::Button::new(if sel { text.color(Color32::WHITE).strong() } else { text })
                    .fill(if sel { self.accent() } else { Color32::TRANSPARENT })
                    .stroke(egui::Stroke::NONE)
                    .min_size(egui::vec2(ui.available_width(), 34.0));
                if ui.add(b).clicked() {
                    self.page = page;
                }
            }
            ui.with_layout(egui::Layout::bottom_up(egui::Align::LEFT), |ui| {
                ui.add_space(8.0);
                ui.label(RichText::new(format!("v{} · stored on this PC", env!("CARGO_PKG_VERSION"))).small().weak());
            });
        });

        egui::CentralPanel::default().show(ctx, |ui| {
            let title = NAV.iter().find(|n| n.0 == self.page).map(|n| n.2).unwrap_or("");
            ui.label(RichText::new(title).size(24.0).strong());
            ui.add_space(6.0);
            if self.page == Page::Transactions {
                self.page_body(ui);
            } else {
                egui::ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
                    ui.set_width(ui.available_width() - 6.0);
                    self.page_body(ui);
                });
            }
        });

        if self.confirm.is_some() {
            let mut decision: Option<bool> = None;
            egui::Window::new("Please confirm").collapsible(false).resizable(false).anchor(Align2::CENTER_CENTER, [0.0, 0.0]).show(ctx, |ui| {
                if let Some(c) = &self.confirm {
                    ui.label(&c.text);
                }
                ui.horizontal(|ui| {
                    if ui.button("Delete").clicked() {
                        decision = Some(true);
                    }
                    if ui.button("Cancel").clicked() {
                        decision = Some(false);
                    }
                });
            });
            match decision {
                Some(true) => {
                    if let Some(c) = self.confirm.take() {
                        self.run(c.act);
                    }
                }
                Some(false) => self.confirm = None,
                None => {}
            }
        }
        if self.restore_pending.is_some() {
            let mut decision: Option<bool> = None;
            egui::Window::new("Replace your data?").collapsible(false).resizable(false).anchor(Align2::CENTER_CENTER, [0.0, 0.0]).show(ctx, |ui| {
                if let Some(b) = &self.restore_pending {
                    ui.label(format!(
                        "This backup has {} accounts, {} categories, {} transactions and {} budgets.",
                        b.accounts.len(),
                        b.categories.len(),
                        b.transactions.len(),
                        b.budgets.len()
                    ));
                }
                ui.label("Restoring replaces what is here now. Items not in the backup are marked deleted.");
                ui.horizontal(|ui| {
                    if ui.button("Restore").clicked() {
                        decision = Some(true);
                    }
                    if ui.button("Cancel").clicked() {
                        decision = Some(false);
                    }
                });
            });
            match decision {
                Some(true) => {
                    if let Some(b) = self.restore_pending.take() {
                        match self.db.restore(&b) {
                            Ok(()) => {
                                self.reload();
                                self.say("Backup restored.", false)
                            }
                            Err(e) => self.say(format!("Restore failed: {e}"), true),
                        }
                    }
                }
                Some(false) => self.restore_pending = None,
                None => {}
            }
        }
        if let Some((text, error, at)) = &self.toast {
            if at.elapsed() < Duration::from_secs(4) {
                let (text, error) = (text.clone(), *error);
                let fill = if error { ui::BAD } else { self.accent() };
                egui::Area::new(egui::Id::new("toast")).anchor(Align2::RIGHT_BOTTOM, [-16.0, -16.0]).order(egui::Order::Foreground).show(ctx, |ui| {
                    egui::Frame::none().fill(fill).rounding(10.0).inner_margin(egui::Margin::symmetric(14.0, 10.0)).show(ui, |ui| {
                        ui.label(RichText::new(text).color(Color32::WHITE));
                    });
                });
                ctx.request_repaint_after(Duration::from_millis(250));
            } else {
                self.toast = None;
            }
        }
    }
}
