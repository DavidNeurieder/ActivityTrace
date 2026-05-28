package com.activitytrace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.activitytrace.capture.CaptureIngestor
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEngine: SearchEngine,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            searchQuery.flatMapLatest { q ->
                if (q.isBlank()) searchEngine.recentItems()
                else searchEngine.search(q)
            }.collect { items ->
                _results.value = items
            }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchQuery.value = text
    }

    fun onSearch(query: String) {
        onQueryChange(query)
    }

    fun addItem(text: String, appPackage: String) {
        viewModelScope.launch {
            CaptureIngestor.ingest(text, appPackage, "manual")
        }
    }

    class Factory(private val searchEngine: SearchEngine) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(searchEngine) as T
        }
    }
}
