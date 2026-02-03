package com.kagawagao.ars

import android.annotation.SuppressLint
import android.content.Context
import android.content.om.OverlayInfo
import android.content.om.OverlayManager
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * ResourceOverlay 辅助类
 * 
 * 提供 Android 14+ 的 OverlayManager API 支持
 * 用于管理系统级别的资源覆盖
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class ResourceOverlayHelper(private val context: Context) {
    
    @get:SuppressLint("WrongConstant")
    private val overlayManager: OverlayManager? by lazy {
        context.getSystemService(Context.OVERLAY_SERVICE) as? OverlayManager
    }
    
    /**
     * 检查是否支持 OverlayManager
     */
    fun isOverlaySupported(): Boolean {
        return overlayManager != null
    }
    
    /**
     * 获取所有的 Overlay 信息
     * 
     * @param targetPackage 目标包名
     * @return Overlay 信息列表
     */
    fun getOverlayInfos(targetPackage: String): List<OverlayInfo>? {
        return try {
            overlayManager?.getOverlayInfosForTarget(targetPackage)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 启用指定的 Overlay
     * 
     * @param overlayPackage Overlay 包名
     * @return 是否成功
     * 
     * 注意：此方法需要系统权限才能执行
     */
    fun enableOverlay(overlayPackage: String): Boolean {
        return try {
            // OverlayManager的setEnabled等方法在Android 14中需要系统权限
            // 这里仅作为API示例，实际使用需要系统签名权限
            // overlayManager?.setEnabledExclusiveInCategory(overlayPackage, 0)
            // 由于权限限制，返回false表示需要系统级权限
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 禁用指定的 Overlay
     * 
     * @param overlayPackage Overlay 包名
     * @return 是否成功
     * 
     * 注意：此方法需要系统权限才能执行
     */
    fun disableOverlay(overlayPackage: String): Boolean {
        return try {
            // OverlayManager的setEnabled等方法在Android 14中需要系统权限
            // 这里仅作为API示例，实际使用需要系统签名权限
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 获取 Overlay 的状态
     * 
     * @param overlayPackage Overlay 包名
     * @return Overlay 信息
     */
    fun getOverlayInfo(overlayPackage: String): OverlayInfo? {
        return try {
            // Android 14 OverlayManager API 需要通过getOverlayInfosForTarget获取
            // 注意：getOverlayInfosForTarget 返回指定包的 overlay 信息列表
            // 如果需要获取特定overlay包的信息，应该查询该包所覆盖的目标包
            val allOverlays = overlayManager?.getOverlayInfosForTarget(overlayPackage)
            // 返回第一个 overlay（如果存在）
            allOverlays?.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
