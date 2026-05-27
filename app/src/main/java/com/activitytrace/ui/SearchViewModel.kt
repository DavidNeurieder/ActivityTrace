package com.activitytrace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchEngine: SearchEngine,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun onSearch(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                _results.value = emptyList()
                return@launch
            }
            searchEngine.search(query).collect { items ->
                _results.value = items
            }
        }
    }

    class Factory(private val searchEngine: SearchEngine) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(searchEngine) as T
        }
    }
}
