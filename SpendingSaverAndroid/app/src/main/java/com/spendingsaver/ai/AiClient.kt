package com.spendingsaver.ai

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/** Status of AI request */
enum class AiStatus {
    IDLE, READY, PROCESSING, COMPLETED, ERROR, CANCELLED
}

/** Typed AI capability registry - using data class instead of enum to avoid Kotlin 1.8.22 compiler bug */
data class AiCapability(
    val label: String,
    val category: String,
    val requiresVision: Boolean,
    val supportsImage: Boolean,
)

/** All AI capabilities */
class AiCapabilities(private val context: Context) {
    val ALL = listOf(
        AiCapability("Text Generation", "text", false, false),
        AiCapability("Image Description", "vision", true, true),
        AiCapability("Image Generation", "image", false, true),
        AiCapability("Voice Input", "audio", false, false),
        AiCapability("Speech-to-Text", "audio", false, false)
    )
}

/** AI Request intent */
data class AiIntent(
    val capability: AiCapability,
    val input: String,
    val attachments: List<String> = emptyList(),
    val parameters: Map<String, Any> = emptyMap(),
)

/** AI Response */
data class AiResponse(
    val status: AiStatus,
    val data: String? = null,
    val error: String? = null,
)

/** AI Client - typed HTTP API to the local AI engine (Cactus/prism-ml/image-studio) */
class AiClient(
    private val context: Context,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
) {
    private var baseUrl: String = context.getString(com.spendingsaver.R.string.ai_service_url).takeIf { it.isNotEmpty() }
        ?: "http://127.0.0.1:8787"

    fun setBaseUrl(url: String) {
        baseUrl = url
    }

    private val _status = MutableStateFlow(AiStatus.IDLE)
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    suspend fun sendIntent(intent: AiIntent): AiResponse {
            try {
                val json: String = gson.toJson(intent)
                val requestBody = okhttp3.RequestBody.create(
                    "application/json".toMediaType(),
                    json
                )
            val request = okhttp3.Request.Builder()
                .url("$baseUrl/api/chat")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val resp = gson.fromJson(response.body?.string(), AiResponse::class.java)
                _status.value = resp.status
                return resp
            }
            return AiResponse(AiStatus.ERROR, error = "HTTP " + response.code)
        } catch (e: Exception) {
            _status.value = AiStatus.ERROR
            return AiResponse(AiStatus.ERROR, error = e.message)
        }
    }
}
