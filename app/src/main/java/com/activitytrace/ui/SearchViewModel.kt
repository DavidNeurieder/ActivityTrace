package com.activitytrace.ui

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEngine: SearchEngine,
    private val captureDao: CaptureDao,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    private val _contentTypeFilter = MutableStateFlow<String?>(savedStateHandle["contentTypeFilter"])
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
    }

    fun onSearch(query: String) {
        onQueryChange(query)
    }

    fun setContentTypeFilter(type: String?) {
        _contentTypeFilter.value = type
        savedStateHandle["contentTypeFilter"] = type
    }

    fun deleteItem(item: CapturedItem) {
        viewModelScope.launch {
            captureDao.delete(item)
        }
    }

    class Factory(
        private val searchEngine: SearchEngine,
        private val captureDao: CaptureDao,
        owner: SavedStateRegistryOwner,
    ) : AbstractSavedStateViewModelFactory(owner, null) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle,
        ): T {
            return SearchViewModel(searchEngine, captureDao, handle) as T
        }
    }
}
