package com.kagawagao.ars

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream

/**
 * 皮肤加载器 - 负责加载和管理皮肤包文件
 * 
 * 支持从以下位置加载皮肤包:
 * 1. 应用内置皮肤包（assets）
 * 2. 外部存储的皮肤包文件
 * 3. 网络下载的皮肤包
 */
@RequiresApi(34)
class SkinLoader(private val context: Context) {
    
    companion object {
        private const val SKIN_DIR = "skins"
    }
    
    private val skinDir: File by lazy {
        File(context.filesDir, SKIN_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 从 assets 加载皮肤包
     * 
     * @param assetPath assets 中的皮肤包路径
     * @param skinName 皮肤包名称
     * @return 皮肤包文件路径，失败返回 null
     */
    fun loadFromAssets(assetPath: String, skinName: String): String? {
        return try {
            val destFile = File(skinDir, skinName)
            
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从外部路径加载皮肤包
     * 
     * @param externalPath 外部皮肤包文件路径
     * @return 皮肤包文件路径，失败返回 null
     */
    fun loadFromExternal(externalPath: String): String? {
        val file = File(externalPath)
        return if (file.exists() && file.isFile) {
            file.absolutePath
        } else {
            null
        }
    }
    
    /**
     * 获取已安装的皮肤包列表
     * 
     * @return 皮肤包文件列表
     */
    fun getInstalledSkins(): List<File> {
        return skinDir.listFiles()?.filter { it.extension == "apk" } ?: emptyList()
    }
    
    /**
     * 删除皮肤包
     * 
     * @param skinName 皮肤包名称
     * @return 是否删除成功
     */
    fun deleteSkin(skinName: String): Boolean {
        val file = File(skinDir, skinName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    /**
     * 清空所有皮肤包
     */
    fun clearAllSkins() {
        skinDir.listFiles()?.forEach { it.delete() }
    }
}
