package com.spendingsaver.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.spendingsaver.data.*
import com.spendingsaver.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Runs while the app has network: pushes every unsynced PendingSync operation
 * to the backend and pulls server-side changes back down. The sync host is
 * the user's self-hosted server (default: this device's LAN IP, otherwise a
 * paired server IP). All writes to Room happen here so the rest of the app
 * stays fully offline-capable.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val gson = Gson()
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val baseUrl by lazy { currentBase(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (baseUrl.isBlank()) return@withContext Result.success() // no server configured
            pushPending()
            pullServerChanges()
            db.pendingSyncDao().purgeSynced()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    // ---- push local changes to the backend in {records:[...]} wire format ----

    private suspend fun pushPending() {
        val pending = db.pendingSyncDao().getUnsynced()
        if (pending.isEmpty()) return

        val records = JsonArray()
        for (p in pending) {
            val rec = JsonObject()
            rec.addProperty("entity_type", p.entityType)
            rec.addProperty("operation", p.operation)
            if (p.remoteId != null) rec.addProperty("remote_id", p.remoteId)
            // data = the row JSON
            rec.add("data", JsonParser.parseString(p.localData))
            records.add(rec)
        }

        val body = JsonObject().apply { add("records", records) }
        try {
            val code = httpPost("$baseUrl/api/sync/upsert", body.toString())
            if (code in 200..299) {
                // Mark all as synced only on a successful batch.
                for (p in pending) db.pendingSyncDao().markSynced(p.id)
            }
        } catch (e: Exception) {
            // Connectivity failure — rows stay unsynced and retry next run.
        }
    }

    // ---- pull server changes into local Room ----

    private suspend fun pullServerChanges() {
        try {
            val body = httpGet("$baseUrl/api/sync/changes?since=0")
                ?: return
            val root = JsonParser.parseString(body)
            if (!root.isJsonObject) return
            val changes = root.asJsonObject.getAsJsonArray("changes") ?: return
            val items = changes
            for (item in items) {
                if (!item.isJsonObject) continue
                val obj = item.asJsonObject
                val type = obj.get("entity_type")?.asString ?: continue
                val data = obj.getAsJsonObject("data") ?: continue
                when (type) {
                    "account" -> upsertAccount(data)
                    "category" -> upsertCategory(data)
                    "transaction" -> upsertTransaction(data)
                    "budget" -> upsertBudget(data)
                }
            }
        } catch (e: Exception) {
            // Pull is best-effort; local-first operation is unaffected.
        }
    }

    private suspend fun upsertAccount(data: JsonObject) {
        val a = gson.fromJson(data, Account::class.java)
        db.accountDao().upsert(a.copy(dirty = false))
    }

    private suspend fun upsertCategory(data: JsonObject) {
        val c = gson.fromJson(data, Category::class.java)
        db.categoryDao().upsert(c.copy(dirty = false))
    }

    private suspend fun upsertTransaction(data: JsonObject) {
        val t = gson.fromJson(data, Transaction::class.java)
        db.transactionDao().upsert(t.copy(dirty = false))
    }

    private suspend fun upsertBudget(data: JsonObject) {
        val b = gson.fromJson(data, Budget::class.java)
        db.budgetDao().upsert(b.copy(dirty = false))
    }

    // ---- HTTP helpers ----

    private fun httpPost(url: String, jsonBody: String): Int {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val out: OutputStream = conn.outputStream
            out.write(jsonBody.toByteArray(Charsets.UTF_8))
            out.flush()
            out.close()
            return conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode !in 200..299) {
                null
            } else {
                val reader = BufferedReader(conn.inputStream.reader())
                reader.use { it.readText() }
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Default to localhost for first-run when no paired server is set. */
        fun currentBase(context: Context): String {
            return SettingsRepository(context).settings.value.syncHost
                .ifBlank { "http://127.0.0.1:8125" }
        }
    }
}