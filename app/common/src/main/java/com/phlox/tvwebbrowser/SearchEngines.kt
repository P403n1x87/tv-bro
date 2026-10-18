package com.phlox.tvwebbrowser

/**
 * The built-in search engines: the parallel [titles]/[names]/[urls] tables and the decisions over
 * them (which entry a stored URL corresponds to, what that engine is called).
 *
 * This lives in the shared library rather than in `domain/` because the Gecko module — which can
 * only see this library — feeds the chosen engine's name to the home page's JS bridge. [Config]
 * stores the chosen URL and nothing else; the tables and the mapping are kept together here so they
 * cannot drift apart.
 */
object SearchEngines {
    /** Display names, in the settings order; the last entry is the custom engine. */
    val titles = arrayOf("Google", "Bing", "Yahoo!", "DuckDuckGo", "Yandex", "Startpage", "Custom")

    /** Short names the home page's JS bridge understands; parallel to [titles]. */
    val names = arrayOf("google", "bing", "yahoo", "ddg", "yandex", "startpage", "custom")

    /** URL templates with a `[query]` placeholder; parallel to [titles] (the last one is empty). */
    val urls =
        listOf(
            "https://www.google.com/search?q=[query]",
            "https://www.bing.com/search?q=[query]",
            "https://search.yahoo.com/search?p=[query]",
            "https://duckduckgo.com/?q=[query]",
            "https://yandex.com/search/?text=[query]",
            "https://www.startpage.com/sp/search?query=[query]",
            "",
        )

    /**
     * Index of the entry [url] selects: the index of the matching template, or the last (custom)
     * entry when the URL is user-provided — which is also how such a URL is displayed.
     */
    fun indexFor(url: String): Int {
        val index = urls.indexOf(url)
        return if (index == -1 || index >= names.size) names.lastIndex else index
    }

    /** Short name of the engine [url] selects (the custom name for a user-provided URL). */
    fun nameFor(url: String): String = names[indexFor(url)]
}
