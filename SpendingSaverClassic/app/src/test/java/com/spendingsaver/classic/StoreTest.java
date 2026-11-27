package com.spendingsaver.classic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.util.Scanner;
import org.junit.Test;

public class StoreTest {
    private static String realBackup() throws Exception {
        // A backup exported by the real SpendingSaver Android app (Nokia 7.2, release 1.1.2).
        InputStream in = StoreTest.class.getResourceAsStream("/android-backup.json");
        assertNotNull(in);
        Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
        return s.next();
    }

    private static void rejects(String json, String reason) {
        try {
            Store.parse(json);
            fail("should reject: " + reason);
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().length() > 0);
        }
    }

    @Test
    public void readsABackupFromTheRealAndroidApp() throws Exception {
        Store.Data d = Store.parse(realBackup());
        assertEquals(1, d.accounts.size());
        assertEquals("Everyday Checking", d.accounts.get(0).name);
        assertEquals(125050L, d.accounts.get(0).cents);
        assertEquals(2, d.transactions.size());
        assertEquals(-5420L, d.transactions.get(0).cents);
        assertEquals(200000L, d.transactions.get(1).cents);
        assertEquals("2026-11-27", d.transactions.get(1).date);
    }

    @Test
    public void writesTheSameFormatAndReadsItBack() throws Exception {
        Store.Data d = Store.parse(realBackup());
        Store.Data again = Store.parse(Store.toJson(d, false));
        assertEquals(d.accounts.get(0).cents, again.accounts.get(0).cents);
        assertEquals(d.transactions.size(), again.transactions.size());
        assertEquals(d.transactions.get(0).description, again.transactions.get(0).description);
    }

    @Test
    public void rejectsFilesThatAreNotBackups() {
        rejects("not json", "garbage");
        rejects("{\"version\":2,\"accounts\":[],\"categories\":[],\"transactions\":[],\"budgets\":[]}", "wrong version");
        rejects("{\"version\":1,\"accounts\":[],\"categories\":[],\"transactions\":[]}", "missing budgets");
        rejects("{\"version\":1,\"accounts\":[{\"id\":\"a\",\"name\":\"x\",\"type\":\"t\",\"balance\":1},{\"id\":\"a\",\"name\":\"y\",\"type\":\"t\",\"balance\":2}],\"categories\":[],\"transactions\":[],\"budgets\":[]}", "duplicate ids");
        rejects("{\"version\":1,\"accounts\":[{\"id\":\"a\",\"name\":\"x\",\"type\":\"t\",\"balance\":1e30}],\"categories\":[],\"transactions\":[],\"budgets\":[]}", "out of range");
        rejects("{\"version\":1,\"accounts\":[],\"categories\":[],\"transactions\":[{\"id\":\"t\",\"accountId\":\"a\",\"amount\":1,\"description\":\"d\",\"date\":\"yesterday\"}],\"budgets\":[]}", "bad date");
    }

    @Test
    public void parsesWhatPeopleType() {
        assertEquals(Long.valueOf(1250), Fmt.parseCents("12.50", false));
        assertEquals(Long.valueOf(123456), Fmt.parseCents("$1,234.56", false));
        assertEquals(Long.valueOf(-500), Fmt.parseCents("-5", true));
        assertNull(Fmt.parseCents("-5", false));
        assertNull(Fmt.parseCents("abc", false));
        assertNull(Fmt.parseCents("", true));
        assertNull(Fmt.parseCents("99999999999", true));
    }

    @Test
    public void formatsMoneyAndDates() {
        assertEquals("$1,250.50", Fmt.money(125050, "USD"));
        assertEquals("-$0.07", Fmt.money(-7, "USD"));
        assertEquals("CAD 3.00", Fmt.money(300, "CAD"));
        assertTrue(Fmt.isDate("2026-11-27"));
        assertFalse(Fmt.isDate("2026-13-01"));
        assertFalse(Fmt.isDate("2026-2-3"));
    }
}
