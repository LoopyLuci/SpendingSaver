package com.spendingsaver.classic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * SpendingSaver Classic: the whole app for old Android (4.0.3 and up), written with plain system views and
 * no libraries. It keeps its own data on this device (see Store) and reads and writes the same backup file
 * as the other SpendingSaver apps.
 */
public class MainActivity extends Activity {
    private static final int TEAL = 0xFF00695C, BG = 0xFFF4F7F6, RED = 0xFFB3261E, GREY = 0xFF5F6B68, INK = 0xFF1B1F1E;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final String[] ACCOUNT_TYPES = {"Checking", "Savings", "Credit card", "Cash", "Investment", "Retirement"};
    private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "CAD", "AUD", "JPY", "CHF", "INR", "MXN", "BRL"};
    private static final String BACKUP_NAME = "spendingsaver-backup.json";

    private Store store;
    private LinearLayout body;
    private TextView title;
    private ScrollView scroll;
    private Runnable current;
    private final ArrayList<Runnable> stack = new ArrayList<Runnable>();
    private Runnable home, pendingAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(getFilesDir());
        home = new Runnable() { public void run() { showHome(); } };

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        title = tv("SpendingSaver", 20, Color.WHITE, true);
        title.setPadding(dp(16), dp(14), dp(16), dp(14));
        title.setBackgroundColor(TEAL);
        root.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));

        scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(8), dp(12), dp(16));
        scroll.addView(body, new ScrollView.LayoutParams(MATCH, WRAP));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xFFE3EBE9);
        addNav(nav, "Home", home);
        addNav(nav, "Activity", new Runnable() { public void run() { showTransactions(); } });
        addNav(nav, "Accounts", new Runnable() { public void run() { showAccounts(); } });
        addNav(nav, "Budgets", new Runnable() { public void run() { showBudgets(); } });
        addNav(nav, "More", new Runnable() { public void run() { showMore(); } });
        root.addView(nav, new LinearLayout.LayoutParams(MATCH, WRAP));

        setContentView(root);
        reset(home);
    }

    // ------------------------------------------------------------------ navigation

    private void reset(Runnable r) {
        stack.clear();
        current = r;
        r.run();
    }

    private void open(Runnable r) {
        if (current != null) stack.add(current);
        current = r;
        r.run();
    }

    private void refresh() { current.run(); }

    private void back() {
        hideKeyboard();
        if (!stack.isEmpty()) {
            current = stack.remove(stack.size() - 1);
            current.run();
        } else if (current != home) {
            reset(home);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() { back(); }

    private void addNav(LinearLayout nav, String label, final Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setSingleLine(true);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hideKeyboard(); reset(r); } });
        nav.addView(b, new LinearLayout.LayoutParams(0, WRAP, 1f));
    }

    private void hideKeyboard() {
        View v = getCurrentFocus();
        if (v != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ view helpers

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams weighted(int l, int r) {
        LinearLayout.LayoutParams p = lp(0, WRAP, l, 0, r, 0);
        p.weight = 1f;
        return p;
    }

    private void add(View v) { body.addView(v, lp(MATCH, WRAP, 0, 5, 0, 5)); }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), color == Color.WHITE ? 0xFFD5DEDB : color);
        l.setBackgroundDrawable(g);
        l.setPadding(dp(14), dp(12), dp(14), dp(12));
        return l;
    }

    private LinearLayout card() { return card(Color.WHITE); }

    private Button button(String label, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(c);
        return b;
    }

    private void screen(String name) {
        body.removeAllViews();
        title.setText(name);
        scroll.scrollTo(0, 0);
    }

    private TextView label(String s) {
        TextView t = tv(s, 13, GREY, false);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private EditText edit(String text, int inputType) {
        EditText e = new EditText(this);
        e.setText(text == null ? "" : text);
        e.setInputType(inputType);
        return e;
    }

    /** A text box that also offers a drop-down of suggestions; anything can still be typed by hand. */
    private AutoCompleteTextView auto(String text, ArrayList<String> options) {
        final AutoCompleteTextView a = new AutoCompleteTextView(this);
        a.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, options));
        a.setThreshold(1);
        a.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        a.setText(text == null ? "" : text);
        a.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { a.showDropDown(); } });
        return a;
    }

    private Spinner spinner(ArrayList<String> labels, int selected) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        if (selected >= 0 && selected < labels.size()) s.setSelection(selected);
        return s;
    }

    private ArrayList<String> list(String... items) { return new ArrayList<String>(Arrays.asList(items)); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private void confirm(String message, String yes, final Runnable onYes) {
        new AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(yes, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { onYes.run(); } })
            .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void saveOrWarn() {
        if (!store.save()) toast("Could not save. Is the device storage full?");
    }

    private View.OnClickListener openScreen(final Runnable r) {
        return new View.OnClickListener() { public void onClick(View v) { open(r); } };
    }

    private final Runnable accountForm = new Runnable() { public void run() { showAccountForm(); } };
    private final Runnable newTxnForm = new Runnable() { public void run() { showTxnForm(null); } };

    // ------------------------------------------------------------------ calculations

    private String primaryCurrency() {
        HashMap<String, Integer> n = new HashMap<String, Integer>();
        String best = "USD";
        int bestN = 0;
        for (Store.Account a : store.activeAccounts()) {
            Integer c = n.get(a.currency);
            int v = c == null ? 1 : c + 1;
            n.put(a.currency, v);
            if (v > bestN) { bestN = v; best = a.currency; }
        }
        return best;
    }

    private String accountName(String id) {
        Store.Account a = store.account(id);
        return a == null ? "?" : a.name;
    }

    private String categoryName(String id) {
        Store.Category c = store.category(id);
        return c == null || c.deleted ? "Uncategorized" : c.name;
    }

    private String currencyOf(Store.Txn t) {
        Store.Account a = store.account(t.accountId);
        return a == null ? "USD" : a.currency;
    }

    private ArrayList<Store.Txn> sortedTransactions() {
        ArrayList<Store.Txn> r = store.liveTransactions();
        Collections.sort(r, new Comparator<Store.Txn>() {
            public int compare(Store.Txn a, Store.Txn b) {
                int c = b.date.compareTo(a.date);
                return c != 0 ? c : (a.createdAt < b.createdAt ? 1 : (a.createdAt > b.createdAt ? -1 : 0));
            }
        });
        return r;
    }

    /** How much of this budget is used right now: expenses in its category within its period. */
    private long spent(Store.Budget b) {
        String today = Fmt.today();
        String from, to = today;
        if (b.period.equals("weekly")) {
            from = Fmt.daysAgo(6);
        } else if (b.period.equals("yearly")) {
            from = today.substring(0, 4) + "-01-01";
            to = today.substring(0, 4) + "-12-31";
        } else {
            from = today.substring(0, 7) + "-01";
            to = today.substring(0, 7) + "-31";
        }
        long sum = 0;
        for (Store.Txn t : store.liveTransactions()) {
            if (t.cents < 0 && b.categoryId.equals(t.categoryId) && t.date.compareTo(from) >= 0 && t.date.compareTo(to) <= 0) sum -= t.cents;
        }
        return sum;
    }

    // ------------------------------------------------------------------ home

    private void showHome() {
        screen("SpendingSaver");
        final String cur = primaryCurrency();
        ArrayList<Store.Account> accounts = store.activeAccounts();

        LinearLayout hero = card(TEAL);
        hero.addView(tv("NET WORTH", 12, 0xFFB2DFDB, true));
        HashMap<String, Long> totals = new HashMap<String, Long>();
        for (Store.Account a : accounts) {
            Long t = totals.get(a.currency);
            totals.put(a.currency, (t == null ? 0 : t) + a.cents);
        }
        Long main = totals.get(cur);
        hero.addView(tv(Fmt.money(main == null ? 0 : main, cur), 30, Color.WHITE, true));
        for (String c : totals.keySet()) if (!c.equals(cur)) hero.addView(tv(Fmt.money(totals.get(c), c), 15, 0xFFE0F2F1, false));
        add(hero);

        String month = Fmt.today().substring(0, 7);
        long income = 0, expenses = 0;
        for (Store.Txn t : store.liveTransactions()) {
            if (!currencyOf(t).equals(cur) || !t.date.startsWith(month)) continue;
            if (t.cents > 0) income += t.cents; else expenses -= t.cents;
        }
        LinearLayout row = new LinearLayout(this);
        LinearLayout in = card(), out = card();
        in.addView(tv("Income this month", 12, GREY, false));
        in.addView(tv(Fmt.money(income, cur), 18, TEAL, true));
        out.addView(tv("Spent this month", 12, GREY, false));
        out.addView(tv(Fmt.money(expenses, cur), 18, RED, true));
        row.addView(in, weighted(0, 5));
        row.addView(out, weighted(5, 0));
        add(row);

        if (accounts.isEmpty()) {
            LinearLayout c = card();
            c.addView(tv("Welcome! Start by adding an account, such as your checking account.", 15, INK, false));
            c.addView(button("Add an account", openScreen(accountForm)));
            add(c);
        } else {
            add(button("Add a transaction", openScreen(newTxnForm)));
        }

        ArrayList<Store.Txn> recent = sortedTransactions();
        if (!recent.isEmpty()) {
            add(label("Recent activity"));
            for (int i = 0; i < Math.min(5, recent.size()); i++) add(txnRow(recent.get(i), false));
        }
    }

    private View txnRow(final Store.Txn t, boolean actions) {
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(tv(t.description, 16, INK, true));
        left.addView(tv(t.date + "  ·  " + accountName(t.accountId) + "  ·  " + categoryName(t.categoryId), 12, GREY, false));
        top.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView amt = tv(Fmt.money(t.cents, currencyOf(t)), 16, t.cents < 0 ? RED : TEAL, true);
        amt.setGravity(Gravity.RIGHT);
        top.addView(amt, new LinearLayout.LayoutParams(WRAP, WRAP));
        c.addView(top);
        if (actions) {
            LinearLayout bar = new LinearLayout(this);
            bar.addView(button("Edit", openScreen(new Runnable() { public void run() { showTxnForm(t); } })), new LinearLayout.LayoutParams(0, WRAP, 1f));
            bar.addView(button("Delete", new View.OnClickListener() {
                public void onClick(View v) {
                    confirm("Delete \"" + t.description + "\"?", "Delete", new Runnable() {
                        public void run() { t.deleted = true; t.updatedAt = System.currentTimeMillis(); saveOrWarn(); refresh(); }
                    });
                }
            }), new LinearLayout.LayoutParams(0, WRAP, 1f));
            c.addView(bar);
        }
        return c;
    }

    // ------------------------------------------------------------------ transactions

    private void showTransactions() {
        screen("Activity");
        add(button("Add a transaction", new View.OnClickListener() {
            public void onClick(View v) {
                if (store.activeAccounts().isEmpty()) { toast("Add an account first."); return; }
                open(newTxnForm);
            }
        }));
        ArrayList<Store.Txn> all = sortedTransactions();
        if (all.isEmpty()) add(tv("No transactions yet. Record your first income or expense.", 15, GREY, false));
        for (int i = 0; i < Math.min(300, all.size()); i++) add(txnRow(all.get(i), true));
        if (all.size() > 300) add(tv("Showing the newest 300 of " + all.size() + ".", 12, GREY, false));
    }

    private void showTxnForm(final Store.Txn existing) {
        screen(existing == null ? "New transaction" : "Edit transaction");
        final ArrayList<Store.Account> accounts = store.activeAccounts();
        if (accounts.isEmpty()) {
            add(tv("Add an account first.", 16, INK, false));
            return;
        }
        final ArrayList<Store.Category> cats = store.activeCategories();

        final Spinner kind = spinner(list("Expense", "Income"), existing != null && existing.cents > 0 ? 1 : 0);
        LinkedHashSet<String> past = new LinkedHashSet<String>();
        for (Store.Txn t : sortedTransactions()) {
            past.add(t.description);
            if (past.size() >= 40) break;
        }
        final AutoCompleteTextView desc = auto(existing == null ? "" : existing.description, new ArrayList<String>(past));
        String amountText = "";
        if (existing != null) {
            long abs = Math.abs(existing.cents);
            amountText = (abs / 100) + "." + (abs % 100 < 10 ? "0" : "") + (abs % 100);
        }
        final EditText amount = edit(amountText, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText date = edit(existing == null ? Fmt.today() : existing.date, InputType.TYPE_CLASS_TEXT);
        ArrayList<String> accLabels = new ArrayList<String>();
        int accSel = 0;
        for (int i = 0; i < accounts.size(); i++) {
            accLabels.add(accounts.get(i).name);
            if (existing != null && accounts.get(i).id.equals(existing.accountId)) accSel = i;
        }
        final Spinner account = spinner(accLabels, accSel);
        ArrayList<String> catLabels = list("(no category)");
        int catSel = 0;
        for (int i = 0; i < cats.size(); i++) {
            catLabels.add(cats.get(i).name);
            if (existing != null && cats.get(i).id.equals(existing.categoryId)) catSel = i + 1;
        }
        final Spinner category = spinner(catLabels, catSel);
        final EditText notes = edit(existing == null ? "" : existing.notes, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        add(label("Type")); add(kind);
        add(label("Description (pick a past one or type)")); add(desc);
        add(label("Amount")); add(amount);
        add(label("Date (yyyy-MM-dd)"));
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.addView(date, new LinearLayout.LayoutParams(0, WRAP, 1f));
        dateRow.addView(button("Pick", new View.OnClickListener() {
            public void onClick(View v) {
                Calendar c = Calendar.getInstance();
                if (Fmt.isDate(date.getText().toString())) {
                    String[] p = date.getText().toString().split("-");
                    c.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]));
                }
                new DatePickerDialog(MainActivity.this, new DatePickerDialog.OnDateSetListener() {
                    public void onDateSet(DatePicker view, int y, int m, int d) {
                        date.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d));
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            }
        }));
        add(dateRow);
        add(label("Account")); add(account);
        add(label("Category")); add(category);
        add(label("Notes (optional)")); add(notes);

        LinearLayout bar = new LinearLayout(this);
        bar.addView(button("Cancel", new View.OnClickListener() { public void onClick(View v) { back(); } }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(button("Save", new View.OnClickListener() {
            public void onClick(View v) {
                String d = desc.getText().toString().trim();
                Long cents = Fmt.parseCents(amount.getText().toString(), false);
                String when = date.getText().toString().trim();
                if (d.length() == 0) { toast("Please enter a description."); return; }
                if (d.length() > 200) { toast("The description is too long."); return; }
                if (cents == null || cents == 0) { toast("Please enter an amount above zero."); return; }
                if (!Fmt.isDate(when)) { toast("Please enter the date as yyyy-MM-dd, for example 2026-11-27."); return; }
                Store.Txn t = existing != null ? existing : new Store.Txn();
                if (existing == null) {
                    t.id = Store.newId();
                    t.createdAt = System.currentTimeMillis();
                    store.data.transactions.add(t);
                }
                t.description = d;
                t.cents = kind.getSelectedItemPosition() == 1 ? cents : -cents;
                t.date = when;
                t.accountId = accounts.get(account.getSelectedItemPosition()).id;
                int ci = category.getSelectedItemPosition();
                t.categoryId = ci == 0 ? null : cats.get(ci - 1).id;
                String n = notes.getText().toString().trim();
                t.notes = n.length() == 0 ? null : n;
                t.updatedAt = System.currentTimeMillis();
                saveOrWarn();
                back();
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        add(bar);
    }

    // ------------------------------------------------------------------ accounts

    private void showAccounts() {
        screen("Accounts");
        add(button("Add an account", openScreen(accountForm)));
        ArrayList<Store.Account> accounts = store.activeAccounts();
        if (accounts.isEmpty()) add(tv("No accounts yet.", 15, GREY, false));
        for (final Store.Account a : accounts) {
            LinearLayout c = card();
            LinearLayout top = new LinearLayout(this);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(tv(a.name, 17, INK, true));
            left.addView(tv(a.type + "  ·  " + a.currency, 12, GREY, false));
            top.addView(left, new LinearLayout.LayoutParams(0, WRAP, 1f));
            top.addView(tv(Fmt.money(a.cents, a.currency), 17, a.cents < 0 ? RED : TEAL, true));
            c.addView(top);
            c.addView(button("Delete", new View.OnClickListener() {
                public void onClick(View v) {
                    confirm("Delete the account \"" + a.name + "\"? Its transactions will no longer be counted.", "Delete", new Runnable() {
                        public void run() { a.deleted = true; a.updatedAt = System.currentTimeMillis(); saveOrWarn(); refresh(); }
                    });
                }
            }));
            add(c);
        }
    }

    private void showAccountForm() {
        screen("New account");
        final EditText name = edit("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        final AutoCompleteTextView type = auto("Checking", new ArrayList<String>(Arrays.asList(ACCOUNT_TYPES)));
        final EditText balance = edit("", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final AutoCompleteTextView currency = auto("USD", new ArrayList<String>(Arrays.asList(CURRENCIES)));
        add(label("Account name")); add(name);
        add(label("Type (pick one or type your own)")); add(type);
        add(label("Starting balance (blank = 0, negative for debt)")); add(balance);
        add(label("Currency (pick one or type its 3-letter code)")); add(currency);
        LinearLayout bar = new LinearLayout(this);
        bar.addView(button("Cancel", new View.OnClickListener() { public void onClick(View v) { back(); } }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(button("Add account", new View.OnClickListener() {
            public void onClick(View v) {
                String n = name.getText().toString().trim(), ty = type.getText().toString().trim();
                String cu = currency.getText().toString().trim().toUpperCase(Locale.US);
                String bal = balance.getText().toString().trim();
                Long cents = bal.length() == 0 ? Long.valueOf(0) : Fmt.parseCents(bal, true);
                if (n.length() == 0 || n.length() > 120) { toast("Please enter a name."); return; }
                if (ty.length() == 0) { toast("Please choose or type an account type."); return; }
                if (cents == null) { toast("The starting balance is not a number."); return; }
                if (!cu.matches("[A-Z]{3}")) { toast("The currency must be a 3-letter code such as USD."); return; }
                Store.Account a = new Store.Account();
                a.id = Store.newId();
                a.name = n;
                a.type = ty.toLowerCase(Locale.US);
                a.cents = cents;
                a.currency = cu;
                a.updatedAt = System.currentTimeMillis();
                store.data.accounts.add(a);
                saveOrWarn();
                back();
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        add(bar);
    }

    // ------------------------------------------------------------------ budgets

    private void showBudgets() {
        screen("Budgets");
        add(button("Add a budget", new View.OnClickListener() {
            public void onClick(View v) {
                if (store.activeCategories().isEmpty()) { toast("Add a category first."); return; }
                open(new Runnable() { public void run() { showBudgetForm(); } });
            }
        }));
        boolean any = false;
        String cur = primaryCurrency();
        for (final Store.Budget b : store.data.budgets) {
            if (b.deleted) continue;
            any = true;
            LinearLayout c = card();
            long used = spent(b);
            int pct = b.cents <= 0 ? 0 : (int) Math.min(100, used * 100 / b.cents);
            c.addView(tv(categoryName(b.categoryId) + "  ·  " + b.period, 16, INK, true));
            c.addView(tv(Fmt.money(used, cur) + " of " + Fmt.money(b.cents, cur), 14, used > b.cents ? RED : GREY, false));
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(pct);
            c.addView(bar, new LinearLayout.LayoutParams(MATCH, WRAP));
            c.addView(button("Delete", new View.OnClickListener() {
                public void onClick(View v) {
                    confirm("Delete this budget?", "Delete", new Runnable() {
                        public void run() { b.deleted = true; b.updatedAt = System.currentTimeMillis(); saveOrWarn(); refresh(); }
                    });
                }
            }));
            add(c);
        }
        if (!any) add(tv("No budgets yet. A budget limits what you spend in a category.", 15, GREY, false));
    }

    private void showBudgetForm() {
        screen("New budget");
        final ArrayList<Store.Category> cats = store.activeCategories();
        ArrayList<String> labels = new ArrayList<String>();
        for (Store.Category c : cats) labels.add(c.name);
        final Spinner category = spinner(labels, 0);
        final EditText amount = edit("", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final Spinner period = spinner(list("monthly", "weekly", "yearly"), 0);
        add(label("Category")); add(category);
        add(label("Limit")); add(amount);
        add(label("Period")); add(period);
        LinearLayout bar = new LinearLayout(this);
        bar.addView(button("Cancel", new View.OnClickListener() { public void onClick(View v) { back(); } }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(button("Add budget", new View.OnClickListener() {
            public void onClick(View v) {
                Long cents = Fmt.parseCents(amount.getText().toString(), false);
                if (cents == null || cents == 0) { toast("Please enter a limit above zero."); return; }
                Store.Budget b = new Store.Budget();
                b.id = Store.newId();
                b.categoryId = cats.get(category.getSelectedItemPosition()).id;
                b.cents = cents;
                b.period = (String) period.getSelectedItem();
                b.startDate = Fmt.today();
                b.updatedAt = System.currentTimeMillis();
                store.data.budgets.add(b);
                saveOrWarn();
                back();
            }
        }), new LinearLayout.LayoutParams(0, WRAP, 1f));
        add(bar);
    }

    // ------------------------------------------------------------------ more: categories, backup, about

    private void showMore() {
        screen("More");
        add(button("Categories", openScreen(new Runnable() { public void run() { showCategories(); } })));
        add(button("Backup and restore", openScreen(new Runnable() { public void run() { showBackup(); } })));
        add(button("About", openScreen(new Runnable() { public void run() { showAbout(); } })));
    }

    private void showCategories() {
        screen("Categories");
        final EditText name = edit("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        add(label("New category")); add(name);
        add(button("Add category", new View.OnClickListener() {
            public void onClick(View v) {
                String n = name.getText().toString().trim();
                if (n.length() == 0 || n.length() > 120) { toast("Please enter a name."); return; }
                Store.Category c = new Store.Category();
                c.id = Store.newId();
                c.name = n;
                c.color = "#9e9e9e";
                c.updatedAt = System.currentTimeMillis();
                store.data.categories.add(c);
                saveOrWarn();
                refresh();
            }
        }));
        for (final Store.Category c : store.activeCategories()) {
            LinearLayout row = card();
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(tv(c.name, 16, INK, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
            line.addView(button("Delete", new View.OnClickListener() {
                public void onClick(View v) {
                    confirm("Delete the category \"" + c.name + "\"? Its transactions become uncategorized.", "Delete", new Runnable() {
                        public void run() { c.deleted = true; c.updatedAt = System.currentTimeMillis(); saveOrWarn(); refresh(); }
                    });
                }
            }));
            row.addView(line);
            add(row);
        }
    }

    private static File downloads() { return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS); }

    /** On Android 6 to 9 the storage permission is asked for the first time it is needed. */
    private boolean storageReady(Runnable then) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingAfterPermission = then;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 7);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        Runnable r = pendingAfterPermission;
        pendingAfterPermission = null;
        if (r != null && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) r.run();
        else if (r != null) toast("Without storage access the backup file cannot be saved or read.");
    }

    private void showBackup() {
        screen("Backup and restore");
        add(tv("A backup is one file in your Downloads folder. It uses the same format as the other SpendingSaver apps, so you can copy it to them. It is not sync.", 14, GREY, false));
        add(button("Save a backup to Downloads", new View.OnClickListener() { public void onClick(View v) { saveBackup(); } }));
        add(button("Restore from a backup file", new View.OnClickListener() { public void onClick(View v) { chooseBackup(); } }));
        add(button("Delete all my data", new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Delete ALL accounts, transactions and budgets on this device? This cannot be undone. Save a backup first if you want to keep them.", "Delete everything", new Runnable() {
                    public void run() { store.reset(); toast("All data deleted."); reset(home); }
                });
            }
        }));
    }

    private void saveBackup() {
        if (!storageReady(new Runnable() { public void run() { saveBackup(); } })) return;
        try {
            File dir = downloads();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, BACKUP_NAME);
            FileOutputStream out = new FileOutputStream(f);
            try { out.write(Store.toJson(store.data, false).getBytes("UTF-8")); } finally { out.close(); }
            toast("Saved: " + f.getAbsolutePath());
        } catch (Exception e) {
            toast(Build.VERSION.SDK_INT >= 29
                ? "This Android version does not let this app write to Downloads. Use the main SpendingSaver app on newer phones."
                : "Could not save the backup: " + e.getMessage());
        }
    }

    private void chooseBackup() {
        if (!storageReady(new Runnable() { public void run() { chooseBackup(); } })) return;
        File[] files = downloads().listFiles();
        final ArrayList<File> found = new ArrayList<File>();
        if (files != null) {
            Arrays.sort(files, new Comparator<File>() {
                public int compare(File a, File b) { return a.lastModified() < b.lastModified() ? 1 : (a.lastModified() > b.lastModified() ? -1 : 0); }
            });
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(".json") && f.length() <= 8 * 1024 * 1024 && found.size() < 30) found.add(f);
            }
        }
        if (found.isEmpty()) { toast("No .json backup files found in the Downloads folder."); return; }
        String[] names = new String[found.size()];
        for (int i = 0; i < names.length; i++) names[i] = found.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Choose a backup")
            .setItems(names, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { restore(found.get(which)); }
            }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void restore(File f) {
        final Store.Data d;
        try {
            d = Store.parse(Store.readAll(f));
        } catch (Exception e) {
            toast("Could not use that file: " + e.getMessage());
            return;
        }
        int a = 0, t = 0, b = 0;
        for (Store.Account x : d.accounts) if (!x.deleted) a++;
        for (Store.Txn x : d.transactions) if (!x.deleted) t++;
        for (Store.Budget x : d.budgets) if (!x.deleted) b++;
        confirm("This file has " + a + " account(s), " + t + " transaction(s) and " + b + " budget(s).\n\nRestoring replaces everything currently in this app.", "Replace my data", new Runnable() {
            public void run() {
                if (d.categories.isEmpty()) {
                    for (String[] c : Store.DEFAULT_CATEGORIES) {
                        Store.Category k = new Store.Category();
                        k.id = Store.newId();
                        k.name = c[0];
                        k.color = c[1];
                        k.updatedAt = System.currentTimeMillis();
                        d.categories.add(k);
                    }
                }
                store.replaceAll(d);
                toast("Backup restored.");
                reset(home);
            }
        });
    }

    private void showAbout() {
        screen("About");
        String version = "";
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        LinearLayout c = card();
        c.addView(tv("SpendingSaver Classic " + version, 18, INK, true));
        c.addView(tv("\nThis is the version of SpendingSaver for older Android devices (4.0.3 and up), such as the Kindle Fire. It does the everyday jobs: accounts, transactions, categories, budgets and backups.\n\nYour data stays on this device. Nothing is sent anywhere and there is no sign-in. The newer SpendingSaver app has more features, such as the tax estimate, the assistant and phone-to-computer sync.", 14, GREY, false));
        add(c);
    }
}
