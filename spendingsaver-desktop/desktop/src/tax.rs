//! US federal tax estimate for planning, the same engine as the Android app (tax years 2025 and 2026).
//! Not tax advice. Leaves out the business-income deduction, AMT, capital-gain rates and most credits.
//! Figures: IRS Rev. Proc. 2025-32 (2026) and the 2025 law; the Social Security wage base is from the SSA.

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Filing {
    Single,
    MarriedJoint,
    MarriedSeparate,
    HeadOfHousehold,
}

impl Filing {
    pub const ALL: [Filing; 4] = [Filing::Single, Filing::MarriedJoint, Filing::MarriedSeparate, Filing::HeadOfHousehold];
    pub fn label(self) -> &'static str {
        match self {
            Filing::Single => "Single",
            Filing::MarriedJoint => "Married filing jointly",
            Filing::MarriedSeparate => "Married filing separately",
            Filing::HeadOfHousehold => "Head of household",
        }
    }
}

pub const RATES: [f64; 7] = [0.10, 0.12, 0.22, 0.24, 0.32, 0.35, 0.37];
pub const YEARS: [i32; 2] = [2026, 2025];
pub const NO_TAX_STATES: [&str; 9] = ["AK", "FL", "NV", "NH", "SD", "TN", "TX", "WA", "WY"];
pub const STATES: [(&str, &str); 51] = [
    ("AL", "Alabama"), ("AK", "Alaska"), ("AZ", "Arizona"), ("AR", "Arkansas"), ("CA", "California"), ("CO", "Colorado"),
    ("CT", "Connecticut"), ("DE", "Delaware"), ("DC", "District of Columbia"), ("FL", "Florida"), ("GA", "Georgia"),
    ("HI", "Hawaii"), ("ID", "Idaho"), ("IL", "Illinois"), ("IN", "Indiana"), ("IA", "Iowa"), ("KS", "Kansas"),
    ("KY", "Kentucky"), ("LA", "Louisiana"), ("ME", "Maine"), ("MD", "Maryland"), ("MA", "Massachusetts"),
    ("MI", "Michigan"), ("MN", "Minnesota"), ("MS", "Mississippi"), ("MO", "Missouri"), ("MT", "Montana"),
    ("NE", "Nebraska"), ("NV", "Nevada"), ("NH", "New Hampshire"), ("NJ", "New Jersey"), ("NM", "New Mexico"),
    ("NY", "New York"), ("NC", "North Carolina"), ("ND", "North Dakota"), ("OH", "Ohio"), ("OK", "Oklahoma"),
    ("OR", "Oregon"), ("PA", "Pennsylvania"), ("RI", "Rhode Island"), ("SC", "South Carolina"), ("SD", "South Dakota"),
    ("TN", "Tennessee"), ("TX", "Texas"), ("UT", "Utah"), ("VT", "Vermont"), ("VA", "Virginia"), ("WA", "Washington"),
    ("WV", "West Virginia"), ("WI", "Wisconsin"), ("WY", "Wyoming"),
];

struct YearTable {
    standard: [f64; 4], // single, mfj, mfs, hoh
    tops: [[f64; 6]; 4],
    ss_base: f64,
    child_credit: f64,
}

fn idx(f: Filing) -> usize {
    match f {
        Filing::Single => 0,
        Filing::MarriedJoint => 1,
        Filing::MarriedSeparate => 2,
        Filing::HeadOfHousehold => 3,
    }
}

fn table(year: i32) -> YearTable {
    if year == 2025 {
        YearTable {
            standard: [15_750.0, 31_500.0, 15_750.0, 23_625.0],
            tops: [
                [11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 626_350.0],
                [23_850.0, 96_950.0, 206_700.0, 394_600.0, 501_050.0, 751_600.0],
                [11_925.0, 48_475.0, 103_350.0, 197_300.0, 250_525.0, 375_800.0],
                [17_000.0, 64_850.0, 103_350.0, 197_300.0, 250_500.0, 626_350.0],
            ],
            ss_base: 176_100.0,
            child_credit: 2_200.0,
        }
    } else {
        YearTable {
            standard: [16_100.0, 32_200.0, 16_100.0, 24_150.0],
            tops: [
                [12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 640_600.0],
                [24_800.0, 100_800.0, 211_400.0, 403_550.0, 512_450.0, 768_700.0],
                [12_400.0, 50_400.0, 105_700.0, 201_775.0, 256_225.0, 384_350.0],
                [17_700.0, 67_450.0, 105_700.0, 201_750.0, 256_200.0, 640_600.0],
            ],
            ss_base: 184_500.0,
            child_credit: 2_200.0,
        }
    }
}

pub fn standard_deduction(year: i32, f: Filing) -> f64 {
    table(year).standard[idx(f)]
}

#[derive(Clone, Debug)]
pub struct Profile {
    pub year: i32,
    pub filing: Filing,
    pub wages: f64,
    pub self_employment: f64,
    pub other_income: f64,
    pub adjustments: f64,
    pub use_itemized: bool,
    pub itemized: f64,
    pub children: u32,
    pub withheld: f64,
    pub estimated_paid: f64,
    pub state: String,
    pub state_rate_pct: f64,
}

impl Default for Profile {
    fn default() -> Self {
        Profile {
            year: 2026, filing: Filing::Single, wages: 0.0, self_employment: 0.0, other_income: 0.0, adjustments: 0.0,
            use_itemized: false, itemized: 0.0, children: 0, withheld: 0.0, estimated_paid: 0.0, state: String::new(), state_rate_pct: 0.0,
        }
    }
}

#[derive(Clone, Debug, Default)]
pub struct BracketRow {
    pub rate: f64,
    pub taxed: f64,
    pub tax: f64,
}

#[derive(Clone, Debug, Default)]
pub struct Result {
    pub gross: f64,
    pub agi: f64,
    pub deduction: f64,
    pub taxable: f64,
    pub brackets: Vec<BracketRow>,
    pub income_tax_before_credits: f64,
    pub child_credit: f64,
    pub income_tax: f64,
    pub se_tax: f64,
    pub additional_medicare: f64,
    pub fica: f64,
    pub state_tax: f64,
    pub marginal: f64,
    pub due: f64,
    pub paid: f64,
    /// Positive: still owed. Negative: refund.
    pub balance: f64,
    pub effective: f64,
    pub quarterly: f64,
}

pub fn estimate(p: &Profile) -> Result {
    let t = table(p.year);
    let wages = p.wages.max(0.0);
    let se = p.self_employment.max(0.0);
    let other = p.other_income.max(0.0);

    let se_base = se * 0.9235;
    let se_ss = 0.124 * se_base.min((t.ss_base - wages).max(0.0));
    let se_med = 0.029 * se_base;
    let se_tax = if se_base < 400.0 { 0.0 } else { se_ss + se_med };

    let gross = wages + se + other;
    let agi = (gross - 0.5 * se_tax - p.adjustments.max(0.0)).max(0.0);
    let deduction = if p.use_itemized { p.itemized.max(0.0) } else { t.standard[idx(p.filing)] };
    let taxable = (agi - deduction).max(0.0);

    let tops = t.tops[idx(p.filing)];
    let mut rows = Vec::new();
    let mut lower = 0.0;
    let mut before = 0.0;
    for (i, rate) in RATES.iter().enumerate() {
        let upper = tops.get(i).copied().unwrap_or(f64::MAX);
        let slice = (taxable.min(upper) - lower).max(0.0);
        rows.push(BracketRow { rate: *rate, taxed: slice, tax: slice * rate });
        before += slice * rate;
        lower = upper;
    }

    let phase = if p.filing == Filing::MarriedJoint { 400_000.0 } else { 200_000.0 };
    let reduction = ((agi - phase).max(0.0) / 1000.0).ceil() * 50.0;
    let credit = before.min((p.children as f64 * t.child_credit - reduction).max(0.0));
    let income_tax = before - credit;

    let med_threshold = match p.filing {
        Filing::MarriedJoint => 250_000.0,
        Filing::MarriedSeparate => 125_000.0,
        _ => 200_000.0,
    };
    let add_med = 0.009 * (wages + se_base - med_threshold).max(0.0);
    let fica = 0.062 * wages.min(t.ss_base) + 0.0145 * wages;

    let rate = if NO_TAX_STATES.contains(&p.state.as_str()) { 0.0 } else { p.state_rate_pct.max(0.0) / 100.0 };
    let state_tax = taxable * rate;

    let due = income_tax + se_tax + add_med + state_tax;
    let paid = p.withheld.max(0.0) + p.estimated_paid.max(0.0);
    let marginal = if taxable <= 0.0 { 0.0 } else { rows.iter().rev().find(|r| r.taxed > 0.0).map(|r| r.rate).unwrap_or(0.0) };
    Result {
        gross, agi, deduction, taxable, brackets: rows, income_tax_before_credits: before, child_credit: credit, income_tax,
        se_tax, additional_medicare: add_med, fica, state_tax, marginal, due, paid, balance: due - paid,
        effective: if gross > 0.0 { (due + fica) / gross } else { 0.0 },
        quarterly: (due - p.withheld.max(0.0)).max(0.0) / 4.0,
    }
}

pub fn quarter_dates(year: i32) -> [String; 4] {
    [format!("Apr 15, {year}"), format!("Jun 15, {year}"), format!("Sep 15, {year}"), format!("Jan 15, {}", year + 1)]
}

#[cfg(test)]
mod tests {
    use super::*;

    fn p(f: impl FnOnce(&mut Profile)) -> Profile {
        let mut x = Profile::default();
        f(&mut x);
        x
    }

    #[test]
    fn matches_the_android_app() {
        let r = estimate(&p(|x| x.wages = 60_000.0));
        assert_eq!(r.taxable, 43_900.0);
        assert!((r.income_tax - 5_020.0).abs() < 0.01);
        assert_eq!(r.marginal, 0.12);
        assert!((r.fica - 4_590.0).abs() < 0.01);
    }

    #[test]
    fn married_joint_2025() {
        let r = estimate(&p(|x| { x.year = 2025; x.filing = Filing::MarriedJoint; x.wages = 100_000.0 }));
        assert!((r.income_tax - 7_743.0).abs() < 0.01);
    }

    #[test]
    fn self_employment_and_half_deduction() {
        let r = estimate(&p(|x| x.self_employment = 50_000.0));
        let se = 50_000.0 * 0.9235 * 0.153;
        assert!((r.se_tax - se).abs() < 0.01);
        assert!((r.agi - (50_000.0 - se / 2.0)).abs() < 0.01);
    }

    #[test]
    fn child_credit_never_goes_below_zero_tax() {
        let r = estimate(&p(|x| { x.wages = 25_000.0; x.children = 3 }));
        assert_eq!(r.income_tax, 0.0);
    }

    #[test]
    fn no_tax_states_ignore_the_rate() {
        let r = estimate(&p(|x| { x.wages = 60_000.0; x.state = "TX".into(); x.state_rate_pct = 5.0 }));
        assert_eq!(r.state_tax, 0.0);
        let r = estimate(&p(|x| { x.wages = 60_000.0; x.state = "CA".into(); x.state_rate_pct = 5.0 }));
        assert!((r.state_tax - 43_900.0 * 0.05).abs() < 0.01);
    }

    #[test]
    fn refund_when_overpaid() {
        let r = estimate(&p(|x| { x.wages = 60_000.0; x.withheld = 9_000.0 }));
        assert!(r.balance < 0.0);
        assert_eq!(r.quarterly, 0.0);
    }
}
