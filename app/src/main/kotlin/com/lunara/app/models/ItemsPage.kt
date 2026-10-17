/**
 * Ankit Khatri KC (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.models

import com.lunara.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
