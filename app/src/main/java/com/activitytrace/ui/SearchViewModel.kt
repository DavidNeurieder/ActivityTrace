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
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchEngine: SearchEngine,
    application: Application,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("activity_trace", Context.MODE_PRIVATE)

    private val _query = MutableStateFlow(prefs.getString("search_query", "") ?: "")
    val query = _query.asStateFlow()
    private val _queryToPersist = MutableStateFlow<String?>(null)

    private val _results = MutableStateFlow<List<CapturedItem>>(emptyList())
    val results = _results.asStateFlow()

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
        } catch (e: Throwable) {
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

        viewModelScope.launch {
            combine(
                _query, _contentTypeFilter, _bookmarkedOnly,
                _appFilter, _dateFilter,
            ) { q, type, bookmarked, app, date ->
                FilterState(q, type, bookmarked, app, date)
            }
                .flatMapLatest { state ->
                    if (state.bookmarked) {
                        val dao = getCaptureDao()
                        if (dao != null) dao.bookmarkedItems()
                        else flowOf(emptyList())
                    } else if (state.query.isBlank()) {
                        searchEngine.recentItems(
                            contentType = state.contentType,
                            appPackage = state.appPackage,
                            dateRange = state.dateRange,
                        )
                    } else {
                        searchEngine.search(
                            rawQuery = state.query,
                            contentType = state.contentType,
                            appPackage = state.appPackage,
                            dateRange = state.dateRange,
                        )
                    }
                }
                .catch { e ->
                    Log.e(TAG, "Search failed", e)
                    emit(emptyList())
                }
                .collect { items ->
                    _results.value = items
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
        _query.value = text
        _queryToPersist.value = text
    }

    fun setContentTypeFilter(type: String?) {
        _contentTypeFilter.value = type
        if (type != "bookmarked") {
            _bookmarkedOnly.value = false
            prefs.edit().putBoolean("bookmarked_filter", false).apply()
        }
        prefs.edit().putString("content_type_filter", type).apply()
    }

    fun setBookmarkedFilter(enabled: Boolean) {
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
        _appFilter.value = appPackage
    }

    fun setDateFilter(dateRange: Pair<Long, Long>?) {
        _dateFilter.value = dateRange
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
    )

    companion object {
        private const val TAG = "SearchViewModel"
    }
}
