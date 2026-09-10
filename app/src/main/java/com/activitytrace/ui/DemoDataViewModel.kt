package com.activitytrace.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.activitytrace.demo.DemoDataRepository
import com.activitytrace.demo.DemoDataScenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State machine for the Demo Data screen.
 *
 * A dataset is either EMPTY (no rows), READY (rows present and idle) or the
 * screen is BUSY (a generation/clear is in flight). The ViewModel deliberately
 * stores counts and flags, never Android resources, so UI tests can drive it
 * with a fake repository.
 */
class DemoDataViewModel(
    private val repository: DemoDataRepository,
) : ViewModel() {

    /** 0 == EMPTY, > 0 == READY. Derived from the live row counts. */
    val showcaseCount: StateFlow<Int> = repository.recordCount(DemoDataScenario.SHOWCASE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val benchmarkCount: StateFlow<Int> = repository.recordCount(DemoDataScenario.SEARCH_BENCHMARK)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    private val _lastGeneratedAt = MutableStateFlow(repository.lastGeneratedAt(DemoDataScenario.SHOWCASE))
    val lastGeneratedAt: StateFlow<Long> = _lastGeneratedAt.asStateFlow()

    fun generateShowcase() = runAction { repository.generate(DemoDataScenario.SHOWCASE) }

    fun regenerateShowcase() = runAction { repository.regenerate(DemoDataScenario.SHOWCASE) }

    /** Clears the showcase. No confirmation lives here; the screen asks first. */
    fun clearShowcase() = runAction { repository.clear(DemoDataScenario.SHOWCASE) }

    fun generateBenchmark() = runAction { repository.regenerate(DemoDataScenario.SEARCH_BENCHMARK) }

    fun clearBenchmark() = runAction { repository.clear(DemoDataScenario.SEARCH_BENCHMARK) }

    fun consumeError() {
        _error.value = false
    }

    private fun runAction(block: suspend () -> Int) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = false
            try {
                block()
                _lastGeneratedAt.value = repository.lastGeneratedAt(DemoDataScenario.SHOWCASE)
            } catch (_: Throwable) {
                _error.value = true
            } finally {
                _busy.value = false
            }
        }
    }

    class Factory(private val repository: DemoDataRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            DemoDataViewModel(repository) as T
    }
}