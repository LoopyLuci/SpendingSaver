package com.spendingsaver

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.spendingsaver.data.Account
import com.spendingsaver.data.AppDatabase
import com.spendingsaver.data.Transaction
import com.spendingsaver.repository.DataBackupRepository
import com.spendingsaver.repository.InvalidBackupException
import com.spendingsaver.sync.RoomSyncStore
import com.spendingsaver.sync.SyncMapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the Room-backed sync store and backup restore against a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class RoomSyncAndBackupTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomSyncStore
    private lateinit var backup: DataBackupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomSyncStore(db)
        backup = DataBackupRepository(db, Gson())
    }

    @After
    fun tearDown() = db.close()

    private fun account(id: String, name: String, updatedAt: Long, deleted: Boolean = false) =
        Account(id = id, name = name, type = "checking", balance = 10.0, deleted = deleted, updatedAt = updatedAt)

    @Test
    fun modifiedSince_includes_tombstones_and_respects_the_watermark() = runBlocking {
        db.accountDao().upsert(account("old", "Old", 1_000))
        db.accountDao().upsert(account("new", "New", 5_000))
        db.accountDao().upsert(account("gone", "Gone", 6_000, deleted = true))

        val ids = store.modifiedSince(5_000).map { it.id }.toSet()
        assertEquals(setOf("new", "gone"), ids)
    }

    @Test
    fun apply_is_last_write_wins_and_keeps_the_newer_local_row() = runBlocking {
        db.accountDao().upsert(account("a", "Local newer", 9_000))

        val stale = SyncMapper.toRecord(account("a", "Hub older", 4_000))
        assertEquals(0, store.apply(listOf(stale)))
        assertEquals("Local newer", db.accountDao().getById("a")!!.name)

        val newer = SyncMapper.toRecord(account("a", "Hub newer", 12_000))
        assertEquals(1, store.apply(listOf(newer)))
        assertEquals("Hub newer", db.accountDao().getById("a")!!.name)
    }

    @Test
    fun apply_propagates_a_remote_tombstone() = runBlocking {
        db.accountDao().upsert(account("a", "Mine", 1_000))
        store.apply(listOf(SyncMapper.toRecord(account("a", "Mine", 2_000, deleted = true))))
        assertTrue(db.accountDao().getById("a")!!.deleted)
    }

    @Test
    fun restore_replaces_active_rows_and_tombstones_the_missing_ones() = runBlocking {
        db.accountDao().upsert(account("keep", "Keep", 1_000))
        db.accountDao().upsert(account("drop", "Drop", 1_000))
        val json = Gson().toJson(
            DataBackupRepository.BackupDocument(accounts = listOf(account("keep", "Keep restored", 500))),
        )

        val before = System.currentTimeMillis()
        backup.restore(json)

        val keep = db.accountDao().getById("keep")!!
        assertEquals("Keep restored", keep.name)
        assertTrue("restored rows are re-stamped so they sync", keep.updatedAt >= before)
        val drop = db.accountDao().getById("drop")!!
        assertTrue("missing rows become tombstones, not hard deletes", drop.deleted)
        assertTrue(drop.updatedAt >= before)
        assertEquals(listOf("keep"), db.accountDao().getAllOnce().map { it.id })
    }

    @Test
    fun restore_of_an_invalid_file_changes_nothing() = runBlocking {
        db.accountDao().upsert(account("a", "Untouched", 1_000))
        try {
            backup.restore("{ this is not a backup")
            fail("expected InvalidBackupException")
        } catch (_: InvalidBackupException) {
        }
        assertEquals("Untouched", db.accountDao().getById("a")!!.name)
        assertFalse(db.accountDao().getById("a")!!.deleted)
    }

    @Test
    fun export_then_restore_round_trips() = runBlocking {
        db.accountDao().upsert(account("a", "Acct", 1_000))
        db.transactionDao().upsert(
            Transaction(id = "t", accountId = "a", amount = -4.5, description = "Coffee", date = "2026-01-02", updatedAt = 1_000),
        )
        val json = backup.export()

        db.transactionDao().upsert(
            Transaction(id = "t", accountId = "a", amount = -99.0, description = "Changed", date = "2026-01-02", updatedAt = 2_000),
        )
        backup.restore(json)

        val t = db.transactionDao().getById("t")
        assertNotNull(t)
        assertEquals("Coffee", t!!.description)
        assertEquals(-4.5, t.amount, 0.0)
        assertNull(db.transactionDao().getById("missing"))
    }
}
