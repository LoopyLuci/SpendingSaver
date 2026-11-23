package com.spendingsaver.repository

import com.google.gson.Gson
import com.spendingsaver.data.Account
import com.spendingsaver.data.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BackupTest {
    private val gson = Gson()

    private fun acct(id: String, deleted: Boolean = false, updatedAt: Long = 1) =
        Account(id, "n-$id", "checking", 1.0, "USD", deleted, updatedAt)

    private fun valid(
        accounts: String = "[]", categories: String = "[]", transactions: String = "[]", budgets: String = "[]",
    ) = """{"version":1,"exportedAt":1,"accounts":$accounts,"categories":$categories,"transactions":$transactions,"budgets":$budgets}"""

    // ---- codec ----
    @Test
    fun `an exported document parses back unchanged`() {
        val doc = DataBackupRepository.BackupDocument(
            accounts = listOf(acct("a1")),
            transactions = listOf(Transaction("t1", "a1", -5.0, "x", null, "2026-01-01")),
        )
        val parsed = BackupCodec.parse(gson, gson.toJson(doc))
        assertEquals(doc.accounts, parsed.accounts)
        assertEquals(doc.transactions, parsed.transactions)
    }

    @Test
    fun `empty but complete backup is accepted`() {
        assertTrue(BackupCodec.parse(gson, valid()).accounts.isEmpty())
    }

    @Test
    fun `files that are not backups are rejected before anything could be wiped`() {
        listOf(
            "", "not json", "[]", "{}", "null", """{"accounts":[]}""",
            """{"version":2,"accounts":[],"categories":[],"transactions":[],"budgets":[]}""",
            """{"version":1,"accounts":[],"categories":[],"transactions":[]}""",
            """{"version":1,"accounts":null,"categories":[],"transactions":[],"budgets":[]}""",
            """{"version":"1","accounts":[],"categories":[],"transactions":[],"budgets":[]}""",
        ).forEach { json ->
            assertThrows<InvalidBackupException>("should reject: $json") { BackupCodec.parse(gson, json) }
        }
    }

    @Test
    fun `rows missing required fields are rejected`() {
        assertThrows<InvalidBackupException> { BackupCodec.parse(gson, valid(accounts = """[{"id":"a"}]""")) }
        assertThrows<InvalidBackupException> { BackupCodec.parse(gson, valid(transactions = """[{"id":"t","amount":1}]""")) }
        assertThrows<InvalidBackupException> { BackupCodec.parse(gson, valid(categories = """[{"id":"","name":"x"}]""")) }
    }

    @Test
    fun `duplicate ids are rejected`() {
        val a = """{"id":"a","name":"n","type":"checking","balance":1,"currency":"USD"}"""
        assertThrows<InvalidBackupException> { BackupCodec.parse(gson, valid(accounts = "[$a,$a]")) }
    }

    // ---- planner ----
    private fun plan(existing: List<Account>, restored: List<Account>, now: Long = 100) = BackupPlanner.plan(
        existing, restored, id = { it.id }, isDeleted = { it.deleted },
        tombstone = { it.copy(deleted = true, updatedAt = now) }, restamp = { it.copy(updatedAt = now) },
    )

    @Test
    fun `restore restamps backup rows and tombstones active rows that are absent`() {
        val result = plan(existing = listOf(acct("keep"), acct("drop")), restored = listOf(acct("keep", updatedAt = 5), acct("new")))
        val byId = result.associateBy { it.id }
        assertEquals(setOf("keep", "new", "drop"), byId.keys)
        assertEquals(100, byId.getValue("keep").updatedAt)
        assertEquals(100, byId.getValue("new").updatedAt)
        assertTrue(byId.getValue("drop").deleted)
        assertEquals(100, byId.getValue("drop").updatedAt)
    }

    @Test
    fun `restore leaves existing tombstones alone`() {
        val result = plan(existing = listOf(acct("old", deleted = true, updatedAt = 7)), restored = emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `restoring a row that was soft deleted brings it back`() {
        val result = plan(existing = listOf(acct("a", deleted = true)), restored = listOf(acct("a")))
        assertEquals(false, result.single().deleted)
    }
}
