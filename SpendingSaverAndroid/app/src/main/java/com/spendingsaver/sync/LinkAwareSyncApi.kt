package com.spendingsaver.sync

import com.spendingsaver.link.LinkManager

/** Scheme used to store a hub that is reached through the Transferd link: `link://<desktop node id>`. */
const val LINK_SCHEME = "link://"

/**
 * Sends sync requests over the Transferd link when the stored hub is `link://<id>`, and straight
 * to the address otherwise. The sync protocol and engine are identical either way.
 */
class LinkAwareSyncApi(
    private val delegate: SyncApi,
    private val resolve: suspend (nodeId: String) -> String,
) : SyncApi {
    constructor(delegate: SyncApi, link: LinkManager) : this(delegate, link::resolve)

    private suspend fun target(hubUrl: String): String =
        if (hubUrl.startsWith(LINK_SCHEME)) resolve(hubUrl.removePrefix(LINK_SCHEME)) else hubUrl

    override suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String) =
        delegate.pair(target(hubUrl), key, deviceId, deviceName)

    override suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest) =
        delegate.exchange(target(hubUrl), token, request)
}
