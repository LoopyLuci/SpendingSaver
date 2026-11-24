//! Pure calculations over the in-memory data. No UI and no I/O, so they are unit tested.
use crate::model::*;
use chrono::{Datelike, Duration, NaiveDate};
use std::collections::HashMap;

pub fn parse_date(s: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(s.get(..10).unwrap_or(s), "%Y-%m-%d").ok()
}

/// First and last day of a budget period around `today` (weekly is Monday to Sunday).
pub fn window(period: &str, today: NaiveDate) -> (NaiveDate, NaiveDate) {
    match period.to_lowercase().as_str() {
        "weekly" => {
            let start = today - Duration::days(today.weekday().num_days_from_monday() as i64);
            (start, start + Duration::days(6))
        }
        "yearly" => (
            NaiveDate::from_ymd_opt(today.year(), 1, 1).unwrap(),
            NaiveDate::from_ymd_opt(today.year(), 12, 31).unwrap(),
        ),
        _ => {
            let start = today.with_day(1).unwrap();
            let next = if today.month() == 12 {
                NaiveDate::from_ymd_opt(today.year() + 1, 1, 1).unwrap()
            } else {
                NaiveDate::from_ymd_opt(today.year(), today.month() + 1, 1).unwrap()
            };
            (start, next - Duration::days(1))
        }
    }
}

pub fn spent(txs: &[Transaction], category_id: &str, period: &str, today: NaiveDate) -> f64 {
    let (a, b) = window(period, today);
    txs.iter()
        .filter(|t| t.amount < 0.0 && t.category_id.as_deref() == Some(category_id))
        .filter(|t| parse_date(&t.date).map(|d| d >= a && d <= b).unwrap_or(false))
        .map(|t| -t.amount)
        .sum()
}

pub fn net_worth(d: &Data) -> f64 {
    d.accounts.iter().map(|a| a.balance).sum()
}

#[derive(Default, Clone, Copy)]
pub struct Totals {
    pub income: f64,
    pub expenses: f64,
}

pub fn totals(txs: &[Transaction], from: NaiveDate, to: NaiveDate) -> Totals {
    let mut t = Totals::default();
    for x in txs {
        if let Some(d) = parse_date(&x.date) {
            if d >= from && d <= to {
                if x.amount > 0.0 {
                    t.income += x.amount
                } else {
                    t.expenses += -x.amount
                }
            }
        }
    }
    t
}

/// Expenses grouped by category inside [from, to], largest first.
pub fn by_category(d: &Data, from: NaiveDate, to: NaiveDate) -> Vec<(String, f64)> {
    let mut m: HashMap<String, f64> = HashMap::new();
    for t in d.transactions.iter().filter(|t| t.amount < 0.0) {
        if parse_date(&t.date).map(|x| x >= from && x <= to).unwrap_or(false) {
            *m.entry(d.category_name(&t.category_id).to_string()).or_default() += -t.amount;
        }
    }
    let mut v: Vec<_> = m.into_iter().collect();
    v.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
    v
}

/// Income and expense totals for each of the last `n` calendar months, oldest first.
pub fn monthly(txs: &[Transaction], today: NaiveDate, n: usize) -> Vec<(String, Totals)> {
    let mut out = Vec::new();
    let mut y = today.year();
    let mut m = today.month() as i32;
    for _ in 0..n {
        let start = NaiveDate::from_ymd_opt(y, m as u32, 1).unwrap();
        let (_, end) = window("monthly", start);
        out.push((start.format("%b").to_string(), totals(txs, start, end)));
        m -= 1;
        if m == 0 {
            m = 12;
            y -= 1;
        }
    }
    out.reverse();
    out
}

/// Money with thousands separators and a currency symbol, e.g. -$1,234.50.
pub fn money(amount: f64, currency: &str) -> String {
    let sym = match currency.to_uppercase().as_str() {
        "USD" | "CAD" | "AUD" | "NZD" | "MXN" | "HKD" | "SGD" => "$",
        "EUR" => "€",
        "GBP" => "£",
        "JPY" | "CNY" => "¥",
        "INR" => "₹",
        "KRW" => "₩",
        "CHF" => "CHF ",
        other => return format!("{}{} {}", if amount < 0.0 { "-" } else { "" }, group(amount.abs()), other),
    };
    format!("{}{}{}", if amount < 0.0 { "-" } else { "" }, sym, group(amount.abs()))
}

fn group(v: f64) -> String {
    let s = format!("{:.2}", v);
    let (int, frac) = s.split_once('.').unwrap();
    let mut out = String::new();
    for (i, c) in int.chars().enumerate() {
        if i > 0 && (int.len() - i) % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    format!("{out}.{frac}")
}

/// Accepts "1,234.50", "1.234,50", "12,5", "$40" and plain numbers.
pub fn parse_amount(text: &str) -> Option<f64> {
    let t: String = text.trim().chars().filter(|c| !c.is_whitespace()).collect();
    let t = t.trim_start_matches(['$', '€', '£']);
    if t.is_empty() {
        return None;
    }
    let norm = if t.contains(',') && t.contains('.') {
        if t.rfind(',') > t.rfind('.') { t.replace('.', "").replace(',', ".") } else { t.replace(',', "") }
    } else if t.contains(',') {
        let last = t.rsplit(',').next().unwrap_or("");
        if last.len() == 3 && t.matches(',').count() >= 1 && last.chars().all(|c| c.is_ascii_digit()) { t.replace(',', "") } else { t.replace(',', ".") }
    } else {
        t.to_string()
    };
    norm.parse::<f64>().ok().filter(|v| v.is_finite())
}

/// Suggest a category from a description, using the same keywords as the Android app.
pub fn auto_category<'a>(desc: &str, cats: &'a [Category]) -> Option<&'a Category> {
    let d = desc.to_lowercase();
    let rules: [(&[&str], &str); 7] = [
        (&["salary", "payroll", "direct deposit"], "income"),
        (&["rent", "mortgage"], "housing"),
        (&["grocery", "restaurant", "doordash", "uber eats"], "food"),
        (&["gas", "uber", "lyft"], "transportation"),
        (&["netflix", "spotify", "hulu", "disney"], "entertainment"),
        (&["electric", "water", "internet"], "utilities"),
        (&["doctor", "pharmacy"], "healthcare"),
    ];
    for (words, name) in rules {
        if words.iter().any(|w| d.contains(w)) {
            if let Some(c) = cats.iter().find(|c| c.name.to_lowercase() == name) {
                return Some(c);
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn d(s: &str) -> NaiveDate {
        NaiveDate::parse_from_str(s, "%Y-%m-%d").unwrap()
    }
    fn tx(amount: f64, cat: &str, date: &str) -> Transaction {
        Transaction { id: new_id(), account_id: "a".into(), amount, description: "x".into(), category_id: Some(cat.into()), date: date.into(), notes: None, created_at: 0, deleted: false, updated_at: 0 }
    }

    #[test]
    fn windows() {
        assert_eq!(window("monthly", d("2026-02-10")), (d("2026-02-01"), d("2026-02-28")));
        assert_eq!(window("monthly", d("2026-12-31")), (d("2026-12-01"), d("2026-12-31")));
        assert_eq!(window("weekly", d("2026-11-24")), (d("2026-11-23"), d("2026-11-29")));
        assert_eq!(window("yearly", d("2026-06-01")), (d("2026-01-01"), d("2026-12-31")));
    }

    #[test]
    fn spent_counts_only_expenses_in_window() {
        let txs = vec![tx(-40.0, "f", "2026-11-10"), tx(-60.0, "f", "2026-11-12"), tx(-25.0, "f", "2026-01-05"), tx(100.0, "f", "2026-11-03")];
        assert_eq!(spent(&txs, "f", "monthly", d("2026-11-24")), 100.0);
        assert_eq!(spent(&txs, "f", "yearly", d("2026-11-24")), 125.0);
    }

    #[test]
    fn money_formats() {
        assert_eq!(money(1234.5, "USD"), "$1,234.50");
        assert_eq!(money(-1000000.0, "USD"), "-$1,000,000.00");
        assert_eq!(money(5.0, "EUR"), "€5.00");
        assert_eq!(money(5.0, "SEK"), "5.00 SEK");
    }

    #[test]
    fn amounts_parse_like_the_phone() {
        assert_eq!(parse_amount("1,234.50"), Some(1234.5));
        assert_eq!(parse_amount("1.234,50"), Some(1234.5));
        assert_eq!(parse_amount("12,5"), Some(12.5));
        assert_eq!(parse_amount("1,000"), Some(1000.0));
        assert_eq!(parse_amount("$40"), Some(40.0));
        assert_eq!(parse_amount("abc"), None);
        assert_eq!(parse_amount(""), None);
    }

    #[test]
    fn monthly_series_is_oldest_first() {
        let txs = vec![tx(-10.0, "f", "2026-11-02"), tx(-20.0, "f", "2026-10-02")];
        let m = monthly(&txs, d("2026-11-24"), 3);
        assert_eq!(m.len(), 3);
        assert_eq!(m[2].0, "Nov");
        assert_eq!(m[2].1.expenses, 10.0);
        assert_eq!(m[1].1.expenses, 20.0);
    }

    #[test]
    fn auto_category_matches_keywords() {
        let cats = vec![Category { id: "1".into(), name: "Food".into(), parent_id: None, color: None, deleted: false, updated_at: 0 }];
        assert_eq!(auto_category("Grocery Mart", &cats).map(|c| c.id.as_str()), Some("1"));
        assert!(auto_category("zzz", &cats).is_none());
    }
}
