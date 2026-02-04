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
@RequiresApi(34)
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
     * 注意：此方法需要系统权限才能执行。
     * 在非系统/签名应用中，此方法不受支持并将抛出 [UnsupportedOperationException]。
     */
    fun enableOverlay(overlayPackage: String): Boolean {
        // OverlayManager 的 setEnabled 系列方法在 Android 14+ 中需要系统/签名权限。
        // 为避免误导调用方为"普通失败"，这里明确抛出异常表明当前环境不支持该操作。
        throw UnsupportedOperationException(
            "Enabling overlays via OverlayManager requires system/signature-level permissions " +
                "and is not supported for this application."
        )
    }
    
    /**
     * 禁用指定的 Overlay
     * 
     * @param overlayPackage Overlay 包名
     * @return 是否成功
     *
     * 注意：此方法需要系统权限才能执行。
     * 在非系统/签名应用中，此方法不受支持并将抛出 [UnsupportedOperationException]。
     */
    fun disableOverlay(overlayPackage: String): Boolean {
        // OverlayManager 的 setEnabled 系列方法在 Android 14+ 中需要系统/签名权限。
        // 为避免误导调用方为"普通失败"，这里明确抛出异常表明当前环境不支持该操作。
        throw UnsupportedOperationException(
            "Disabling overlays via OverlayManager requires system/signature-level permissions " +
                "and is not supported for this application."
        )
    }
    
    /**
     * 获取 Overlay 的状态
     * 
     * @param targetPackage 目标包名（被覆盖的应用包名，通常是当前应用）
     * @return Overlay 信息列表
     * 
     * 注意：getOverlayInfosForTarget 方法需要传入目标包名（被覆盖的应用），而不是 overlay 包名
     */
    fun getOverlayInfo(targetPackage: String): List<OverlayInfo>? {
        return try {
            // Android 14 OverlayManager API 通过 getOverlayInfosForTarget 获取
            // 参数是目标包名（被覆盖的包），返回所有覆盖该包的 overlay 信息列表
            overlayManager?.getOverlayInfosForTarget(targetPackage)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
