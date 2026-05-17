package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.kagawagao.ars.ArsActivity
import com.kagawagao.ars.ArsSkinEngine
import com.kagawagao.ars.SkinPackage
import com.kagawagao.ars.SkinResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ARS V2 演示主界面
 *
 * 展示的核心功能:
 * - 深色/浅色主题切换（无需皮肤 APK）
 * - 从文件路径加载皮肤包
 * - 重置为默认皮肤
 * - 引擎诊断信息
 * - 皮肤切换回调处理
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class MainActivity : ArsActivity() {

    private lateinit var tvCurrentTheme: TextView
    private lateinit var tvSkinStatus: TextView
    private lateinit var btnSwitchTheme: Button
    private lateinit var btnLoadSkin: Button
    private lateinit var btnResetSkin: Button
    private lateinit var btnDiagnostics: Button
    private lateinit var svDiagnostics: ScrollView
    private lateinit var tvDiagnostics: TextView

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        // CoroutineScope is cancelled manually (no lifecycle-aware scope needed here)
    }

    // ─── Initialization ────────────────────────────────────────────────

    private fun initViews() {
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme)
        tvSkinStatus = findViewById(R.id.tvSkinStatus)
        btnSwitchTheme = findViewById(R.id.btnSwitchTheme)
        btnLoadSkin = findViewById(R.id.btnLoadSkin)
        btnResetSkin = findViewById(R.id.btnResetSkin)
        btnDiagnostics = findViewById(R.id.btnDiagnostics)
        svDiagnostics = findViewById(R.id.svDiagnostics)
        tvDiagnostics = findViewById(R.id.tvDiagnostics)
    }

    private fun setupListeners() {
        btnSwitchTheme.setOnClickListener { toggleTheme() }
        btnLoadSkin.setOnClickListener { showSkinPathDialog() }
        btnResetSkin.setOnClickListener { resetSkinToDefault() }
        btnDiagnostics.setOnClickListener { toggleDiagnostics() }
    }

    // ─── Theme Toggle ──────────────────────────────────────────────────

    /**
     * Toggle between LIGHT and DARK theme mode.
     *
     * This does NOT require a skin APK — it changes the Configuration
     * UI mode so that `values-night/` resources are activated. Views
     * are automatically updated via the engine's View-tree walk.
     */
    private fun toggleTheme() {
        val currentMode = ArsSkinEngine.currentThemeMode
        val newMode = when (currentMode) {
            SkinPackage.ThemeMode.LIGHT -> SkinPackage.ThemeMode.DARK
            SkinPackage.ThemeMode.DARK -> SkinPackage.ThemeMode.LIGHT
        }
        setSkinThemeMode(newMode)
        updateUI()
    }

    // ─── Skin Loading ──────────────────────────────────────────────────

    /**
     * Show a dialog prompting the user for a skin APK path.
     */
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
                if (path.isNotEmpty()) {
                    loadSkinFromPath(path)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * Load a skin from a file path and apply it.
     */
    private fun loadSkinFromPath(path: String) {
        scope.launch {
            val result = switchSkin(path)
            withContext(Dispatchers.Main) {
                when (result) {
                    is SkinResult.Success -> {
                        val skin = ArsSkinEngine.activeSkin
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.skin_loaded, skin?.name ?: "?"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is SkinResult.Error -> {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.skin_load_failed, result.error.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                updateUI()
            }
        }
    }

    /**
     * Reset to the default (host app) resources.
     */
    private fun resetSkinToDefault() {
        scope.launch {
            resetSkin()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.skin_reset_done),
                    Toast.LENGTH_SHORT
                ).show()
                updateUI()
            }
        }
    }

    // ─── Diagnostics ───────────────────────────────────────────────────

    /**
     * Toggle the diagnostics panel visibility and refresh its content.
     */
    private fun toggleDiagnostics() {
        if (svDiagnostics.visibility == View.GONE) {
            refreshDiagnostics()
            svDiagnostics.visibility = View.VISIBLE
            btnDiagnostics.text = "隐藏诊断"
        } else {
            svDiagnostics.visibility = View.GONE
            btnDiagnostics.text = getString(R.string.btn_diagnostics)
        }
    }

    /**
     * Populate the diagnostics panel with engine state.
     */
    private fun refreshDiagnostics() {
        val diag = ArsSkinEngine.getDiagnostics()
        val text = buildString {
            appendLine("═══ ARS Engine Diagnostics ═══")
            appendLine()
            appendLine("Active Skin: ${diag.activeSkinName ?: "(default)"}")
            appendLine("Skin Package: ${diag.activeSkinPackage ?: "N/A"}")
            appendLine("Skin Version: ${if (diag.activeSkinVersion >= 0) diag.activeSkinVersion else "N/A"}")
            appendLine("Theme Mode:  ${diag.themeMode}")
            appendLine()
            appendLine("Registered Views: ${diag.registeredViewCount}")
            appendLine("Alive Views:      ${diag.aliveViewCount}")
            appendLine("Listeners:        ${diag.listenerCount}")
            appendLine("Custom Handlers:  ${diag.registeredAttributeCount}")
            appendLine("Cached Mappings:  ${diag.cachedIdMappings}")
            appendLine("Last Switch:      ${if (diag.lastSwitchDurationMs >= 0) "${diag.lastSwitchDurationMs}ms" else "N/A"}")
            if (diag.lastError != null) {
                appendLine("Last Error:       ${diag.lastError!!.message}")
            }
        }
        tvDiagnostics.text = text
    }

    // ─── UI Update ─────────────────────────────────────────────────────

    /**
     * Refresh all UI elements to reflect the current engine state.
     */
    private fun updateUI() {
        val currentMode = ArsSkinEngine.currentThemeMode
        val modeName = when (currentMode) {
            SkinPackage.ThemeMode.LIGHT -> "浅色 ☀️"
            SkinPackage.ThemeMode.DARK -> "深色 🌙"
        }

        tvCurrentTheme.text = getString(R.string.current_theme, modeName)

        btnSwitchTheme.text = when (currentMode) {
            SkinPackage.ThemeMode.LIGHT -> getString(R.string.switch_to_dark)
            SkinPackage.ThemeMode.DARK -> getString(R.string.switch_to_light)
        }

        val activeSkin = ArsSkinEngine.activeSkin
        tvSkinStatus.text = if (activeSkin != null) {
            getString(R.string.skin_status, "${activeSkin.name} (v${activeSkin.version})")
        } else {
            getString(R.string.skin_status, getString(R.string.no_skin_active))
        }

        if (svDiagnostics.visibility == View.VISIBLE) {
            refreshDiagnostics()
        }
    }

    // ─── Skin Change Callback ──────────────────────────────────────────

    /**
     * Called after the engine has applied skin changes to all Views.
     * Update any non-View UI state here.
     */
    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        updateUI()
    }
}
