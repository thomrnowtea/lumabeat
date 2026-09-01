package com.lumabeat.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lumabeat.app.BuildConfig
import com.lumabeat.app.audio.AudioLevelAnalyzer
import com.lumabeat.app.audio.BeatPreset
import com.lumabeat.app.audio.PlaybackCaptureService
import com.lumabeat.app.media.ArtworkColorIntensity
import com.lumabeat.app.media.MediaColorGradient
import com.lumabeat.app.media.MediaColorRepository
import com.lumabeat.app.media.MediaNotificationListenerService
import com.lumabeat.app.update.AppUpdateInstaller
import com.lumabeat.app.update.AppUpdateStatus
import com.lumabeat.app.update.GitHubReleaseRepository
import com.lumabeat.app.update.UpdateFailure
import com.lumabeat.app.update.UpdateInstallException
import com.lumabeat.app.update.UpdateRepositoryException
import com.lumabeat.app.update.isNewerRelease
import com.lumabeat.app.wiz.LightColor
import com.lumabeat.app.wiz.WizLanController
import com.lumabeat.app.wiz.WizLight
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LumaBeatUiState(
    val lights: List<WizLight> = emptyList(),
    val includedLightKeys: Set<String> = emptySet(),
    val isDiscovering: Boolean = false,
    val beatPreset: BeatPreset = BeatPreset.MARCADO,
    val currentBrightness: Int = BeatPreset.MARCADO.baselineBrightness,
    val isAudioReactive: Boolean = false,
    val signalPresent: Boolean = false,
    val audioLevel: Float = 0f,
    val autoStartEnabled: Boolean = false,
    val keepScreenOnEnabled: Boolean = true,
    val mediaColorsEnabled: Boolean = false,
    val artworkColorIntensity: ArtworkColorIntensity = ArtworkColorIntensity.VIVID,
    val notificationAccessGranted: Boolean = false,
    val mediaPalette: List<LightColor> = emptyList(),
    val automaticUpdateChecks: Boolean = true,
    val appUpdateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val message: String? = null,
)

class LumaBeatViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = WizLanController(application)
    private val audioLevelAnalyzer = AudioLevelAnalyzer()
    private val releaseRepository = GitHubReleaseRepository("LumaBeat/${BuildConfig.VERSION_NAME}")
    private val updateInstaller = AppUpdateInstaller(application)
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, 0)
    private val initialPreset = loadPreset()
    private val initialArtworkColorIntensity = loadArtworkColorIntensity()
    private val excludedLightKeys = preferences
        .getStringSet(EXCLUDED_LIGHTS_KEY, emptySet())
        .orEmpty()
        .toMutableSet()
    private var audioJob: Job? = null
    private var colorGradientJob: Job? = null
    private var lightPowerMonitorJob: Job? = null
    private var updateJob: Job? = null
    // Lighting state is real-time: if the network stalls, keep the newest target
    // instead of replaying an obsolete queue of peaks and releases later.
    private val brightnessCommands = Channel<BrightnessCommand>(Channel.CONFLATED)
    private val mutableState = MutableStateFlow(
        LumaBeatUiState(
            beatPreset = initialPreset,
            currentBrightness = initialPreset.baselineBrightness,
            autoStartEnabled = preferences.getBoolean(AUTO_START_KEY, false),
            keepScreenOnEnabled = preferences.getBoolean(KEEP_SCREEN_ON_KEY, true),
            mediaColorsEnabled = preferences.getBoolean(MEDIA_COLORS_KEY, false),
            artworkColorIntensity = initialArtworkColorIntensity,
            notificationAccessGranted = hasNotificationAccess(),
            automaticUpdateChecks = preferences.getBoolean(AUTOMATIC_UPDATES_KEY, true),
        ),
    )
    val state: StateFlow<LumaBeatUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (command in brightnessCommands) {
                runCatching {
                    controller.setBrightness(command.lights, command.brightness)
                }.onSuccess {
                    logPulse(command.phase, command.detectedAtMillis, command.brightness)
                }.onFailure { error ->
                    Log.w(PULSE_LOG_TAG, "Could not update WiZ brightness.", error)
                }
            }
        }
        viewModelScope.launch {
            MediaColorRepository.colors.collect { colors ->
                mutableState.update { current ->
                    current.copy(mediaPalette = current.artworkColorIntensity.apply(colors))
                }
            }
        }
        if (state.value.automaticUpdateChecks) checkForUpdates(manual = false)
    }

    fun discoverLights() {
        if (state.value.isDiscovering) return
        lightPowerMonitorJob?.cancel()
        viewModelScope.launch {
            mutableState.update { it.copy(isDiscovering = true, message = null) }
            runCatching { controller.discover() }
                .onSuccess { lights -> showDiscoveredLights(lights) }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isDiscovering = false,
                            message = error.message ?: "The local network could not be scanned.",
                        )
                    }
                    if (state.value.lights.isNotEmpty()) startLightPowerMonitor()
                }
        }
    }

    fun setBeatPreset(preset: BeatPreset) {
        preferences.edit().putString(PRESET_KEY, preset.name).apply()
        mutableState.update {
            it.copy(
                beatPreset = preset,
                currentBrightness = if (it.isAudioReactive) {
                    it.currentBrightness
                } else {
                    preset.baselineBrightness
                },
            )
        }
    }

    fun setLightIncluded(light: WizLight, included: Boolean) {
        if (light.isOn != true) return
        val key = light.stableKey()
        if (included) excludedLightKeys.remove(key) else excludedLightKeys.add(key)
        preferences.edit().putStringSet(EXCLUDED_LIGHTS_KEY, excludedLightKeys.toSet()).apply()
        mutableState.update { current ->
            current.copy(
                includedLightKeys = if (included) {
                    current.includedLightKeys + key
                } else {
                    current.includedLightKeys - key
                },
                message = if (included) {
                    "${light.displayName} will follow the beat."
                } else {
                    "${light.displayName} was excluded from the effect."
                },
            )
        }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(AUTO_START_KEY, enabled).apply()
        mutableState.update { it.copy(autoStartEnabled = enabled) }
    }

    fun setKeepScreenOnEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEEP_SCREEN_ON_KEY, enabled).apply()
        mutableState.update { it.copy(keepScreenOnEnabled = enabled) }
    }

    fun setMediaColorsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(MEDIA_COLORS_KEY, enabled).apply()
        mutableState.update {
            it.copy(
                mediaColorsEnabled = enabled,
                message = when {
                    !enabled -> "Artwork colors disabled."
                    it.notificationAccessGranted -> "Artwork colors enabled."
                    else -> "Allow notification access to read media artwork."
                },
            )
        }
        if (enabled && hasNotificationAccess()) {
            NotificationListenerService.requestRebind(
                ComponentName(getApplication(), MediaNotificationListenerService::class.java),
            )
        }
    }

    fun setArtworkColorIntensity(intensity: ArtworkColorIntensity) {
        preferences.edit().putString(ARTWORK_COLOR_INTENSITY_KEY, intensity.name).apply()
        mutableState.update {
            it.copy(
                artworkColorIntensity = intensity,
                mediaPalette = intensity.apply(MediaColorRepository.colors.value),
                message = "Artwork colors set to ${intensity.label.lowercase()}.",
            )
        }
    }

    fun openNotificationAccessSettings() {
        val application = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(application.packageManager) != null) {
            application.startActivity(intent)
        } else {
            mutableState.update {
                it.copy(
                    message = "This TV firmware does not provide notification access settings. " +
                        "Enable LumaBeat's notification listener through ADB.",
                )
            }
        }
    }

    fun refreshNotificationAccess() {
        mutableState.update { it.copy(notificationAccessGranted = hasNotificationAccess()) }
    }

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        preferences.edit().putBoolean(AUTOMATIC_UPDATES_KEY, enabled).apply()
        mutableState.update { it.copy(automaticUpdateChecks = enabled) }
        if (enabled) checkForUpdates(manual = false)
    }

    fun startAudioReactiveBrightness() {
        if (participatingLights().isEmpty()) {
            mutableState.update { it.copy(message = "Select at least one light for the effect.") }
            return
        }
        if (audioJob?.isActive == true) return

        val application = getApplication<Application>()
        mutableState.update {
            it.copy(
                isAudioReactive = true,
                signalPresent = false,
                audioLevel = 0f,
                message = "Preparing the system audio mix…",
            )
        }
        PlaybackCaptureService.start(application)
        audioJob = viewModelScope.launch {
            try {
                PlaybackCaptureService.awaitForeground()
                mutableState.update {
                    it.copy(message = "Listening to the device audio output.")
                }
                startMediaColorGradient()
                collectAudioLevels()
            } finally {
                stopMediaColorGradient()
                PlaybackCaptureService.stop(application)
            }
        }
    }

    fun stopAudioReactiveBrightness() {
        audioJob?.cancel()
        audioJob = null
        stopMediaColorGradient()
        PlaybackCaptureService.stop(getApplication())
        mutableState.update {
            it.copy(
                isAudioReactive = false,
                signalPresent = false,
                audioLevel = 0f,
                message = "Beat tracking stopped.",
            )
        }
    }

    fun audioPermissionDenied() {
        mutableState.update {
            it.copy(message = "Audio permission is required to detect percussion.")
        }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (updateJob?.isActive == true) return
        mutableState.update { it.copy(appUpdateStatus = AppUpdateStatus.Checking) }
        updateJob = viewModelScope.launch {
            try {
                val release = releaseRepository.latest()
                val status = when {
                    release == null && manual -> AppUpdateStatus.Failed(UpdateFailure.NO_RELEASE)
                    release == null -> AppUpdateStatus.Idle
                    isNewerRelease(BuildConfig.VERSION_CODE.toLong(), release) ->
                        AppUpdateStatus.Available(release)
                    else -> AppUpdateStatus.UpToDate(BuildConfig.VERSION_NAME)
                }
                mutableState.update { it.copy(appUpdateStatus = status) }
            } catch (failure: UpdateRepositoryException) {
                mutableState.update {
                    it.copy(
                        appUpdateStatus = if (manual) {
                            AppUpdateStatus.Failed(failure.failure, failure.message)
                        } else {
                            AppUpdateStatus.Idle
                        },
                    )
                }
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        appUpdateStatus = if (manual) {
                            AppUpdateStatus.Failed(UpdateFailure.UNKNOWN, failure.message)
                        } else {
                            AppUpdateStatus.Idle
                        },
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        if (updateJob?.isActive == true) return
        val release = when (val status = state.value.appUpdateStatus) {
            is AppUpdateStatus.Available -> status.release
            is AppUpdateStatus.Failed -> status.release
            else -> null
        } ?: return
        updateJob = viewModelScope.launch {
            try {
                mutableState.update {
                    it.copy(appUpdateStatus = AppUpdateStatus.Downloading(release, null, 0, null))
                }
                val file = updateInstaller.download(release) { progress ->
                    mutableState.update {
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.Downloading(
                                release = release,
                                progress = progress.fraction,
                                downloadedBytes = progress.downloadedBytes,
                                totalBytes = progress.totalBytes,
                            ),
                        )
                    }
                }
                mutableState.update { it.copy(appUpdateStatus = AppUpdateStatus.Verifying(release)) }
                updateInstaller.validate(file, release)
                mutableState.update { it.copy(appUpdateStatus = AppUpdateStatus.ReadyToInstall(release)) }
            } catch (failure: UpdateInstallException) {
                mutableState.update {
                    it.copy(appUpdateStatus = AppUpdateStatus.Failed(failure.failure, failure.message, release))
                }
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(appUpdateStatus = AppUpdateStatus.Failed(UpdateFailure.UNKNOWN, failure.message, release))
                }
            }
        }
    }

    fun installUpdate() {
        val release = when (val status = state.value.appUpdateStatus) {
            is AppUpdateStatus.ReadyToInstall -> status.release
            is AppUpdateStatus.InstallPermissionRequired -> status.release
            is AppUpdateStatus.InstallerOpened -> status.release
            else -> null
        } ?: return
        if (!updateInstaller.canRequestInstalls()) {
            mutableState.update {
                it.copy(appUpdateStatus = AppUpdateStatus.InstallPermissionRequired(release))
            }
            return
        }
        runCatching { updateInstaller.openInstaller(release) }
            .onSuccess {
                mutableState.update { it.copy(appUpdateStatus = AppUpdateStatus.InstallerOpened(release)) }
            }
            .onFailure { failure ->
                val typed = failure as? UpdateInstallException
                mutableState.update {
                    it.copy(
                        appUpdateStatus = AppUpdateStatus.Failed(
                            typed?.failure ?: UpdateFailure.INSTALLER_UNAVAILABLE,
                            failure.message,
                            release,
                        ),
                    )
                }
            }
    }

    fun openInstallPermissionSettings() {
        val release = (state.value.appUpdateStatus as? AppUpdateStatus.InstallPermissionRequired)
            ?.release ?: return
        runCatching { updateInstaller.openInstallPermissionSettings() }.onFailure { failure ->
            val typed = failure as? UpdateInstallException
            mutableState.update {
                it.copy(
                    appUpdateStatus = AppUpdateStatus.Failed(
                        typed?.failure ?: UpdateFailure.INSTALLER_UNAVAILABLE,
                        failure.message,
                        release,
                    ),
                )
            }
        }
    }

    fun refreshInstallPermission() {
        val release = (state.value.appUpdateStatus as? AppUpdateStatus.InstallPermissionRequired)
            ?.release ?: return
        if (updateInstaller.canRequestInstalls()) {
            mutableState.update { it.copy(appUpdateStatus = AppUpdateStatus.ReadyToInstall(release)) }
        }
    }

    override fun onCleared() {
        brightnessCommands.close()
        lightPowerMonitorJob?.cancel()
        controller.close()
        PlaybackCaptureService.stop(getApplication())
        super.onCleared()
    }

    private suspend fun collectAudioLevels() {
        var lastBeatMillis = 0L
        var releaseSent = true
        audioLevelAnalyzer.levels { state.value.beatPreset }
            .catch { error -> showAudioError(error) }
            .collect { level ->
                mutableState.update {
                    it.copy(
                        signalPresent = level.signalPresent,
                        audioLevel = level.normalized,
                    )
                }
                logAudioLevel(level.isBeat, level.inputRms, level.normalized)
                val now = SystemClock.elapsedRealtime()
                if (level.isBeat) {
                    val preset = state.value.beatPreset
                    queueBrightness("peak", now, preset.peakBrightness)
                    lastBeatMillis = now
                    releaseSent = false
                    mutableState.update { it.copy(currentBrightness = preset.peakBrightness) }
                } else if (!releaseSent && shouldRelease(now, lastBeatMillis)) {
                    val preset = state.value.beatPreset
                    queueBrightness("release", now, preset.baselineBrightness)
                    releaseSent = true
                    mutableState.update { it.copy(currentBrightness = preset.baselineBrightness) }
                }
            }
    }

    private fun shouldRelease(now: Long, lastBeatMillis: Long): Boolean =
        now - lastBeatMillis >= state.value.beatPreset.pulseDurationMillis

    private fun queueBrightness(phase: String, detectedAtMillis: Long, brightness: Int) {
        brightnessCommands.trySend(
            BrightnessCommand(
                phase = phase,
                detectedAtMillis = detectedAtMillis,
                brightness = brightness,
                lights = participatingLights(),
            ),
        )
    }

    private fun showDiscoveredLights(lights: List<WizLight>) {
        mutableState.update {
            it.copy(
                lights = lights,
                includedLightKeys = lights
                    .map { light -> light.stableKey() }
                    .filterNot(excludedLightKeys::contains)
                    .toSet(),
                isDiscovering = false,
                message = if (lights.isEmpty()) {
                    "No WiZ lights were found on this network."
                } else {
                    "${lights.size} light${if (lights.size == 1) "" else "s"} ready."
                },
            )
        }
        startLightPowerMonitor()
    }

    private fun startLightPowerMonitor() {
        lightPowerMonitorJob?.cancel()
        lightPowerMonitorJob = viewModelScope.launch {
            while (isActive) {
                delay(LIGHT_POWER_POLL_MILLIS)
                val currentLights = state.value.lights
                if (currentLights.isEmpty()) continue
                runCatching { controller.refreshPowerStates(currentLights) }
                    .onSuccess { refreshed ->
                        mutableState.update { current -> current.copy(lights = refreshed) }
                    }
                    .onFailure { error ->
                        Log.w(LIGHT_LOG_TAG, "Could not refresh WiZ power states.", error)
                    }
            }
        }
    }

    private fun showAudioError(error: Throwable) {
        mutableState.update {
            it.copy(
                isAudioReactive = false,
                signalPresent = false,
                audioLevel = 0f,
                message = error.message ?: "The audio output could not be analyzed.",
            )
        }
    }

    private fun participatingLights(): List<WizLight> = state.value.lights.filter { light ->
        light.isOn == true && light.stableKey() in state.value.includedLightKeys
    }

    private fun startMediaColorGradient() {
        colorGradientJob?.cancel()
        colorGradientJob = viewModelScope.launch {
            var activePalette = emptyList<LightColor>()
            var transitionFrom: LightColor? = null
            var transitionStartedAtMillis = SystemClock.elapsedRealtime()
            var lastColor: LightColor? = null
            var lastLightKeys = emptySet<String>()
            while (true) {
                val current = state.value
                val now = SystemClock.elapsedRealtime()
                if (current.mediaPalette != activePalette) {
                    activePalette = current.mediaPalette
                    transitionFrom = lastColor?.takeUnless { color -> color == activePalette.firstOrNull() }
                    transitionStartedAtMillis = now
                }
                val lights = participatingLights()
                val lightKeys = lights.mapTo(mutableSetOf()) { light -> light.stableKey() }
                val color = if (current.mediaColorsEnabled && current.notificationAccessGranted) {
                    MediaColorGradient.colorAt(
                        palette = activePalette,
                        elapsedMillis = now - transitionStartedAtMillis,
                        segmentDurationMillis = COLOR_GRADIENT_SEGMENT_MILLIS,
                        transitionFrom = transitionFrom,
                    )
                } else {
                    null
                }
                if (color != null && (color != lastColor || lightKeys != lastLightKeys)) {
                    runCatching { controller.setColor(lights, color) }
                        .onFailure { error -> Log.w(COLOR_LOG_TAG, "Could not update WiZ color.", error) }
                }
                lastColor = color
                lastLightKeys = lightKeys
                delay(COLOR_GRADIENT_FRAME_MILLIS)
            }
        }
    }

    private fun stopMediaColorGradient() {
        colorGradientJob?.cancel()
        colorGradientJob = null
    }

    private fun hasNotificationAccess(): Boolean = NotificationManagerCompat
        .getEnabledListenerPackages(getApplication())
        .contains(getApplication<Application>().packageName)

    private fun WizLight.stableKey(): String = macAddress.ifBlank { ipAddress }

    private fun logAudioLevel(isBeat: Boolean, inputRms: Float, level: Float) {
        Log.d(
            AUDIO_LOG_TAG,
            "beat=$isBeat rms=$inputRms level=$level preset=${state.value.beatPreset.name}",
        )
    }

    private fun logPulse(phase: String, detectedAtMillis: Long, brightness: Int) {
        Log.d(
            PULSE_LOG_TAG,
            "phase=$phase brightness=$brightness " +
                "dispatchMs=${SystemClock.elapsedRealtime() - detectedAtMillis}",
        )
    }

    private fun loadPreset(): BeatPreset = runCatching {
        BeatPreset.valueOf(
            preferences.getString(PRESET_KEY, BeatPreset.MARCADO.name).orEmpty(),
        )
    }.getOrDefault(BeatPreset.MARCADO)

    private fun loadArtworkColorIntensity(): ArtworkColorIntensity = runCatching {
        ArtworkColorIntensity.valueOf(
            preferences.getString(
                ARTWORK_COLOR_INTENSITY_KEY,
                ArtworkColorIntensity.VIVID.name,
            ).orEmpty(),
        )
    }.getOrDefault(ArtworkColorIntensity.VIVID)

    private companion object {
        const val AUDIO_LOG_TAG = "LumaBeatAudio"
        const val PULSE_LOG_TAG = "LumaBeatPulse"
        const val COLOR_LOG_TAG = "LumaBeatColor"
        const val LIGHT_LOG_TAG = "LumaBeatLights"
        const val PREFERENCES_NAME = "lumabeat_preferences"
        const val PRESET_KEY = "beat_preset"
        const val EXCLUDED_LIGHTS_KEY = "excluded_light_keys"
        const val AUTO_START_KEY = "auto_start_enabled"
        const val KEEP_SCREEN_ON_KEY = "keep_screen_on_enabled"
        const val MEDIA_COLORS_KEY = "media_colors_enabled"
        const val ARTWORK_COLOR_INTENSITY_KEY = "artwork_color_intensity"
        const val AUTOMATIC_UPDATES_KEY = "automatic_update_checks"
        const val COLOR_GRADIENT_SEGMENT_MILLIS = 5_000L
        const val COLOR_GRADIENT_FRAME_MILLIS = 120L
        const val LIGHT_POWER_POLL_MILLIS = 2_000L
    }
}

private data class BrightnessCommand(
    val phase: String,
    val detectedAtMillis: Long,
    val brightness: Int,
    val lights: List<WizLight>,
)
