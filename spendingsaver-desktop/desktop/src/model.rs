//! Data model. Field names match the Android app's JSON backup (camelCase), so one backup file
//! moves between the phone and the desktop with no server involved.
use serde::{Deserialize, Serialize};

pub fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

pub fn new_id() -> String {
    uuid::Uuid::new_v4().to_string()
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: String,
    pub name: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub balance: f64,
    #[serde(default = "usd")]
    pub currency: String,
    #[serde(default)]
    pub deleted: bool,
    #[serde(default)]
    pub updated_at: i64,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Category {
    pub id: String,
    pub name: String,
    #[serde(default)]
    pub parent_id: Option<String>,
    #[serde(default)]
    pub color: Option<String>,
    #[serde(default)]
    pub deleted: bool,
    #[serde(default)]
    pub updated_at: i64,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Transaction {
    pub id: String,
    pub account_id: String,
    /// Positive is income, negative is an expense.
    pub amount: f64,
    pub description: String,
    #[serde(default)]
    pub category_id: Option<String>,
    /// yyyy-MM-dd
    pub date: String,
    #[serde(default)]
    pub notes: Option<String>,
    #[serde(default)]
    pub created_at: i64,
    #[serde(default)]
    pub deleted: bool,
    #[serde(default)]
    pub updated_at: i64,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Budget {
    pub id: String,
    pub category_id: String,
    pub amount: f64,
    /// weekly | monthly | yearly
    pub period: String,
    #[serde(default)]
    pub start_date: String,
    #[serde(default)]
    pub deleted: bool,
    #[serde(default)]
    pub updated_at: i64,
}

fn usd() -> String {
    "USD".into()
}

/// The user's data, as held in memory. Reloaded from the database after every write.
#[derive(Clone, Debug, Default)]
pub struct Data {
    pub accounts: Vec<Account>,
    pub categories: Vec<Category>,
    pub transactions: Vec<Transaction>,
    pub budgets: Vec<Budget>,
}

impl Data {
    pub fn category_name(&self, id: &Option<String>) -> &str {
        id.as_ref()
            .and_then(|i| self.categories.iter().find(|c| &c.id == i))
            .map(|c| c.name.as_str())
            .unwrap_or("Uncategorized")
    }
    pub fn account_name(&self, id: &str) -> &str {
        self.accounts.iter().find(|a| a.id == id).map(|a| a.name.as_str()).unwrap_or("Unknown")
    }
}

/// The backup document, identical to the Android app's.
#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    pub version: i32,
    #[serde(default)]
    pub exported_at: i64,
    #[serde(default)]
    pub accounts: Vec<Account>,
    #[serde(default)]
    pub categories: Vec<Category>,
    #[serde(default)]
    pub transactions: Vec<Transaction>,
    #[serde(default)]
    pub budgets: Vec<Budget>,
}

pub const BACKUP_VERSION: i32 = 1;
