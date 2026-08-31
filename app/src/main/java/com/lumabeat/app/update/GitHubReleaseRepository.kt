package com.lumabeat.app.update

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AppReleaseRepository {
    suspend fun latest(includePrereleases: Boolean = false): AppReleaseMetadata?
}

class UpdateRepositoryException(
    val failure: UpdateFailure,
    message: String,
) : Exception(message)

class GitHubReleaseRepository(
    private val userAgent: String,
) : AppReleaseRepository {
    override suspend fun latest(includePrereleases: Boolean): AppReleaseMetadata? =
        withContext(Dispatchers.IO) {
            val releases = runCatching {
                JSONArray(readTrustedText(RELEASES_API, MAX_RELEASES_BYTES))
            }.getOrElse { error ->
                if (error is UpdateRepositoryException) throw error
                throw UpdateRepositoryException(
                    UpdateFailure.INVALID_METADATA,
                    "Invalid GitHub releases response",
                )
            }
            selectLatestValidRelease(sequence {
                for (index in 0 until minOf(releases.length(), MAX_RELEASES_TO_INSPECT)) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft", true)) continue
                    val prerelease = release.optBoolean("prerelease", false)
                    if (prerelease && !includePrereleases) continue
                    yield {
                        loadRelease(release, prerelease)
                    }
                }
            })
        }

    private fun loadRelease(release: JSONObject, prerelease: Boolean): AppReleaseMetadata {
        val tag = release.optString("tag_name").trim()
        val releaseUrl = release.optString("html_url").trim()
        requireTrustedGitHubUrl(releaseUrl, expectedTag = tag, releasePage = true)
        val metadataUrl = release.optJSONArray("assets")
            ?.objects()
            ?.firstOrNull { it.optString("name") == METADATA_ASSET }
            ?.optString("browser_download_url")
            ?.trim()
            ?: throw UpdateRepositoryException(
                UpdateFailure.INVALID_METADATA,
                "$tag has no $METADATA_ASSET asset",
            )
        requireTrustedGitHubUrl(metadataUrl, expectedTag = tag, expectedAsset = METADATA_ASSET)
        return parseMetadata(
            text = readTrustedText(metadataUrl, MAX_METADATA_BYTES),
            expectedTag = tag,
            releaseUrl = releaseUrl,
            prerelease = prerelease,
        )
    }

    private fun parseMetadata(
        text: String,
        expectedTag: String,
        releaseUrl: String,
        prerelease: Boolean,
    ): AppReleaseMetadata {
        val json = runCatching { JSONObject(text) }.getOrElse {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Invalid release metadata JSON")
        }
        val metadata = runCatching {
            AppReleaseMetadata(
                schemaVersion = json.optInt("schemaVersion", -1),
                version = json.optString("version").trim(),
                versionCode = json.optLong("versionCode", -1),
                tag = json.optString("tag").trim(),
                apkUrl = json.optString("apk").trim(),
                sha256 = json.optString("sha256").trim().lowercase(),
                packageName = json.optString("packageName").trim(),
                releaseUrl = releaseUrl,
                prerelease = prerelease,
            )
        }.getOrElse {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Invalid release metadata fields")
        }
        if (metadata.tag != expectedTag) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Release tag does not match metadata")
        }
        requireTrustedReleaseMetadata(metadata)
        return metadata
    }

    private fun readTrustedText(url: String, maxBytes: Int): String {
        var current = URL(url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requireTrustedGitHubUrl(current.toString())
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json, application/json")
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val response = connection.responseCode
                if (response in 300..399) {
                    current = resolveRedirect(current, connection, redirectCount)
                    return@repeat
                }
                requireSuccessfulResponse(connection, response, maxBytes)
                return readLimitedBody(connection, maxBytes)
            } finally {
                connection.disconnect()
            }
        }
        throw UpdateRepositoryException(UpdateFailure.NETWORK, "Redirect resolution failed")
    }

    private fun resolveRedirect(
        current: URL,
        connection: HttpURLConnection,
        redirectCount: Int,
    ): URL {
        if (redirectCount >= MAX_REDIRECTS) {
            throw UpdateRepositoryException(UpdateFailure.NETWORK, "Too many redirects")
        }
        val location = connection.getHeaderField("Location")
            ?: throw UpdateRepositoryException(UpdateFailure.NETWORK, "Redirect without location")
        return URI(current.toString()).resolve(location).toURL()
    }

    private fun requireSuccessfulResponse(
        connection: HttpURLConnection,
        response: Int,
        maxBytes: Int,
    ) {
        if (response == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") {
            throw UpdateRepositoryException(UpdateFailure.RATE_LIMITED, "GitHub API rate limit reached")
        }
        if (response !in 200..299) {
            throw UpdateRepositoryException(UpdateFailure.NETWORK, "GitHub returned HTTP $response")
        }
        if (connection.contentLengthLong > maxBytes) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Response is too large")
        }
    }

    private fun readLimitedBody(connection: HttpURLConnection, maxBytes: Int): String =
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream(minOf(maxBytes, 16 * 1024))
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Response is too large")
                }
                output.write(buffer, 0, count)
            }
            output.toString(Charsets.UTF_8.name())
        }

    private fun requireTrustedGitHubUrl(
        value: String,
        expectedTag: String? = null,
        expectedAsset: String? = null,
        releasePage: Boolean = false,
    ) {
        val uri = trustedUri(value)
        val host = uri.host.lowercase()
        if (host !in TRUSTED_HOSTS) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Untrusted host")
        }
        if (host == "api.github.com" && !uri.path.startsWith("/repos/$REPOSITORY/")) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Unexpected API path")
        }
        if (host == "github.com") {
            requireGitHubReleasePath(uri, expectedTag, expectedAsset, releasePage)
        }
    }

    private fun trustedUri(value: String): URI {
        val uri = runCatching { URI(value) }.getOrNull()
            ?: throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Invalid URL")
        if (uri.scheme != "https" || uri.host == null || uri.userInfo != null || uri.port !in listOf(-1, 443)) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Untrusted URL")
        }
        return uri
    }

    private fun requireGitHubReleasePath(
        uri: URI,
        expectedTag: String?,
        expectedAsset: String?,
        releasePage: Boolean,
    ) {
        val prefix = "/$REPOSITORY/releases/"
        if (!uri.path.startsWith(prefix)) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Unexpected release path")
        }
        if (expectedTag == null) return
        val expectedPath = if (releasePage) {
            "${prefix}tag/$expectedTag"
        } else {
            "${prefix}download/$expectedTag/${expectedAsset ?: APK_ASSET}"
        }
        if (uri.path != expectedPath || uri.rawQuery != null || uri.rawFragment != null) {
            throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Release URL does not match metadata")
        }
    }

    private fun JSONArray.objects(): Sequence<JSONObject> = sequence {
        for (index in 0 until length()) optJSONObject(index)?.let { yield(it) }
    }

    companion object {
        const val EXPECTED_PACKAGE = "com.lumabeat.app"
        const val SUPPORTED_SCHEMA = 1
        const val REPOSITORY = "thomrnowtea/lumabeat"
        private const val RELEASES_API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=20"
        private const val METADATA_ASSET = "release.json"
        private const val APK_ASSET = "LumaBeat.apk"
        private const val MAX_RELEASES_TO_INSPECT = 20
        private const val MAX_RELEASES_BYTES = 1024 * 1024
        private const val MAX_METADATA_BYTES = 64 * 1024
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 20_000
        private val TRUSTED_HOSTS = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
    }
}

internal fun selectLatestValidRelease(
    candidates: Sequence<() -> AppReleaseMetadata>,
): AppReleaseMetadata? {
    var latest: AppReleaseMetadata? = null
    for (loadCandidate in candidates) {
        val candidate = try {
            loadCandidate()
        } catch (error: UpdateRepositoryException) {
            if (error.failure == UpdateFailure.INVALID_METADATA) continue
            throw error
        }
        if (latest == null || candidate.versionCode > latest.versionCode) latest = candidate
    }
    return latest
}

internal fun requireTrustedReleaseMetadata(release: AppReleaseMetadata) {
    if (release.schemaVersion != GitHubReleaseRepository.SUPPORTED_SCHEMA ||
        release.packageName != GitHubReleaseRepository.EXPECTED_PACKAGE
    ) {
        throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Unsupported release identity")
    }
    requireExactReleaseUrl(
        release.releaseUrl,
        "/${GitHubReleaseRepository.REPOSITORY}/releases/tag/${release.tag}",
    )
    requireExactReleaseUrl(
        release.apkUrl,
        "/${GitHubReleaseRepository.REPOSITORY}/releases/download/${release.tag}/LumaBeat.apk",
    )
}

private fun requireExactReleaseUrl(value: String, expectedPath: String) {
    val uri = runCatching { URI(value) }.getOrNull()
    if (uri?.scheme != "https" || uri.host?.lowercase() != "github.com" ||
        uri.userInfo != null || uri.port !in listOf(-1, 443) || uri.path != expectedPath ||
        uri.rawQuery != null || uri.rawFragment != null
    ) {
        throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "Untrusted release URL")
    }
}

fun isNewerRelease(installedVersionCode: Long, release: AppReleaseMetadata): Boolean =
    release.versionCode > installedVersionCode
