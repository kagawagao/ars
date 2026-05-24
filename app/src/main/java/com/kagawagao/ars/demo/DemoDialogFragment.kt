package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsDialogFragment

/**
 * Demonstrates [ArsDialogFragment] — inherits [ArsDialogFragment].
 *
 * The dialog's Window gets a skin-aware Context, a SkinLayoutInflater
 * Factory2 is installed, and skin changes automatically walk the
 * dialog's decorView.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoDialogFragment : ArsDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_demo, container, false)
    }
}
