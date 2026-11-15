package com.venice.freecourses

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class FreeCoursesPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(FreeCoursesProvider())
    }
}
