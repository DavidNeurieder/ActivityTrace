package com.activitytrace.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEngine: SearchEngine,
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow(prefs.getString("search_query", "") ?: "")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

    private val searchQuery = MutableStateFlow(prefs.getString("search_query", "") ?: "")

    private val _contentTypeFilter = MutableStateFlow<String?>(prefs.getString("content_type_filter", null))
    val contentTypeFilter = _contentTypeFilter.asStateFlow()

    init {
        viewModelScope.launch {
            combine(searchQuery, _contentTypeFilter) { q, filter -> q to filter }
                .flatMapLatest { (q, filter) ->
                    if (q.isBlank()) searchEngine.recentItems(filter)
                    else searchEngine.search(q, filter)
                }.collect { items ->
                    _results.value = items
                }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchQuery.value = text
        prefs.edit().putString("search_query", text).apply()
    }

    fun onSearch(query: String) {
        onQueryChange(query)
    }

    fun setContentTypeFilter(type: String?) {
        _contentTypeFilter.value = type
        prefs.edit().putString("content_type_filter", type).apply()
    }

    class Factory(
        private val searchEngine: SearchEngine,
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(searchEngine, application) as T
        }
    }
}
