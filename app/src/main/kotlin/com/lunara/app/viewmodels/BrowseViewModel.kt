/**
 * Lunara Project (C) 2026
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See NOTICE for contributors
 */

package com.lunara.app.viewmodels
 
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.innertube.YouTube
import com.lunara.innertube.models.YTItem
import com.lunara.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val browseId: String? = savedStateHandle.get<String>("browseId")
 
    /** Null while the answer is still coming; a list, possibly empty, once it has. */
    val items = MutableStateFlow<List<YTItem>?>(null)
    val title = MutableStateFlow<String?>("")
 
    init {
        viewModelScope.launch {
            if (browseId == null) {
                items.value = emptyList()
                return@launch
            }
            YouTube.browse(browseId, null).onSuccess { result ->
                // Store the title
                title.value = result.title
 
                // Flatten the nested structure to get all YTItems
                val allItems = result.items.flatMap { it.items }
                items.value = allItems
            }.onFailure {
                reportException(it)
                // Something to say, rather than a page that waits for ever.
                items.value = emptyList()
            }
        }
    }
}
