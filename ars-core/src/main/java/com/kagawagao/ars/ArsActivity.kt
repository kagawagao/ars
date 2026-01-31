package com.kagawagao.ars

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

/**
 * ARS Activity 基类
 * 
 * 提供自动换肤支持的 Activity
 * 继承此类的 Activity 会自动响应主题切换
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
open class ArsActivity : AppCompatActivity(), ArsSkinManager.ThemeChangeListener {
    
    private lateinit var skinManager: ArsSkinManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skinManager = ArsSkinManager.getInstance(this)
        skinManager.registerThemeChangeListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        skinManager.unregisterThemeChangeListener(this)
    }
    
    override fun onThemeChanged(mode: ArsSkinManager.ThemeMode) {
        // 主题改变时重建 Activity
        recreate()
    }
    
    /**
     * 加载皮肤包
     */
    protected fun loadSkin(skinPath: String): Boolean {
        return skinManager.loadSkin(skinPath)
    }
    
    /**
     * 重置为默认皮肤
     */
    protected fun resetSkin() {
        skinManager.resetToDefault()
    }
    
    /**
     * 切换主题模式
     */
    protected fun switchTheme(mode: ArsSkinManager.ThemeMode) {
        skinManager.switchThemeMode(mode)
    }
}
