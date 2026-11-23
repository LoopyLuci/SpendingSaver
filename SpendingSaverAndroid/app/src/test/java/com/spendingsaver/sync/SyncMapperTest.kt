package com.spendingsaver.sync

import com.spendingsaver.data.Account
import com.spendingsaver.data.Budget
import com.spendingsaver.data.Category
import com.spendingsaver.data.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SyncMapperTest {
    @Test
    fun `iso conversion round trips and accepts offsets`() {
        val ms = 1_732_270_000_123L
        assertEquals(ms, SyncMapper.fromIso(SyncMapper.toIso(ms)))
        assertEquals(1_732_270_000_000L, SyncMapper.fromIso("2024-11-22T10:06:40+00:00"))
        assertNull(SyncMapper.fromIso("yesterday"))
        assertNull(SyncMapper.fromIso(null))
    }

    @Test
    fun `account survives a wire round trip including tombstone state`() {
        val a = Account("a1", "Main", "checking", 12.5, "EUR", deleted = true, updatedAt = 1_700_000_000_000)
        assertEquals(a, SyncMapper.accountFromRecord(SyncMapper.toRecord(a)))
    }

    @Test
    fun `transaction round trips with nullable fields`() {
        val t = Transaction("t1", "a1", -3.2, "Coffee", null, "2026-01-02", null, 1_700_000_000_000, false, 1_700_000_005_000)
        assertEquals(t, SyncMapper.transactionFromRecord(SyncMapper.toRecord(t), null))
    }

    @Test
    fun `budget round trips`() {
        val b = Budget("b1", "c1", 100.0, "monthly", "2026-01-01", false, 1_700_000_000_000)
        assertEquals(b, SyncMapper.budgetFromRecord(SyncMapper.toRecord(b)))
    }

    @Test
    fun `remote category update keeps the android only parent`() {
        val local = Category("c1", "Food", parentId = "root", color = null, updatedAt = 1_000)
        val remote = SyncMapper.toRecord(Category("c1", "Groceries", null, "#fff", updatedAt = 2_000))
        val merged = SyncMapper.categoryFromRecord(remote, local)!!
        assertEquals("Groceries", merged.name)
        assertEquals("root", merged.parentId)
        assertEquals("#fff", merged.color)
    }

    @Test
    fun `malformed records are skipped rather than crashing`() {
        val bad = SyncRecord(SyncEntity.ACCOUNT, "x", SyncMapper.toIso(1), false, mapOf("name" to 5, "balance" to "n/a"))
        assertNull(SyncMapper.accountFromRecord(bad))
        assertNull(SyncMapper.accountFromRecord(bad.copy(updatedAt = "garbage", data = emptyMap())))
        assertNull(SyncMapper.transactionFromRecord(bad.copy(entity = SyncEntity.TRANSACTION), null))
        assertNull(SyncMapper.budgetFromRecord(bad.copy(entity = SyncEntity.BUDGET)))
    }

    @Test
    fun `wire timestamps are utc instants the hub can parse`() {
        assertTrue(SyncMapper.toIso(0).endsWith("Z"))
    }
}
