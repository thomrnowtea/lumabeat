package com.lumabeat.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lumabeat.app.BuildConfig
import com.lumabeat.app.R
import com.lumabeat.app.audio.BeatPreset
import com.lumabeat.app.media.ArtworkColorIntensity
import com.lumabeat.app.update.AppUpdateStatus
import com.lumabeat.app.update.UpdateFailure
import com.lumabeat.app.wiz.WizLight

private val BackgroundTop = LumaBeatColor.Canvas
private val BackgroundBottom = LumaBeatColor.CanvasRaised
private val Surface = LumaBeatColor.Surface
private val SurfaceRaised = LumaBeatColor.SurfaceRaised
private val Violet = LumaBeatColor.Accent
private val VioletSoft = LumaBeatColor.AccentContainer
private val Cyan = LumaBeatColor.Cyan
private val Green = LumaBeatColor.Success
private val TextPrimary = LumaBeatColor.TextPrimary
private val TextSecondary = LumaBeatColor.TextSecondary

private enum class AppPage {
    Dashboard,
    Settings,
    Licenses,
}

@Composable
fun LumaBeatApp(viewModel: LumaBeatViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    var autoStartHandled by rememberSaveable { mutableStateOf(false) }
    var blackoutEnabled by rememberSaveable { mutableStateOf(false) }
    val mediaProjectionManager = remember(context) {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    val playbackCapturePermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val projectionData = result.data
        if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
            viewModel.startAudioReactiveBrightness(result.resultCode, projectionData)
        } else {
            viewModel.audioCapturePermissionDenied()
        }
    }
    val requestPlaybackCapture = {
        playbackCapturePermission.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
    val audioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) requestPlaybackCapture()
        else viewModel.audioPermissionDenied()
    }
    val requestAudioCapture = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPlaybackCapture()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.state.value.lights.isEmpty()) viewModel.discoverLights()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationAccess()
        viewModel.refreshInstallPermission()
        if (state.keepScreenOnEnabled) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.autoStartEnabled, state.isDiscovering, state.lights.size) {
        if (!state.autoStartEnabled) {
            autoStartHandled = false
        } else if (!autoStartHandled && !state.isDiscovering && state.lights.isNotEmpty()) {
            autoStartHandled = true
            requestAudioCapture()
        }
    }

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
            else -> requestAudioCapture()
        }
    }
    LumaBeatTheme {
        if (blackoutEnabled) {
            BlackoutScreen(activity = activity, onExit = { blackoutEnabled = false })
        } else {
            LumaBeatScreen(
                state = state,
                onDiscover = viewModel::discoverLights,
                onPresetSelected = viewModel::setBeatPreset,
                onLightIncludedChange = viewModel::setLightIncluded,
                onListeningToggle = toggleListening,
                onEnterBlackout = { blackoutEnabled = true },
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
}

@Composable
private fun BlackoutScreen(activity: Activity?, onExit: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    BackHandler(onBack = onExit)
    DisposableEffect(activity) {
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness
        val wasKeepingScreenOn = window?.attributes?.flags
            ?.and(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        val systemBars = WindowInsetsCompat.Type.systemBars()
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window?.attributes = window?.attributes?.apply { screenBrightness = 0f }
        val insetsController = window?.let { currentWindow ->
            WindowCompat.getInsetsController(currentWindow, currentWindow.decorView)
        }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(systemBars)
        onDispose {
            if (originalBrightness != null) {
                window.attributes = window.attributes.apply { screenBrightness = originalBrightness }
            }
            insetsController?.show(systemBars)
            if (!wasKeepingScreenOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onExit()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onExit),
    )
}

@Composable
private fun LumaBeatScreen(
    state: LumaBeatUiState,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
    onEnterBlackout: () -> Unit,
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
            .background(
                Brush.linearGradient(
                    listOf(BackgroundTop, LumaBeatColor.CanvasGlow, BackgroundBottom),
                ),
            ),
    ) {
        val fontScale = LocalDensity.current.fontScale
        val isTvLayout = shouldUseCompactDashboard(
            widthDp = maxWidth.value,
            heightDp = maxHeight.value,
            fontScale = fontScale,
        )
        when (currentPage) {
            AppPage.Dashboard -> if (isTvLayout) {
                TvDashboard(
                    state = state,
                    onOpenSettings = { currentPage = AppPage.Settings },
                    onDiscover = onDiscover,
                    onPresetSelected = onPresetSelected,
                    onLightIncludedChange = onLightIncludedChange,
                    onListeningToggle = onListeningToggle,
                    onEnterBlackout = onEnterBlackout,
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
                    onEnterBlackout = onEnterBlackout,
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
    onEnterBlackout: () -> Unit,
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
                    onEnterBlackout = onEnterBlackout,
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
                if (BuildConfig.ARTWORK_COLORS_AVAILABLE) {
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
}

@Composable
private fun PhoneDashboard(
    state: LumaBeatUiState,
    onOpenSettings: () -> Unit,
    onDiscover: () -> Unit,
    onPresetSelected: (BeatPreset) -> Unit,
    onLightIncludedChange: (WizLight, Boolean) -> Unit,
    onListeningToggle: () -> Unit,
    onEnterBlackout: () -> Unit,
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
        ListeningCard(
            state = state,
            compact = false,
            onToggle = onListeningToggle,
            onEnterBlackout = onEnterBlackout,
        )
        PresetPanel(state.beatPreset, compact = false, onPresetSelected)
        if (BuildConfig.ARTWORK_COLORS_AVAILABLE) {
            ArtworkColorsPanel(
                state = state,
                compact = false,
                onEnabledChange = onMediaColorsChange,
                onIntensityChange = onArtworkColorIntensityChange,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }
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
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandLogo(compact = true)
            Text(
                "LumaBeat",
                modifier = Modifier
                    .padding(start = 11.dp)
                    .weight(1f),
                color = TextPrimary,
                fontSize = 24.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            StatusPill(state)
            Spacer(Modifier.width(8.dp))
            SettingsButton(onOpenSettings)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandLogo(compact = false)
                Column(
                    modifier = Modifier
                        .padding(start = 11.dp)
                        .weight(1f),
                ) {
                    Text(
                        "LumaBeat",
                        color = TextPrimary,
                        fontSize = 28.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        "Percussion becomes light",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 2,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SettingsButton(onOpenSettings)
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                StatusPill(state)
            }
        }
    }
}

@Composable
private fun BrandLogo(compact: Boolean) {
    Image(
        painter = painterResource(R.drawable.lumabeat_logo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(if (compact) 40.dp else 48.dp)
            .clip(RoundedCornerShape(LumaBeatRadius.Medium))
            .border(1.dp, LumaBeatColor.Border, RoundedCornerShape(LumaBeatRadius.Medium)),
    )
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(SurfaceRaised)
            .border(1.dp, LumaBeatColor.Border, CircleShape)
            .tvFocus(CircleShape)
            .semantics { contentDescription = "Open settings" },
    ) {
        Text("...", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusPill(state: LumaBeatUiState) {
    val active = state.isAudioReactive && state.signalPresent
    val activeLights = state.activeLightCount()
    val label = when {
        active -> "Audio active"
        state.isAudioReactive -> "Waiting for audio"
        activeLights > 0 -> "$activeLights ready"
        state.lights.any { it.isOn == false } -> "Lights off"
        state.lights.isNotEmpty() -> "None selected"
        else -> "No lights"
    }
    val statusColor = when {
        active -> Green
        state.lights.any { it.isOn == false } && activeLights == 0 -> LumaBeatColor.Warning
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (active) LumaBeatColor.SuccessContainer else SurfaceRaised)
            .border(1.dp, LumaBeatColor.Border.copy(alpha = 0.8f), RoundedCornerShape(100.dp))
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        Text(label, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ListeningCard(
    state: LumaBeatUiState,
    compact: Boolean,
    onToggle: () -> Unit,
    onEnterBlackout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeLightCount = state.activeLightCount()
    val activeLightLabel = "$activeLightCount ${if (activeLightCount == 1) "light" else "lights"}"
    val title = when {
        state.isAudioReactive && state.signalPresent -> "Following the beat"
        state.isAudioReactive -> "Listening for audio"
        state.lights.isNotEmpty() && !state.hasActiveLights() -> "Turn on a light"
        else -> "Ready to listen"
    }
    val subtitle = when {
        state.isAudioReactive -> "${state.beatPreset.label} response · $activeLightLabel"
        !state.hasActiveLights() -> "Available lights are required to start"
        else -> "${state.beatPreset.label} percussion profile"
    }
    val trackingEnabled = state.isAudioReactive || state.hasActiveLights()
    val primaryButtonBrush = when {
        !trackingEnabled -> Brush.horizontalGradient(listOf(SurfaceRaised, SurfaceRaised))
        state.isAudioReactive -> Brush.horizontalGradient(
            listOf(LumaBeatColor.SurfaceInteractive, SurfaceRaised),
        )
        else -> Brush.horizontalGradient(
            listOf(LumaBeatColor.AccentStrong, Violet, Cyan),
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(if (compact) 20.dp else 26.dp),
        border = LumaBeatPanelBorder,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .then(if (compact) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(if (compact) 15.dp else 24.dp),
            verticalArrangement = if (compact) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(18.dp)
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = if (compact) 18.sp else 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(subtitle, color = TextSecondary, fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(if (compact) 48.dp else 68.dp)
                        .clip(CircleShape)
                        .background(Violet.copy(alpha = 0.16f + state.audioLevel * 0.34f))
                        .border(
                            2.dp,
                            Brush.sweepGradient(listOf(Violet, Cyan, Green, Violet)),
                            CircleShape,
                        ),
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
            ListeningActions(
                state = state,
                compact = compact,
                trackingEnabled = trackingEnabled,
                primaryButtonBrush = primaryButtonBrush,
                onToggle = onToggle,
                onEnterBlackout = onEnterBlackout,
            )
            if (!compact && state.isAudioReactive) {
                Text(
                    "Beat tracking stays active. Press any key or tap to return.",
                    color = LumaBeatColor.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ListeningActions(
    state: LumaBeatUiState,
    compact: Boolean,
    trackingEnabled: Boolean,
    primaryButtonBrush: Brush,
    onToggle: () -> Unit,
    onEnterBlackout: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val stackActions = !compact && maxWidth < 340.dp
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackingButton(
                    state = state,
                    compact = false,
                    enabled = trackingEnabled,
                    background = primaryButtonBrush,
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(),
                )
                BlackoutButton(
                    compact = false,
                    enabled = state.isAudioReactive,
                    onClick = onEnterBlackout,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackingButton(
                    state = state,
                    compact = compact,
                    enabled = trackingEnabled,
                    background = primaryButtonBrush,
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                )
                BlackoutButton(
                    compact = compact,
                    enabled = state.isAudioReactive,
                    onClick = onEnterBlackout,
                    modifier = Modifier.width(blackoutButtonWidthDp(compact, fontScale).dp),
                )
            }
        }
    }
}

internal fun shouldUseCompactDashboard(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
    widthDp >= 700f && heightDp >= 360f && widthDp > heightDp && fontScale <= 1.15f

internal fun blackoutButtonWidthDp(compact: Boolean, fontScale: Float): Int = when {
    compact -> 136
    fontScale > 1.15f -> 184
    else -> 148
}

@Composable
private fun TrackingButton(
    state: LumaBeatUiState,
    compact: Boolean,
    enabled: Boolean,
    background: Brush,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(if (compact) 44.dp else 58.dp)
            .clip(shape)
            .background(background)
            .tvFocus(shape),
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContentColor = LumaBeatColor.TextMuted,
        ),
    ) {
        Text(
            text = if (state.isAudioReactive) "Stop tracking" else "Start beat tracking",
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun BlackoutButton(
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(if (compact) 44.dp else 58.dp)
            .tvFocus(shape),
        shape = shape,
        border = BorderStroke(1.dp, LumaBeatColor.Border),
    ) {
        Text("Black screen", fontSize = if (compact) 12.sp else 14.sp, maxLines = 1)
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
        border = BorderStroke(1.dp, if (selected) Violet else LumaBeatColor.Border),
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
        border = LumaBeatPanelBorder,
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
                    Text("${state.activeLightCount()} of ${state.lights.size} follow the beat", color = TextSecondary, fontSize = 11.sp)
                }
                if (state.isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(19.dp), color = Violet, strokeWidth = 2.dp)
                } else if (compact) {
                    OutlinedButton(
                        onClick = onDiscover,
                        modifier = Modifier
                            .height(38.dp)
                            .tvFocus(RoundedCornerShape(LumaBeatRadius.Medium)),
                        shape = RoundedCornerShape(LumaBeatRadius.Medium),
                        contentPadding = PaddingValues(horizontal = LumaBeatSpace.Md),
                    ) {
                        Text(if (state.lights.isEmpty()) "Find" else "Refresh", fontSize = 11.sp)
                    }
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
            if (!compact) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onDiscover,
                    enabled = !state.isDiscovering,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .tvFocus(RoundedCornerShape(13.dp)),
                    shape = RoundedCornerShape(13.dp),
                ) {
                    Text(if (state.lights.isEmpty()) "Find lights" else "Refresh lights")
                }
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
    val available = light.isOn == true
    val shape = RoundedCornerShape(14.dp)
    val selectedColor = if (available && included) LumaBeatColor.SuccessContainer else SurfaceRaised
    val rowColor by animateColorAsState(
        targetValue = selectedColor,
        animationSpec = tween(durationMillis = 180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "light-availability-color",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(rowColor)
            .border(1.dp, LumaBeatColor.Border.copy(alpha = 0.6f), shape)
            .tvFocus(shape)
            .clickable(enabled = available) { onIncludedChange(!included) }
            .padding(horizontal = 11.dp, vertical = if (compact) 5.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 38.dp)
                .clip(CircleShape)
                .background(if (available && included) Color(0xFF1B4A3A) else Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("●", color = if (available) Green else TextSecondary, fontSize = 14.sp)
        }
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        ) {
            Text(light.displayName, color = TextPrimary, fontSize = if (compact) 13.sp else 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                text = when {
                    light.isOn == false -> "Off · resumes when powered"
                    light.isOn == null -> "Unavailable · waiting for device"
                    included -> "Included · ${light.ipAddress}"
                    else -> "Excluded · ${light.ipAddress}"
                },
                color = TextSecondary,
                fontSize = if (compact) 10.sp else 12.sp,
                maxLines = 1,
            )
        }
        Switch(
            checked = included,
            enabled = available,
            onCheckedChange = null,
            modifier = Modifier
                .semantics {
                    contentDescription = if (available) {
                        "Include ${light.displayName} in dynamic changes"
                    } else {
                        "${light.displayName} is off and unavailable"
                    }
                }
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
        border = LumaBeatPanelBorder,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumaBeatSpace.Sm),
            ) {
                state.mediaPalette.take(3).forEach { color ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(LumaBeatRadius.Small))
                                .background(Color(color.red, color.green, color.blue))
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.28f),
                                    RoundedCornerShape(LumaBeatRadius.Small),
                                ),
                        )
                        Spacer(Modifier.height(3.dp))
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
        InternalPageHeader("Settings", "Playback, updates, and application preferences", onBack)
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
                    compact = true,
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
                        compact = true,
                    )
                    SettingsNavigationRow(
                        "Licenses",
                        "Open-source notices and application version",
                        onOpenLicenses,
                        compact = true,
                    )
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
    compact: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = LumaBeatPanelBorder,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Playback", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SettingSwitchRow(
                title = "Auto start",
                subtitle = "Start tracking after lights are found",
                checked = state.autoStartEnabled,
                onCheckedChange = onAutoStartChange,
                compact = compact,
            )
            SettingSwitchRow(
                title = "Keep screen awake",
                subtitle = "Prevent the device from suspending LumaBeat",
                checked = state.keepScreenOnEnabled,
                onCheckedChange = onKeepScreenOnChange,
                compact = compact,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceRaised,
                shape = RoundedCornerShape(16.dp),
            ) {
                if (compact) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Audio source", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("Shared playback audio", color = TextSecondary, fontSize = 11.sp)
                    }
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("Audio source", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text("Shared playback audio", color = TextSecondary, fontSize = 12.sp)
                    }
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
    compact: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = LumaBeatPanelBorder,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Application", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            SettingSwitchRow(
                title = "Automatic update checks",
                subtitle = "Check verified GitHub Releases at startup",
                checked = state.automaticUpdateChecks,
                onCheckedChange = onAutomaticUpdatesChange,
                compact = compact,
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
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .tvFocus(shape)
            .clickable(onClick = onClick)
            .padding(if (compact) 12.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
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
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SurfaceRaised)
            .tvFocus(shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = if (compact) 11.sp else 12.sp, maxLines = 1)
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
            border = LumaBeatPanelBorder,
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

private fun WizLight.stableKey(): String = macAddress.ifBlank { ipAddress }

internal fun LumaBeatUiState.activeLightCount(): Int = lights.count { light ->
    light.isOn == true && light.stableKey() in includedLightKeys
}

private fun LumaBeatUiState.hasActiveLights(): Boolean = activeLightCount() > 0
