package com.example.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val dao: AppRuleDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("no_ads_preferences", Context.MODE_PRIVATE)

    private val _isMasterProtectionEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_MASTER_PROTECTION, true)
    )
    val isMasterProtectionEnabled: Flow<Boolean> = _isMasterProtectionEnabled.asStateFlow()
    fun isMasterProtectionEnabledNow(): Boolean = _isMasterProtectionEnabled.value

    private val _darkModePreference = MutableStateFlow(
        prefs.getInt(KEY_DARK_MODE, 2) // 0: System, 1: Light, 2: Dark (default to dark theme)
    )
    val darkModePreference: Flow<Int> = _darkModePreference.asStateFlow()

    private val _autoBlockNewApps = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_BLOCK_NEW, true)
    )
    val autoBlockNewApps: Flow<Boolean> = _autoBlockNewApps.asStateFlow()

    private val _blockTelemetry = MutableStateFlow(
        prefs.getBoolean(KEY_BLOCK_TELEMETRY, true)
    )
    val blockTelemetry: Flow<Boolean> = _blockTelemetry.asStateFlow()

    private val _filterAggressiveness = MutableStateFlow(
        prefs.getString(KEY_AGGRESSIVENESS, "Balanced") ?: "Balanced"
    )
    val filterAggressiveness: Flow<String> = _filterAggressiveness.asStateFlow()

    fun allRulesFlow(): Flow<List<AppRuleEntity>> = dao.getAllAppRulesFlow()
    fun totalAdsBlockedFlow(): Flow<Int?> = dao.getTotalAdsBlockedFlow()
    fun totalDataSavedKbFlow(): Flow<Long?> = dao.getTotalDataSavedKbFlow()

    suspend fun setMasterProtection(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_MASTER_PROTECTION, enabled).apply()
        _isMasterProtectionEnabled.value = enabled
    }

    suspend fun setDarkMode(mode: Int) = withContext(Dispatchers.IO) {
        prefs.edit().putInt(KEY_DARK_MODE, mode).apply()
        _darkModePreference.value = mode
    }

    suspend fun setAutoBlockNewApps(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_AUTO_BLOCK_NEW, enabled).apply()
        _autoBlockNewApps.value = enabled
    }

    suspend fun setBlockTelemetry(enabled: Boolean) = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_BLOCK_TELEMETRY, enabled).apply()
        _blockTelemetry.value = enabled
    }

    suspend fun setFilterAggressiveness(level: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_AGGRESSIVENESS, level).apply()
        _filterAggressiveness.value = level
    }

    suspend fun resetAllStats() = withContext(Dispatchers.IO) {
        dao.resetAllStats()
    }

    suspend fun toggleAppBlocked(packageName: String, currentBlocked: Boolean) = withContext(Dispatchers.IO) {
        dao.updateAdBlockedStatus(packageName, !currentBlocked)
    }

    suspend fun setAllBlocked(blocked: Boolean) = withContext(Dispatchers.IO) {
        dao.setAllBlockedStatus(blocked)
    }

    /**
     * Dynamically fetches real installed user apps and games from the device PackageManager.
     * ZERO hardcoded/random placeholder apps are used.
     * Preserves existing user rules and cleans up uninstalled packages.
     */
    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val myPackage = context.packageName

        // 1. Query all launchable activities (real user-installed apps and games)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launchableActivities = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        // Also query specific game launcher category
        val gameIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory("android.intent.category.GAME")
        }
        val gameActivities = try {
            packageManager.queryIntentActivities(gameIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }

        val gamePackagesFromIntent = gameActivities
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        val launchablePackages = launchableActivities
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != myPackage }
            .toSet()

        // 2. Query all installed applications to capture any non-system user apps
        val allInstalledApps = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }

        // Map existing rules in DB to preserve user's toggle states and metrics
        val existingRulesMap = dao.getAllAppRulesList().associateBy { it.packageName }
        val freshRules = mutableListOf<AppRuleEntity>()
        val processedPackages = mutableSetOf<String>()

        val autoBlock = _autoBlockNewApps.value

        // Helper to process an application info
        fun processApp(appInfo: ApplicationInfo) {
            val pkg = appInfo.packageName
            if (pkg == myPackage || processedPackages.contains(pkg)) return
            processedPackages.add(pkg)

            val appName = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }

            val isGame = isGamePackage(appInfo, gamePackagesFromIntent.contains(pkg))
            val category = if (isGame) "Game" else detectCategory(appName, pkg)

            val existing = existingRulesMap[pkg]
            if (existing != null) {
                // Preserve user's toggle and real stats
                freshRules.add(
                    existing.copy(
                        appName = appName,
                        isGame = isGame,
                        category = category
                    )
                )
            } else {
                freshRules.add(
                    AppRuleEntity(
                        packageName = pkg,
                        appName = appName,
                        isGame = isGame,
                        isAdBlocked = autoBlock,
                        adsBlockedCount = 0,
                        dataSavedKb = 0L,
                        category = category
                    )
                )
            }
        }

        // 1. Process all launchable user apps/games
        for (resolveInfo in launchableActivities) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == myPackage || processedPackages.contains(pkg)) continue
            val appInfo = try {
                resolveInfo.activityInfo.applicationInfo ?: packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                continue
            }
            processApp(appInfo)
        }

        // Also process any games found via game launcher intent
        for (resolveInfo in gameActivities) {
            val pkg = resolveInfo.activityInfo?.packageName ?: continue
            if (pkg == myPackage || processedPackages.contains(pkg)) continue
            val appInfo = try {
                resolveInfo.activityInfo.applicationInfo ?: packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                continue
            }
            processApp(appInfo)
        }

        // 2. Process any other non-system or updated-system apps
        for (appInfo in allInstalledApps) {
            val isNonSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            if (isNonSystem || launchablePackages.contains(appInfo.packageName) || gamePackagesFromIntent.contains(appInfo.packageName)) {
                processApp(appInfo)
            }
        }

        // Insert or update in database
        if (freshRules.isNotEmpty()) {
            dao.insertAll(freshRules)
            // Clean up uninstalled apps
            val currentPackages = freshRules.map { it.packageName }
            dao.deletePackagesNotIn(currentPackages)
        }
    }

    private fun isGamePackage(appInfo: ApplicationInfo, hasGameIntent: Boolean): Boolean {
        if (hasGameIntent) return true
        return try {
            // Standard Android Game classification:
            // 1. Android 8.0+ (API 26+) ApplicationInfo.category == CATEGORY_GAME
            val isCategoryGame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else false

            // 2. Android 5.0+ (API 21+) ApplicationInfo.FLAG_IS_GAME
            val isFlagGame = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

            // 3. Application metaData manifest declaration ("isGame")
            val isMetaGame = appInfo.metaData?.getBoolean("isGame", false) == true

            isCategoryGame || isFlagGame || isMetaGame
        } catch (_: Exception) {
            false
        }
    }

    private fun detectCategory(name: String, pkg: String): String {
        val lower = (name + " " + pkg).lowercase()
        return when {
            lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") || lower.contains("web") -> "Web Browser"
            lower.contains("social") || lower.contains("chat") || lower.contains("message") || lower.contains("talk") -> "Social & Chat"
            lower.contains("music") || lower.contains("audio") || lower.contains("sound") || lower.contains("podcast") -> "Audio & Music"
            lower.contains("video") || lower.contains("tube") || lower.contains("stream") || lower.contains("player") -> "Video Player"
            lower.contains("camera") || lower.contains("photo") || lower.contains("gallery") -> "Photo & Media"
            lower.contains("shop") || lower.contains("store") -> "Shopping"
            lower.contains("mail") || lower.contains("office") || lower.contains("doc") -> "Productivity"
            lower.contains("tool") || lower.contains("util") || lower.contains("settings") -> "System & Tools"
            lower.contains("news") || lower.contains("feed") -> "News & Reading"
            else -> "Application"
        }
    }

    companion object {
        private const val KEY_MASTER_PROTECTION = "key_master_protection"
        private const val KEY_DARK_MODE = "key_dark_mode"
        private const val KEY_AUTO_BLOCK_NEW = "key_auto_block_new"
        private const val KEY_BLOCK_TELEMETRY = "key_block_telemetry"
        private const val KEY_AGGRESSIVENESS = "key_aggressiveness"
    }
}

