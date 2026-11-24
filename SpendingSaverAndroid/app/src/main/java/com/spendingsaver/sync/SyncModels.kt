package com.spendingsaver.sync

import com.google.gson.annotations.SerializedName

/**
 * Wire format of the optional desktop sync hub (protocol v1). See
 * `spendingsaver-desktop/backend/app/modules/sync_v1.py` for the server side.
 *
 * Timestamps are ISO-8601 UTC strings on the wire and epoch milliseconds in the
 * local database; [SyncMapper] converts between them.
 */
const val SYNC_PROTOCOL_VERSION = 1

object SyncEntity {
    const val ACCOUNT = "account"
    const val CATEGORY = "category"
    const val TRANSACTION = "transaction"
    const val BUDGET = "budget"
}

data class SyncRecord(
    val entity: String,
    val id: String,
    @SerializedName("updated_at") val updatedAt: String,
    val deleted: Boolean = false,
    val data: Map<String, Any?> = emptyMap(),
)

data class ExchangeRequest(
    val cursor: Long,
    val changes: List<SyncRecord>,
    val limit: Int? = null,
)

data class Rejection(
    val entity: String? = null,
    val id: String? = null,
    val reason: String? = null,
)

data class ExchangeResponse(
    val protocol: Int = 0,
    val cursor: Long = 0,
    @SerializedName("has_more") val hasMore: Boolean = false,
    val changes: List<SyncRecord> = emptyList(),
    val applied: Int = 0,
    val rejected: List<Rejection> = emptyList(),
)

data class PairRequest(
    val key: String,
    @SerializedName("device_id") val deviceId: String,
    val name: String,
)

data class PairResponse(
    val protocol: Int = 0,
    @SerializedName("device_id") val deviceId: String? = null,
    val token: String? = null,
    val hub: String? = null,
)

/** Local sync state, persisted outside the finance database. */
data class SyncState(
    val hubUrl: String = "",
    val deviceId: String = "",
    val token: String? = null,
    /** Hub change-log position already pulled. */
    val cursor: Long = 0,
    /** Local rows with `updatedAt >= lastPushMs` still need to be pushed. */
    val lastPushMs: Long = 0,
    val lastSyncAtMs: Long? = null,
) {
    val isPaired: Boolean get() = !token.isNullOrBlank() && hubUrl.isNotBlank()
}

interface SyncStateStore {
    fun load(): SyncState
    fun save(state: SyncState)
}

/** Network side of sync; implemented over HTTP and faked in tests. */
interface SyncApi {
    suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String): PairResponse
    suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest): ExchangeResponse
}

/** Database side of sync; implemented over Room and faked in tests. */
interface SyncStore {
    /** Every row (including soft-deleted ones) with `updatedAt >= sinceMs`. */
    suspend fun modifiedSince(sinceMs: Long): List<SyncRecord>

    /**
     * Applies hub changes with last-write-wins: a record only replaces a local
     * row that is strictly older. Returns the number of rows changed.
     */
    suspend fun apply(records: List<SyncRecord>): Int
}

class SyncException(message: String, val kind: Kind, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NOT_PAIRED, UNAUTHORIZED, NETWORK, PROTOCOL, REJECTED_KEY, RATE_LIMITED, SERVER }
}

data class SyncResult(
    val pushed: Int,
    val pulled: Int,
    val rejected: List<Rejection>,
)
