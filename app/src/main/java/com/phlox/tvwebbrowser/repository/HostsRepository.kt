package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.dao.HostsDao

/**
 * The single gateway to per-host configuration (popup blocking level, etc.).
 * See [FavoritesRepository] for the rationale.
 */
class HostsRepository(
    private val dao: HostsDao,
) {
    /**
     * Suspending like the rest of the data layer: its caller, [com.phlox.tvwebbrowser.singleton.FaviconsPool],
     * reads the host config from inside a coroutine, so Room runs the query off the main thread.
     */
    suspend fun findByHostName(name: String): HostConfig? = dao.findByHostName(name)

    suspend fun upsert(config: HostConfig): Long = dao.insert(config)

    suspend fun update(config: HostConfig) = dao.update(config)
}
