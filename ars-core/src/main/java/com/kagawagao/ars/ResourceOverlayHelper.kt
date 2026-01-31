package com.kagawagao.ars

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
    
    private val overlayManager: OverlayManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getSystemService(Context.OVERLAY_SERVICE) as? OverlayManager
        } else {
            null
        }
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
            overlayManager?.getOverlayInfosForTarget(targetPackage, 0)
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
     */
    fun enableOverlay(overlayPackage: String): Boolean {
        return try {
            overlayManager?.setEnabled(overlayPackage, true, 0)
            true
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
     */
    fun disableOverlay(overlayPackage: String): Boolean {
        return try {
            overlayManager?.setEnabled(overlayPackage, false, 0)
            true
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
            overlayManager?.getOverlayInfo(overlayPackage, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
