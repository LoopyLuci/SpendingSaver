package com.spendingsaver.ai

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.InstallIn
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiHiltModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideAiCapabilities(@ApplicationContext context: Context): AiCapabilities = AiCapabilities(context)

    @Provides
    @Singleton
    fun provideAiClient(
        @ApplicationContext context: Context,
        gson: Gson,
        okHttpClient: OkHttpClient,
    ): AiClient = AiClient(context, gson, okHttpClient)

    @Provides
    @Singleton
    fun provideAiRepository(
        aiClient: AiClient,
        gson: Gson,
        okHttpClient: OkHttpClient,
        capabilities: AiCapabilities,
    ): AiRepository = AiRepository(aiClient, gson, okHttpClient, capabilities)
}
