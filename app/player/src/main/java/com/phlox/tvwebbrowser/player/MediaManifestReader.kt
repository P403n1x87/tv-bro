package com.phlox.tvwebbrowser.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads a bounded prefix of a manifest body through the scoped, credentials-aware transport. */
object MediaManifestReader {
    const val DEFAULT_LIMIT = 256 * 1024

    // The transport surface is Media3's unstable API; the opt-in is kept inside this function so
    // callers (lint-checked in the app module) see a stable signature.
    @OptIn(markerClass = [UnstableApi::class])
    fun read(
        url: String,
        context: MediaRequestContext,
        limit: Int = DEFAULT_LIMIT,
    ): Pair<String, String> {
        val source = ScopedMediaDataSource(context)
        try {
            source.open(DataSpec.Builder().setUri(Uri.parse(url)).build())
            val finalUrl = source.uri?.toString() ?: url
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (output.size() < limit) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                val count = source.read(buffer, 0, minOf(buffer.size, limit - output.size()))
                if (count < 0) return finalUrl to output.toString("UTF-8")
                output.write(buffer, 0, count)
            }
            throw IOException("Media manifest exceeds inspection limit")
        } finally {
            source.close()
        }
    }
}
