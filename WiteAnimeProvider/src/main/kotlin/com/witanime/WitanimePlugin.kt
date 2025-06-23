package com.witanime

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class WitanimePlugin: Plugin() {
    override fun load(context: Context) {
        // Register the main API provider
        registerMainAPI(WitanimeProvider())
    }
}