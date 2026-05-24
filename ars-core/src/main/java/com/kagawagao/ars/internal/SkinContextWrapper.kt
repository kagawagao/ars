package com.kagawagao.ars.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * ContextWrapper that intercepts [getResources] to return a skin-aware
 * [SkinResources] instance.
 *
 * Created by [ArsSkinEngine.wrapContext] and used internally by
 * `ArsActivity` / `ArsFragment` base classes. When any View or code calls
 * `getResources()` on this Context, they transparently receive skin-overridden
 * resource values.
 *
 * [getTheme] is delegated to the base Context (not overridden), ensuring
 * the host activity's theme is preserved.
 *
 * @property base The base Context to wrap.
 * @property skinResources The skin-aware Resources proxy.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class SkinContextWrapper(
    base: Context,
    private val skinResources: SkinResources
) : ContextWrapper(base) {

    /**
     * Returns the skin-aware [SkinResources] instance.
     *
     * All `getColor()`, `getDrawable()`, `getDimension()`, etc. calls on
     * the returned Resources will return skin-overridden values when available.
     */
    override fun getResources(): Resources = skinResources

    /**
     * getTheme() is NOT overridden — it delegates to the base Context
     * via ContextWrapper, preserving the host Activity's theme.
     */
}
