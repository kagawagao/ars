package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsFragment
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.SkinPackage

/**
 * Demonstrates [ArsFragment] — the base Fragment for skin-aware apps.
 *
 * All Views in this Fragment's layout are automatically registered
 * for skin updates via [SkinLayoutInflater]. Skin changes trigger
 * a View-tree walk starting from this Fragment's root View.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DemoFragment : ArsFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_demo, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tvFragmentSkinStatus)?.text =
            "皮肤: ${ArsSkinEngine.activeSkin?.name ?: "默认"}  |  主题: ${ArsSkinEngine.currentThemeMode}"
    }

    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        view?.findViewById<TextView>(R.id.tvFragmentSkinStatus)?.text =
            "皮肤: ${current?.name ?: "默认"}  |  主题: ${ArsSkinEngine.currentThemeMode}"
    }
}
