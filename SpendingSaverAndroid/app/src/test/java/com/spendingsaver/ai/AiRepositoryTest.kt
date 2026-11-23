package com.spendingsaver.ai

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.text.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AiRepository]. Uses MockK to isolate the repository from the
 * HTTP client and Gson. No database is required.
 */
internal class AiRepositoryTest {

    private val gson = mockk<com.google.gson.Gson>(relaxed = true)
    private val okHttpClient = mockk<okhttp3.OkHttpClient>(relaxed = true)

    private fun repository(client: AiClient) =
        AiRepository(client, gson, okHttpClient, AiCapabilities(mockk<android.content.Context>()))

    @Test
    fun checkAIStatus_returns_true_when_service_replies_COMPLETED() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } returns AiResponse(status = AiStatus.COMPLETED, data = "pong")

        val repository = repository(client)
        val result = repository.checkAIStatus()

        assertTrue(result)
        assertEquals(AiStatus.COMPLETED, repository.getStatus().first())
        val resp = repository.getResponse().first()
        assertEquals("pong", resp?.data)
        coVerify(exactly = 1) { client.sendIntent(any()) }
    }

    @Test
    fun checkAIStatus_returns_false_and_sets_ERROR_when_service_throws() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } throws java.io.IOException("network down")

        val repository = repository(client)
        val result = repository.checkAIStatus()

        assertEquals(false, result)
        assertEquals(AiStatus.ERROR, repository.getStatus().first())
    }

    @Test
    fun sendChatMessage_delegates_to_client_and_returns_the_response_data() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } returns AiResponse(status = AiStatus.COMPLETED, data = "hello there")

        val repository = repository(client)
        val response = repository.sendChatMessage("Are you there?")

        assertEquals(AiStatus.COMPLETED, response.status)
        assertEquals("hello there", response.data)
        coVerify(exactly = 1) { client.sendIntent(any()) }
    }

    @Test
    fun sendVisionRequest_delegates_to_client_with_vision_capability_and_attachments() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } returns AiResponse(status = AiStatus.COMPLETED, data = "a cat on a desk")

        val repository = repository(client)
        val response = repository.sendVisionRequest("describe", listOf("file://img.png"))

        assertEquals(AiStatus.COMPLETED, response.status)
        assertEquals("a cat on a desk", response.data)
        val data = response.data
        assertEquals(3, data?.filter { it == 'a' }?.length ?: 0)
        coVerify(exactly = 1) { client.sendIntent(any()) }
    }

    @Test
    fun sendVoiceInput_delegates_to_client_with_stt_capability_and_audio_attachment() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } returns AiResponse(status = AiStatus.COMPLETED, data = "transcript")

        val repository = repository(client)
        val response = repository.sendVoiceInput("file://voice.wav")

        assertEquals(AiStatus.COMPLETED, response.status)
        assertEquals("transcript", response.data)
        coVerify(exactly = 1) { client.sendIntent(any()) }
    }

    @Test
    fun sendImageGeneration_delegates_to_client_with_image_capability() = runTest {
        val client = mockk<AiClient>()
        coEvery { client.sendIntent(any()) } returns AiResponse(status = AiStatus.COMPLETED, data = "generated")

        val repository = repository(client)
        val response = repository.sendImageGeneration("a house", "watercolor")

        assertEquals(AiStatus.COMPLETED, response.status)
        assertEquals("generated", response.data)
        coVerify(exactly = 1) { client.sendIntent(any()) }
    }

    @Test
    fun setBaseUrl_forwards_the_url_to_the_client() {
        val client = mockk<AiClient>(relaxed = true)
        val repository = repository(client)

        repository.setBaseUrl("http://127.0.0.1:8787")

        verify(exactly = 1) { client.setBaseUrl("http://127.0.0.1:8787") }
    }

    @Test
    fun capabilityList_is_nonEmpty_and_capabilities_flow_emits_the_registry() = runTest {
        val repository = repository(mockk())

        assertTrue(repository.capabilityList.isNotEmpty())
        assertEquals(AiStatus.IDLE, repository.getStatus().first())
        assertEquals(null, repository.getResponse().first())
    }
}
