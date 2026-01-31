package com.kagawagao.ars.demo

import android.os.Build
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsApplication

/**
 * Demo Application
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoApplication : ArsApplication() {
    
    override fun onCreate() {
        super.onCreate()
        // 初始化工作已在父类完成
    }
}
