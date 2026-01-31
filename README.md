# ARS - Android Resource Switch

基于 Android ResourceOverlay 的极致换肤框架

## 特性

- ✅ 支持深浅色主题切换
- ✅ 支持动态加载皮肤包资源
- ✅ 支持 Android 14 及以上版本
- ✅ 基于 ResourceOverlay 机制实现
- ✅ 简洁的 API 设计
- ✅ 完善的示例应用

## 系统要求

- **最低 SDK 版本**: Android 14 (API Level 34)
- **目标 SDK 版本**: Android 14 (API Level 34)
- **编译 SDK 版本**: Android 14 (API Level 34)

## 快速开始

### 1. 添加依赖

在项目的 `settings.gradle.kts` 中添加：

```kotlin
include(":ars-core")
```

在 app 模块的 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    implementation(project(":ars-core"))
}
```

### 2. 初始化

让你的 Application 继承 `ArsApplication`：

```kotlin
import com.kagawagao.ars.ArsApplication

class MyApplication : ArsApplication() {
    override fun onCreate() {
        super.onCreate()
        // 其他初始化代码
    }
}
```

或者在你的 Application 中手动初始化：

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ArsSkinManager.getInstance(this)
    }
}
```

### 3. 使用换肤功能

#### 方式一：继承 ArsActivity

```kotlin
import com.kagawagao.ars.ArsActivity
import com.kagawagao.ars.ArsSkinManager

class MainActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 切换到深色模式
        switchTheme(ArsSkinManager.ThemeMode.DARK)
        
        // 加载皮肤包
        loadSkin("/path/to/skin.apk")
        
        // 重置为默认皮肤
        resetSkin()
    }
}
```

#### 方式二：手动使用 ArsSkinManager

```kotlin
import com.kagawagao.ars.ArsSkinManager

class MainActivity : AppCompatActivity() {
    private lateinit var skinManager: ArsSkinManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        skinManager = ArsSkinManager.getInstance(this)
        
        // 切换主题
        skinManager.switchThemeMode(ArsSkinManager.ThemeMode.DARK)
        
        // 加载皮肤包
        skinManager.loadSkin("/path/to/skin.apk")
        
        // 重置皮肤
        skinManager.resetToDefault()
    }
}
```

## 核心组件

### ArsSkinManager

皮肤管理器，负责管理皮肤包的加载和主题切换。

**主要方法：**

- `getInstance(context)`: 获取单例实例
- `loadSkin(skinPath)`: 加载皮肤包
- `resetToDefault()`: 重置为默认皮肤
- `switchThemeMode(mode)`: 切换主题模式
- `registerThemeChangeListener(listener)`: 注册主题变化监听器
- `unregisterThemeChangeListener(listener)`: 注销主题变化监听器

### SkinLoader

皮肤加载器，负责从不同来源加载皮肤包。

**主要方法：**

- `loadFromAssets(assetPath, skinName)`: 从 assets 加载皮肤包
- `loadFromExternal(externalPath)`: 从外部路径加载皮肤包
- `getInstalledSkins()`: 获取已安装的皮肤包列表
- `deleteSkin(skinName)`: 删除指定皮肤包
- `clearAllSkins()`: 清空所有皮肤包

### ResourceOverlayHelper

ResourceOverlay 辅助类，提供 Android 14+ 的 OverlayManager API 支持。

**主要方法：**

- `isOverlaySupported()`: 检查是否支持 OverlayManager
- `getOverlayInfos(targetPackage)`: 获取所有的 Overlay 信息
- `enableOverlay(overlayPackage)`: 启用指定的 Overlay
- `disableOverlay(overlayPackage)`: 禁用指定的 Overlay

## 深浅色主题支持

框架支持自动适配系统的深浅色主题，也支持手动切换：

```kotlin
// 切换到深色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.DARK)

// 切换到浅色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.LIGHT)
```

在资源文件中定义深浅色主题资源：

```xml
<!-- res/values/colors.xml -->
<resources>
    <color name="theme_background">#FFFFFFFF</color>
    <color name="theme_text">#FF000000</color>
</resources>

<!-- res/values-night/colors.xml -->
<resources>
    <color name="theme_background">#FF121212</color>
    <color name="theme_text">#FFFFFFFF</color>
</resources>
```

## 动态加载皮肤包

### 创建皮肤包

皮肤包本质上是一个 Android APK 文件，包含需要替换的资源：

1. 创建一个新的 Android 应用项目
2. 在项目中定义需要替换的资源（保持资源名称一致）
3. 编译生成 APK 文件
4. 将 APK 文件作为皮肤包使用

### 加载皮肤包

```kotlin
val skinLoader = SkinLoader(context)

// 从 assets 加载
val skinPath = skinLoader.loadFromAssets("skins/custom_skin.apk", "custom_skin.apk")
skinPath?.let {
    skinManager.loadSkin(it)
}

// 从外部存储加载
val externalSkinPath = skinLoader.loadFromExternal("/sdcard/Download/skin.apk")
externalSkinPath?.let {
    skinManager.loadSkin(it)
}
```

## 示例应用

项目包含一个完整的示例应用，演示了框架的所有功能：

- 深浅色主题切换
- 皮肤包加载
- 主题变化监听

运行示例应用：

```bash
./gradlew :app:installDebug
```

## 架构设计

```
┌─────────────────────────────────────┐
│         Application Layer           │
│   (Activities, Fragments, Views)    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         ARS Core Layer              │
│  ┌──────────────────────────────┐   │
│  │    ArsSkinManager            │   │
│  │  (Singleton Pattern)         │   │
│  └──────────┬───────────────────┘   │
│             │                        │
│  ┌──────────▼───────────────────┐   │
│  │  SkinLoader                  │   │
│  │  (Load from various sources) │   │
│  └──────────────────────────────┘   │
│                                      │
│  ┌──────────────────────────────┐   │
│  │  ResourceOverlayHelper       │   │
│  │  (Android 14+ API)           │   │
│  └──────────────────────────────┘   │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Android Framework Layer        │
│  (ResourceOverlay, AssetManager)    │
└─────────────────────────────────────┘
```

## 技术实现

### ResourceOverlay 机制

ARS 框架基于 Android 的 ResourceOverlay 机制实现：

1. **资源加载**: 通过反射调用 AssetManager 的 `addAssetPath` 方法加载外部 APK 资源
2. **资源替换**: 创建新的 Resources 实例，使用皮肤包的资源替换原有资源
3. **主题切换**: 利用 Android 的 values-night 资源目录支持深浅色主题

### 兼容性保障

- 最低支持 Android 14 (API Level 34)
- 使用 `@RequiresApi` 注解确保 API 级别检查
- 基于标准 Android API，不依赖隐藏 API

## 注意事项

1. **Android 版本**: 框架仅支持 Android 14 及以上版本
2. **资源命名**: 皮肤包中的资源名称必须与原应用保持一致
3. **性能考虑**: 皮肤切换会触发 Activity 重建，注意保存状态
4. **权限要求**: 从外部存储加载皮肤包需要存储权限

## License

MIT License

Copyright (c) 2024 kagawagao

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
