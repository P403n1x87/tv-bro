package com.phlox.tvwebbrowser.repository

import com.phlox.tvwebbrowser.singleton.AppDatabase

/**
 * The single construction point for the data layer.
 *
 * The app uses manual dependency injection (no Hilt/Koin), so this object is the equivalent of the
 * generated graph: it wires each repository to its DAO exactly once and hands the same instance to
 * every caller. Keeping construction in one place means the switch from a Room-backed DAO to a fake
 * (or a different backend) is a change here only, not at the call sites.
 *
 * Note that the repositories are created lazily, on first use, which is after `TVBro.onCreate` has
 * installed `TVBro.instance` and [AppDatabase] is safe to open.
 */
object Repositories {
    val favorites: FavoritesRepository by lazy { FavoritesRepository(AppDatabase.db.favoritesDao()) }
    val history: HistoryRepository by lazy { HistoryRepository(AppDatabase.db.historyDao()) }
    val tabs: TabsRepository by lazy { TabsRepository(AppDatabase.db.tabsDao()) }
    val downloads: DownloadsRepository by lazy { DownloadsRepository(AppDatabase.db.downloadDao()) }
    val hosts: HostsRepository by lazy { HostsRepository(AppDatabase.db.hostsDao()) }
}
