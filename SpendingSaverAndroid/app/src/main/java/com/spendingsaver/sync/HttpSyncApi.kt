package com.spendingsaver.sync

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** HTTP implementation of [SyncApi] against the desktop backend's `/api/sync/v1` endpoints. */
class HttpSyncApi(
    private val client: OkHttpClient,
    private val gson: Gson,
) : SyncApi {

    override suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String): PairResponse {
        val body = gson.toJson(PairRequest(key = key, deviceId = deviceId, name = deviceName))
        val request = Request.Builder()
            .url("$hubUrl/api/sync/v1/pair")
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request, PairResponse::class.java, isPairing = true)
    }

    override suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest): ExchangeResponse {
        val http = Request.Builder()
            .url("$hubUrl/api/sync/v1/exchange")
            .header("Authorization", "Bearer $token")
            .post(gson.toJson(request).toRequestBody(JSON))
            .build()
        return execute(http, ExchangeResponse::class.java, isPairing = false)
    }

    private suspend fun <T> execute(request: Request, type: Class<T>, isPairing: Boolean): T =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> try {
                            gson.fromJson(text, type) ?: throw SyncException("Empty reply from the hub", SyncException.Kind.PROTOCOL)
                        } catch (e: JsonParseException) {
                            throw SyncException("The hub sent an unreadable reply", SyncException.Kind.PROTOCOL, e)
                        }
                        response.code == 401 && isPairing ->
                            throw SyncException("The pairing key was not accepted", SyncException.Kind.REJECTED_KEY)
                        response.code == 401 ->
                            throw SyncException("The hub no longer trusts this device. Pair again.", SyncException.Kind.UNAUTHORIZED)
                        response.code == 429 ->
                            throw SyncException("Too many attempts. Try again in a few minutes.", SyncException.Kind.RATE_LIMITED)
                        response.code == 404 ->
                            throw SyncException("That address is not a SpendingSaver hub (or it is too old to sync)", SyncException.Kind.PROTOCOL)
                        else ->
                            throw SyncException("The hub returned HTTP ${response.code}", SyncException.Kind.SERVER)
                    }
                }
            } catch (e: IOException) {
                throw SyncException("Could not reach the hub: ${e.message ?: "network error"}", SyncException.Kind.NETWORK, e)
            }
        }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
