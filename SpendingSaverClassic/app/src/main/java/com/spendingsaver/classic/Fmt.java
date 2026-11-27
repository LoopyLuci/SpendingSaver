package com.spendingsaver.classic;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Small formatting and parsing helpers (money, dates). */
final class Fmt {
    private Fmt() {}

    static String money(long cents, String currency) {
        long abs = Math.abs(cents);
        String whole = String.format(Locale.US, "%,d", abs / 100);
        String s = whole + "." + (abs % 100 < 10 ? "0" : "") + (abs % 100);
        String sym = "USD".equals(currency) ? "$" : "EUR".equals(currency) ? "€" : "GBP".equals(currency) ? "£" : currency + " ";
        return (cents < 0 ? "-" : "") + sym + s;
    }

    /** Parses what a person types: 12, 12.5, 1,234.56, $9. Returns null when it is not a sensible amount. */
    static Long parseCents(String text, boolean allowNegative) {
        if (text == null) return null;
        String t = text.trim().replace(",", "").replace("$", "");
        if (t.length() == 0) return null;
        try {
            BigDecimal d = new BigDecimal(t);
            if (d.abs().compareTo(new BigDecimal("1000000000")) > 0) return null;
            if (!allowNegative && d.signum() < 0) return null;
            return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isDate(String s) {
        if (s == null || !s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        try { f.parse(s); return true; } catch (java.text.ParseException e) { return false; }
    }

    static String today() { return daysAgo(0); }

    static String daysAgo(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }
}
