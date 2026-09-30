package com.spendingsaver.repository

import com.google.gson.Gson
import com.spendingsaver.data.PendingSync
import com.spendingsaver.data.PendingSyncDao
import com.spendingsaver.data.SyncEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox writer: every repository-level write funnels through here so the
 * offline sync queue captures each local change for later replay.
 */
@Singleton
class SyncQueue @Inject constructor(
    private val pendingSyncDao: PendingSyncDao,
    private val gson: Gson
) {
    suspend fun enqueue(entityType: String, operation: String, entity: SyncEntity) {
        pendingSyncDao.insert(
            PendingSync(
                entityType = entityType,
                operation = operation,
                remoteId = entity.remoteId,
                localData = gson.toJson(entity)
            )
        )
    }
}
