package com.kagawagao.ars

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * 皮肤属性管理类
 * 
 * 负责:
 * 1. 解析 View 的可换肤属性
 * 2. 应用皮肤资源到 View
 * 3. 支持常见属性的动态切换
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class SkinAttribute(private val context: Context) {
    
    companion object {
        // 支持的属性列表
        private val SUPPORTED_ATTRS = listOf(
            "background",
            "src",
            "textColor",
            "drawableLeft",
            "drawableTop",
            "drawableRight",
            "drawableBottom"
        )
    }
    
    /**
     * 应用皮肤到视图
     * 
     * @param view 目标视图
     * @param attrs 属性集
     */
    fun applySkin(view: View, attrs: AttributeSet?) {
        if (attrs == null) return
        
        val skinManager = ArsSkinManager.getInstance(context)
        
        for (i in 0 until attrs.attributeCount) {
            val attrName = attrs.getAttributeName(i)
            val attrValue = attrs.getAttributeValue(i)
            
            if (attrName in SUPPORTED_ATTRS && attrValue.startsWith("@")) {
                val resId = attrValue.substring(1).toIntOrNull() ?: continue
                applyAttribute(view, attrName, resId, skinManager)
            }
        }
    }
    
    /**
     * 应用单个属性
     */
    private fun applyAttribute(view: View, attrName: String, resId: Int, skinManager: ArsSkinManager) {
        try {
            when (attrName) {
                "background" -> {
                    val drawable = getDrawable(resId, skinManager)
                    view.background = drawable
                }
                "src" -> {
                    if (view is ImageView) {
                        val drawable = getDrawable(resId, skinManager)
                        view.setImageDrawable(drawable)
                    }
                }
                "textColor" -> {
                    if (view is TextView) {
                        val color = skinManager.getColor(resId)
                        view.setTextColor(color)
                    }
                }
                // 可以添加更多属性支持
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取 Drawable 资源
     */
    private fun getDrawable(resId: Int, skinManager: ArsSkinManager): Drawable? {
        val resources = skinManager.getTargetResources()
        return resources.getDrawable(resId, context.theme)
    }
    
    /**
     * 存储的皮肤属性项
     */
    data class SkinItem(
        val view: View,
        val attrName: String,
        val resId: Int
    )
}
