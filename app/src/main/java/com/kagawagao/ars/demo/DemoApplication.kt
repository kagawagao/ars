package com.kagawagao.ars.demo

import android.os.Build
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsApplication

/**
 * ARS V2 Demo Application.
 *
 * Extends [ArsApplication] which initializes [ArsSkinEngine] on startup.
 * No additional configuration needed — the engine is ready when any
 * [ArsActivity] is created.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoApplication : ArsApplication() {

    override fun onCreate() {
        super.onCreate()
        // ArsSkinEngine.init(this) has already been called by ArsApplication
    }
}
