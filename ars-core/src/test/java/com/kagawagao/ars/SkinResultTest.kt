package com.kagawagao.ars

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SkinResult] sealed class.
 *
 * Validates the result wrapper's convenience methods and
 * correct success/error discrimination.
 */
class SkinResultTest {

    @Test
    fun `Success stores and returns value`() {
        val result = SkinResult.Success("hello")
        assertTrue("should be success", result.isSuccess)
        assertFalse("should not be error", result.isError)
        assertEquals("value should be stored", "hello", result.getOrNull())
        assertEquals("value should be returned", "hello", result.getOrThrow())
    }

    @Test
    fun `Error stores and reports error`() {
        val error = SkinError.FileNotFound("/test")
        val result = SkinResult.Error(error)
        assertFalse("should not be success", result.isSuccess)
        assertTrue("should be error", result.isError)
        assertNull("getOrNull should return null", result.getOrNull())
    }

    @Test
    fun `Error getOrThrow throws exception`() {
        val error = SkinError.CorruptedPackage("/test")
        val result = SkinResult.Error(error)

        try {
            result.getOrThrow()
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue("Exception message should contain skin error details", e.message!!.contains("corrupted"))
        }
    }

    @Test
    fun `Success with null value works`() {
        val result = SkinResult.Success(null)
        assertTrue("should be success", result.isSuccess)
        assertNull("value should be null", result.getOrNull())
    }

    @Test
    fun `Success with integer value works`() {
        val result = SkinResult.Success(42)
        assertTrue("should be success", result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `when expression handles both cases`() {
        fun handle(result: SkinResult<String>): String = when (result) {
            is SkinResult.Success -> "got: ${result.value}"
            is SkinResult.Error -> "failed: ${result.error.message}"
        }

        assertEquals("got: hello", handle(SkinResult.Success("hello")))
        val errorResult = handle(SkinResult.Error(SkinError.SwitchInProgress))
        assertTrue("error message should describe switch in progress", errorResult.contains("switch"))
    }
}
