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
@RequiresApi(34)
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
            
            if (attrName in SUPPORTED_ATTRS) {
                val resId = attrs.getAttributeResourceValue(i, 0)
                if (resId != 0) {
                    applyAttribute(view, attrName, resId, skinManager)
                }
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
                "drawableLeft", "drawableTop", "drawableRight", "drawableBottom" -> {
                    if (view is TextView) {
                        val drawables = view.compoundDrawables
                        val left = if (attrName == "drawableLeft") getDrawable(resId, skinManager) else drawables[0]
                        val top = if (attrName == "drawableTop") getDrawable(resId, skinManager) else drawables[1]
                        val right = if (attrName == "drawableRight") getDrawable(resId, skinManager) else drawables[2]
                        val bottom = if (attrName == "drawableBottom") getDrawable(resId, skinManager) else drawables[3]
                        view.setCompoundDrawablesWithIntrinsicBounds(left, top, right, bottom)
                    }
                }
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
        return androidx.core.content.res.ResourcesCompat.getDrawable(resources, resId, context.theme)
    }
}
