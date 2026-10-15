package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.dao.HostsDao

/**
 * The single gateway to per-host configuration (popup blocking level, etc.).
 * See [FavoritesRepository] for the rationale.
 */
class HostsRepository(private val dao: HostsDao) {

    /**
     * Deliberately non-`suspend`: the favicon pool reads the host config from a synchronous
     * WebView/Gecko callback. Kept as-is here so this pass stays behaviour preserving; the
     * main-thread-query cleanup will revisit it.
     */
    fun findByHostName(name: String): HostConfig? = dao.findByHostName(name)

    suspend fun upsert(config: HostConfig): Long = dao.insert(config)

    suspend fun update(config: HostConfig) = dao.update(config)
}
