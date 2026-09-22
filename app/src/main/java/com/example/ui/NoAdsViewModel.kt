package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.AppRuleEntity
import com.example.data.NoAdsDatabase
import com.example.service.NoAdsVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class FilterType {
    ALL,
    GAMES,
    APPS,
    BLOCKED,
    ALLOWED
}

data class NoAdsUiState(
    val masterProtectionEnabled: Boolean = true,
    val rules: List<AppRuleEntity> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: FilterType = FilterType.ALL,
    val totalAdsBlocked: Int = 0,
    val totalDataSavedMb: String = "0.0 MB",
    val protectedAppsCount: Int = 0,
    val totalAppsCount: Int = 0,
    val darkModePref: Int = 0, // 0: System, 1: Light, 2: Dark
    val autoBlockNewApps: Boolean = true,
    val blockTelemetry: Boolean = true,
    val filterAggressiveness: String = "Balanced",
    val ramUsageMb: String = "1.8 MB",
    val cpuUsagePercent: String = "< 0.1%",
    val isRefreshing: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val isInitialized: Boolean = false,
    val isFirewallActive: Boolean = false,
    val firewallEngineAuthor: String = "Developed by MJ MASUM BILLA"
)

class NoAdsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery

    private val _activeFilter = MutableStateFlow(FilterType.ALL)
    val activeFilter = _activeFilter

    private val _isRefreshing = MutableStateFlow(false)
    private val _showSettingsDialog = MutableStateFlow(false)

    init {
        val db = NoAdsDatabase.getDatabase(application)
        repository = AppRepository(application, db.appRuleDao())

        viewModelScope.launch {
            repository.syncInstalledApps()
            // If master protection was previously enabled, start firewall service
            val masterEnabled = repository.isMasterProtectionEnabledNow()
            if (masterEnabled) {
                try {
                    NoAdsVpnService.startService(application)
                } catch (_: Exception) {}
            }
        }
    }

    private val queryAndFilterFlow = combine(_searchQuery, _activeFilter) { query, filter ->
        Pair(query, filter)
    }

    private val settingsFlow = combine(
        repository.autoBlockNewApps,
        repository.blockTelemetry,
        repository.filterAggressiveness,
        _isRefreshing,
        _showSettingsDialog
    ) { autoBlock, telemetry, agg, refreshing, showSettings ->
        SettingsBundle(autoBlock, telemetry, agg, refreshing, showSettings)
    }

    private val appStatsFlow = combine(
        repository.allRulesFlow(),
        repository.isMasterProtectionEnabled,
        NoAdsVpnService.isServiceRunning
    ) { allRules, masterEnabled, isVpnRunning ->
        Triple(allRules, masterEnabled, isVpnRunning)
    }

    val uiState: StateFlow<NoAdsUiState> = combine(
        appStatsFlow,
        repository.darkModePreference,
        settingsFlow,
        queryAndFilterFlow
    ) { appStats, darkMode, settings, qf ->
        val (allRules, masterEnabled, isVpnRunning) = appStats
        val (query, filter) = qf
        val filtered = allRules.filter { rule ->
            val matchesQuery = query.isBlank() ||
                    rule.appName.contains(query, ignoreCase = true) ||
                    rule.packageName.contains(query, ignoreCase = true) ||
                    rule.category.contains(query, ignoreCase = true)

            val matchesFilter = when (filter) {
                FilterType.ALL -> true
                FilterType.GAMES -> rule.isGame
                FilterType.APPS -> !rule.isGame
                FilterType.BLOCKED -> rule.isAdBlocked
                FilterType.ALLOWED -> !rule.isAdBlocked
            }

            matchesQuery && matchesFilter
        }

        val totalBlocked = allRules.sumOf { it.adsBlockedCount }
        val totalKb = allRules.sumOf { it.dataSavedKb }
        val mbString = "%.1f MB".format(totalKb / 1024f)
        val protectedCount = allRules.count { it.isAdBlocked }

        NoAdsUiState(
            masterProtectionEnabled = masterEnabled,
            rules = filtered,
            searchQuery = query,
            activeFilter = filter,
            totalAdsBlocked = totalBlocked,
            totalDataSavedMb = mbString,
            protectedAppsCount = protectedCount,
            totalAppsCount = allRules.size,
            darkModePref = darkMode,
            autoBlockNewApps = settings.autoBlock,
            blockTelemetry = settings.telemetry,
            filterAggressiveness = settings.agg,
            ramUsageMb = "1.8 MB",
            cpuUsagePercent = "< 0.1%",
            isRefreshing = settings.refreshing,
            showSettingsDialog = settings.showSettings,
            isInitialized = true,
            isFirewallActive = isVpnRunning && masterEnabled,
            firewallEngineAuthor = "Developed by MJ MASUM BILLA"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NoAdsUiState()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onFilterSelected(filter: FilterType) {
        _activeFilter.value = filter
    }

    fun toggleMasterProtection(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMasterProtection(enabled)
            val app = getApplication<Application>()
            if (enabled) {
                NoAdsVpnService.startService(app)
            } else {
                NoAdsVpnService.stopService(app)
            }
        }
    }

    fun toggleAppBlocked(packageName: String, currentBlocked: Boolean) {
        viewModelScope.launch {
            repository.toggleAppBlocked(packageName, currentBlocked)
            val app = getApplication<Application>()
            if (uiState.value.masterProtectionEnabled) {
                NoAdsVpnService.reloadRules(app)
            }
        }
    }

    fun setAllAppsBlocked(blocked: Boolean) {
        viewModelScope.launch {
            repository.setAllBlocked(blocked)
            val app = getApplication<Application>()
            if (uiState.value.masterProtectionEnabled) {
                NoAdsVpnService.reloadRules(app)
            }
        }
    }

    fun setDarkModePref(mode: Int) {
        viewModelScope.launch {
            repository.setDarkMode(mode)
        }
    }

    fun cycleDarkMode() {
        val current = uiState.value.darkModePref
        val next = (current + 1) % 3
        setDarkModePref(next)
    }

    fun setShowSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun setAutoBlockNewApps(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoBlockNewApps(enabled)
        }
    }

    fun setBlockTelemetry(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBlockTelemetry(enabled)
        }
    }

    fun setFilterAggressiveness(level: String) {
        viewModelScope.launch {
            repository.setFilterAggressiveness(level)
        }
    }

    fun resetAllStats() {
        viewModelScope.launch {
            repository.resetAllStats()
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncInstalledApps()
            _isRefreshing.value = false
        }
    }

    private data class SettingsBundle(
        val autoBlock: Boolean,
        val telemetry: Boolean,
        val agg: String,
        val refreshing: Boolean,
        val showSettings: Boolean
    )
}

