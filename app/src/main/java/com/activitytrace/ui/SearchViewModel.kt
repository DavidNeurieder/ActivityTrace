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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _canOpenPackages = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val canOpenPackages = _canOpenPackages.asStateFlow()

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
            combine(_query, _contentTypeFilter) { q, filter -> q to filter }
                .flatMapLatest { (q, filter) ->
                    val flow = if (q.isBlank()) searchEngine.recentItems(filter)
                    else searchEngine.search(q, filter)
                    flow.catch { e ->
                        Log.e(TAG, "Search failed", e)
                        emit(emptyList())
                    }
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

    companion object {
        private const val TAG = "SearchViewModel"
    }
}
