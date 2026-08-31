package com.lumabeat.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class UpdateDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long?,
) {
    val fraction: Float? = totalBytes?.takeIf { it > 0 }?.let {
        (downloadedBytes.toDouble() / it).toFloat().coerceIn(0f, 1f)
    }
}

class UpdateInstallException(
    val failure: UpdateFailure,
    message: String,
) : Exception(message)

internal fun signerLineageAllowsUpgrade(
    installedCurrent: List<ByteArray>,
    installedHasMultipleSigners: Boolean,
    archiveCurrent: List<ByteArray>,
    archiveHistory: List<ByteArray>,
    archiveHasMultipleSigners: Boolean,
): Boolean {
    if (installedCurrent.isEmpty() || archiveCurrent.isEmpty()) return false
    if (installedHasMultipleSigners || archiveHasMultipleSigners) {
        return installedHasMultipleSigners && archiveHasMultipleSigners &&
            installedCurrent.sameCertificateSet(archiveCurrent)
    }
    if (installedCurrent.size != 1 || archiveCurrent.size != 1) return false
    return archiveHistory.any { MessageDigest.isEqual(installedCurrent.single(), it) }
}

private fun List<ByteArray>.sameCertificateSet(other: List<ByteArray>): Boolean {
    if (size != other.size) return false
    val matched = BooleanArray(other.size)
    for (certificate in this) {
        val index = other.indices.firstOrNull {
            !matched[it] && MessageDigest.isEqual(certificate, other[it])
        } ?: return false
        matched[index] = true
    }
    return true
}

class AppUpdateInstaller(private val context: Context) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val downloadState = context.getSharedPreferences(DOWNLOAD_STATE, Context.MODE_PRIVATE)
    private var verifiedApk: File? = null
    private var verifiedRelease: AppReleaseMetadata? = null

    suspend fun download(
        release: AppReleaseMetadata,
        progress: (UpdateDownloadProgress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        verifiedApk = null
        verifiedRelease = null
        requireInstallableRelease(release)
        val externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw UpdateInstallException(UpdateFailure.DOWNLOAD, "Download directory is unavailable")
        val updatesDirectory = File(externalDownloads, UPDATE_DIRECTORY).apply { mkdirs() }
        val destination = File(updatesDirectory, "LumaBeat-${release.versionCode}.apk")
        val resumedDownloadId = resumableDownloadId(release)
        if (resumedDownloadId == null && destination.isFile && destination.length() in 1..MAX_APK_BYTES) {
            return@withContext destination
        }
        val downloadId = resumedDownloadId ?: startDownload(release, destination, updatesDirectory)
        try {
            while (true) {
                val snapshot = query(downloadId)
                if (snapshot.totalBytes != null && snapshot.totalBytes > MAX_APK_BYTES) {
                    downloadManager.remove(downloadId)
                    throw UpdateInstallException(UpdateFailure.DOWNLOAD, "APK exceeds the safe size limit")
                }
                progress(UpdateDownloadProgress(snapshot.downloadedBytes, snapshot.totalBytes))
                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        clearDownloadState()
                        break
                    }
                    DownloadManager.STATUS_FAILED -> throw UpdateInstallException(
                        UpdateFailure.DOWNLOAD,
                        "DownloadManager failure ${snapshot.reason}",
                    )
                }
                delay(POLL_INTERVAL_MS)
            }
        } catch (cancelled: CancellationException) {
            downloadManager.remove(downloadId)
            clearDownloadState()
            throw cancelled
        } catch (failure: UpdateInstallException) {
            downloadManager.remove(downloadId)
            clearDownloadState()
            destination.delete()
            throw failure
        }
        if (!destination.isFile || destination.length() <= 0 || destination.length() > MAX_APK_BYTES) {
            destination.delete()
            throw UpdateInstallException(UpdateFailure.DOWNLOAD, "Downloaded APK is missing or invalid")
        }
        destination
    }

    suspend fun validate(file: File, release: AppReleaseMetadata) = withContext(Dispatchers.IO) {
        try {
            requireInstallableRelease(release)
            requireValidFile(file)
            requireMatchingChecksum(file, release)
            val archive = archivePackageInfo(file)
                ?: throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "Downloaded file is not an APK")
            val installed = installedPackageInfo()
            requireMatchingPackageIdentity(archive, installed, release)
            requireMatchingSigner(archive, installed)
            verifiedApk = file
            verifiedRelease = release
        } catch (cancelled: CancellationException) {
            verifiedApk = null
            verifiedRelease = null
            throw cancelled
        } catch (failure: UpdateInstallException) {
            file.delete()
            verifiedApk = null
            verifiedRelease = null
            throw failure
        } catch (failure: Exception) {
            file.delete()
            verifiedApk = null
            verifiedRelease = null
            throw UpdateInstallException(
                UpdateFailure.INVALID_PACKAGE,
                failure.message ?: "APK validation failed",
            )
        }
    }

    private fun requireValidFile(file: File) {
        if (!file.isFile || file.length() <= 0 || file.length() > MAX_APK_BYTES) {
            throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "Downloaded APK is missing or invalid")
        }
    }

    private fun requireMatchingChecksum(file: File, release: AppReleaseMetadata) {
        val actual = sha256(file).hexToBytes()
        if (!MessageDigest.isEqual(actual, release.sha256.hexToBytes())) {
            throw UpdateInstallException(UpdateFailure.CHECKSUM, "SHA-256 does not match release metadata")
        }
    }

    private fun requireMatchingPackageIdentity(
        archive: PackageInfo,
        installed: PackageInfo,
        release: AppReleaseMetadata,
    ) {
        if (archive.packageName != context.packageName || archive.packageName != release.packageName) {
            throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "APK package name does not match LumaBeat")
        }
        if (archive.longVersionCodeCompat() != release.versionCode) {
            throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "APK version code does not match metadata")
        }
        if (!isNewerRelease(installed.longVersionCodeCompat(), release)) {
            throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "APK is not newer than the installed app")
        }
        if (archive.versionName != release.version) {
            throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "APK version name does not match metadata")
        }
    }

    private fun requireMatchingSigner(archive: PackageInfo, installed: PackageInfo) {
        if (!signerMatchesInstalledApp(archive, installed)) {
            throw UpdateInstallException(UpdateFailure.SIGNATURE, "APK signature does not match the installed app")
        }
    }

    fun canRequestInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.getOrElse {
            throw UpdateInstallException(
                UpdateFailure.INSTALLER_UNAVAILABLE,
                it.message ?: "Install settings are unavailable",
            )
        }
    }

    @Suppress("DEPRECATION")
    fun openInstaller(release: AppReleaseMetadata) {
        requireInstallableRelease(release)
        val file = verifiedApk?.takeIf { verifiedRelease == release && it.isFile }
            ?: throw UpdateInstallException(UpdateFailure.INVALID_PACKAGE, "No verified APK is ready")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
        }
        runCatching { context.startActivity(intent) }.getOrElse {
            throw UpdateInstallException(
                UpdateFailure.INSTALLER_UNAVAILABLE,
                it.message ?: "Package installer is unavailable",
            )
        }
    }

    private fun resumableDownloadId(release: AppReleaseMetadata): Long? {
        val id = downloadState.getLong(KEY_DOWNLOAD_ID, NO_DOWNLOAD)
        if (id == NO_DOWNLOAD) return null
        val matches = downloadState.getLong(KEY_RELEASE_CODE, -1) == release.versionCode &&
            downloadState.getString(KEY_RELEASE_TAG, null) == release.tag
        if (matches) {
            val status = runCatching { query(id).status }.getOrNull()
            if (status in setOf(
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED,
                    DownloadManager.STATUS_SUCCESSFUL,
                )
            ) return id
        }
        downloadManager.remove(id)
        clearDownloadState()
        return null
    }

    private fun startDownload(
        release: AppReleaseMetadata,
        destination: File,
        updatesDirectory: File,
    ): Long {
        if (destination.exists() && !destination.delete()) {
            throw UpdateInstallException(UpdateFailure.DOWNLOAD, "Previous update could not be replaced")
        }
        updatesDirectory.listFiles()?.filter { it != destination }?.forEach { old ->
            if (old.isFile && old.name.startsWith("LumaBeat-") && old.name.endsWith(".apk")) old.delete()
        }
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("LumaBeat ${release.version}")
            .setDescription("Downloading verified application update")
            .setMimeType(APK_MIME_TYPE)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "$UPDATE_DIRECTORY/${destination.name}",
            )
        val id = runCatching { downloadManager.enqueue(request) }.getOrElse {
            throw UpdateInstallException(UpdateFailure.DOWNLOAD, it.message ?: "Download could not start")
        }
        downloadState.edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putLong(KEY_RELEASE_CODE, release.versionCode)
            .putString(KEY_RELEASE_TAG, release.tag)
            .apply()
        return id
    }

    private fun clearDownloadState() {
        downloadState.edit().clear().apply()
    }

    private fun query(downloadId: Long): DownloadSnapshot {
        val query = DownloadManager.Query().setFilterById(downloadId)
        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) throw UpdateInstallException(UpdateFailure.DOWNLOAD, "Download disappeared")
            DownloadSnapshot(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                downloadedBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                ).coerceAtLeast(0),
                totalBytes = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                ).takeIf { it > 0 },
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        } ?: throw UpdateInstallException(UpdateFailure.DOWNLOAD, "DownloadManager query failed")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageInfo(context.packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerMatchesInstalledApp(archive: PackageInfo, installed: PackageInfo): Boolean =
        signerLineageAllowsUpgrade(
            installedCurrent = installed.currentCertificateBytes(),
            installedHasMultipleSigners = installed.hasMultipleSignersCompat(),
            archiveCurrent = archive.currentCertificateBytes(),
            archiveHistory = archive.certificateHistoryBytes(),
            archiveHasMultipleSigners = archive.hasMultipleSignersCompat(),
        )

    @Suppress("DEPRECATION")
    private fun PackageInfo.currentCertificateBytes(): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
        } else {
            signatures?.map { it.toByteArray() }.orEmpty()
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.certificateHistoryBytes(): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.signingCertificateHistory?.map { it.toByteArray() }.orEmpty()
        } else {
            signatures?.map { it.toByteArray() }.orEmpty()
        }

    private fun PackageInfo.hasMultipleSignersCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && signingInfo?.hasMultipleSigners() == true

    private fun requireInstallableRelease(release: AppReleaseMetadata) {
        try {
            requireTrustedReleaseMetadata(release)
        } catch (failure: UpdateRepositoryException) {
            throw UpdateInstallException(
                UpdateFailure.INVALID_METADATA,
                failure.message ?: "Release metadata is invalid",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun String.hexToBytes(): ByteArray {
        if (length % 2 != 0) return byteArrayOf()
        return runCatching {
            ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrDefault(byteArrayOf())
    }

    private data class DownloadSnapshot(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val reason: Int,
    )

    private companion object {
        const val DOWNLOAD_STATE = "app_update_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_RELEASE_CODE = "release_code"
        const val KEY_RELEASE_TAG = "release_tag"
        const val NO_DOWNLOAD = -1L
        const val UPDATE_DIRECTORY = "updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val POLL_INTERVAL_MS = 300L
        const val MAX_APK_BYTES = 128L * 1024L * 1024L
    }
}
