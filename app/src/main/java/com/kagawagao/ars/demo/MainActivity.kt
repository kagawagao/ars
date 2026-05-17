package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.kagawagao.ars.ArsActivity
import com.kagawagao.ars.ArsDialogFragment
import com.kagawagao.ars.ArsOverlaySkin
import com.kagawagao.ars.ArsPopupWindow
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.ArsSpinnerAdapter
import com.kagawagao.ars.ArsToast
import com.kagawagao.ars.SkinPackage
import com.kagawagao.ars.SkinResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ARS V2 Demo — demonstrates all supported UI patterns.
 *
 * Layout: activity_main.xml (scrollable pattern catalog)
 *
 * Patterns covered:
 *   🟢 Activity (self), Fragment, DialogFragment, BottomSheet,
 *      Dialog (ArsDialog), AlertDialog, PopupWindow (ArsPopupWindow),
 *      Snackbar (ArsOverlaySkin), Toast (ArsToast),
 *      RecyclerView, Spinner (ArsSpinnerAdapter),
 *      ViewStub, CustomView (DemoCustomView), Dynamic View,
 *      Live Preview (ImageView, ProgressBar, CheckBox)
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class MainActivity : ArsActivity() {

    // ─── Views ────────────────────────────────────────────────────────
    private lateinit var tvCurrentTheme: TextView
    private lateinit var tvSkinStatus: TextView
    private lateinit var btnSwitchTheme: Button
    private lateinit var btnDiagnostics: Button
    private lateinit var tvDiagnostics: TextView
    private lateinit var livePreview: LinearLayout
    private lateinit var viewStub: ViewStub
    private var viewStubInflated = false

    private val scope = CoroutineScope(Dispatchers.Main)

    // ─── Lifecycle ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateUI()
    }

    // ─── Init ─────────────────────────────────────────────────────────

    private fun initViews() {
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme)
        tvSkinStatus = findViewById(R.id.tvSkinStatus)
        btnSwitchTheme = findViewById(R.id.btnSwitchTheme)
        btnDiagnostics = findViewById(R.id.btnDiagnostics)
        tvDiagnostics = findViewById(R.id.tvDiagnostics)
        livePreview = findViewById(R.id.livePreview)
        viewStub = findViewById(R.id.viewStub)
    }

    @Suppress("DEPRECATION")
    private fun setupListeners() {
        // ── Controls ──
        btnSwitchTheme.setOnClickListener { toggleTheme() }
        findViewById<Button>(R.id.btnLoadSkin).setOnClickListener { showSkinPathDialog() }
        findViewById<Button>(R.id.btnResetSkin).setOnClickListener { resetSkinToDefault() }
        btnDiagnostics.setOnClickListener { toggleDiagnostics() }

        // ── 🟢 Core Patterns ──
        findViewById<Button>(R.id.btnFragment).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, DemoFragment())
                .addToBackStack(null)
                .commit()
        }
        findViewById<Button>(R.id.btnDialogFragment).setOnClickListener {
            DemoDialogFragment().show(supportFragmentManager, "dialog")
        }
        findViewById<Button>(R.id.btnBottomSheet).setOnClickListener {
            DemoBottomSheet().show(supportFragmentManager, "bottomSheet")
        }
        findViewById<Button>(R.id.btnArsDialog).setOnClickListener {
            DemoDialog(this).show()
        }
        findViewById<Button>(R.id.btnAlertDialog).setOnClickListener {
            showSkinnedAlertDialog()
        }

        // ── 🟡 Overlay Patterns ──
        findViewById<Button>(R.id.btnPopupWindow).setOnClickListener { v ->
            showSkinnedPopup(v)
        }
        findViewById<Button>(R.id.btnSnackbar).setOnClickListener { v ->
            showSkinnedSnackbar(v)
        }
        findViewById<Button>(R.id.btnToast).setOnClickListener {
            ArsToast.showText(this, "皮肤配色 Toast — ${ArsSkinEngine.currentThemeMode}")
        }

        // ── 🟢 List Patterns ──
        findViewById<Button>(R.id.btnRecyclerView).setOnClickListener {
            showRecyclerDemo()
        }
        findViewById<Button>(R.id.btnSpinner).setOnClickListener {
            showSpinnerDemo()
        }

        // ── 🟢 Special Patterns ──
        findViewById<Button>(R.id.btnViewStub).setOnClickListener {
            inflateViewStub()
        }
        findViewById<Button>(R.id.btnCustomView).setOnClickListener {
            showCustomViewDemo()
        }
        findViewById<Button>(R.id.btnDynamicView).setOnClickListener {
            addDynamicView()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Theme Toggle
    // ═══════════════════════════════════════════════════════════════════

    private fun toggleTheme() {
        val newMode = when (ArsSkinEngine.currentThemeMode) {
            SkinPackage.ThemeMode.LIGHT -> SkinPackage.ThemeMode.DARK
            SkinPackage.ThemeMode.DARK -> SkinPackage.ThemeMode.LIGHT
        }
        setSkinThemeMode(newMode)
        updateUI()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Skin Loading
    // ═══════════════════════════════════════════════════════════════════

    private fun showSkinPathDialog() {
        val input = EditText(this).apply {
            hint = "/sdcard/Download/skin.apk"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.skin_switch_prompt))
            .setView(input)
            .setPositiveButton("加载") { _, _ ->
                val path = input.text.toString().trim()
                if (path.isNotEmpty()) loadSkin(path)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun loadSkin(path: String) {
        scope.launch {
            val result = switchSkin(path)
            withContext(Dispatchers.Main) {
                when (result) {
                    is SkinResult.Success -> {
                        val skin = ArsSkinEngine.activeSkin
                        Toast.makeText(this@MainActivity,
                            getString(R.string.skin_loaded, skin?.name ?: "?"),
                            Toast.LENGTH_SHORT).show()
                    }
                    is SkinResult.Error -> {
                        Toast.makeText(this@MainActivity,
                            getString(R.string.skin_load_failed, result.error.message),
                            Toast.LENGTH_LONG).show()
                    }
                }
                updateUI()
            }
        }
    }

    private fun resetSkinToDefault() {
        scope.launch {
            resetSkin()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, R.string.skin_reset_done, Toast.LENGTH_SHORT).show()
                updateUI()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // AlertDialog (semi-auto: wrapContext)
    // ═══════════════════════════════════════════════════════════════════

    private fun showSkinnedAlertDialog() {
        val ctx = ArsSkinEngine.wrapContext(this)
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_demo, null)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("AlertDialog (wrapContext)")
            .setView(view)
            .setPositiveButton("确定", null)
            .create()

        // Register skin-change listener for the dialog's lifetime
        val skinListener = object : com.kagawagao.ars.SkinChangeListener {
            override fun onSkinChanged(previous: SkinPackage?, current: SkinPackage?) {
                dialog.window?.decorView?.let {
                    ArsOverlaySkin.refresh(it)
                }
            }
        }
        ArsSkinEngine.registerSkinChangeListener(skinListener)
        dialog.setOnDismissListener { ArsSkinEngine.unregisterSkinChangeListener(skinListener) }

        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════════════
    // ArsPopupWindow
    // ═══════════════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private fun showSkinnedPopup(anchor: View) {
        ArsPopupWindow(this).apply {
            contentView = LayoutInflater.from(skinContext)
                .inflate(R.layout.popup_demo, null)
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isOutsideTouchable = true
            showAsDropDown(anchor, 0, 16)
        }
        // popup auto-refreshes on skin change and auto-unregisters on dismiss
    }

    // ═══════════════════════════════════════════════════════════════════
    // Snackbar (ArsOverlaySkin)
    // ═══════════════════════════════════════════════════════════════════

    private fun showSkinnedSnackbar(hostView: View) {
        val snackbar = Snackbar.make(
            hostView,
            "Snackbar — 主题: ${ArsSkinEngine.currentThemeMode}",
            Snackbar.LENGTH_LONG
        ).apply {
            setAction("关闭") {}
            show()
        }

        // Walk content on skin change
        val listener = ArsOverlaySkin.autoRefresh(snackbar.view)
        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                ArsSkinEngine.unregisterSkinChangeListener(listener)
            }
        })
    }

    // ═══════════════════════════════════════════════════════════════════
    // RecyclerView Demo
    // ═══════════════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private fun showRecyclerDemo() {
        val rv = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = DemoRecyclerAdapter(5)
        }

        AlertDialog.Builder(this)
            .setTitle("RecyclerView 演示")
            .setView(rv)
            .setPositiveButton("关闭", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Spinner Demo (ArsSpinnerAdapter)
    // ═══════════════════════════════════════════════════════════════════

    private fun showSpinnerDemo() {
        val items = listOf("浅色皮肤", "深色皮肤", "节日皮肤", "默认皮肤")
        val adapter = ArrayAdapter(this, R.layout.spinner_item_demo, items)
        val skinned = ArsSpinnerAdapter.wrap(adapter, this)

        @Suppress("DEPRECATION")
        val spinner = android.widget.Spinner(this).apply {
            this.adapter = skinned
        }

        AlertDialog.Builder(this)
            .setTitle("Spinner (ArsSpinnerAdapter)")
            .setView(spinner)
            .setPositiveButton("关闭", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════════
    // ViewStub Demo
    // ═══════════════════════════════════════════════════════════════════

    private fun inflateViewStub() {
        if (viewStubInflated) {
            Toast.makeText(this, "ViewStub 已展开", Toast.LENGTH_SHORT).show()
            return
        }
        viewStubInflated = true
        viewStub.inflate()
        // inflated views are automatically registered for skin updates
        Toast.makeText(this, "ViewStub 已展开，自动注册换肤", Toast.LENGTH_SHORT).show()

        // Refresh live preview area to include the new View
        refreshSkin()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Custom View Demo
    // ═══════════════════════════════════════════════════════════════════

    private fun showCustomViewDemo() {
        val customView = DemoCustomView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                200
            )
        }

        AlertDialog.Builder(this)
            .setTitle("自定义 View (DemoCustomView)")
            .setView(customView)
            .setPositiveButton("关闭", null)
            .show()
        // DemoCustomView registers its own SkinChangeListener
    }

    // ═══════════════════════════════════════════════════════════════════
    // Dynamic View Demo
    // ═══════════════════════════════════════════════════════════════════

    private fun addDynamicView() {
        val ctx = ArsSkinEngine.wrapContext(this)

        // Create a dynamically styled card
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            background = android.graphics.drawable.ColorDrawable(
                ctx.resources.getColor(R.color.theme_card, null)
            )
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        card.addView(TextView(ctx).apply {
            text = "动态添加的 View #${livePreview.childCount}"
            setTextColor(ctx.resources.getColor(R.color.theme_text, null))
            textSize = 14f
            setPadding(0, 0, 0, 8)
        })

        card.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            progress = (Math.random() * 100).toInt()
            progressTintList = ctx.resources.getColorStateList(R.color.theme_accent, null)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 6
            )
        })

        livePreview.addView(card)
        Toast.makeText(this, "已添加动态 View — 调用 refreshSkin() 可在切换后刷新", Toast.LENGTH_SHORT).show()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Diagnostics
    // ═══════════════════════════════════════════════════════════════════

    private fun toggleDiagnostics() {
        if (tvDiagnostics.visibility == View.GONE) {
            refreshDiagnostics()
            tvDiagnostics.visibility = View.VISIBLE
            btnDiagnostics.text = "隐藏诊断"
        } else {
            tvDiagnostics.visibility = View.GONE
            btnDiagnostics.text = getString(R.string.btn_diagnostics)
        }
    }

    private fun refreshDiagnostics() {
        val diag = ArsSkinEngine.getDiagnostics()
        tvDiagnostics.text = buildString {
            appendLine("═══ ARS Engine ═══")
            appendLine("Skin:   ${diag.activeSkinName ?: "(default)"}")
            appendLine("Pkg:    ${diag.activeSkinPackage ?: "N/A"}")
            appendLine("Theme:  ${diag.themeMode}")
            appendLine("Views:  ${diag.registeredViewCount} reg / ${diag.aliveViewCount} alive")
            appendLine("Listen: ${diag.listenerCount}")
            appendLine("Cache:  ${diag.cachedIdMappings}")
            appendLine("Last:   ${if (diag.lastSwitchDurationMs >= 0) "${diag.lastSwitchDurationMs}ms" else "N/A"}")
            if (diag.lastError != null) appendLine("Error:  ${diag.lastError!!.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UI Update
    // ═══════════════════════════════════════════════════════════════════

    private fun updateUI() {
        val mode = ArsSkinEngine.currentThemeMode
        val modeName = when (mode) {
            SkinPackage.ThemeMode.LIGHT -> "浅色 ☀️"
            SkinPackage.ThemeMode.DARK -> "深色 🌙"
        }
        tvCurrentTheme.text = getString(R.string.current_theme, modeName)
        btnSwitchTheme.text = when (mode) {
            SkinPackage.ThemeMode.LIGHT -> getString(R.string.switch_to_dark)
            SkinPackage.ThemeMode.DARK -> getString(R.string.switch_to_light)
        }
        val skin = ArsSkinEngine.activeSkin
        tvSkinStatus.text = getString(R.string.skin_status,
            skin?.let { "${it.name} v${it.version}" } ?: getString(R.string.no_skin_active))
        if (tvDiagnostics.visibility == View.VISIBLE) refreshDiagnostics()
    }

    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        updateUI()
    }
}
