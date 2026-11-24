//! Recurring items, savings goals and debts. These are desktop-only and stay on this PC: they are not part
//! of the Android backup and are not shared by phone sync. Recurring items post ordinary transactions, and
//! those do sync like any other transaction.
use crate::calc::parse_date;
use crate::db::Db;
use crate::model::*;
use chrono::{Datelike, Duration, NaiveDate};
use rusqlite::{params, Connection};

pub const SCHEMA: &str = "
CREATE TABLE IF NOT EXISTS recurring(id TEXT PRIMARY KEY, name TEXT NOT NULL, amount REAL NOT NULL, account_id TEXT NOT NULL,
  category_id TEXT, frequency TEXT NOT NULL, next_date TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1);
CREATE TABLE IF NOT EXISTS goals(id TEXT PRIMARY KEY, name TEXT NOT NULL, target REAL NOT NULL, saved REAL NOT NULL DEFAULT 0, due TEXT);
CREATE TABLE IF NOT EXISTS debts(id TEXT PRIMARY KEY, name TEXT NOT NULL, balance REAL NOT NULL, apr REAL NOT NULL DEFAULT 0,
  min_payment REAL NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS external_refs(kind TEXT NOT NULL, external_id TEXT NOT NULL, local_id TEXT NOT NULL,
  PRIMARY KEY(kind, external_id));
";

pub const FREQUENCIES: [&str; 5] = ["weekly", "biweekly", "monthly", "quarterly", "yearly"];

#[derive(Clone, Debug, PartialEq)]
pub struct Recurring {
    pub id: String,
    pub name: String,
    /// Negative is a bill, positive is income.
    pub amount: f64,
    pub account_id: String,
    pub category_id: Option<String>,
    pub frequency: String,
    pub next_date: String,
    pub active: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Goal {
    pub id: String,
    pub name: String,
    pub target: f64,
    pub saved: f64,
    pub due: Option<String>,
}

#[derive(Clone, Debug, PartialEq)]
pub struct Debt {
    pub id: String,
    pub name: String,
    pub balance: f64,
    pub apr: f64,
    pub min_payment: f64,
}

/// Adds whole months, clamping to the last day of a shorter month (31 Jan + 1 month is 28/29 Feb).
pub fn add_months(d: NaiveDate, months: u32) -> NaiveDate {
    let total = d.year() * 12 + d.month0() as i32 + months as i32;
    let (y, m) = (total.div_euclid(12), total.rem_euclid(12) as u32 + 1);
    let mut day = d.day();
    loop {
        if let Some(x) = NaiveDate::from_ymd_opt(y, m, day) {
            return x;
        }
        day -= 1;
    }
}

pub fn next_after(d: NaiveDate, frequency: &str) -> NaiveDate {
    match frequency {
        "weekly" => d + Duration::days(7),
        "biweekly" => d + Duration::days(14),
        "quarterly" => add_months(d, 3),
        "yearly" => add_months(d, 12),
        _ => add_months(d, 1),
    }
}

/// Every date this item has come due on or before `today` (at most 400, so a very old date cannot flood).
pub fn due_dates(r: &Recurring, today: NaiveDate) -> Vec<NaiveDate> {
    let mut out = Vec::new();
    let Some(mut d) = parse_date(&r.next_date) else { return out };
    while d <= today && out.len() < 400 {
        out.push(d);
        d = next_after(d, &r.frequency);
    }
    out
}

/// Approximate cost of an item over a month, for the monthly total.
pub fn monthly_equivalent(r: &Recurring) -> f64 {
    r.amount
        * match r.frequency.as_str() {
            "weekly" => 52.0 / 12.0,
            "biweekly" => 26.0 / 12.0,
            "quarterly" => 1.0 / 3.0,
            "yearly" => 1.0 / 12.0,
            _ => 1.0,
        }
}

/// Months to pay off a debt with a fixed payment, or None if the payment never covers the interest.
pub fn payoff_months(balance: f64, apr: f64, payment: f64) -> Option<u32> {
    if balance <= 0.0 {
        return Some(0);
    }
    if payment <= 0.0 {
        return None;
    }
    let r = apr / 100.0 / 12.0;
    if r > 0.0 && payment <= balance * r {
        return None;
    }
    let (mut b, mut n) = (balance, 0u32);
    while b > 0.005 && n < 1200 {
        b = b * (1.0 + r) - payment;
        n += 1;
    }
    Some(n)
}

/// Total interest paid by that schedule.
pub fn payoff_interest(balance: f64, apr: f64, payment: f64) -> Option<f64> {
    let n = payoff_months(balance, apr, payment)?;
    let r = apr / 100.0 / 12.0;
    let (mut b, mut interest) = (balance, 0.0);
    for _ in 0..n {
        let i = b * r;
        interest += i;
        b = (b + i - payment).max(0.0);
    }
    Some(interest)
}

/// Monthly amount needed to reach a goal by its due date (None if there is no date or it has passed).
pub fn goal_monthly_need(g: &Goal, today: NaiveDate) -> Option<f64> {
    let due = parse_date(g.due.as_deref()?)?;
    if due <= today {
        return None;
    }
    let months = ((due.year() - today.year()) * 12 + due.month() as i32 - today.month() as i32).max(1) as f64;
    Some(((g.target - g.saved).max(0.0)) / months)
}

impl Db {
    pub fn recurring(&self) -> Vec<Recurring> {
        let c: &Connection = self.conn();
        let Ok(mut s) = c.prepare("SELECT id,name,amount,account_id,category_id,frequency,next_date,active FROM recurring ORDER BY next_date") else { return vec![] };
        s.query_map([], |r| {
            Ok(Recurring { id: r.get(0)?, name: r.get(1)?, amount: r.get(2)?, account_id: r.get(3)?, category_id: r.get(4)?,
                frequency: r.get(5)?, next_date: r.get(6)?, active: r.get::<_, i64>(7)? != 0 })
        })
        .map(|m| m.flatten().collect())
        .unwrap_or_default()
    }
    pub fn save_recurring(&self, r: &Recurring) -> rusqlite::Result<()> {
        self.conn().execute(
            "INSERT INTO recurring(id,name,amount,account_id,category_id,frequency,next_date,active) VALUES(?1,?2,?3,?4,?5,?6,?7,?8)
             ON CONFLICT(id) DO UPDATE SET name=?2,amount=?3,account_id=?4,category_id=?5,frequency=?6,next_date=?7,active=?8",
            params![r.id, r.name, r.amount, r.account_id, r.category_id, r.frequency, r.next_date, r.active as i64],
        )?;
        Ok(())
    }
    pub fn delete_recurring(&self, id: &str) {
        let _ = self.conn().execute("DELETE FROM recurring WHERE id=?1", params![id]);
    }
    pub fn goals(&self) -> Vec<Goal> {
        let Ok(mut s) = self.conn().prepare("SELECT id,name,target,saved,due FROM goals ORDER BY name") else { return vec![] };
        s.query_map([], |r| Ok(Goal { id: r.get(0)?, name: r.get(1)?, target: r.get(2)?, saved: r.get(3)?, due: r.get(4)? }))
            .map(|m| m.flatten().collect())
            .unwrap_or_default()
    }
    pub fn save_goal(&self, g: &Goal) -> rusqlite::Result<()> {
        self.conn().execute(
            "INSERT INTO goals(id,name,target,saved,due) VALUES(?1,?2,?3,?4,?5)
             ON CONFLICT(id) DO UPDATE SET name=?2,target=?3,saved=?4,due=?5",
            params![g.id, g.name, g.target, g.saved, g.due],
        )?;
        Ok(())
    }
    pub fn delete_goal(&self, id: &str) {
        let _ = self.conn().execute("DELETE FROM goals WHERE id=?1", params![id]);
    }
    pub fn debts(&self) -> Vec<Debt> {
        let Ok(mut s) = self.conn().prepare("SELECT id,name,balance,apr,min_payment FROM debts ORDER BY name") else { return vec![] };
        s.query_map([], |r| Ok(Debt { id: r.get(0)?, name: r.get(1)?, balance: r.get(2)?, apr: r.get(3)?, min_payment: r.get(4)? }))
            .map(|m| m.flatten().collect())
            .unwrap_or_default()
    }
    pub fn save_debt(&self, d: &Debt) -> rusqlite::Result<()> {
        self.conn().execute(
            "INSERT INTO debts(id,name,balance,apr,min_payment) VALUES(?1,?2,?3,?4,?5)
             ON CONFLICT(id) DO UPDATE SET name=?2,balance=?3,apr=?4,min_payment=?5",
            params![d.id, d.name, d.balance, d.apr, d.min_payment],
        )?;
        Ok(())
    }
    pub fn delete_debt(&self, id: &str) {
        let _ = self.conn().execute("DELETE FROM debts WHERE id=?1", params![id]);
    }

    /// Posts a transaction for every occurrence that has come due and moves each item's next date forward.
    /// Safe to run on every start: an occurrence is posted once because `next_date` advances in the same pass.
    pub fn post_due_recurring(&self, today: NaiveDate) -> usize {
        let mut posted = 0;
        for r in self.recurring().into_iter().filter(|r| r.active) {
            let dates = due_dates(&r, today);
            if dates.is_empty() {
                continue;
            }
            for d in &dates {
                let t = Transaction {
                    id: new_id(),
                    account_id: r.account_id.clone(),
                    amount: r.amount,
                    description: r.name.clone(),
                    category_id: r.category_id.clone(),
                    date: d.format("%Y-%m-%d").to_string(),
                    notes: Some("recurring".into()),
                    created_at: 0,
                    deleted: false,
                    updated_at: 0,
                };
                if self.save_transaction(&t).is_ok() {
                    posted += 1;
                }
            }
            let last = *dates.last().unwrap();
            let mut next = r.clone();
            next.next_date = next_after(last, &r.frequency).format("%Y-%m-%d").to_string();
            let _ = self.save_recurring(&next);
        }
        posted
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        parse_date(s).unwrap()
    }

    #[test]
    fn month_end_is_clamped() {
        assert_eq!(add_months(d("2025-01-31"), 1), d("2025-02-28"));
        assert_eq!(add_months(d("2024-01-31"), 1), d("2024-02-29"));
        assert_eq!(add_months(d("2025-11-30"), 3), d("2026-02-28"));
        assert_eq!(next_after(d("2025-03-01"), "weekly"), d("2025-03-08"));
        assert_eq!(next_after(d("2025-03-01"), "yearly"), d("2026-03-01"));
    }

    #[test]
    fn due_dates_catch_up_and_stop_at_today() {
        let r = Recurring { id: "r".into(), name: "Rent".into(), amount: -900.0, account_id: "a".into(), category_id: None,
            frequency: "monthly".into(), next_date: "2025-01-15".into(), active: true };
        let ds = due_dates(&r, d("2025-03-20"));
        assert_eq!(ds, vec![d("2025-01-15"), d("2025-02-15"), d("2025-03-15")]);
        assert!(due_dates(&r, d("2025-01-14")).is_empty());
    }

    #[test]
    fn posting_is_idempotent_and_advances() {
        let db = Db::memory().unwrap();
        let r = Recurring { id: "r".into(), name: "Gym".into(), amount: -30.0, account_id: "a".into(), category_id: None,
            frequency: "monthly".into(), next_date: "2025-01-10".into(), active: true };
        db.save_recurring(&r).unwrap();
        assert_eq!(db.post_due_recurring(d("2025-03-12")), 3);
        assert_eq!(db.post_due_recurring(d("2025-03-12")), 0);
        assert_eq!(db.recurring()[0].next_date, "2025-04-10");
        assert_eq!(db.load().unwrap().transactions.len(), 3);
    }

    #[test]
    fn inactive_items_do_not_post() {
        let db = Db::memory().unwrap();
        let r = Recurring { id: "r".into(), name: "Old".into(), amount: -5.0, account_id: "a".into(), category_id: None,
            frequency: "weekly".into(), next_date: "2025-01-01".into(), active: false };
        db.save_recurring(&r).unwrap();
        assert_eq!(db.post_due_recurring(d("2025-06-01")), 0);
    }

    #[test]
    fn debt_payoff_math() {
        assert_eq!(payoff_months(1000.0, 0.0, 100.0), Some(10));
        assert_eq!(payoff_months(1000.0, 24.0, 10.0), None); // interest is 20 a month
        let m = payoff_months(5000.0, 18.0, 200.0).unwrap();
        assert!((30..=33).contains(&m), "{m}");
        let i = payoff_interest(5000.0, 18.0, 200.0).unwrap();
        assert!(i > 1250.0 && i < 1400.0, "{i}");
        assert_eq!(payoff_months(0.0, 5.0, 10.0), Some(0));
    }

    #[test]
    fn goal_need_and_monthly_equivalent() {
        let g = Goal { id: "g".into(), name: "Trip".into(), target: 1200.0, saved: 0.0, due: Some("2026-01-31".into()) };
        let need = goal_monthly_need(&g, d("2025-01-15")).unwrap();
        assert!((need - 100.0).abs() < 1e-9, "{need}");
        let past = Goal { due: Some("2024-01-01".into()), ..g.clone() };
        assert_eq!(goal_monthly_need(&past, d("2025-01-15")), None);
        let r = Recurring { id: "r".into(), name: "x".into(), amount: -12.0, account_id: "a".into(), category_id: None,
            frequency: "yearly".into(), next_date: "2025-01-01".into(), active: true };
        assert!((monthly_equivalent(&r) + 1.0).abs() < 1e-9);
    }
}
