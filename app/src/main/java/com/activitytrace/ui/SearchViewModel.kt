package com.activitytrace.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.activitytrace.model.CapturedItem
import com.activitytrace.search.SearchEngine
import com.activitytrace.search.SearchPage
import com.activitytrace.store.ActivityTraceDatabase
import com.activitytrace.store.CaptureDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEngine: SearchEngine,
    application: Application,
    private val debounceMillis: Long = DEBOUNCE_MILLIS_DEFAULT,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow(prefs.getString("search_query", "") ?: "")
    val query = _query.asStateFlow()
    private val _queryToPersist = MutableStateFlow<String?>(null)

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount = _loadedCount.asStateFlow()

    private val _hasMore = MutableStateFlow(false)
    val hasMore = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore = _loadingMore.asStateFlow()

    private val _offset = MutableStateFlow(0)

    private val _contentTypeFilter = MutableStateFlow<String?>(prefs.getString("content_type_filter", null))
    val contentTypeFilter = _contentTypeFilter.asStateFlow()

    private val _bookmarkedOnly = MutableStateFlow(prefs.getBoolean("bookmarked_filter", false))
    val bookmarkedOnly = _bookmarkedOnly.asStateFlow()

    private val _showStats = MutableStateFlow(prefs.getBoolean("show_stats", false))
    val showStats = _showStats.asStateFlow()

    private val _appFilter = MutableStateFlow<String?>(null)
    val appFilter = _appFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow<Pair<Long, Long>?>(null)
    val dateFilter = _dateFilter.asStateFlow()

    private val _canOpenPackages = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val canOpenPackages = _canOpenPackages.asStateFlow()

    private fun getCaptureDao(): CaptureDao? {
        return try {
            ActivityTraceDatabase.getInstance(getApplication()).captureDao()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get captureDao", e)
            null
        }
    }

    init {
        viewModelScope.launch {
            _queryToPersist
                .debounce(500)
                .collect { text ->
                    if (text != null) {
                        prefs.edit().putString("search_query", text).apply()
                    }
                }
        }

        val baseInputs = combine(
            _query, _contentTypeFilter, _bookmarkedOnly,
            _appFilter, _dateFilter,
        ) { q, type, bookmarked, app, date ->
            FilterState(q, type, bookmarked, app, date, 0)
        }
        val searchInputs = combine(baseInputs, _offset) { state, offset ->
            state.copy(offset = offset)
        }
        val debouncedInputs = if (debounceMillis > 0) {
            searchInputs.debounce(debounceMillis)
        } else {
            searchInputs
        }

        viewModelScope.launch {
            debouncedInputs
                .flatMapLatest { state ->
                    if (state.bookmarked) {
                        val dao = getCaptureDao()
                        if (dao != null) dao.bookmarkedItems().map { SearchPage(it, true) }
                        else flowOf(SearchPage(emptyList(), true))
                    } else if (state.query.isBlank()) {
                        searchEngine.recentPaged(
                            contentType = state.contentType,
                            appPackage = state.appPackage,
                            dateRange = state.dateRange,
                            pageSize = PAGE_SIZE,
                            offset = state.offset,
                        )
                    } else {
                        searchEngine.searchPaged(
                            rawQuery = state.query,
                            contentType = state.contentType,
                            appPackage = state.appPackage,
                            dateRange = state.dateRange,
                            pageSize = PAGE_SIZE,
                            offset = state.offset,
                        )
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Search failed", e)
                    emit(SearchPage(emptyList(), true))
                }
                .collect { page ->
                    if (page.items.isEmpty() && _offset.value > 0) {
                        _hasMore.value = false
                    } else if (page.items.isEmpty()) {
                        _results.value = emptyList()
                        _loadedCount.value = 0
                        _hasMore.value = false
                    } else if (_offset.value == 0) {
                        _results.value = page.items
                        _loadedCount.value = page.items.size
                    } else {
                        _results.value = _results.value + page.items
                        _loadedCount.value = _loadedCount.value + page.items.size
                    }
                    _hasMore.value = !page.isLastPage && page.items.isNotEmpty()
                    _loadingMore.value = false
                }
        }

        viewModelScope.launch {
            results.collect { items ->
                val packages = items.map { it.appPackage }.distinct()
                val map = packages.associateWith { pkg ->
                    resolveCanOpen(pkg)
                }
                _canOpenPackages.value = map
            }
        }
    }

    private fun resolveCanOpen(appPackage: String): Boolean {
        if (appPackage == "local") return true
        return try {
            val pm = getApplication<Application>().packageManager
            val intent = pm.getLaunchIntentForPackage(appPackage)
            if (intent != null) return true
            val resolveIntent = Intent(Intent.ACTION_MAIN).apply { setPackage(appPackage) }
            pm.queryIntentActivities(resolveIntent, 0)
                .any { it.activityInfo.packageName == appPackage }
        } catch (_: Exception) {
            false
        }
    }

    fun onQueryChange(text: String) {
        resetOffsetForNewSearch()
        _query.value = text
        _queryToPersist.value = text
    }

    fun setContentTypeFilter(type: String?) {
        resetOffsetForNewSearch()
        _contentTypeFilter.value = type
        if (type != "bookmarked") {
            _bookmarkedOnly.value = false
            prefs.edit().putBoolean("bookmarked_filter", false).apply()
        }
        prefs.edit().putString("content_type_filter", type).apply()
    }

    fun setBookmarkedFilter(enabled: Boolean) {
        resetOffsetForNewSearch()
        _bookmarkedOnly.value = enabled
        _contentTypeFilter.value = null
        prefs.edit().putBoolean("bookmarked_filter", enabled).apply()
        prefs.edit().putString("content_type_filter", null).apply()
    }

    fun setShowStats(enabled: Boolean) {
        _showStats.value = enabled
        prefs.edit().putBoolean("show_stats", enabled).apply()
    }

    fun toggleBookmark(item: CapturedItem) {
        viewModelScope.launch {
            try {
                getCaptureDao()?.setBookmarked(item.id, !item.isBookmarked)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle bookmark", e)
            }
        }
    }

    fun setAppFilter(appPackage: String?) {
        resetOffsetForNewSearch()
        _appFilter.value = appPackage
    }

    fun setDateFilter(dateRange: Pair<Long, Long>?) {
        resetOffsetForNewSearch()
        _dateFilter.value = dateRange
    }

    fun loadMore() {
        if (_hasMore.value && !_loadingMore.value) {
            _loadingMore.value = true
            _offset.value += PAGE_SIZE
        }
    }

    private fun resetOffsetForNewSearch() {
        if (_offset.value != 0) {
            _offset.value = 0
        }
    }

    fun quickDateFilterToday() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        setDateFilter(start to cal.timeInMillis)
    }

    fun quickDateFilterThisWeek() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.WEEK_OF_YEAR, 1)
        setDateFilter(start to cal.timeInMillis)
    }

    fun quickDateFilterThisMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        setDateFilter(start to cal.timeInMillis)
    }

    fun getDistinctAppPackages(): List<String> {
        return _results.value.map { it.appPackage }.distinct().sorted()
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

    private data class FilterState(
        val query: String,
        val contentType: String?,
        val bookmarked: Boolean,
        val appPackage: String?,
        val dateRange: Pair<Long, Long>?,
        val offset: Int,
    )

    companion object {
        private const val TAG = "SearchViewModel"
        private const val PAGE_SIZE = 50
        private const val DEBOUNCE_MILLIS_DEFAULT = 300L
    }
}
