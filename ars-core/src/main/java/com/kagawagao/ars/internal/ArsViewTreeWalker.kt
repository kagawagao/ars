package com.kagawagao.ars.internal

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.R
import java.util.LinkedList
import java.util.Queue

/**
 * Non-recursive BFS walker for applying skin updates to a View tree.
 *
 * Uses a queue-based approach to avoid stack overflow on deep hierarchies
 * (DD-03). Processes parents before children for visually consistent updates.
 *
 * Views tagged with `R.id.ars_skip_skinning` are skipped entirely — neither
 * the View itself nor its children receive skin updates.
 *
 * ## Usage
 *
 * ```kotlin
 * ArsViewTreeWalker.walk(window.decorView, ArsSkinEngine)
 * ```
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object ArsViewTreeWalker {

    /**
     * Walk the View tree starting from [root] and apply the current skin.
     *
     * Performs a BFS traversal using a [LinkedList] queue. For each View:
     * 1. Skip if tagged with `ars_skip_skinning`.
     * 2. Look up the View's [SkinViewMeta] from the engine.
     * 3. If metadata exists, apply the skin via [ArsSkinEngine.applySkinToView].
     * 4. If the View is a [ViewGroup], enqueue all children.
     *
     * This method is called on the main thread during skin switch.
     *
     * @param root The root View (typically `window.decorView`).
     * @param engine The [ArsSkinEngine] for metadata lookups and skin application.
     */
    fun walk(root: View, engine: ArsSkinEngine) {
        val queue: Queue<View> = LinkedList()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val view = queue.poll()

            // Skip if tagged for exclusion
            if (shouldSkip(view)) continue

            // Apply skin if metadata exists
            val meta = engine.getViewMeta(view)
            if (meta != null) {
                engine.applySkinToView(view, meta)
            }

            // Enqueue children
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    view.getChildAt(i)?.let { queue.add(it) }
                }
            }
        }
    }

    // ─── Private Helpers ──────────────────────────────────────────────

    /**
     * Check if a View should be excluded from skinning.
     *
     * A View is excluded if it has the `ars_skip_skinning` tag set to `true`.
     * Developers can use this to opt specific Views or subtrees out of
     * automatic skinning (e.g., custom Views that manage their own theming).
     *
     * @param view The View to check.
     * @return `true` if the View should be skipped.
     */
    private fun shouldSkip(view: View): Boolean {
        return try {
            val tag = view.getTag(R.id.ars_skip_skinning)
            tag == true
        } catch (_: Exception) {
            // If the ID resource isn't available (e.g., in tests), don't skip
            false
        }
    }
}
