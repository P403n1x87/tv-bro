package com.phlox.tvwebbrowser.activity.downloads

import android.os.Environment
import android.text.format.Formatter
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.domain.DownloadStoragePolicy
import com.phlox.tvwebbrowser.repository.DownloadsRepository
import com.phlox.tvwebbrowser.repository.Repositories
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The storage facts behind the downloads screen's header: where files land, how much room the
 * destination partition has left and how much the logged downloads already use.
 *
 * The repository, directory, formatter and IO dispatcher are constructor parameters with production
 * defaults, so a test can drive it deterministically. Disk reads run on [ioDispatcher]; only the
 * observable values are touched on the model's main dispatcher.
 */
class DownloadStorageModel(
  private val downloads: DownloadsRepository = Repositories.downloads,
  private val downloadsDir: () -> File = {
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
  },
  private val formatBytes: (Long) -> String = {
    Formatter.formatShortFileSize(TVBro.instance, it)
  },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ActiveModel() {
  /** The directory downloads are written to; null until the first [refresh]. */
  val location = ObservableValue<String?>(null)

  /** The destination's free space, formatted. */
  val freeSpace = ObservableValue<String?>(null)

  /** The total on-disk footprint of the logged downloads, formatted. */
  val usedSpace = ObservableValue<String?>(null)

  fun refresh() = modelScope.launch {
    val dir = downloadsDir()
    val free = withContext(ioDispatcher) { dir.freeSpace }
    val used = DownloadStoragePolicy.usedBytes(downloads.all())
    location.value = dir.absolutePath
    freeSpace.value = formatBytes(free)
    usedSpace.value = formatBytes(used)
  }
}
