package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppRuleEntity
import com.example.ui.theme.AmberPaused
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldPrimary
import com.example.util.rememberAppIcon

@Composable
fun NoAdsDashboardScreen(
    viewModel: NoAdsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedAppForDetail by remember { mutableStateOf<AppRuleEntity?>(null) }

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleMasterProtection(true)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            LightweightEngineFooter(
                ramUsage = uiState.ramUsageMb,
                cpuUsage = uiState.cpuUsagePercent
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dashboard Top Header
            DashboardHeader(
                darkModePref = uiState.darkModePref,
                onCycleTheme = { viewModel.cycleDarkMode() },
                onOpenSettings = { viewModel.setShowSettingsDialog(true) }
            )

            // Master Protection Toggle Hero Card
            MasterProtectionCard(
                isEnabled = uiState.masterProtectionEnabled,
                onToggle = { enable ->
                    if (enable) {
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnPrepareLauncher.launch(vpnIntent)
                        } else {
                            viewModel.toggleMasterProtection(true)
                        }
                    } else {
                        viewModel.toggleMasterProtection(false)
                    }
                },
                totalBlocked = uiState.totalAdsBlocked,
                dataSaved = uiState.totalDataSavedMb,
                protectedAppsCount = uiState.protectedAppsCount,
                totalAppsCount = uiState.totalAppsCount,
                ramUsage = uiState.ramUsageMb,
                isFirewallActive = uiState.isFirewallActive
            )

            // Search Bar & Filter Chips
            SearchAndFilterSection(
                searchQuery = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                activeFilter = uiState.activeFilter,
                onFilterSelected = { viewModel.onFilterSelected(it) },
                onBlockAll = { viewModel.setAllAppsBlocked(true) },
                onAllowAll = { viewModel.setAllAppsBlocked(false) }
            )

            // List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (uiState.activeFilter) {
                        FilterType.GAMES -> "Installed Games (${uiState.rules.size})"
                        FilterType.APPS -> "Installed Applications (${uiState.rules.size})"
                        else -> "Installed Apps & Games (${uiState.rules.size})"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = if (uiState.masterProtectionEnabled) "Tap to inspect trackers" else "Protection Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Scrollable List of Apps and Games
            if (uiState.rules.isEmpty()) {
                EmptyStateView(
                    searchQuery = uiState.searchQuery,
                    activeFilter = uiState.activeFilter,
                    isRefreshing = uiState.isRefreshing || !uiState.isInitialized,
                    onClearSearch = { viewModel.onSearchQueryChanged("") }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("app_list"),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = uiState.rules,
                        key = { it.packageName },
                        contentType = { "app_row" }
                    ) { rule ->
                        AppRowItem(
                            rule = rule,
                            isMasterProtectionEnabled = uiState.masterProtectionEnabled,
                            onToggle = { viewModel.toggleAppBlocked(rule.packageName, rule.isAdBlocked) },
                            onClickInfo = { selectedAppForDetail = rule }
                        )
                    }
                }
            }
        }
    }

    // Tracker Inspector Preview Dialog when user taps an app row
    selectedAppForDetail?.let { app ->
        TrackerInspectorDialog(
            app = app,
            isMasterEnabled = uiState.masterProtectionEnabled,
            onDismiss = { selectedAppForDetail = null },
            onToggleBlocked = {
                viewModel.toggleAppBlocked(app.packageName, app.isAdBlocked)
                selectedAppForDetail = app.copy(isAdBlocked = !app.isAdBlocked)
            }
        )
    }

    // Sleek Settings Menu Dialog
    if (uiState.showSettingsDialog) {
        SettingsDialog(
            darkModePref = uiState.darkModePref,
            onSetDarkMode = { viewModel.setDarkModePref(it) },
            autoBlockNewApps = uiState.autoBlockNewApps,
            onToggleAutoBlock = { viewModel.setAutoBlockNewApps(it) },
            blockTelemetry = uiState.blockTelemetry,
            onToggleBlockTelemetry = { viewModel.setBlockTelemetry(it) },
            filterAggressiveness = uiState.filterAggressiveness,
            onSetAggressiveness = { viewModel.setFilterAggressiveness(it) },
            isRefreshing = uiState.isRefreshing,
            onRefreshApps = { viewModel.refreshInstalledApps() },
            onResetStats = { viewModel.resetAllStats() },
            onDismiss = { viewModel.setShowSettingsDialog(false) }
        )
    }
}

@Composable
private fun EmptyStateView(
    searchQuery: String,
    activeFilter: FilterType = FilterType.ALL,
    isRefreshing: Boolean = false,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isRefreshing) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = EmeraldPrimary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Scanning device installed applications...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = "No search results",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = "No apps found matching \"$searchQuery\"",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Check the spelling or try a different filter tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onClearSearch) {
                    Text(
                        text = "Clear Search",
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                when (activeFilter) {
                    FilterType.GAMES -> {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "No games found",
                            tint = Color(0xFFF59E0B).copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No games found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No games detected on this device via standard system classification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    FilterType.APPS -> {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "No applications found",
                            tint = CyanAccent.copy(alpha = 0.6f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No applications found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No standalone applications match the current filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "No results",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No apps found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "No installed user applications detected on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ultra-premium Tracker Inspector Preview Dialog.
 * Shows deep telemetry inspection, detected ad SDKs, data saved,
 * and live ad-blocking toggling without lag.
 */
@Composable
private fun TrackerInspectorDialog(
    app: AppRuleEntity,
    isMasterEnabled: Boolean,
    onDismiss: () -> Unit,
    onToggleBlocked: () -> Unit
) {
    val appIcon = rememberAppIcon(app.packageName)
    val isEffectivelyProtected = isMasterEnabled && app.isAdBlocked

    val trackerItems = listOf(
        Triple("Google AdMob / DoubleClick", "Banner & Interstitial Ads", isEffectivelyProtected),
        Triple("Unity Ads / IronSource", "Rewarded Video Engine", isEffectivelyProtected),
        Triple("AppLovin MAX / InMobi", "In-App Bidding Auctions", isEffectivelyProtected),
        Triple("Meta Audience Network", "Behavioral Ad Tracking", isEffectivelyProtected),
        Triple("AppsFlyer / Adjust Beacon", "Device Telemetry & Profiling", isEffectivelyProtected),
        Triple("Crashlytics / Firebase", "Crash & Diagnostic Telemetry", false)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = app.appName,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (app.isGame) Icons.Default.SportsEsports else Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                    Text(
                        text = "TRACKER INSPECTOR PREVIEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isEffectivelyProtected) EmeraldPrimary else AmberPaused,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Glassmorphism package & status pill
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                EmeraldPrimary.copy(alpha = 0.35f),
                                CyanAccent.copy(alpha = 0.35f)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Package: ${app.packageName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            Text(
                                text = "Category: ${app.category} • ${if (app.isGame) "Game" else "Application"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            color = if (isEffectivelyProtected) EmeraldPrimary.copy(alpha = 0.18f) else AmberPaused.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isEffectivelyProtected) "SHIELD ACTIVE" else "ADS ALLOWED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isEffectivelyProtected) EmeraldPrimary else AmberPaused,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Stats metric row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${app.adsBlockedCount}",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldPrimary,
                                fontSize = 16.sp
                            )
                            Text("Ads Blocked", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "%.1f MB".format(app.dataSavedKb / 1024f),
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 16.sp
                            )
                            Text("Data Saved", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "0 ms",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldLight,
                                fontSize = 16.sp
                            )
                            Text("Filter Lag", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Text(
                    text = "Detected Trackers & Telemetry Endpoints:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                // List of inspected telemetry and ad SDKs
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trackerItems.forEach { (name, type, blocked) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = type,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = if (blocked) EmeraldPrimary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (blocked) "BLOCKED" else "PASS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (blocked) EmeraldPrimary else Color.Gray,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Interactive Toggle row inside inspector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ad Blocking for this App",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (app.isAdBlocked) "Actively filtering ads and trackers" else "Whitelisted (ads permitted)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = app.isAdBlocked,
                        onCheckedChange = { onToggleBlocked() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldPrimary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Done")
            }
        }
    )
}
