package com.lumabeat.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumabeat.app.BuildConfig
import com.lumabeat.app.audio.BeatPreset
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

private enum class OptionsPage {
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
    onOpenNotificationAccess: () -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
) {
    var optionsExpanded by remember { mutableStateOf(false) }
    var optionsPage by remember { mutableStateOf<OptionsPage?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(BackgroundTop, BackgroundBottom))),
    ) {
        val isTvLayout = maxWidth >= 700.dp && maxWidth > maxHeight
        if (isTvLayout) {
            TvDashboard(
                state = state,
                optionsExpanded = optionsExpanded,
                onOptionsExpandedChange = { optionsExpanded = it },
                onOptionsPageSelected = { optionsPage = it },
                onDiscover = onDiscover,
                onPresetSelected = onPresetSelected,
                onLightIncludedChange = onLightIncludedChange,
                onListeningToggle = onListeningToggle,
            )
        } else {
            PhoneDashboard(
                state = state,
                optionsExpanded = optionsExpanded,
                onOptionsExpandedChange = { optionsExpanded = it },
                onOptionsPageSelected = { optionsPage = it },
                onDiscover = onDiscover,
                onPresetSelected = onPresetSelected,
                onLightIncludedChange = onLightIncludedChange,
                onListeningToggle = onListeningToggle,
            )
        }
    }

    when (optionsPage) {
        OptionsPage.Settings -> SettingsDialog(
            state = state,
            onAutoStartChange = onAutoStartChange,
            onKeepScreenOnChange = onKeepScreenOnChange,
            onMediaColorsChange = onMediaColorsChange,
            onOpenNotificationAccess = onOpenNotificationAccess,
            onAutomaticUpdatesChange = onAutomaticUpdatesChange,
            onCheckForUpdates = onCheckForUpdates,
            onDownloadUpdate = onDownloadUpdate,
            onInstallUpdate = onInstallUpdate,
            onOpenInstallPermission = onOpenInstallPermission,
            onDismiss = { optionsPage = null },
        )
        OptionsPage.Licenses -> LicensesDialog(onDismiss = { optionsPage = null })
        null -> Unit
    }
}

@Composable
private fun TvDashboard(
    state: LumaBeatUiState,
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onOptionsPageSelected: (OptionsPage) -> Unit,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
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
            optionsExpanded = optionsExpanded,
            onOptionsExpandedChange = onOptionsExpandedChange,
            onOptionsPageSelected = onOptionsPageSelected,
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
            LightsPanel(
                state = state,
                compact = true,
                onDiscover = onDiscover,
                onLightIncludedChange = onLightIncludedChange,
                modifier = Modifier
                    .weight(0.88f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PhoneDashboard(
    state: LumaBeatUiState,
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onOptionsPageSelected: (OptionsPage) -> Unit,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
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
            optionsExpanded = optionsExpanded,
            onOptionsExpandedChange = onOptionsExpandedChange,
            onOptionsPageSelected = onOptionsPageSelected,
        )
        ListeningCard(state, compact = false, onToggle = onListeningToggle)
        PresetPanel(state.beatPreset, compact = false, onPresetSelected)
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
    optionsExpanded: Boolean,
    onOptionsExpandedChange: (Boolean) -> Unit,
    onOptionsPageSelected: (OptionsPage) -> Unit,
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
                onClick = { onOptionsExpandedChange(true) },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SurfaceRaised)
                    .tvFocus(CircleShape)
                    .semantics { contentDescription = "More options" },
            ) {
                Text("⋮", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(
                expanded = optionsExpanded,
                onDismissRequest = { onOptionsExpandedChange(false) },
                containerColor = SurfaceRaised,
            ) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    onClick = {
                        onOptionsExpandedChange(false)
                        onOptionsPageSelected(OptionsPage.Settings)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Licenses") },
                    onClick = {
                        onOptionsExpandedChange(false)
                        onOptionsPageSelected(OptionsPage.Licenses)
                    },
                )
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
private fun SettingsDialog(
    state: LumaBeatUiState,
    onAutoStartChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onMediaColorsChange: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth > maxHeight
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (compact) 0.76f else 0.90f)
                    .heightIn(max = maxHeight * 0.92f)
                    .widthIn(max = 760.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 20.dp else 24.dp),
                ) {
                    Text("Settings", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Preferences for a permanent Android TV setup.", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                    if (compact) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                SettingSwitchRow(
                                    title = "Auto start",
                                    subtitle = "Starts after finding lights",
                                    checked = state.autoStartEnabled,
                                    onCheckedChange = onAutoStartChange,
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SettingSwitchRow(
                                    title = "Keep awake",
                                    subtitle = "Prevents TV sleep",
                                    checked = state.keepScreenOnEnabled,
                                    onCheckedChange = onKeepScreenOnChange,
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                SettingSwitchRow(
                                    title = "Artwork colors",
                                    subtitle = "Cycles through the top 3",
                                    checked = state.mediaColorsEnabled,
                                    onCheckedChange = onMediaColorsChange,
                                )
                            }
                        }
                    } else {
                        SettingSwitchRow(
                            title = "Auto start",
                            subtitle = "Starts beat tracking after finding lights",
                            checked = state.autoStartEnabled,
                            onCheckedChange = onAutoStartChange,
                        )
                        Spacer(Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "Keep screen awake",
                            subtitle = "Prevents Android TV from suspending this screen",
                            checked = state.keepScreenOnEnabled,
                            onCheckedChange = onKeepScreenOnChange,
                        )
                        Spacer(Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "Artwork colors",
                            subtitle = "Cycles through the three dominant album-art colors",
                            checked = state.mediaColorsEnabled,
                            onCheckedChange = onMediaColorsChange,
                        )
                    }
                    if (state.mediaColorsEnabled) {
                        Spacer(Modifier.height(10.dp))
                        ArtworkAccessRow(state, onOpenNotificationAccess)
                    }
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                    SettingSwitchRow(
                        title = "Automatic update checks",
                        subtitle = "Checks verified GitHub Releases at startup",
                        checked = state.automaticUpdateChecks,
                        onCheckedChange = onAutomaticUpdatesChange,
                    )
                    Spacer(Modifier.height(10.dp))
                    UpdatePanel(
                        status = state.appUpdateStatus,
                        onCheck = onCheckForUpdates,
                        onDownload = onDownloadUpdate,
                        onInstall = onInstallUpdate,
                        onOpenInstallPermission = onOpenInstallPermission,
                    )
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = SurfaceRaised,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text("Audio source", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text("Device output mix", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(48.dp)
                                .tvFocus(RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkAccessRow(
    state: LumaBeatUiState,
    onOpenNotificationAccess: () -> Unit,
) {
    Surface(color = SurfaceRaised, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (state.notificationAccessGranted) "Media access granted" else "Media access required",
                    color = if (state.notificationAccessGranted) Green else TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (state.notificationAccessGranted) {
                        "Album artwork is read locally; audio and DRM content are untouched."
                    } else {
                        "Allow LumaBeat to read media notifications and album artwork."
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.mediaPalette.take(3).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(color.red, color.green, color.blue)),
                    )
                }
            }
            if (!state.notificationAccessGranted) {
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onOpenNotificationAccess,
                    modifier = Modifier
                        .height(44.dp)
                        .tvFocus(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Grant access")
                }
            }
        }
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
private fun LicensesDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxWidth > maxHeight
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (compact) 0.72f else 0.90f)
                    .heightIn(max = maxHeight * 0.92f)
                    .widthIn(max = 720.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(if (compact) 20.dp else 24.dp),
                ) {
                    Text("Licenses", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("LumaBeat ${BuildConfig.VERSION_NAME}", color = Violet, fontSize = 14.sp)
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    Text("Copyright © 2026 LumaBeat. All rights reserved.", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    HorizontalDivider(color = SurfaceRaised)
                    Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Open-source software", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "AndroidX, Jetpack Compose, Kotlin, and kotlinx.coroutines · Apache License 2.0",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            )
                            Text("apache.org/licenses/LICENSE-2.0", color = Cyan, fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(48.dp)
                                .tvFocus(RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text("Close")
                        }
                    }
                }
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
