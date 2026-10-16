package com.phlox.tvwebbrowser.domain

import com.phlox.tvwebbrowser.Config

/** Pure web-engine identity checks, lifted out of `Config` (which only stores the choice). */
object WebEnginePolicy {
    fun isGecko(engine: String): Boolean = engine == Config.ENGINE_GECKO_VIEW
}
