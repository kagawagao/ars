package com.kagawagao.ars.demo

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.kagawagao.ars.ArsActivity
import com.kagawagao.ars.ArsSkinManager

/**
 * 主界面 - 演示 ARS 框架的使用
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class MainActivity : ArsActivity() {
    
    private lateinit var tvCurrentTheme: TextView
    private lateinit var btnSwitchTheme: Button
    private lateinit var btnLoadSkin: Button
    private lateinit var btnResetSkin: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupListeners()
        updateThemeDisplay()
    }
    
    private fun initViews() {
        tvCurrentTheme = findViewById(R.id.tvCurrentTheme)
        btnSwitchTheme = findViewById(R.id.btnSwitchTheme)
        btnLoadSkin = findViewById(R.id.btnLoadSkin)
        btnResetSkin = findViewById(R.id.btnResetSkin)
    }
    
    private fun setupListeners() {
        btnSwitchTheme.setOnClickListener {
            val skinManager = ArsSkinManager.getInstance(this)
            val newMode = if (skinManager.currentThemeMode == ArsSkinManager.ThemeMode.LIGHT) {
                ArsSkinManager.ThemeMode.DARK
            } else {
                ArsSkinManager.ThemeMode.LIGHT
            }
            switchTheme(newMode)
        }
        
        btnLoadSkin.setOnClickListener {
            // 这里可以从文件选择器加载皮肤包
            // 目前仅演示API调用
            Toast.makeText(this, "皮肤加载功能需要外部皮肤包", Toast.LENGTH_SHORT).show()
        }
        
        btnResetSkin.setOnClickListener {
            resetSkin()
            Toast.makeText(this, "已重置为默认皮肤", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateThemeDisplay() {
        val skinManager = ArsSkinManager.getInstance(this)
        val themeName = if (skinManager.currentThemeMode == ArsSkinManager.ThemeMode.LIGHT) {
            "浅色"
        } else {
            "深色"
        }
        tvCurrentTheme.text = getString(R.string.current_theme, themeName)
        
        btnSwitchTheme.text = if (skinManager.currentThemeMode == ArsSkinManager.ThemeMode.LIGHT) {
            getString(R.string.switch_to_dark)
        } else {
            getString(R.string.switch_to_light)
        }
    }
    
    override fun onThemeChanged(mode: ArsSkinManager.ThemeMode) {
        super.onThemeChanged(mode)
        // 主题改变后更新显示
        updateThemeDisplay()
    }
}
