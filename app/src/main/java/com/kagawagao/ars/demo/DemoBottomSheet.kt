package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsDialogFragment

/**
 * Demonstrates BottomSheetDialogFragment — inherits [ArsDialogFragment].
 *
 * BottomSheetDialogFragment extends DialogFragment internally, so
 * [ArsDialogFragment] covers both patterns automatically.
 *
 * Note: For full Material bottom sheet behavior (slide-up, drag handle),
 * extend Material's BottomSheetDialogFragment and manually integrate
 * ARS Context wrapping + Factory2 as shown in docs/ui-patterns.md.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoBottomSheet : ArsDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_demo, container, false)
    }
}
