package com.spendingsaver.classic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/**
 * The data and its file. Everything lives in one JSON file in the app's private folder, in the same
 * layout as the SpendingSaver backup (version 1), so a backup is simply a copy of it. Money is held as
 * whole cents. Rows are never removed, only flagged deleted, exactly like the other SpendingSaver apps.
 */
final class Store {
    static final class Account { String id, name, type, currency; long cents; boolean deleted; long updatedAt; }
    static final class Category { String id, name, color, parentId; boolean deleted; long updatedAt; }
    static final class Txn { String id, accountId, description, categoryId, date, notes; long cents, createdAt, updatedAt; boolean deleted; }
    static final class Budget { String id, categoryId, period, startDate; long cents; boolean deleted; long updatedAt; }

    static final class Data {
        final ArrayList<Account> accounts = new ArrayList<Account>();
        final ArrayList<Category> categories = new ArrayList<Category>();
        final ArrayList<Txn> transactions = new ArrayList<Txn>();
        final ArrayList<Budget> budgets = new ArrayList<Budget>();
    }

    static final String[][] DEFAULT_CATEGORIES = {
        {"Income", "#4caf50"}, {"Housing", "#2196f3"}, {"Food", "#ff9800"}, {"Transportation", "#9c27b0"},
        {"Entertainment", "#e91e63"}, {"Utilities", "#009688"}, {"Healthcare", "#f44336"}, {"Other", "#9e9e9e"},
    };

    private final File file;
    Data data = new Data();

    Store(File dir) {
        file = new File(dir, "data.json");
        if (file.isFile()) {
            try { data = parse(readAll(file)); } catch (Exception e) { data = new Data(); }
        }
        if (data.categories.isEmpty() && data.accounts.isEmpty() && data.transactions.isEmpty()) seedCategories();
    }

    private void seedCategories() {
        for (String[] c : DEFAULT_CATEGORIES) {
            Category k = new Category();
            k.id = newId(); k.name = c[0]; k.color = c[1]; k.updatedAt = System.currentTimeMillis();
            data.categories.add(k);
        }
        save();
    }

    static String newId() { return UUID.randomUUID().toString(); }

    /** Writes the file safely: a temporary copy first, then a rename, so a crash never leaves half a file. */
    boolean save() {
        try {
            File tmp = new File(file.getParentFile(), "data.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try { out.write(toJson(data, true).getBytes("UTF-8")); out.getFD().sync(); } finally { out.close(); }
            return tmp.renameTo(file);
        } catch (Exception e) {
            return false;
        }
    }

    void replaceAll(Data d) { data = d; save(); }

    /** Erases everything and starts again with the standard categories. */
    void reset() { data = new Data(); seedCategories(); }

    // ------------------------------------------------------------------ reading

    static String readAll(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[8192];
            int r;
            while ((r = in.read(chunk)) > 0) buf.write(chunk, 0, r);
        } finally { in.close(); }
        return buf.toString("UTF-8");
    }

    private static String str(JSONObject o, String k, int max, boolean required) throws Exception {
        if (!o.has(k) || o.isNull(k)) {
            if (required) throw new Exception("A row is missing \"" + k + "\"");
            return null;
        }
        Object v = o.get(k);
        if (!(v instanceof String)) throw new Exception("\"" + k + "\" must be text");
        String s = (String) v;
        if (s.length() > max) throw new Exception("\"" + k + "\" is too long");
        if (required && s.trim().length() == 0) throw new Exception("\"" + k + "\" is empty");
        return s;
    }

    private static long cents(JSONObject o, String k) throws Exception {
        if (!o.has(k) || o.isNull(k)) throw new Exception("A row is missing \"" + k + "\"");
        Object v = o.get(k);
        if (!(v instanceof Number)) throw new Exception("\"" + k + "\" must be a number");
        double d = ((Number) v).doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d) || Math.abs(d) > 1e12) throw new Exception("\"" + k + "\" is out of range");
        return new BigDecimal(Double.toString(d)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private static String date(JSONObject o, String k, boolean required) throws Exception {
        String s = str(o, k, 10, required);
        if (s != null && !Fmt.isDate(s)) throw new Exception("\"" + k + "\" is not a date (yyyy-MM-dd)");
        return s;
    }

    /** Reads a SpendingSaver backup (version 1). Throws with a plain-language reason when it is not one. */
    static Data parse(String text) throws Exception {
        JSONObject root;
        try {
            root = new JSONObject(text);
        } catch (JSONException e) {
            throw new Exception("The file is not valid JSON");
        }
        if (root.optInt("version", -1) != 1) throw new Exception("This is not a SpendingSaver backup (version 1)");
        JSONArray accounts = arr(root, "accounts"), categories = arr(root, "categories"),
            txns = arr(root, "transactions"), budgets = arr(root, "budgets");
        long now = System.currentTimeMillis();
        Data d = new Data();
        HashSet<String> ids = new HashSet<String>();
        try {
            for (int i = 0; i < accounts.length(); i++) {
                JSONObject o = accounts.getJSONObject(i);
                Account a = new Account();
                a.id = str(o, "id", 64, true); a.name = str(o, "name", 120, true); a.type = str(o, "type", 60, true);
                a.cents = cents(o, "balance");
                a.currency = o.has("currency") && !o.isNull("currency") ? str(o, "currency", 3, true) : "USD";
                a.deleted = o.optBoolean("deleted", false); a.updatedAt = o.optLong("updatedAt", now);
                if (!ids.add(a.id)) throw new Exception("The backup contains duplicate ids");
                d.accounts.add(a);
            }
            for (int i = 0; i < categories.length(); i++) {
                JSONObject o = categories.getJSONObject(i);
                Category c = new Category();
                c.id = str(o, "id", 64, true); c.name = str(o, "name", 120, true);
                c.parentId = str(o, "parentId", 64, false); c.color = str(o, "color", 20, false);
                c.deleted = o.optBoolean("deleted", false); c.updatedAt = o.optLong("updatedAt", now);
                if (!ids.add(c.id)) throw new Exception("The backup contains duplicate ids");
                d.categories.add(c);
            }
            for (int i = 0; i < txns.length(); i++) {
                JSONObject o = txns.getJSONObject(i);
                Txn t = new Txn();
                t.id = str(o, "id", 64, true); t.accountId = str(o, "accountId", 64, true);
                t.cents = cents(o, "amount"); t.description = str(o, "description", 200, true);
                t.categoryId = str(o, "categoryId", 64, false); t.date = date(o, "date", true);
                t.notes = str(o, "notes", 2000, false);
                t.createdAt = o.optLong("createdAt", now); t.deleted = o.optBoolean("deleted", false); t.updatedAt = o.optLong("updatedAt", now);
                if (!ids.add(t.id)) throw new Exception("The backup contains duplicate ids");
                d.transactions.add(t);
            }
            for (int i = 0; i < budgets.length(); i++) {
                JSONObject o = budgets.getJSONObject(i);
                Budget b = new Budget();
                b.id = str(o, "id", 64, true); b.categoryId = str(o, "categoryId", 64, true); b.cents = cents(o, "amount");
                b.period = str(o, "period", 10, true); b.startDate = date(o, "startDate", false);
                if (!b.period.equals("weekly") && !b.period.equals("monthly") && !b.period.equals("yearly")) throw new Exception("A budget has an unknown period");
                b.deleted = o.optBoolean("deleted", false); b.updatedAt = o.optLong("updatedAt", now);
                if (!ids.add(b.id)) throw new Exception("The backup contains duplicate ids");
                d.budgets.add(b);
            }
        } catch (JSONException e) {
            throw new Exception("The backup contains malformed data");
        }
        return d;
    }

    private static JSONArray arr(JSONObject root, String k) throws Exception {
        JSONArray a = root.optJSONArray(k);
        if (a == null) throw new Exception("The backup is incomplete: missing \"" + k + "\"");
        return a;
    }

    // ------------------------------------------------------------------ writing

    private static double dollars(long cents) { return BigDecimal.valueOf(cents, 2).doubleValue(); }

    static String toJson(Data d, boolean includeDeleted) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("exportedAt", System.currentTimeMillis());
        JSONArray accounts = new JSONArray(), categories = new JSONArray(), txns = new JSONArray(), budgets = new JSONArray();
        for (Account a : d.accounts) {
            if (a.deleted && !includeDeleted) continue;
            JSONObject o = new JSONObject();
            o.put("id", a.id); o.put("name", a.name); o.put("type", a.type); o.put("balance", dollars(a.cents));
            o.put("currency", a.currency); o.put("deleted", a.deleted); o.put("updatedAt", a.updatedAt);
            accounts.put(o);
        }
        for (Category c : d.categories) {
            if (c.deleted && !includeDeleted) continue;
            JSONObject o = new JSONObject();
            o.put("id", c.id); o.put("name", c.name);
            if (c.parentId != null) o.put("parentId", c.parentId);
            if (c.color != null) o.put("color", c.color);
            o.put("deleted", c.deleted); o.put("updatedAt", c.updatedAt);
            categories.put(o);
        }
        for (Txn t : d.transactions) {
            if (t.deleted && !includeDeleted) continue;
            JSONObject o = new JSONObject();
            o.put("id", t.id); o.put("accountId", t.accountId); o.put("amount", dollars(t.cents)); o.put("description", t.description);
            if (t.categoryId != null) o.put("categoryId", t.categoryId);
            o.put("date", t.date);
            if (t.notes != null && t.notes.length() > 0) o.put("notes", t.notes);
            o.put("createdAt", t.createdAt); o.put("deleted", t.deleted); o.put("updatedAt", t.updatedAt);
            txns.put(o);
        }
        for (Budget b : d.budgets) {
            if (b.deleted && !includeDeleted) continue;
            JSONObject o = new JSONObject();
            o.put("id", b.id); o.put("categoryId", b.categoryId); o.put("amount", dollars(b.cents)); o.put("period", b.period);
            o.put("startDate", b.startDate == null ? "" : b.startDate);
            o.put("deleted", b.deleted); o.put("updatedAt", b.updatedAt);
            budgets.put(o);
        }
        root.put("accounts", accounts); root.put("categories", categories);
        root.put("transactions", txns); root.put("budgets", budgets);
        return root.toString();
    }

    // ------------------------------------------------------------------ lookups

    Account account(String id) { for (Account a : data.accounts) if (a.id.equals(id)) return a; return null; }
    Category category(String id) { if (id == null) return null; for (Category c : data.categories) if (c.id.equals(id)) return c; return null; }

    ArrayList<Account> activeAccounts() {
        ArrayList<Account> r = new ArrayList<Account>();
        for (Account a : data.accounts) if (!a.deleted) r.add(a);
        return r;
    }

    ArrayList<Category> activeCategories() {
        ArrayList<Category> r = new ArrayList<Category>();
        for (Category c : data.categories) if (!c.deleted) r.add(c);
        return r;
    }

    /** Transactions that count: not deleted, and on an account that still exists. */
    ArrayList<Txn> liveTransactions() {
        ArrayList<Txn> r = new ArrayList<Txn>();
        for (Txn t : data.transactions) {
            if (t.deleted) continue;
            Account a = account(t.accountId);
            if (a != null && !a.deleted) r.add(t);
        }
        return r;
    }
}
