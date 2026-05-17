package com.kagawagao.ars

/**
 * Listener interface for skin change events.
 *
 * Callbacks are fired **after** all Views have been updated with the new skin.
 * Implementations are called on the main thread.
 *
 * Register via [ArsSkinEngine.registerSkinChangeListener].
 */
interface SkinChangeListener {
    /**
     * Called when the active skin has changed.
     *
     * @param previous The previously active skin, or `null` if the default
     *                 (host app) resources were active.
     * @param current The newly active skin, or `null` if reset to default.
     */
    fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?)
}
