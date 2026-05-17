package com.kagawagao.ars

/**
 * Sealed hierarchy of all possible skin operation errors.
 *
 * Every error carries a human-readable [message] and an optional [cause].
 * Framework operations return [SkinResult] — never throw.
 *
 * @property message Descriptive error message.
 * @property cause The underlying exception, if any.
 */
sealed class SkinError(
    val message: String,
    val cause: Throwable? = null
) {
    /**
     * The APK file does not exist or cannot be read.
     *
     * @param path The file path that was not found.
     * @param cause Optional underlying exception.
     */
    class FileNotFound(path: String, cause: Throwable? = null)
        : SkinError("Skin file not found: $path", cause)

    /**
     * The APK is corrupted or cannot be parsed by [android.content.pm.PackageManager].
     *
     * @param path The file path of the corrupted APK.
     * @param cause Optional underlying exception.
     */
    class CorruptedPackage(path: String, cause: Throwable? = null)
        : SkinError("Skin package is corrupted: $path", cause)

    /**
     * The APK is not an ARS skin package — it is missing the required
     * ARS metadata entries in its AndroidManifest.xml.
     *
     * @param packageName The package name of the APK.
     */
    class NotASkinPackage(packageName: String)
        : SkinError("Package '$packageName' is not an ARS skin package")

    /**
     * The skin targets a different host application than the current one.
     *
     * @param skinTarget The target package declared in the skin's metadata.
     * @param hostPackage The actual host application package name.
     */
    class TargetMismatch(skinTarget: String, hostPackage: String)
        : SkinError("Skin targets '$skinTarget' but host is '$hostPackage'")

    /**
     * The skin's format version is incompatible with this version of the framework.
     *
     * @param skinVersion The version declared in the skin's metadata.
     * @param frameworkVersion The version expected by the framework.
     */
    class IncompatibleVersion(skinVersion: Int, frameworkVersion: Int)
        : SkinError("Skin version $skinVersion is incompatible with framework version $frameworkVersion")

    /**
     * Storage error — disk full, permission denied, I/O failure, etc.
     *
     * @param message A human-readable description of the storage failure.
     * @param cause Optional underlying exception.
     */
    class StorageError(message: String, cause: Throwable? = null)
        : SkinError(message, cause)

    /**
     * A specific resource was not found in either the skin or the base resources.
     * This is not necessarily a failure — it can be used for diagnostics.
     *
     * @param resName The resource name (e.g., "primary").
     * @param resType The resource type (e.g., "color").
     */
    class ResourceNotFound(resName: String, resType: String)
        : SkinError("Resource '$resName' of type '$resType' not found in skin or base")

    /**
     * A skin switch was requested while another switch is already in progress.
     * The caller can retry when the current switch completes.
     */
    object SwitchInProgress
        : SkinError("A skin switch is already in progress")
}
