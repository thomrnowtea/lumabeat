package com.lumabeat.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumabeat.app.BuildConfig
import com.lumabeat.app.audio.BeatPreset
import com.lumabeat.app.media.ArtworkColorIntensity
import com.lumabeat.app.update.AppUpdateStatus
import com.lumabeat.app.update.UpdateFailure
import com.lumabeat.app.wiz.WizLight

private val BackgroundTop = Color(0xFF070A13)
private val BackgroundBottom = Color(0xFF101123)
private val Surface = Color(0xFF151829)
private val SurfaceRaised = Color(0xFF22263A)
private val Violet = Color(0xFF8A7CFF)
private val VioletSoft = Color(0xFF302B61)
private val Cyan = Color(0xFF72D7F7)
private val Green = Color(0xFF65E6B5)
private val TextPrimary = Color(0xFFF8F7FF)
private val TextSecondary = Color(0xFFB3B4C8)
private val FocusBorder = Color(0xFFE5E0FF)

private val LumaBeatColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    background = BackgroundTop,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
)

private enum class AppPage {
    Dashboard,
    Settings,
    Licenses,
}

@Composable
fun LumaBeatApp(viewModel: LumaBeatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var autoStartHandled by rememberSaveable { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startAudioReactiveBrightness()
        else viewModel.audioPermissionDenied()
    }

    LaunchedEffect(Unit) {
        viewModel.discoverLights()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationAccess()
        viewModel.refreshInstallPermission()
    }

    LaunchedEffect(state.autoStartEnabled, state.isDiscovering, state.lights.size) {
        if (!state.autoStartEnabled) {
            autoStartHandled = false
        } else if (!autoStartHandled && !state.isDiscovering && state.lights.isNotEmpty()) {
            autoStartHandled = true
            if (
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startAudioReactiveBrightness()
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val activity = context as? Activity
    DisposableEffect(activity, state.keepScreenOnEnabled) {
        if (state.keepScreenOnEnabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (state.keepScreenOnEnabled) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val toggleListening = {
        when {
            state.isAudioReactive -> viewModel.stopAudioReactiveBrightness()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startAudioReactiveBrightness()
            else -> audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    MaterialTheme(colorScheme = LumaBeatColors) {
        LumaBeatScreen(
            state = state,
            onDiscover = viewModel::discoverLights,
            onPresetSelected = viewModel::setBeatPreset,
            onLightIncludedChange = viewModel::setLightIncluded,
            onListeningToggle = toggleListening,
            onAutoStartChange = viewModel::setAutoStartEnabled,
            onKeepScreenOnChange = viewModel::setKeepScreenOnEnabled,
            onMediaColorsChange = viewModel::setMediaColorsEnabled,
            onArtworkColorIntensityChange = viewModel::setArtworkColorIntensity,
            onOpenNotificationAccess = viewModel::openNotificationAccessSettings,
            onAutomaticUpdatesChange = viewModel::setAutomaticUpdateChecks,
            onCheckForUpdates = { viewModel.checkForUpdates() },
            onDownloadUpdate = viewModel::downloadUpdate,
            onInstallUpdate = viewModel::installUpdate,
            onOpenInstallPermission = viewModel::openInstallPermissionSettings,
        )
    }
}

@Composable
private fun LumaBeatScreen(
    state: LumaBeatUiState,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onMediaColorsChange: (Boolean) -> Unit,
    onArtworkColorIntensityChange: (ArtworkColorIntensity) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    var currentPage by rememberSaveable { mutableStateOf(AppPage.Dashboard) }

    BackHandler(enabled = currentPage != AppPage.Dashboard) {
        currentPage = if (currentPage == AppPage.Licenses) AppPage.Settings else AppPage.Dashboard
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BackgroundTop, BackgroundBottom))),
    ) {
        val isTvLayout = maxWidth >= 700.dp && maxWidth > maxHeight
        when (currentPage) {
            AppPage.Dashboard -> if (isTvLayout) {
                TvDashboard(
                    state = state,
                    onOpenSettings = { currentPage = AppPage.Settings },
                    onDiscover = onDiscover,
                    onPresetSelected = onPresetSelected,
                    onLightIncludedChange = onLightIncludedChange,
                    onListeningToggle = onListeningToggle,
                    onMediaColorsChange = onMediaColorsChange,
                    onArtworkColorIntensityChange = onArtworkColorIntensityChange,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                )
            } else {
                PhoneDashboard(
                    state = state,
                    onOpenSettings = { currentPage = AppPage.Settings },
                    onDiscover = onDiscover,
                    onPresetSelected = onPresetSelected,
                    onLightIncludedChange = onLightIncludedChange,
                    onListeningToggle = onListeningToggle,
                    onMediaColorsChange = onMediaColorsChange,
                    onArtworkColorIntensityChange = onArtworkColorIntensityChange,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                )
            }
            AppPage.Settings -> SettingsScreen(
                state = state,
                compact = isTvLayout,
                onBack = { currentPage = AppPage.Dashboard },
                onOpenLicenses = { currentPage = AppPage.Licenses },
                onAutoStartChange = onAutoStartChange,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onAutomaticUpdatesChange = onAutomaticUpdatesChange,
                onCheckForUpdates = onCheckForUpdates,
                onDownloadUpdate = onDownloadUpdate,
                onInstallUpdate = onInstallUpdate,
                onOpenInstallPermission = onOpenInstallPermission,
            )
            AppPage.Licenses -> LicensesScreen(
                compact = isTvLayout,
                onBack = { currentPage = AppPage.Settings },
            )
        }
    }
}

@Composable
private fun TvDashboard(
    state: LumaBeatUiState,
    onOpenSettings: () -> Unit,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
    onMediaColorsChange: (Boolean) -> Unit,
    onArtworkColorIntensityChange: (ArtworkColorIntensity) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Header(
            state = state,
            compact = true,
            onOpenSettings = onOpenSettings,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.12f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ListeningCard(
                    state = state,
                    compact = true,
                    onToggle = onListeningToggle,
                    modifier = Modifier.weight(1f),
                )
                PresetPanel(
                    selectedPreset = state.beatPreset,
                    compact = true,
                    onPresetSelected = onPresetSelected,
                )
            }
            Column(
                modifier = Modifier
                    .weight(0.88f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LightsPanel(
                    state = state,
                    compact = true,
                    onDiscover = onDiscover,
                    onLightIncludedChange = onLightIncludedChange,
                    modifier = Modifier.weight(1f),
                )
                ArtworkColorsPanel(
                    state = state,
                    compact = true,
                    onEnabledChange = onMediaColorsChange,
                    onIntensityChange = onArtworkColorIntensityChange,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                )
            }
        }
    }
}

@Composable
private fun PhoneDashboard(
    state: LumaBeatUiState,
    onOpenSettings: () -> Unit,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
    onMediaColorsChange: (Boolean) -> Unit,
    onArtworkColorIntensityChange: (ArtworkColorIntensity) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Header(
            state = state,
            compact = false,
            onOpenSettings = onOpenSettings,
        )
        ListeningCard(state, compact = false, onToggle = onListeningToggle)
        PresetPanel(state.beatPreset, compact = false, onPresetSelected)
        ArtworkColorsPanel(
            state = state,
            compact = false,
            onEnabledChange = onMediaColorsChange,
            onIntensityChange = onArtworkColorIntensityChange,
            onOpenNotificationAccess = onOpenNotificationAccess,
        )
        LightsPanel(
            state = state,
            compact = false,
            onDiscover = onDiscover,
            onLightIncludedChange = onLightIncludedChange,
        )
    }
}

@Composable
private fun Header(
    state: LumaBeatUiState,
    compact: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 38.dp else 44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Violet, Cyan))),
            contentAlignment = Alignment.Center,
        ) {
            Text("L", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        Column(
            modifier = Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(
                text = "LumaBeat",
                color = TextPrimary,
                fontSize = if (compact) 24.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = if (compact) 25.sp else 31.sp,
            )
            if (!compact) {
                Text("Percussion becomes light", color = TextSecondary, fontSize = 13.sp)
            }
        }
        StatusPill(state)
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceRaised)
                    .tvFocus(CircleShape)
                    .semantics { contentDescription = "Open settings" },
            ) {
                Text("⋮", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusPill(state: LumaBeatUiState) {
    val active = state.isAudioReactive && state.signalPresent
    val label = when {
        active -> "Audio active"
        state.isAudioReactive -> "Waiting for audio"
        state.lights.isNotEmpty() && state.includedLightKeys.isEmpty() -> "0 active"
        state.lights.isNotEmpty() -> "Ready"
        else -> "No lights"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) Color(0xFF123B31) else SurfaceRaised)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) Green else TextSecondary),
        )
        Text(label, color = if (active) Green else TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ListeningCard(
    state: LumaBeatUiState,
    compact: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(if (compact) 20.dp else 26.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 15.dp else 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.isAudioReactive) "Following the beat" else "Ready to listen",
                        color = TextPrimary,
                        fontSize = if (compact) 18.sp else 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("${state.beatPreset.label} profile", color = TextSecondary, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 48.dp else 68.dp)
                        .clip(CircleShape)
                        .background(Violet.copy(alpha = 0.16f + state.audioLevel * 0.34f))
                        .border(2.dp, Violet.copy(alpha = 0.40f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${state.currentBrightness}%",
                        color = TextPrimary,
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { state.audioLevel },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 6.dp else 9.dp)
                    .clip(CircleShape),
                color = if (state.signalPresent) Cyan else Violet,
                trackColor = SurfaceRaised,
            )
            Button(
                onClick = onToggle,
                enabled = state.isAudioReactive || state.includedLightKeys.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 44.dp else 58.dp)
                    .tvFocus(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = if (state.isAudioReactive) {
                    ButtonDefaults.buttonColors(containerColor = SurfaceRaised)
                } else {
                    ButtonDefaults.buttonColors(containerColor = Violet)
                },
            ) {
                Text(
                    text = if (state.isAudioReactive) "Stop tracking" else "Start beat tracking",
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PresetPanel(
    selectedPreset: BeatPreset,
    compact: Boolean,
    onPresetSelected: (BeatPreset) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Beat response", color = TextPrimary, fontSize = if (compact) 15.sp else 19.sp, fontWeight = FontWeight.SemiBold)
            Text("Percussion sensitivity", color = TextSecondary, fontSize = 11.sp)
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        if (compact) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BeatPreset.entries.forEach { preset ->
                    PresetButton(
                        preset = preset,
                        selected = preset == selectedPreset,
                        compact = true,
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BeatPreset.entries.forEach { preset ->
                    PresetButton(
                        preset = preset,
                        selected = preset == selectedPreset,
                        compact = false,
                        onClick = { onPresetSelected(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    preset: BeatPreset,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 43.dp else 66.dp)
            .tvFocus(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) VioletSoft else Surface,
            contentColor = TextPrimary,
        ),
        border = ButtonDefaults.outlinedButtonBorder(selected),
    ) {
        if (compact) {
            Text(
                text = preset.label,
                maxLines = 1,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(preset.label, fontWeight = FontWeight.SemiBold)
                    Text(preset.description, color = TextSecondary, fontSize = 12.sp)
                }
                Text(if (selected) "●" else "○", color = if (selected) Violet else TextSecondary)
            }
        }
    }
}

@Composable
private fun LightsPanel(
    state: LumaBeatUiState,
    compact: Boolean,
    onDiscover: () -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 15.dp else 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("WiZ lights", color = TextPrimary, fontSize = if (compact) 17.sp else 19.sp, fontWeight = FontWeight.SemiBold)
                    Text("${state.includedLightKeys.size} of ${state.lights.size} follow the beat", color = TextSecondary, fontSize = 11.sp)
                }
                if (state.isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Violet, strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(if (compact) 7.dp else 13.dp))
            if (state.lights.isEmpty()) {
                EmptyLights(state, Modifier.weight(1f, fill = compact))
            } else if (compact) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(state.lights, key = { it.stableKey() }) { light ->
                        LightRow(
                            light = light,
                            included = light.stableKey() in state.includedLightKeys,
                            compact = true,
                            onIncludedChange = { onLightIncludedChange(light, it) },
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    state.lights.forEach { light ->
                        LightRow(
                            light = light,
                            included = light.stableKey() in state.includedLightKeys,
                            compact = false,
                            onIncludedChange = { onLightIncludedChange(light, it) },
                        )
                    }
                }
            }
            if (!compact) {
                state.message?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(message, color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(if (compact) 7.dp else 14.dp))
            OutlinedButton(
                onClick = onDiscover,
                enabled = !state.isDiscovering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 42.dp else 56.dp)
                    .tvFocus(RoundedCornerShape(13.dp)),
                shape = RoundedCornerShape(13.dp),
            ) {
                Text(if (state.lights.isEmpty()) "Find lights" else "Refresh lights")
            }
        }
    }
}

@Composable
private fun EmptyLights(state: LumaBeatUiState, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = if (state.isDiscovering) "Scanning the network…" else "No lights detected",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LightRow(
    light: WizLight,
    included: Boolean,
    compact: Boolean,
    onIncludedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (included) Color(0xFF1B2C31) else SurfaceRaised.copy(alpha = 0.72f))
            .tvFocus(shape)
            .clickable { onIncludedChange(!included) }
            .padding(horizontal = 11.dp, vertical = if (compact) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 38.dp)
                .clip(CircleShape)
                .background(if (included) Color(0xFF174838) else Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("●", color = if (included) Green else TextSecondary, fontSize = 14.sp)
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(light.displayName, color = TextPrimary, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                text = if (included) "Included · ${light.ipAddress}" else "Excluded · ${light.ipAddress}",
                color = TextSecondary,
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1,
            )
        }
        Switch(
            checked = included,
            onCheckedChange = null,
            modifier = Modifier
                .semantics { contentDescription = "Include ${light.displayName} in dynamic changes" }
                .graphicsLayer {
                    scaleX = if (compact) 0.78f else 0.9f
                    scaleY = if (compact) 0.78f else 0.9f
                },
        )
    }
}

@Composable
private fun ArtworkColorsPanel(
    state: LumaBeatUiState,
    compact: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onIntensityChange: (ArtworkColorIntensity) -> Unit,
    onOpenNotificationAccess: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(if (compact) 20.dp else 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 15.dp else 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Artwork colors",
                        color = TextPrimary,
                        fontSize = if (compact) 16.sp else 19.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (state.mediaColorsEnabled) "Smooth gradient active" else "Use the current media artwork",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = state.mediaColorsEnabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "Use artwork colors" },
                )
            }
            if (state.mediaColorsEnabled) {
                if (compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DetectedPalette(state, Modifier.weight(1f))
                        IntensitySelector(state.artworkColorIntensity, onIntensityChange, Modifier.weight(1.1f))
                    }
                } else {
                    DetectedPalette(state)
                    IntensitySelector(state.artworkColorIntensity, onIntensityChange)
                }
                if (!state.notificationAccessGranted) {
                    OutlinedButton(
                        onClick = onOpenNotificationAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .tvFocus(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Grant media access")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedPalette(state: LumaBeatUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Detected palette · ${state.mediaPalette.size}/3",
            color = TextSecondary,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(5.dp))
        if (state.mediaPalette.isEmpty()) {
            Text("Waiting for media artwork", color = TextSecondary, fontSize = 11.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                state.mediaPalette.take(3).forEach { color ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(color.red, color.green, color.blue))
                                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                        )
                        Text(
                            "#%02X%02X%02X".format(color.red, color.green, color.blue),
                            color = TextSecondary,
                            fontSize = 8.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntensitySelector(
    selected: ArtworkColorIntensity,
    onSelected: (ArtworkColorIntensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Color intensity", color = TextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ArtworkColorIntensity.entries.forEach { intensity ->
                val isSelected = intensity == selected
                OutlinedButton(
                    onClick = { onSelected(intensity) },
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .tvFocus(RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) VioletSoft else SurfaceRaised,
                        contentColor = TextPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(intensity.label, fontSize = 9.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: LumaBeatUiState,
    compact: Boolean,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (compact) 28.dp else 20.dp, vertical = if (compact) 12.dp else 18.dp),
    ) {
        InternalPageHeader("Settings", "Permanent playback and application preferences", onBack)
        Spacer(Modifier.height(if (compact) 16.dp else 20.dp))
        if (compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GeneralSettingsCard(
                    state = state,
                    onAutoStartChange = onAutoStartChange,
                    onKeepScreenOnChange = onKeepScreenOnChange,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    UpdateSettingsCard(
                        state = state,
                        onAutomaticUpdatesChange = onAutomaticUpdatesChange,
                        onCheckForUpdates = onCheckForUpdates,
                        onDownloadUpdate = onDownloadUpdate,
                        onInstallUpdate = onInstallUpdate,
                        onOpenInstallPermission = onOpenInstallPermission,
                    )
                    SettingsNavigationRow("Licenses", "Open-source notices and application version", onOpenLicenses)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GeneralSettingsCard(state, onAutoStartChange, onKeepScreenOnChange)
                UpdateSettingsCard(
                    state = state,
                    onAutomaticUpdatesChange = onAutomaticUpdatesChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onDownloadUpdate = onDownloadUpdate,
                    onInstallUpdate = onInstallUpdate,
                    onOpenInstallPermission = onOpenInstallPermission,
                )
                SettingsNavigationRow("Licenses", "Open-source notices and application version", onOpenLicenses)
            }
        }
    }
}

@Composable
private fun InternalPageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .height(46.dp)
                .tvFocus(RoundedCornerShape(13.dp)),
            shape = RoundedCornerShape(13.dp),
        ) {
            Text("Back")
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GeneralSettingsCard(
    state: LumaBeatUiState,
    onAutoStartChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Playback", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SettingSwitchRow(
                title = "Auto start",
                subtitle = "Start tracking after lights are found",
                checked = state.autoStartEnabled,
                onCheckedChange = onAutoStartChange,
            )
            SettingSwitchRow(
                title = "Keep screen awake",
                subtitle = "Prevent Android TV from suspending LumaBeat",
                checked = state.keepScreenOnEnabled,
                onCheckedChange = onKeepScreenOnChange,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceRaised,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Audio source", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Device output mix", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    state: LumaBeatUiState,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Application", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SettingSwitchRow(
                title = "Automatic update checks",
                subtitle = "Check verified GitHub Releases at startup",
                checked = state.automaticUpdateChecks,
                onCheckedChange = onAutomaticUpdatesChange,
            )
            UpdatePanel(
                status = state.appUpdateStatus,
                onCheck = onCheckForUpdates,
                onDownload = onDownloadUpdate,
                onInstall = onInstallUpdate,
                onOpenInstallPermission = onOpenInstallPermission,
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .tvFocus(shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text(">", color = Violet, fontSize = 22.sp)
    }
}

@Composable
private fun UpdatePanel(
    status: AppUpdateStatus,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    val presentation = status.presentation()
    Surface(color = SurfaceRaised, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(presentation.title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Text(presentation.detail, color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.width(12.dp))
            when (presentation.action) {
                UpdateAction.Progress ->
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                UpdateAction.Check -> UpdateActionButton("Check", onCheck)
                UpdateAction.Download -> UpdateActionButton("Download", onDownload)
                UpdateAction.Install -> UpdateActionButton("Install", onInstall)
                UpdateAction.AllowInstall -> UpdateActionButton("Allow", onOpenInstallPermission)
                UpdateAction.OpenInstaller -> UpdateActionButton("Open again", onInstall)
                UpdateAction.RetryCheck -> UpdateActionButton("Retry", onCheck)
                UpdateAction.RetryDownload -> UpdateActionButton("Retry", onDownload)
            }
        }
    }
}

private data class UpdatePresentation(
    val title: String,
    val detail: String,
    val action: UpdateAction,
)

private enum class UpdateAction {
    Progress,
    Check,
    Download,
    Install,
    AllowInstall,
    OpenInstaller,
    RetryCheck,
    RetryDownload,
}

private fun AppUpdateStatus.presentation(): UpdatePresentation = when (this) {
    AppUpdateStatus.Idle -> UpdatePresentation(
        "Software updates",
        "Installed version ${BuildConfig.VERSION_NAME}",
        UpdateAction.Check,
    )
    AppUpdateStatus.Checking -> UpdatePresentation(
        "Checking for updates",
        "Reading official GitHub Releases…",
        UpdateAction.Progress,
    )
    is AppUpdateStatus.UpToDate -> UpdatePresentation(
        "LumaBeat is up to date",
        "Installed version $installedVersion",
        UpdateAction.Check,
    )
    is AppUpdateStatus.Available -> UpdatePresentation(
        "Update ${release.version} available",
        "Download, verify, then approve installation.",
        UpdateAction.Download,
    )
    is AppUpdateStatus.Downloading -> UpdatePresentation(
        "Downloading update",
        progress?.let { "${(it * 100).toInt()}% downloaded" } ?: "Downloading…",
        UpdateAction.Progress,
    )
    is AppUpdateStatus.Verifying -> UpdatePresentation(
        "Verifying APK",
        "Checking SHA-256, package name, version, and signature.",
        UpdateAction.Progress,
    )
    is AppUpdateStatus.ReadyToInstall -> UpdatePresentation(
        "Verified update ready",
        "Version ${release.version} passed every check.",
        UpdateAction.Install,
    )
    is AppUpdateStatus.InstallPermissionRequired -> UpdatePresentation(
        "Install permission required",
        "Allow LumaBeat to request package installation.",
        UpdateAction.AllowInstall,
    )
    is AppUpdateStatus.InstallerOpened -> UpdatePresentation(
        "Android installer opened",
        "Confirm the update in Android's installer.",
        UpdateAction.OpenInstaller,
    )
    is AppUpdateStatus.Failed -> failurePresentation()
}

private fun AppUpdateStatus.Failed.failurePresentation() = UpdatePresentation(
    if (failure == UpdateFailure.NO_RELEASE) "No published release yet" else "Update check failed",
    detail ?: "Try again when the network is available.",
    if (release == null) UpdateAction.RetryCheck else UpdateAction.RetryDownload,
)

@Composable
private fun UpdateActionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .tvFocus(RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(label)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceRaised)
            .tvFocus(shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LicensesScreen(compact: Boolean, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = if (compact) 28.dp else 20.dp, vertical = if (compact) 12.dp else 18.dp),
    ) {
        InternalPageHeader("Licenses", "LumaBeat ${BuildConfig.VERSION_NAME}", onBack)
        Spacer(Modifier.height(if (compact) 16.dp else 20.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text("LumaBeat", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Copyright © 2026 LumaBeat. All rights reserved.", color = TextSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = SurfaceRaised)
                Spacer(Modifier.height(20.dp))
                Text("Open-source software", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "AndroidX, Jetpack Compose, Kotlin, and kotlinx.coroutines · Apache License 2.0",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Text("apache.org/licenses/LICENSE-2.0", color = Cyan, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Modifier.tvFocus(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.025f else 1f, label = "tv-focus-scale")
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (focused) Modifier.border(2.dp, FocusBorder, shape) else Modifier)
}

private fun WizLight.stableKey(): String = macAddress.ifBlank { ipAddress }
