package com.kagawagao.ars.demo

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsDialog
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.SkinPackage

/**
 * Demonstrates [ArsDialog] — a manually created Dialog with full ARS support.
 *
 * Context is automatically wrapped in the constructor. Factory2 is installed
 * in [onCreate]. Skin changes automatically walk the dialog's decorView.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoDialog(context: Context) : ArsDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_demo)
        setTitle("ArsDialog 演示")
    }

    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        super.onSkinApplied(previous, current)
        // Update dialog chrome: window background + title text color
        setWindowBackgroundColor(R.color.theme_background)
        setTitleTextColor(R.color.theme_text)
        // Update title text to reflect current mode
        setTitle("ArsDialog — ${ArsSkinEngine.currentThemeMode}")
    }
}
