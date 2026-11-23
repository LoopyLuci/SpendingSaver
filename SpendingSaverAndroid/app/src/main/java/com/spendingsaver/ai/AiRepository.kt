package com.spendingsaver.ai

import android.content.Context
import com.google.gson.Gson
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

class AiRepository @Inject constructor(
    private val aiClient: AiClient,
    private val gson: Gson,
    private val okHttpClient: OkHttpClient,
    private val capabilities: AiCapabilities,
) {
    private val _aiStatus = MutableStateFlow(AiStatus.IDLE)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private val _aiResponse = MutableStateFlow<AiResponse?>(null)
    val aiResponse: StateFlow<AiResponse?> = _aiResponse.asStateFlow()

    val capabilityList: List<AiCapability> = capabilities.ALL
    private val _capabilityFlow = MutableStateFlow<List<AiCapability>>(capabilityList)
    val capabilityFlow: StateFlow<List<AiCapability>> = _capabilityFlow

    fun getStatus(): StateFlow<AiStatus> = _aiStatus
    fun getResponse(): StateFlow<AiResponse?> = _aiResponse
    fun getCapabilities(): StateFlow<List<AiCapability>> = _capabilityFlow

    suspend fun checkAIStatus(): Boolean {
        try {
            val response = aiClient.sendIntent(
                AiIntent(
                    capability = capabilities.ALL[0],
                    input = "ping",
                    parameters = mapOf("ping" to true),
                )
            )
            _aiStatus.value = response.status
            _aiResponse.value = response
            return response.status == AiStatus.COMPLETED || response.status == AiStatus.READY
        } catch (e: Exception) {
            _aiStatus.value = AiStatus.ERROR
            _aiResponse.value = AiResponse(status = AiStatus.ERROR, error = e.message)
            return false
        }
    }

    suspend fun sendChatMessage(message: String): AiResponse {
        return aiClient.sendIntent(
            AiIntent(
                capability = capabilities.ALL[0],
                input = message,
                parameters = mapOf("type" to "chat", "context" to "chat"),
            )
        )
    }

    suspend fun sendVisionRequest(prompt: String, imageRefs: List<String>, mode: String = "description"): AiResponse {
        return aiClient.sendIntent(
            AiIntent(
                capability = capabilities.ALL[1],
                input = prompt,
                attachments = imageRefs,
                parameters = mapOf("mode" to mode, "task" to "vision"),
            )
        )
    }

    suspend fun sendVoiceInput(audioRef: String): AiResponse {
        return aiClient.sendIntent(
            AiIntent(
                capability = capabilities.ALL[3],
                input = "Transcribe speech",
                attachments = listOf(audioRef),
                parameters = mapOf("type" to "stt", "format" to "transcript"),
            )
        )
    }

    suspend fun sendImageGeneration(prompt: String, style: String = "default"): AiResponse {
        return aiClient.sendIntent(
            AiIntent(
                capability = capabilities.ALL[2],
                input = prompt,
                parameters = mapOf("task" to "image_generation", "style" to style),
            )
        )
    }

    suspend fun sendTextRequest(prompt: String, mode: String? = null): AiResponse {
        val capability = if (mode == "image") capabilities.ALL[2] else capabilities.ALL[0]
        return aiClient.sendIntent(
            AiIntent(
                capability = capability,
                input = prompt,
                parameters = mapOf("type" to (mode ?: "text") as Any),
            )
        )
    }

    fun getStatusFlow(): StateFlow<AiStatus> = _aiStatus
    fun getResponseFlow(): StateFlow<AiResponse?> = _aiResponse

    fun setBaseUrl(url: String) {
        aiClient.setBaseUrl(url)
    }
}
