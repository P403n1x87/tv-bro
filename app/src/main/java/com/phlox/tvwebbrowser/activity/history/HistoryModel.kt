package com.phlox.tvwebbrowser.activity.history

import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryModel: ActiveModel() {
    val lastLoadedItems = ObservableValue<List<HistoryItem>>(ArrayList())
    private var loading = false
    var searchQuery = ""

    private val history = Repositories.history

    fun loadItems(eraseOldResults: Boolean, offset: Long = 0) = modelScope.launch(Dispatchers.Main) {
        if (loading) {
            return@launch
        }
        loading = true

        lastLoadedItems.value = if ("" == searchQuery) {
            history.page(offset)
        } else {
            history.search(searchQuery)
        }
        loading = false
    }

    /** Removes a single stored visit. The view asks the model rather than touching Room. */
    suspend fun delete(item: HistoryItem) = history.delete(item)

    /** Removes the given stored visits (multi-select delete). */
    suspend fun delete(items: List<HistoryItem>) = history.delete(*items.toTypedArray())

    /** Empties the history. */
    suspend fun deleteAll() = history.deleteOlderThan(Long.MAX_VALUE)
}