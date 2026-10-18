package com.phlox.tvwebbrowser.activity.main

import android.app.Activity
import android.app.AlertDialog
import com.phlox.tvwebbrowser.Config
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.activity.main.dialogs.favorites.FavoriteEditorDialog
import com.phlox.tvwebbrowser.activity.main.view.bookmarks.BookmarksPanelView
import com.phlox.tvwebbrowser.databinding.ActivityMainBinding
import com.phlox.tvwebbrowser.domain.HomePagePolicy
import com.phlox.tvwebbrowser.model.FavoriteItem
import com.phlox.tvwebbrowser.model.WebTabState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the bookmark flows of the main screen: the bookmarks panel (its listener, the "Add this
 * page" / "Remove from list" row and the persistence behind them) and the homepage-link editing
 * the page can trigger.
 *
 * It reaches the rest of the screen only through what it is handed here — [viewModel] owns the
 * data, [panelController] the panel visibility, and the two lambdas report the page intent back to
 * the host.
 */
class BookmarksController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val vb: ActivityMainBinding,
    private val viewModel: MainActivityViewModel,
    private val panelController: PanelController,
    /** The page the star and the panel rows act on. */
    private val currentTab: () -> WebTabState?,
    /** Navigates the current tab to a bookmark the user picked in the panel. */
    private val navigate: (String) -> Unit,
) {
    val isPanelVisible: Boolean get() = panelController.isBookmarksPanelVisible

    val listener =
        object : BookmarksPanelView.Listener {
            override fun onToggleThisPageBookmark() {
                toggleCurrentPage()
            }

            override fun onBookmarkSelected(item: FavoriteItem) {
                item.url?.let(navigate)
                hide()
            }

            override fun onEditBookmark(item: FavoriteItem) {
                FavoriteEditorDialog(
                    activity,
                    initialTitle = item.title,
                    initialUrl = item.url,
                    newBookmark = item.id == 0L,
                ) { title, url ->
                    scope.launch(Dispatchers.Main) {
                        viewModel.onHomePageLinkEdited(item, title, url).join()
                        load()
                        updateState()
                    }
                }.show()
            }

            override fun onDeleteBookmark(item: FavoriteItem) {
                scope.launch(Dispatchers.Main) {
                    viewModel.deleteBookmark(item.id).join()
                    load()
                    updateState()
                }
            }
        }

    /** The view model's bookmarks changed: render them into the panel. */
    fun renderBookmarks(bookmarks: List<FavoriteItem>) {
        vb.vBookmarks.setBookmarks(bookmarks)
    }

    /** The view model recomputed whether the current page is bookmarked: repaint the row and the star. */
    fun renderCurrentPageBookmarked(bookmarked: Boolean) {
        vb.vBookmarks.setCurrentPage(currentTab()?.url, bookmarked)
        vb.vActionBar.setBookmarked(bookmarked)
    }

    /** The toolbar's bookmark button: shows the panel, or closes it when it is already open. */
    fun togglePanel() {
        if (panelController.isBookmarksPanelVisible) hide() else show()
    }

    fun hide() {
        panelController.hideBookmarksPanel()
    }

    /** Opens the editor for the current page with empty bookmark data. */
    fun addCurrentPage() {
        val tab = currentTab() ?: return
        val url = tab.url
        if (url.isNullOrBlank() || HomePagePolicy.isHomePageUrl(url, Config.localHomePageUrl)) return
        val newItem = FavoriteItem()
        FavoriteEditorDialog(
            activity,
            initialTitle = tab.title ?: url,
            initialUrl = url,
            newBookmark = true,
        ) { title, editedUrl ->
            scope.launch(Dispatchers.Main) {
                viewModel.onHomePageLinkEdited(newItem, title, editedUrl).join()
                load()
                updateState()
            }
        }.show()
    }

    /** The page's own bookmark action (from the cursor menu): edit or delete an existing bookmark. */
    fun editBookmarkById(favoriteId: Long) {
        scope.launch {
            val favoriteItem = viewModel.getBookmarkById(favoriteId) ?: return@launch
            AlertDialog
                .Builder(activity)
                .setTitle(R.string.bookmarks)
                .setItems(arrayOf(activity.getString(R.string.edit), activity.getString(R.string.delete))) { _, which ->
                    when (which) {
                        0 -> {
                            editFavorite(favoriteItem)
                        }

                        1 -> {
                            val bookmark = viewModel.homePageLinks.firstOrNull { it.favoriteId == favoriteId }
                            if (bookmark != null) {
                                viewModel.removeHomePageLink(bookmark)
                            } else {
                                viewModel.deleteBookmark(favoriteId)
                            }
                        }
                    }
                }.show()
        }
    }

    /**
     * Asks the view model to recompute whether the current page is bookmarked. The toolbar star and
     * the panel's rows follow [MainActivityViewModel.currentPageBookmarked].
     */
    fun updateState() {
        val url = currentTab()?.url
        scope.launch(Dispatchers.Main) {
            viewModel.refreshBookmarkState(url)
        }
    }

    private fun show() {
        // Read the content first and hand the focus to a row only afterwards: the rows, and the
        // "Add this page" entry (which is hidden again for the home page), are final by then, so the
        // hand-off needs at most the one layout pass that creates them.
        panelController.showBookmarksPanel(scope) {
            load()
            updateState()
        }
    }

    private suspend fun load() {
        viewModel.loadBookmarks()
    }

    /**
     * The bookmarks panel's first row: adds the current page if it is not bookmarked, or removes
     * the existing bookmark when it already is.
     */
    private fun toggleCurrentPage() {
        val tab = currentTab() ?: return
        val url = tab.url
        if (url.isNullOrBlank() || HomePagePolicy.isHomePageUrl(url, Config.localHomePageUrl)) return
        scope.launch(Dispatchers.Main) {
            val existing = viewModel.getBookmarkByUrl(url)
            if (existing != null) {
                viewModel.deleteBookmark(existing.id).join()
                load()
                updateState()
            } else {
                addCurrentPage()
            }
        }
    }

    private fun editFavorite(favoriteItem: FavoriteItem) {
        FavoriteEditorDialog(
            activity,
            initialTitle = favoriteItem.title,
            initialUrl = favoriteItem.url,
            newBookmark = favoriteItem.id == 0L,
        ) { title, url ->
            viewModel.onHomePageLinkEdited(favoriteItem, title, url)
        }.show()
    }
}
