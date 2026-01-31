# ARS Framework 实现细节

## 技术架构

### 1. ResourceOverlay 机制

ARS 框架基于 Android 的 ResourceOverlay 机制实现动态换肤功能。ResourceOverlay 是 Android 系统提供的一种资源覆盖机制，允许在运行时替换应用的资源。

#### 核心原理

```kotlin
// 1. 创建 AssetManager 实例
val assetManager = AssetManager::class.java.newInstance()

// 2. 添加皮肤包路径到 AssetManager
val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
addAssetPath.invoke(assetManager, skinPath)

// 3. 创建新的 Resources 实例
val skinResources = Resources(
    assetManager,
    context.resources.displayMetrics,
    context.resources.configuration
)
```

### 2. 深浅色主题切换

框架支持两种方式的深浅色主题切换：

#### 方式一：系统主题适配

利用 Android 的 `values-night` 资源目录自动适配：

```
res/
  values/
    colors.xml        # 浅色主题颜色
  values-night/
    colors.xml        # 深色主题颜色
```

#### 方式二：程序化切换

通过 `ArsSkinManager.switchThemeMode()` 方法手动切换主题：

```kotlin
// 切换到深色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.DARK)

// 切换到浅色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.LIGHT)
```

### 3. 动态加载皮肤包

#### 皮肤包结构

皮肤包本质上是一个标准的 Android APK 文件，包含需要替换的资源：

```
skin.apk
├── AndroidManifest.xml
├── resources.arsc
└── res/
    ├── drawable/
    ├── layout/
    ├── values/
    │   ├── colors.xml
    │   └── strings.xml
    └── values-night/
        └── colors.xml
```

#### 加载流程

```
1. SkinLoader 加载皮肤包文件
   ↓
2. 验证皮肤包有效性（PackageManager.getPackageArchiveInfo）
   ↓
3. 通过反射创建 AssetManager 并添加皮肤包路径
   ↓
4. 创建新的 Resources 实例
   ↓
5. 通知主题变化监听器
   ↓
6. Activity 重建应用新资源
```

### 4. Android 14+ 兼容性

框架针对 Android 14 (API Level 34) 进行了优化：

#### OverlayManager API

在 Android 14+ 上，框架使用官方的 `OverlayManager` API：

```kotlin
val overlayManager = context.getSystemService(Context.OVERLAY_SERVICE) as OverlayManager

// 启用 Overlay
overlayManager.setEnabled(overlayPackage, true, 0)

// 获取 Overlay 信息
val overlayInfos = overlayManager.getOverlayInfosForTarget(targetPackage, 0)
```

#### 权限要求

由于使用了系统级 API，需要特定的权限：

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 关键组件详解

### ArsSkinManager

**职责**：
- 单例模式管理整个换肤流程
- 维护当前皮肤状态和主题模式
- 管理主题变化监听器
- 提供资源查询和获取接口

**核心方法**：

```kotlin
class ArsSkinManager {
    // 加载皮肤包
    fun loadSkin(skinPath: String): Boolean
    
    // 重置为默认皮肤
    fun resetToDefault()
    
    // 切换主题模式
    fun switchThemeMode(mode: ThemeMode)
    
    // 获取资源
    fun getColor(resId: Int): Int
    fun getTargetResources(): Resources
    
    // 监听器管理
    fun registerThemeChangeListener(listener: ThemeChangeListener)
    fun unregisterThemeChangeListener(listener: ThemeChangeListener)
}
```

### SkinLoader

**职责**：
- 从不同来源加载皮肤包文件
- 管理本地皮肤包存储
- 提供皮肤包查询和删除功能

**支持的加载源**：

1. **Assets 目录**：应用内置皮肤包
2. **外部存储**：用户自定义皮肤包
3. **网络下载**：远程皮肤包（需要额外实现）

### ResourceOverlayHelper

**职责**：
- 封装 Android 14+ 的 OverlayManager API
- 提供 Overlay 启用/禁用功能
- 查询 Overlay 状态和信息

**使用场景**：
- 系统级皮肤切换
- 多 Overlay 管理
- Overlay 状态监控

### SkinAttribute

**职责**：
- 解析 View 的可换肤属性
- 应用皮肤资源到具体 View
- 支持常见属性的动态更新

**支持的属性**：
- `background`: 背景资源
- `src`: ImageView 图片源
- `textColor`: 文字颜色
- `drawable*`: 各方向的 drawable

## 使用示例

### 示例 1：基础换肤

```kotlin
class MyActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 加载皮肤
        btnLoadSkin.setOnClickListener {
            val skinLoader = SkinLoader(this)
            val skinPath = skinLoader.loadFromAssets("skins/red_theme.apk", "red_theme.apk")
            skinPath?.let { loadSkin(it) }
        }
        
        // 重置皮肤
        btnResetSkin.setOnClickListener {
            resetSkin()
        }
    }
}
```

### 示例 2：主题切换

```kotlin
class SettingsActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) {
                ArsSkinManager.ThemeMode.DARK
            } else {
                ArsSkinManager.ThemeMode.LIGHT
            }
            switchTheme(mode)
        }
    }
}
```

### 示例 3：监听主题变化

```kotlin
class MyFragment : Fragment(), ArsSkinManager.ThemeChangeListener {
    private lateinit var skinManager: ArsSkinManager
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        skinManager = ArsSkinManager.getInstance(requireContext())
        skinManager.registerThemeChangeListener(this)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        skinManager.unregisterThemeChangeListener(this)
    }
    
    override fun onThemeChanged(mode: ArsSkinManager.ThemeMode) {
        // 更新 UI
        updateUIForTheme(mode)
    }
    
    private fun updateUIForTheme(mode: ArsSkinManager.ThemeMode) {
        when (mode) {
            ArsSkinManager.ThemeMode.DARK -> {
                // 应用深色主题样式
            }
            ArsSkinManager.ThemeMode.LIGHT -> {
                // 应用浅色主题样式
            }
        }
    }
}
```

## 性能优化

### 1. 资源缓存

框架使用单例模式缓存已加载的资源，避免重复加载：

```kotlin
private var skinResources: Resources? = null
private var skinPackageName: String? = null
```

### 2. 弱引用监听器

使用 `WeakReference` 管理监听器，避免内存泄漏：

```kotlin
private val themeListeners = mutableListOf<WeakReference<ThemeChangeListener>>()
```

### 3. 懒加载

组件采用懒加载策略，减少启动时间：

```kotlin
private val skinDir: File by lazy {
    File(context.filesDir, SKIN_DIR).apply {
        if (!exists()) mkdirs()
    }
}
```

## 安全考虑

### 1. 皮肤包验证

在加载皮肤包前，验证包的有效性：

```kotlin
val packageInfo: PackageInfo = packageManager.getPackageArchiveInfo(
    skinPath,
    PackageManager.GET_ACTIVITIES
) ?: return false
```

### 2. 异常处理

所有资源操作都包含异常处理，确保应用稳定性：

```kotlin
return try {
    // 加载逻辑
    true
} catch (e: Exception) {
    e.printStackTrace()
    false
}
```

### 3. 权限检查

访问外部存储前检查权限：

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## 限制和注意事项

### 1. API 级别限制

- **最低支持**: Android 14 (API Level 34)
- **原因**: 使用了 Android 14 引入的新 API

### 2. 性能影响

- 主题切换会触发 Activity 重建
- 建议在切换前保存 Activity 状态

### 3. 资源命名约定

- 皮肤包中的资源名称必须与原应用保持一致
- 不支持新增资源，只支持替换现有资源

### 4. 反射使用

- 使用反射访问 AssetManager 的 `addAssetPath` 方法
- 在某些 ROM 上可能受到限制

## 未来扩展

### 计划功能

1. **插件化支持**：支持插件式皮肤包加载
2. **网络皮肤包**：支持从服务器下载皮肤包
3. **皮肤包加密**：支持加密皮肤包保护版权
4. **增量更新**：支持皮肤包的增量更新
5. **预览功能**：加载前预览皮肤效果

### 性能优化方向

1. **异步加载**：在后台线程加载皮肤包
2. **资源预加载**：启动时预加载常用资源
3. **智能缓存**：根据使用频率缓存资源

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件
