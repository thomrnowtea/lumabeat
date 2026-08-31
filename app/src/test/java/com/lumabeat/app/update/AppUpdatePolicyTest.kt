package com.lumabeat.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun `only a higher Android version code is offered`() {
        assertTrue(isNewerRelease(1, release(versionCode = 2)))
        assertFalse(isNewerRelease(2, release(versionCode = 2)))
        assertFalse(isNewerRelease(3, release(versionCode = 2)))
    }

    @Test
    fun `metadata accepts only the exact LumaBeat release asset`() {
        requireTrustedReleaseMetadata(release())

        assertThrows(UpdateRepositoryException::class.java) {
            requireTrustedReleaseMetadata(
                release(apkUrl = "https://github.com/thomrnowtea/lumabeat/releases/download/v0.1.0/other.apk"),
            )
        }
    }

    @Test
    fun `selection skips malformed candidates and chooses the greatest code`() {
        val newest = release(version = "0.3.0", versionCode = 3)
        val selected = selectLatestValidRelease(
            sequenceOf(
                { release(version = "0.2.0", versionCode = 2) },
                { throw UpdateRepositoryException(UpdateFailure.INVALID_METADATA, "bad") },
                { newest },
            ),
        )

        assertSame(newest, selected)
    }

    @Test
    fun `single signer upgrade accepts certificate history`() {
        val current = byteArrayOf(1, 2, 3)
        assertTrue(
            signerLineageAllowsUpgrade(
                installedCurrent = listOf(current),
                installedHasMultipleSigners = false,
                archiveCurrent = listOf(byteArrayOf(4, 5, 6)),
                archiveHistory = listOf(byteArrayOf(4, 5, 6), current),
                archiveHasMultipleSigners = false,
            ),
        )
    }

    private fun release(
        version: String = "0.1.0",
        versionCode: Long = 1,
        apkUrl: String = "https://github.com/thomrnowtea/lumabeat/releases/download/v$version/LumaBeat.apk",
    ) = AppReleaseMetadata(
        schemaVersion = 1,
        version = version,
        versionCode = versionCode,
        tag = "v$version",
        apkUrl = apkUrl,
        sha256 = "a".repeat(64),
        packageName = "com.lumabeat.app",
        releaseUrl = "https://github.com/thomrnowtea/lumabeat/releases/tag/v$version",
        prerelease = false,
    )
}
