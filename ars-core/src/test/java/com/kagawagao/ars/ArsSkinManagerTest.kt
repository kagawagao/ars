package com.kagawagao.ars

import org.junit.Test
import org.junit.Assert.*

/**
 * 单元测试示例
 * 
 * 注意：完整的测试需要 Android 运行环境
 * 这里仅提供基本的测试结构
 */
class ArsSkinManagerTest {
    
    @Test
    fun themeMode_enumValues_shouldContainLightAndDark() {
        val modes = ArsSkinManager.ThemeMode.values()
        assertEquals(2, modes.size)
        assertTrue(modes.contains(ArsSkinManager.ThemeMode.LIGHT))
        assertTrue(modes.contains(ArsSkinManager.ThemeMode.DARK))
    }
}
