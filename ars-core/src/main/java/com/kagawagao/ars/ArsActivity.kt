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
@RequiresApi(34)
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
        // 注意：调用 recreate() 会导致 Activity 完全重建，可能造成状态丢失
        // 建议在调用前保存状态（通过 onSaveInstanceState）
        // 或考虑实现更细粒度的视图刷新机制
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
