package com.spendingsaver.data

/**
 * Base contract implemented by every sync-ready entity.
 *
 * Each syncable row carries the metadata the sync layer needs to reconcile
 * local changes with the backend, and the flag [dirty] tells the repository
 * whether a change still needs to be pushed.
 */
interface SyncEntity {
    /** Local UUID primary key (also the client-side identity for new rows). */
    val id: String

    /** Last modification timestamp; used for conflict reconciliation. */
    val updatedAt: Long

    /** Soft-delete flag - rows are never physically removed locally. */
    val deleted: Boolean

    /** True while a local change hasn't been pushed to the server yet. */
    val dirty: Boolean

    /** The backend's uuid for this row once it has been synced (null for new local rows). */
    val remoteId: String?
}
