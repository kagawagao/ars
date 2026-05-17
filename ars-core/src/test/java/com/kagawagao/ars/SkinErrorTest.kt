package com.kagawagao.ars

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SkinError] sealed class hierarchy.
 *
 * Validates that all error types carry descriptive messages
 * and are correctly constructed.
 */
class SkinErrorTest {

    @Test
    fun `FileNotFound has descriptive message with path`() {
        val error = SkinError.FileNotFound("/path/to/skin.apk")
        assertTrue("Message should contain the path", error.message.contains("/path/to/skin.apk"))
        assertTrue("Message should mention file not found", error.message.contains("not found"))
        assertNull("No cause should be set", error.cause)
    }

    @Test
    fun `FileNotFound can carry cause`() {
        val cause = IllegalArgumentException("Test cause")
        val error = SkinError.FileNotFound("/path.apk", cause)
        assertSame("Cause should be preserved", cause, error.cause)
    }

    @Test
    fun `CorruptedPackage has descriptive message`() {
        val error = SkinError.CorruptedPackage("/corrupt.apk")
        assertTrue("Message should contain path", error.message.contains("/corrupt.apk"))
        assertTrue("Message should mention corrupted", error.message.contains("corrupted"))
    }

    @Test
    fun `NotASkinPackage has descriptive message`() {
        val error = SkinError.NotASkinPackage("com.example.notskin")
        assertTrue("Message should contain package name", error.message.contains("com.example.notskin"))
        assertTrue("Message should mention not a skin package", error.message.contains("not an ARS skin package"))
    }

    @Test
    fun `TargetMismatch has descriptive message with both packages`() {
        val error = SkinError.TargetMismatch("com.target.app", "com.host.app")
        assertTrue("Message should contain target package", error.message.contains("com.target.app"))
        assertTrue("Message should contain host package", error.message.contains("com.host.app"))
    }

    @Test
    fun `IncompatibleVersion has descriptive message with versions`() {
        val error = SkinError.IncompatibleVersion(2, 1)
        assertTrue("Message should contain skin version", error.message.contains("2"))
        assertTrue("Message should contain framework version", error.message.contains("1"))
    }

    @Test
    fun `StorageError carries custom message and cause`() {
        val cause = java.io.IOException("Disk full")
        val error = SkinError.StorageError("Failed to write skin file", cause)
        assertEquals("Message should be preserved", "Failed to write skin file", error.message)
        assertSame("Cause should be preserved", cause, error.cause)
    }

    @Test
    fun `ResourceNotFound has descriptive message`() {
        val error = SkinError.ResourceNotFound("primary", "color")
        assertTrue("Message should contain resource name", error.message.contains("primary"))
        assertTrue("Message should contain resource type", error.message.contains("color"))
    }

    @Test
    fun `SwitchInProgress singleton has fixed message`() {
        val error = SkinError.SwitchInProgress
        assertTrue("Message should mention switch in progress", error.message.contains("switch"))
        assertNull("No cause for singleton", error.cause)
    }

    @Test
    fun `all error types are distinct subclasses of SkinError`() {
        val errors: List<SkinError> = listOf(
            SkinError.FileNotFound("/test"),
            SkinError.CorruptedPackage("/test"),
            SkinError.NotASkinPackage("test"),
            SkinError.TargetMismatch("a", "b"),
            SkinError.IncompatibleVersion(1, 2),
            SkinError.StorageError("test"),
            SkinError.ResourceNotFound("name", "type"),
            SkinError.SwitchInProgress
        )

        // Verify all are different classes
        val classes = errors.map { it::class }.toSet()
        assertEquals("All error types should be distinct classes", errors.size, classes.size)

        // Verify all have non-empty messages
        errors.forEach { error ->
            assertFalse("Error message should not be empty: ${error::class.simpleName}", error.message.isEmpty())
        }
    }
}
