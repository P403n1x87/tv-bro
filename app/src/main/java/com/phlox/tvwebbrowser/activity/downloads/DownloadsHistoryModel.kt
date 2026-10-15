package com.phlox.tvwebbrowser.activity.downloads

import com.phlox.tvwebbrowser.model.Download
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The finished downloads, as a lazily paginated window: the view appends what it gets and asks for
 * the next window; the model remembers it all in [allItems] so it knows the offset.
 *
 * The data-layer gateway is a constructor parameter with a production default, so a test can hand
 * it a repository over an in-memory database.
 */
class DownloadsHistoryModel(
  private val downloads: DownloadsRepository = Repositories.downloads,
): ActiveModel() {
  val allItems = ArrayList<Download>()
  val lastLoadedItems = ObservableValue<List<Download>>(ArrayList())
  private var loading = false

  fun loadNextItems() = modelScope.launch(Dispatchers.Main) {
    if (loading) {
      return@launch
    }
    loading = true

    val newItems = downloads.page(allItems.size.toLong())
    lastLoadedItems.value = newItems
    allItems.addAll(newItems)

    loading = false
  }
}