package com.phlox.tvwebbrowser.activity.main.view.tabs

import android.app.AlertDialog
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.phlox.tvwebbrowser.R
import com.phlox.tvwebbrowser.activity.main.SettingsModel
import com.phlox.tvwebbrowser.activity.main.TabsModel
import com.phlox.tvwebbrowser.activity.main.view.PanelRowFocusDelegate
import com.phlox.tvwebbrowser.activity.main.view.RowFocusPanel
import com.phlox.tvwebbrowser.activity.main.view.tabs.TabsAdapter.Listener
import com.phlox.tvwebbrowser.databinding.ViewTabsBinding
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModelsRepository
import com.phlox.tvwebbrowser.utils.observable.ObservableList

class TabsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : RelativeLayout(context, attrs), RowFocusPanel {

  private var vb = ViewTabsBinding.inflate(LayoutInflater.from(context), this)

  private val rowFocus = PanelRowFocusDelegate(this) { currentTabRow() }

  private lateinit var tabsModel: TabsModel
  private val adapter: TabsAdapter = TabsAdapter(this)
  private lateinit var settingsModel: SettingsModel
  var current: Int by adapter::current
  var listener: Listener? by adapter::listener

  private var overlayTransparent = false

  fun setOverlayTransparency(transparent: Boolean) {
    if (overlayTransparent == transparent) return
    overlayTransparent = transparent
    vb.rvTabs.setBackgroundColor(if (transparent) android.graphics.Color.TRANSPARENT
      else androidx.core.content.ContextCompat.getColor(context, R.color.top_bar_background))
    adapter.transparentBackground = transparent
  }

  init {
      init()
  }

  fun init() {
    if (isInEditMode) return
    tabsModel = ActiveModelsRepository.get(TabsModel::class, context)
    settingsModel = ActiveModelsRepository.get(SettingsModel::class, context)
    adapter.tabsModel = tabsModel
    vb.rvTabs.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
    vb.btnAdd.setOnClickListener{
      listener?.onAddNewTabSelected()
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (isInEditMode) return
    tabsModel.tabsStates.subscribe(listChangeObserver)
    tabsModel.currentTab.subscribe(currentTabObserver)
    vb.rvTabs.adapter = adapter
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    if (isInEditMode) return
    tabsModel.tabsStates.unsubscribe(listChangeObserver)
    tabsModel.currentTab.unsubscribe(currentTabObserver)
  }

  fun showTabOptions(tab: WebTabState) {
    val tabIndex = tabsModel.tabsStates.indexOf(tab)
    AlertDialog.Builder(context)
      .setTitle(R.string.tabs)
      .setItems(R.array.tabs_options) { _, i ->
        when (i) {
          //Open new Tab
          0 -> {
            listener?.openInNewTab(settingsModel.homePage, tabIndex + 1)
          }
          //Close current
          1 -> listener?.closeTab(tab)
          //Close all
          2 -> {
            tabsModel.onCloseAllTabs()
            listener?.openInNewTab(settingsModel.homePage, 0)
          }
          //Move left
          3 -> if (tabIndex > 0) {
            tabsModel.tabsStates.swap(tabIndex, tabIndex - 1)
          }
          //Move right
          4 -> if (tabIndex < (tabsModel.tabsStates.size - 1)) {
            tabsModel.tabsStates.swap(tabIndex, tabIndex + 1)
          }
        }
      }
      .show()
  }

  override fun requestRowFocus() = rowFocus.requestRowFocus()

  override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    // The panel is made VISIBLE and focused in the same frame, so the RecyclerView has not laid out
    // its rows yet: [rowFocus] moves the focus onto the current row once the layout pass has created
    // it. Without that the focus stays on this full-size container: DOWN finds nothing below it and
    // RIGHT escapes to the top bar instead of reaching the row's close button. Stop as soon as the
    // focus moves on.
    if (!gainFocus) rowFocus.cancel()
  }

  /**
   * The row of the selected tab, or null while it has not been laid out yet.
   * [RecyclerView.findViewHolderForAdapterPosition] only returns a holder once the current dataset
   * has been laid out, so this never yields a child left over from the previous opening of the panel.
   */
  private fun currentTabRow(): View? =
    vb.rvTabs.findViewHolderForAdapterPosition(current)?.itemView

  fun onTabTitleUpdated(tab: WebTabState) {
    val tabIndex = tabsModel.tabsStates.indexOf(tab)
    adapter.notifyItemChanged(tabIndex)
  }

  fun onFavIconUpdated(tab: WebTabState) {
    val tabIndex = tabsModel.tabsStates.indexOf(tab)
    adapter.notifyItemChanged(tabIndex)
  }

  private fun scrollToSeeCurrentTab() {
    val lm = (vb.rvTabs.layoutManager as LinearLayoutManager)
    if (current < lm.findFirstCompletelyVisibleItemPosition() ||
      current > lm.findLastCompletelyVisibleItemPosition()
    ) {
      vb.rvTabs.scrollToPosition(current)
    }
  }

  private val listChangeObserver: (ObservableList<WebTabState>) -> Unit = {
    adapter.onTabListChanged()

    scrollToSeeCurrentTab()
  }

  private val currentTabObserver: (value: WebTabState?) -> Unit =  {
    val new = tabsModel.tabsStates.indexOf(it)
    if (new != -1 && current != new) {
      adapter.notifyItemChanged(current)
      current = new
      adapter.notifyItemChanged(new)

      scrollToSeeCurrentTab()
    }
  }
}