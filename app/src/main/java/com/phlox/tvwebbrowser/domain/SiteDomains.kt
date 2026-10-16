package com.phlox.tvwebbrowser.domain

/**
 * Registrable-domain logic used to decide whether a navigation leaves the current site.
 *
 * Extracted from the web-engine callback so the redirect-block decision is testable and the view
 * layer stops carrying domain rules. This is the "model" half of the automatic-redirect policy:
 * it answers *what the host domains are*; whether a redirect is blocked is the caller's call, since
 * that depends on settings and per-tab state.
 *
 * Deliberately free of Android types (no `Uri`, no `Context`) so it can be unit-tested with plain
 * JUnit.
 */
object SiteDomains {

    /** Suffixes that, in a two-label host, indicate the registrable domain needs a third label. */
    private val MULTI_LABEL_SUFFIXES = setOf(
        "co", "com", "org", "net", "edu", "gov", "ac", "ne", "or", "nom", "uk", "ca",
        "us", "au", "nz", "jp", "in", "za", "br", "ar", "mx", "tr", "cn", "sg", "hk", "tw", "kr",
        "it", "fr", "de", "es", "pl", "ru", "se", "no", "fi", "dk", "nl", "be", "ch", "at", "ie",
        "pt", "gr", "il", "cz", "sk", "hu", "ro", "bg", "hr", "rs", "ua", "dev", "io", "app"
    )

    /**
     * Best-effort registrable domain of [host]: lower-cased, "www." stripped, keeping the last two
     * labels — or three when the second-to-last label is a known second-level suffix such as
     * "co.uk". Returns null when the host is blank.
     */
    fun siteDomain(host: String?): String? {
        if (host.isNullOrBlank()) return null
        val labels = host.lowercase().removePrefix("www.").split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val base = if (labels[labels.size - 2] in MULTI_LABEL_SUFFIXES) 3 else 2
        return labels.takeLast(base).joinToString(".")
    }

    /**
     * True when the two hosts belong to different registrable domains, i.e. a cross-site navigation.
     * Unknown hosts on either side are treated as "not different", so a redirect is only flagged
     * when both domains can be established.
     */
    fun isDifferentSite(hostA: String?, hostB: String?): Boolean {
        val a = siteDomain(hostA) ?: return false
        val b = siteDomain(hostB) ?: return false
        return a != b
    }
}
