//! The on-device assistant. Deterministic and pure: no network, no model download, no files. It reads the
//! snapshot it is given and answers in plain language, so nothing it does can leave this computer.
use crate::calc::{money, parse_date, window};
use crate::extras::{goal_monthly_need, monthly_equivalent, payoff_months, Debt, Goal, Recurring};
use crate::model::*;
use chrono::NaiveDate;

pub const SUGGESTIONS: [&str; 8] = [
    "What is my balance?",
    "How much did I spend this month?",
    "Top spending categories",
    "How are my budgets?",
    "Biggest expenses",
    "Recent transactions",
    "What are my recurring bills?",
    "How are my goals and debts?",
];

pub struct Snapshot<'a> {
    pub data: &'a Data,
    pub recurring: &'a [Recurring],
    pub goals: &'a [Goal],
    pub debts: &'a [Debt],
    pub currency: &'a str,
    pub today: NaiveDate,
}

#[derive(Clone, Copy, PartialEq)]
enum Win {
    Week,
    Month,
    Year,
    All,
}

impl Win {
    fn label(self) -> &'static str {
        match self {
            Win::Week => "this week",
            Win::Month => "this month",
            Win::Year => "this year",
            Win::All => "overall",
        }
    }
}

fn has(q: &str, words: &[&str]) -> bool {
    words.iter().any(|w| q.contains(w))
}

fn win_for(q: &str) -> Win {
    if has(q, &["all time", "overall", "ever"]) {
        Win::All
    } else if q.contains("week") {
        Win::Week
    } else if q.contains("year") {
        Win::Year
    } else {
        Win::Month
    }
}

fn in_win(t: &Transaction, w: Win, today: NaiveDate) -> bool {
    if w == Win::All {
        return true;
    }
    let period = match w {
        Win::Week => "weekly",
        Win::Year => "yearly",
        _ => "monthly",
    };
    let (a, b) = window(period, today);
    parse_date(&t.date).map(|d| d >= a && d <= b).unwrap_or(false)
}

fn plural(n: usize, one: &str, many: &str) -> String {
    format!("{n} {}", if n == 1 { one } else { many })
}

fn help() -> String {
    format!(
        "I run entirely on this computer and only look at your own data. Try: {}. You can also ask \"how much did I spend on groceries\" and add \"this week\" or \"this year\".",
        SUGGESTIONS.iter().map(|s| format!("\"{s}\"")).collect::<Vec<_>>().join("; ")
    )
}

pub fn answer(question: &str, s: &Snapshot) -> String {
    let q = question.to_lowercase();
    let q = q.trim();
    if q.is_empty() || has(q, &["help", "what can you"]) {
        return help();
    }
    let m = |v: f64| money(v, s.currency);
    let live: Vec<&Transaction> = s.data.transactions.iter().filter(|t| !t.deleted).collect();
    let w = win_for(q);
    let expenses = |w: Win| -> Vec<&Transaction> { live.iter().copied().filter(|t| t.amount < 0.0 && in_win(t, w, s.today)).collect() };

    if has(q, &["recurring", "subscription", "bills"]) {
        let act: Vec<&Recurring> = s.recurring.iter().filter(|r| r.active).collect();
        if act.is_empty() {
            return "You have no recurring items yet. Add them on the Recurring page.".into();
        }
        let total: f64 = act.iter().map(|r| monthly_equivalent(r)).sum();
        let lines: Vec<String> = act.iter().map(|r| format!("• {} ({}): {}, next {}", r.name, r.frequency, m(r.amount), r.next_date)).collect();
        return format!("Recurring items, about {} a month in total:\n{}", m(total), lines.join("\n"));
    }
    if has(q, &["goal", "saving for"]) {
        if s.goals.is_empty() {
            return "You have no savings goals yet. Add one on the Goals page.".into();
        }
        let lines: Vec<String> = s.goals.iter().map(|g| {
            let pct = if g.target > 0.0 { (g.saved / g.target * 100.0) as i64 } else { 0 };
            let need = goal_monthly_need(g, s.today).map(|n| format!(", {} a month needed", m(n))).unwrap_or_default();
            format!("• {}: {} of {} ({pct}%){need}", g.name, m(g.saved), m(g.target))
        }).collect();
        return format!("Savings goals:\n{}", lines.join("\n"));
    }
    if has(q, &["debt", "loan", "owe", "payoff", "pay off"]) {
        if s.debts.is_empty() {
            return "You have no debts recorded. Add them on the Debts page.".into();
        }
        let total: f64 = s.debts.iter().map(|d| d.balance).sum();
        let lines: Vec<String> = s.debts.iter().map(|d| {
            let when = match payoff_months(d.balance, d.apr, d.min_payment) {
                Some(0) => "paid off".to_string(),
                Some(n) => format!("paid off in about {n} months at {}/month", m(d.min_payment)),
                None => "the payment does not cover the interest".to_string(),
            };
            format!("• {}: {} at {:.1}% APR, {when}", d.name, m(d.balance), d.apr)
        }).collect();
        return format!("You owe {} in total:\n{}", m(total), lines.join("\n"));
    }
    if q.contains("budget") {
        let b: Vec<&Budget> = s.data.budgets.iter().filter(|b| !b.deleted).collect();
        if b.is_empty() {
            return "You have no budgets yet. Create one on the Budgets page.".into();
        }
        let lines: Vec<String> = b.iter().map(|b| {
            let spent = crate::calc::spent(&s.data.transactions, &b.category_id, &b.period, s.today);
            let left = b.amount - spent;
            let name = s.data.categories.iter().find(|c| c.id == b.category_id).map(|c| c.name.as_str()).unwrap_or("Unknown");
            let state = if left < 0.0 { format!("over by {}", m(-left)) } else { format!("{} left", m(left)) };
            format!("• {name} ({}): {} of {}, {state}", b.period, m(spent), m(b.amount))
        }).collect();
        return format!("Budget status:\n{}", lines.join("\n"));
    }
    if has(q, &["balance", "net worth", "how much do i have", "total money"]) {
        let acc: Vec<&Account> = s.data.accounts.iter().filter(|a| !a.deleted).collect();
        if acc.is_empty() {
            return "You have no accounts yet. Add one on the Accounts page.".into();
        }
        let total: f64 = acc.iter().map(|a| a.balance).sum();
        let mut sorted = acc.clone();
        sorted.sort_by(|a, b| b.balance.partial_cmp(&a.balance).unwrap());
        let lines: Vec<String> = sorted.iter().map(|a| format!("• {}: {}", a.name, m(a.balance))).collect();
        return format!("Your total balance is {} across {}:\n{}", m(total), plural(acc.len(), "account", "accounts"), lines.join("\n"));
    }
    if has(q, &["categories", "by category", "top categor", "where does my money", "where did my money"]) {
        let ex = expenses(w);
        if ex.is_empty() {
            return format!("No expenses recorded {}.", w.label());
        }
        let total: f64 = ex.iter().map(|t| -t.amount).sum();
        let mut rows: Vec<(String, f64)> = Vec::new();
        for t in &ex {
            let name = s.data.category_name(&t.category_id).to_string();
            match rows.iter_mut().find(|r| r.0 == name) {
                Some(r) => r.1 += -t.amount,
                None => rows.push((name, -t.amount)),
            }
        }
        rows.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap());
        let lines: Vec<String> = rows.iter().take(5).map(|r| format!("• {}: {} ({}%)", r.0, m(r.1), (r.1 / total * 100.0) as i64)).collect();
        return format!("Top spending {} (total {}):\n{}", w.label(), m(total), lines.join("\n"));
    }
    if has(q, &["biggest", "largest", "most expensive"]) {
        let mut ex = expenses(w);
        if ex.is_empty() {
            return format!("No expenses recorded {}.", w.label());
        }
        ex.sort_by(|a, b| a.amount.partial_cmp(&b.amount).unwrap());
        let lines: Vec<String> = ex.iter().take(5).map(|t| format!("• {}: {} on {}", t.description, m(-t.amount), t.date)).collect();
        return format!("Biggest expenses {}:\n{}", w.label(), lines.join("\n"));
    }
    if has(q, &["recent", "latest", "last transactions"]) {
        let mut l = live.clone();
        l.sort_by(|a, b| b.date.cmp(&a.date));
        if l.is_empty() {
            return "No transactions yet.".into();
        }
        let lines: Vec<String> = l.iter().take(5).map(|t| format!("• {} {}: {}", t.date, t.description, m(t.amount))).collect();
        return format!("Most recent transactions:\n{}", lines.join("\n"));
    }
    if has(q, &["income", "earn", "salary"]) {
        let inc: Vec<&&Transaction> = live.iter().filter(|t| t.amount > 0.0 && in_win(t, w, s.today)).collect();
        if inc.is_empty() {
            return format!("No income recorded {}.", w.label());
        }
        return format!("You received {} {} in {}.", m(inc.iter().map(|t| t.amount).sum()), w.label(), plural(inc.len(), "deposit", "deposits"));
    }
    if has(q, &["saving", "saved"]) {
        let tx: Vec<&&Transaction> = live.iter().filter(|t| in_win(t, w, s.today)).collect();
        let inc: f64 = tx.iter().filter(|t| t.amount > 0.0).map(|t| t.amount).sum();
        let out: f64 = tx.iter().filter(|t| t.amount < 0.0).map(|t| -t.amount).sum();
        if inc == 0.0 && out == 0.0 {
            return format!("Nothing recorded {}.", w.label());
        }
        let rate = if inc > 0.0 { format!(" Savings rate: {}%.", ((inc - out) / inc * 100.0) as i64) } else { String::new() };
        return format!("{}: income {}, spending {}, net {}.{rate}", w.label(), m(inc), m(out), m(inc - out));
    }
    if q.contains("how many") && q.contains("transaction") {
        let n = s.data.accounts.iter().filter(|a| !a.deleted).count();
        return format!("You have {} across {}.", plural(live.len(), "transaction", "transactions"), plural(n, "account", "accounts"));
    }
    if has(q, &["spend", "spent", "expense", "cost"]) {
        if let Some(a) = by_name(q, s, w, &m) {
            return a;
        }
        let ex = expenses(w);
        if ex.is_empty() {
            return format!("No expenses recorded {}.", w.label());
        }
        return format!("You spent {} {} in {}.", m(ex.iter().map(|t| -t.amount).sum()), w.label(), plural(ex.len(), "transaction", "transactions"));
    }
    by_name(q, s, w, &m).unwrap_or_else(|| format!("I did not understand that. {}", help()))
}

const STOP: [&str; 15] = ["much", "spend", "spent", "this", "month", "week", "year", "what", "have", "money", "show", "tell", "about", "with", "from"];

fn by_name(q: &str, s: &Snapshot, w: Win, m: &dyn Fn(f64) -> String) -> Option<String> {
    let ex: Vec<&Transaction> = s.data.transactions.iter().filter(|t| !t.deleted && t.amount < 0.0 && in_win(t, w, s.today)).collect();
    let cat = s.data.categories.iter().filter(|c| !c.deleted && q.contains(&c.name.to_lowercase())).max_by_key(|c| c.name.len());
    if let Some(c) = cat {
        let hit: Vec<&&Transaction> = ex.iter().filter(|t| t.category_id.as_deref() == Some(c.id.as_str())).collect();
        return Some(if hit.is_empty() {
            format!("Nothing spent on {} {}.", c.name, w.label())
        } else {
            format!("You spent {} on {} {} ({}).", m(hit.iter().map(|t| -t.amount).sum()), c.name, w.label(), plural(hit.len(), "transaction", "transactions"))
        });
    }
    let word = q
        .split(|c: char| !c.is_ascii_alphanumeric())
        .filter(|x| x.len() >= 4 && !STOP.contains(x))
        .find(|x| s.data.transactions.iter().any(|t| !t.deleted && t.description.to_lowercase().contains(x)))?;
    let hit: Vec<&&Transaction> = ex.iter().filter(|t| t.description.to_lowercase().contains(word)).collect();
    Some(if hit.is_empty() {
        format!("No spending matching \"{word}\" {}.", w.label())
    } else {
        format!("You spent {} on \"{word}\" {} ({}).", m(hit.iter().map(|t| -t.amount).sum()), w.label(), plural(hit.len(), "transaction", "transactions"))
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn data() -> Data {
        let acc = |id: &str, name: &str, b: f64| Account { id: id.into(), name: name.into(), kind: "Checking".into(), balance: b, currency: "USD".into(), deleted: false, updated_at: 0 };
        let cat = |id: &str, name: &str| Category { id: id.into(), name: name.into(), parent_id: None, color: None, deleted: false, updated_at: 0 };
        let tx = |id: &str, amt: f64, desc: &str, c: Option<&str>, date: &str| Transaction {
            id: id.into(), account_id: "a1".into(), amount: amt, description: desc.into(), category_id: c.map(String::from),
            date: date.into(), notes: None, created_at: 0, deleted: false, updated_at: 0,
        };
        Data {
            accounts: vec![acc("a1", "Checking", 1500.0), acc("a2", "Savings", 1000.0)],
            categories: vec![cat("c1", "Groceries"), cat("c2", "Fun")],
            transactions: vec![
                tx("t1", -50.0, "Trader Joes", Some("c1"), "2025-06-10"),
                tx("t2", -30.0, "Cinema night", Some("c2"), "2025-06-11"),
                tx("t3", 2000.0, "Paycheck", None, "2025-06-01"),
                tx("t4", -999.0, "Old thing", Some("c2"), "2024-01-01"),
            ],
            budgets: vec![Budget { id: "b1".into(), category_id: "c1".into(), amount: 200.0, period: "monthly".into(), start_date: String::new(), deleted: false, updated_at: 0 }],
        }
    }

    fn ask(q: &str) -> String {
        let d = data();
        let s = Snapshot { data: &d, recurring: &[], goals: &[], debts: &[], currency: "USD", today: parse_date("2025-06-15").unwrap() };
        answer(q, &s)
    }

    #[test]
    fn balance_and_spending() {
        assert!(ask("What is my balance?").contains("$2,500.00"));
        assert!(ask("how much did I spend this month").contains("$80.00"));
        assert!(ask("how much did I spend on groceries").contains("$50.00"));
        assert!(ask("how much on cinema").contains("$30.00"));
    }

    #[test]
    fn windows_and_budgets() {
        assert!(ask("top spending categories").contains("Groceries"));
        assert!(!ask("top categories this month").contains("$999"));
        assert!(ask("how much did I spend overall").contains("$1,079.00"));
        assert!(ask("how are my budgets").contains("$150.00 left"));
        assert!(ask("income this month").contains("$2,000.00"));
    }

    #[test]
    fn extras_and_unknowns() {
        let d = data();
        let goals = vec![Goal { id: "g".into(), name: "Trip".into(), target: 1000.0, saved: 250.0, due: None }];
        let debts = vec![Debt { id: "d".into(), name: "Card".into(), balance: 1000.0, apr: 0.0, min_payment: 100.0 }];
        let rec = vec![Recurring { id: "r".into(), name: "Rent".into(), amount: -900.0, account_id: "a1".into(), category_id: None, frequency: "monthly".into(), next_date: "2025-07-01".into(), active: true }];
        let s = Snapshot { data: &d, recurring: &rec, goals: &goals, debts: &debts, currency: "USD", today: parse_date("2025-06-15").unwrap() };
        assert!(answer("my goals", &s).contains("25%"));
        assert!(answer("how much debt", &s).contains("10 months"));
        assert!(answer("my recurring bills", &s).contains("Rent"));
        assert!(answer("zzzz qqqq", &s).contains("did not understand"));
        assert!(answer("", &s).contains("on this computer"));
    }
}
