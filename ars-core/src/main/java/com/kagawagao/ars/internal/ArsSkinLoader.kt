package com.kagawagao.ars.internal

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import com.kagawagao.ars.SkinError
import com.kagawagao.ars.SkinPackage
import com.kagawagao.ars.SkinResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Loads and validates skin APK packages.
 *
 * Internal class used by [ArsSkinEngine]. Handles APK validation,
 * ARS metadata extraction, and Resources creation for skin packages.
 *
 * @param context The host application context (used for PackageManager access).
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class ArsSkinLoader(private val context: Context) {

    companion object {
        /** The ARS skin format version that this loader supports. */
        const val FRAMEWORK_VERSION = 1

        // ARS metadata keys in AndroidManifest.xml
        private const val META_SKIN_NAME = "ars-skin-name"
        private const val META_SKIN_VERSION = "ars-skin-version"
        private const val META_TARGET_PACKAGE = "ars-target-package"
    }

    /**
     * Load a skin package from a file path.
     *
     * Performs I/O on [Dispatchers.IO]. Validates the APK structure,
     * verifies ARS metadata, checks target compatibility, and creates
     * a [Resources] instance for the skin.
     *
     * @param skinPath Absolute path to the skin APK file.
     * @return [SkinResult.Success] with a [SkinPackage] on success,
     *         [SkinResult.Error] with a [SkinError] on failure.
     */
    suspend fun load(skinPath: String): SkinResult<SkinPackage> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Validate file exists
            val skinFile = File(skinPath)
            if (!skinFile.exists() || !skinFile.isFile) {
                return@withContext SkinResult.Error(SkinError.FileNotFound(skinPath))
            }

            if (!skinFile.canRead()) {
                return@withContext SkinResult.Error(SkinError.StorageError("Cannot read skin file: $skinPath"))
            }

            // Step 2: Parse via PackageManager
            val packageManager = context.packageManager
            val packageInfo = try {
                packageManager.getPackageArchiveInfo(
                    skinPath,
                    PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES
                )
            } catch (e: Exception) {
                return@withContext SkinResult.Error(SkinError.CorruptedPackage(skinPath, e))
            }

            if (packageInfo == null) {
                return@withContext SkinResult.Error(SkinError.CorruptedPackage(skinPath))
            }

            // Step 3: Verify ARS metadata
            val appInfo = packageInfo.applicationInfo
                ?: return@withContext SkinResult.Error(SkinError.CorruptedPackage(skinPath))

            val metaData = appInfo.metaData
            if (metaData == null) {
                return@withContext SkinResult.Error(
                    SkinError.NotASkinPackage(packageInfo.packageName)
                )
            }

            val skinName = metaData.getString(META_SKIN_NAME)
            val skinVersion = metaData.getInt(META_SKIN_VERSION, -1)
            val targetPackage = metaData.getString(META_TARGET_PACKAGE)

            // Validate required metadata
            if (skinName.isNullOrBlank()) {
                return@withContext SkinResult.Error(
                    SkinError.NotASkinPackage(packageInfo.packageName)
                )
            }

            if (skinVersion < 0) {
                return@withContext SkinResult.Error(
                    SkinError.NotASkinPackage(packageInfo.packageName)
                )
            }

            if (targetPackage.isNullOrBlank()) {
                return@withContext SkinResult.Error(
                    SkinError.NotASkinPackage(packageInfo.packageName)
                )
            }

            // Step 4: Verify target package matches host
            val hostPackage = context.packageName
            if (targetPackage != hostPackage) {
                return@withContext SkinResult.Error(
                    SkinError.TargetMismatch(targetPackage, hostPackage)
                )
            }

            // Step 5: Check version compatibility
            if (skinVersion != FRAMEWORK_VERSION) {
                return@withContext SkinResult.Error(
                    SkinError.IncompatibleVersion(skinVersion, FRAMEWORK_VERSION)
                )
            }

            // Step 6: Create Resources for the skin APK
            val skinResources = try {
                createResourcesForPackage(packageInfo, skinPath, packageManager)
            } catch (e: Exception) {
                return@withContext SkinResult.Error(SkinError.StorageError(
                    "Failed to create Resources for skin package: ${e.message}", e
                ))
            }

            // Step 7: Sync configuration with host
            skinResources.updateConfiguration(
                context.resources.configuration,
                context.resources.displayMetrics
            )

            // Step 8: Build SkinPackage
            val skinPackage = SkinPackage(
                name = skinName,
                packageName = packageInfo.packageName,
                targetPackage = targetPackage,
                version = skinVersion,
                resources = skinResources,
                path = skinPath,
                themeHint = null
            )

            SkinResult.Success(skinPackage)
        } catch (e: Exception) {
            SkinResult.Error(SkinError.StorageError(
                "Unexpected error loading skin: ${e.message}", e
            ))
        }
    }

    /**
     * Download a skin package from a URL and load it.
     *
     * Downloads the APK to a local file, verifies integrity via SHA-256
     * checksum (if provided), then loads it via [load].
     *
     * @param url The URL to download the skin APK from.
     * @param skinName A filename for the downloaded skin (e.g., \"holiday_theme.apk\").
     * @param expectedSha256 Optional SHA-256 hex digest for integrity verification.
     * @return [SkinResult.Success] with the loaded [SkinPackage], or [SkinResult.Error].
     */
    suspend fun loadFromUrl(
        url: String,
        skinName: String,
        expectedSha256: String? = null
    ): SkinResult<SkinPackage> = withContext(Dispatchers.IO) {
        try {
            // Sanitize skinName to prevent path traversal attacks
            val safeName = File(skinName).name
            require(safeName.endsWith(".apk")) {
                "skinName must end with .apk, got: $safeName"
            }
            val destFile = File(context.filesDir, "skins/$safeName").apply {
                parentFile?.mkdirs()
            }

            // Download with progress (not reported to caller in this version)
            downloadFile(url, destFile)

            // Verify checksum if provided
            if (expectedSha256 != null) {
                val actual = sha256(destFile)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    destFile.delete()
                    return@withContext SkinResult.Error(
                        SkinError.CorruptedPackage(destFile.absolutePath,
                            IllegalStateException("SHA-256 mismatch: expected $expectedSha256, got $actual"))
                    )
                }
            }

            // Load the downloaded skin
            load(destFile.absolutePath)
        } catch (e: Exception) {
            SkinResult.Error(SkinError.StorageError(
                "Failed to download skin from $url: ${e.message}", e
            ))
        }
    }

    /**
     * Download a file from a URL to a local destination.
     */
    @Throws(java.io.IOException::class)
    private fun downloadFile(urlString: String, dest: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000

        try {
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP $responseCode")
            }

            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Compute the SHA-256 hex digest of a file.
     */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Create a [Resources] instance for a skin APK using
     * [PackageManager.getResourcesForApplication].
     *
     * This is the standard, non-reflective way to obtain Resources for
     * an external APK. The resulting Resources is used by [SkinResources]
     * as the overlay source — it is NOT used directly by Views.
     *
     * @param packageInfo The parsed package archive info.
     * @param skinPath The file path to the skin APK.
     * @param packageManager The host's PackageManager instance.
     * @return A Resources instance for the skin APK.
     */
    @Suppress("DEPRECATION")
    private fun createResourcesForPackage(
        packageInfo: android.content.pm.PackageInfo,
        skinPath: String,
        packageManager: PackageManager
    ): Resources {
        val appInfo = packageInfo.applicationInfo!!.apply {
            sourceDir = skinPath
            publicSourceDir = skinPath
        }
        return packageManager.getResourcesForApplication(appInfo)
    }
}
