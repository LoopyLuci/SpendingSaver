package com.spendingsaver.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun syncRepository(): SyncRepository
}

/**
 * Optional periodic sync. It only exists while the user has paired a hub *and* switched
 * "Sync automatically" on; failures (hub offline, wrong network) are not errors, the next
 * period simply tries again. It never changes local data except by applying hub changes.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java)
            .syncRepository()
        repo.syncInBackground()
        return Result.success()
    }
}
