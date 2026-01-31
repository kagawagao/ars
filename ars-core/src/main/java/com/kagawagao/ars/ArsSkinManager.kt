package com.kagawagao.ars

import android.app.Activity
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.ref.WeakReference

/**
 * ARS 皮肤管理器 - 基于 ResourceOverlay 的换肤框架核心类
 * 
 * 主要功能:
 * 1. 管理皮肤包的加载和卸载
 * 2. 支持深色/浅色主题切换
 * 3. 支持动态加载外部皮肤包资源
 * 4. 支持 Android 14+ 的 ResourceOverlay 机制
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ArsSkinManager private constructor(private val context: Context) {
    
    private var skinResources: Resources? = null
    private var skinPackageName: String? = null
    private val themeListeners = mutableListOf<WeakReference<ThemeChangeListener>>()
    
    /**
     * 当前主题模式
     */
    var currentThemeMode: ThemeMode = ThemeMode.LIGHT
        private set
    
    companion object {
        @Volatile
        private var instance: ArsSkinManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): ArsSkinManager {
            return instance ?: synchronized(this) {
                instance ?: ArsSkinManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 加载皮肤包
     * 
     * @param skinPath 皮肤包 APK 文件路径
     * @return 是否加载成功
     */
    fun loadSkin(skinPath: String): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo: PackageInfo = packageManager.getPackageArchiveInfo(
                skinPath,
                PackageManager.GET_ACTIVITIES
            ) ?: return false
            
            skinPackageName = packageInfo.packageName
            
            // 创建 AssetManager 实例
            val assetManager = AssetManager::class.java.newInstance()
            val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
            addAssetPath.invoke(assetManager, skinPath)
            
            // 创建皮肤包的 Resources 实例
            val superRes = context.resources
            skinResources = Resources(
                assetManager,
                superRes.displayMetrics,
                superRes.configuration
            )
            
            notifyThemeChanged()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 重置为默认皮肤
     */
    fun resetToDefault() {
        skinResources = null
        skinPackageName = null
        notifyThemeChanged()
    }
    
    /**
     * 切换主题模式
     * 
     * @param mode 新的主题模式
     */
    fun switchThemeMode(mode: ThemeMode) {
        if (currentThemeMode != mode) {
            currentThemeMode = mode
            notifyThemeChanged()
        }
    }
    
    /**
     * 获取资源 ID
     * 
     * @param resName 资源名称
     * @param defType 资源类型（如 "color", "drawable" 等）
     * @return 资源 ID，如果不存在则返回 0
     */
    fun getResourceId(resName: String, defType: String): Int {
        val resources = skinResources ?: context.resources
        val packageName = skinPackageName ?: context.packageName
        return resources.getIdentifier(resName, defType, packageName)
    }
    
    /**
     * 获取颜色资源
     * 
     * @param resId 资源 ID
     * @return 颜色值
     */
    fun getColor(resId: Int): Int {
        val resources = getTargetResources()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            resources.getColor(resId, context.theme)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(resId)
        }
    }
    
    /**
     * 获取目标资源对象
     */
    fun getTargetResources(): Resources {
        return skinResources ?: context.resources
    }
    
    /**
     * 注册主题变化监听器
     */
    fun registerThemeChangeListener(listener: ThemeChangeListener) {
        themeListeners.add(WeakReference(listener))
    }
    
    /**
     * 注销主题变化监听器
     */
    fun unregisterThemeChangeListener(listener: ThemeChangeListener) {
        themeListeners.removeAll { it.get() == listener }
    }
    
    /**
     * 通知主题已改变
     */
    private fun notifyThemeChanged() {
        themeListeners.removeAll { it.get() == null }
        themeListeners.forEach { 
            it.get()?.onThemeChanged(currentThemeMode)
        }
    }
    
    /**
     * 应用皮肤到 Activity
     */
    fun applySkin(activity: Activity) {
        // 在 Activity 创建时应用皮肤
        if (skinResources != null) {
            // 这里可以添加更多的资源应用逻辑
            activity.recreate()
        }
    }
    
    /**
     * 主题模式枚举
     */
    enum class ThemeMode {
        LIGHT,  // 浅色模式
        DARK    // 深色模式
    }
    
    /**
     * 主题变化监听器接口
     */
    interface ThemeChangeListener {
        fun onThemeChanged(mode: ThemeMode)
    }
}
