package com.kagawagao.ars

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * ARS Application 基类
 * 
 * 使用 ARS 框架的应用需要继承此类
 * 或在自己的 Application 中初始化 ArsSkinManager
 */
@RequiresApi(34)
open class ArsApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化皮肤管理器
        ArsSkinManager.getInstance(this)
    }
}
