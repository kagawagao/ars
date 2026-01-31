# ARS Framework API 文档

## 核心类

### ArsSkinManager

皮肤管理器单例类，负责管理整个换肤流程。

#### 获取实例

```kotlin
val skinManager = ArsSkinManager.getInstance(context)
```

#### 方法

##### loadSkin

加载皮肤包。

```kotlin
fun loadSkin(skinPath: String): Boolean
```

**参数**:
- `skinPath`: 皮肤包 APK 文件的绝对路径

**返回值**:
- `true`: 加载成功
- `false`: 加载失败

**示例**:
```kotlin
val success = skinManager.loadSkin("/data/data/com.example.app/files/skins/custom.apk")
if (success) {
    Toast.makeText(this, "皮肤加载成功", Toast.LENGTH_SHORT).show()
}
```

##### resetToDefault

重置为默认皮肤。

```kotlin
fun resetToDefault()
```

**示例**:
```kotlin
skinManager.resetToDefault()
```

##### switchThemeMode

切换主题模式（深色/浅色）。

```kotlin
fun switchThemeMode(mode: ThemeMode)
```

**参数**:
- `mode`: 主题模式，可选值：
  - `ThemeMode.LIGHT`: 浅色模式
  - `ThemeMode.DARK`: 深色模式

**示例**:
```kotlin
// 切换到深色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.DARK)

// 切换到浅色模式
skinManager.switchThemeMode(ArsSkinManager.ThemeMode.LIGHT)
```

##### getResourceId

获取资源 ID。

```kotlin
fun getResourceId(resName: String, defType: String): Int
```

**参数**:
- `resName`: 资源名称
- `defType`: 资源类型（如 "color"、"drawable"、"layout" 等）

**返回值**:
- 资源 ID，如果资源不存在则返回 0

**示例**:
```kotlin
val colorId = skinManager.getResourceId("primary_color", "color")
if (colorId != 0) {
    val color = skinManager.getColor(colorId)
}
```

##### getColor

获取颜色资源值。

```kotlin
fun getColor(resId: Int): Int
```

**参数**:
- `resId`: 颜色资源 ID

**返回值**:
- 颜色值（ARGB 格式）

**示例**:
```kotlin
val color = skinManager.getColor(R.color.primary)
view.setBackgroundColor(color)
```

##### getTargetResources

获取当前使用的 Resources 对象。

```kotlin
fun getTargetResources(): Resources
```

**返回值**:
- 如果已加载皮肤包，返回皮肤包的 Resources
- 否则返回应用默认的 Resources

**示例**:
```kotlin
val resources = skinManager.getTargetResources()
val drawable = resources.getDrawable(R.drawable.icon, theme)
```

##### registerThemeChangeListener

注册主题变化监听器。

```kotlin
fun registerThemeChangeListener(listener: ThemeChangeListener)
```

**参数**:
- `listener`: 主题变化监听器实例

**示例**:
```kotlin
class MyActivity : AppCompatActivity(), ArsSkinManager.ThemeChangeListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ArsSkinManager.getInstance(this).registerThemeChangeListener(this)
    }
    
    override fun onThemeChanged(mode: ArsSkinManager.ThemeMode) {
        // 处理主题变化
    }
}
```

##### unregisterThemeChangeListener

注销主题变化监听器。

```kotlin
fun unregisterThemeChangeListener(listener: ThemeChangeListener)
```

**参数**:
- `listener`: 要注销的监听器实例

**示例**:
```kotlin
override fun onDestroy() {
    super.onDestroy()
    ArsSkinManager.getInstance(this).unregisterThemeChangeListener(this)
}
```

##### applySkin

应用皮肤到 Activity。

```kotlin
fun applySkin(activity: Activity)
```

**参数**:
- `activity`: 要应用皮肤的 Activity

**示例**:
```kotlin
skinManager.applySkin(this)
```

#### 属性

##### currentThemeMode

获取当前主题模式（只读）。

```kotlin
val currentThemeMode: ThemeMode
```

**示例**:
```kotlin
val mode = skinManager.currentThemeMode
when (mode) {
    ArsSkinManager.ThemeMode.LIGHT -> {
        // 当前是浅色模式
    }
    ArsSkinManager.ThemeMode.DARK -> {
        // 当前是深色模式
    }
}
```

#### 嵌套类型

##### ThemeMode

主题模式枚举。

```kotlin
enum class ThemeMode {
    LIGHT,  // 浅色模式
    DARK    // 深色模式
}
```

##### ThemeChangeListener

主题变化监听器接口。

```kotlin
interface ThemeChangeListener {
    fun onThemeChanged(mode: ThemeMode)
}
```

---

### SkinLoader

皮肤加载器，负责从不同来源加载皮肤包。

#### 构造函数

```kotlin
class SkinLoader(context: Context)
```

**参数**:
- `context`: Android Context 对象

#### 方法

##### loadFromAssets

从 assets 目录加载皮肤包。

```kotlin
fun loadFromAssets(assetPath: String, skinName: String): String?
```

**参数**:
- `assetPath`: assets 中的皮肤包路径
- `skinName`: 保存到本地的文件名

**返回值**:
- 成功：本地皮肤包文件的绝对路径
- 失败：null

**示例**:
```kotlin
val skinLoader = SkinLoader(context)
val skinPath = skinLoader.loadFromAssets("skins/red_theme.apk", "red_theme.apk")
skinPath?.let {
    ArsSkinManager.getInstance(context).loadSkin(it)
}
```

##### loadFromExternal

从外部存储加载皮肤包。

```kotlin
fun loadFromExternal(externalPath: String): String?
```

**参数**:
- `externalPath`: 外部皮肤包文件的绝对路径

**返回值**:
- 成功：皮肤包文件路径
- 失败：null

**示例**:
```kotlin
val skinLoader = SkinLoader(context)
val skinPath = skinLoader.loadFromExternal("/sdcard/Download/skin.apk")
skinPath?.let {
    ArsSkinManager.getInstance(context).loadSkin(it)
}
```

##### getInstalledSkins

获取已安装的皮肤包列表。

```kotlin
fun getInstalledSkins(): List<File>
```

**返回值**:
- 皮肤包文件列表

**示例**:
```kotlin
val skinLoader = SkinLoader(context)
val skins = skinLoader.getInstalledSkins()
skins.forEach { skinFile ->
    Log.d("Skin", "Found skin: ${skinFile.name}")
}
```

##### deleteSkin

删除指定的皮肤包。

```kotlin
fun deleteSkin(skinName: String): Boolean
```

**参数**:
- `skinName`: 皮肤包文件名

**返回值**:
- `true`: 删除成功
- `false`: 删除失败

**示例**:
```kotlin
val skinLoader = SkinLoader(context)
val success = skinLoader.deleteSkin("red_theme.apk")
```

##### clearAllSkins

清空所有皮肤包。

```kotlin
fun clearAllSkins()
```

**示例**:
```kotlin
val skinLoader = SkinLoader(context)
skinLoader.clearAllSkins()
```

---

### ResourceOverlayHelper

ResourceOverlay 辅助类，提供 Android 14+ 的 OverlayManager API 支持。

#### 构造函数

```kotlin
class ResourceOverlayHelper(context: Context)
```

**参数**:
- `context`: Android Context 对象

#### 方法

##### isOverlaySupported

检查是否支持 OverlayManager。

```kotlin
fun isOverlaySupported(): Boolean
```

**返回值**:
- `true`: 系统支持 OverlayManager
- `false`: 系统不支持

**示例**:
```kotlin
val overlayHelper = ResourceOverlayHelper(context)
if (overlayHelper.isOverlaySupported()) {
    // 使用 Overlay 功能
}
```

##### getOverlayInfos

获取所有的 Overlay 信息。

```kotlin
fun getOverlayInfos(targetPackage: String): List<OverlayInfo>?
```

**参数**:
- `targetPackage`: 目标包名

**返回值**:
- 成功：Overlay 信息列表
- 失败：null

**示例**:
```kotlin
val overlayHelper = ResourceOverlayHelper(context)
val overlays = overlayHelper.getOverlayInfos(packageName)
overlays?.forEach { overlay ->
    Log.d("Overlay", "Package: ${overlay.packageName}, Enabled: ${overlay.isEnabled}")
}
```

##### enableOverlay

启用指定的 Overlay。

```kotlin
fun enableOverlay(overlayPackage: String): Boolean
```

**参数**:
- `overlayPackage`: Overlay 包名

**返回值**:
- `true`: 启用成功
- `false`: 启用失败

**示例**:
```kotlin
val overlayHelper = ResourceOverlayHelper(context)
val success = overlayHelper.enableOverlay("com.example.skin.overlay")
```

##### disableOverlay

禁用指定的 Overlay。

```kotlin
fun disableOverlay(overlayPackage: String): Boolean
```

**参数**:
- `overlayPackage`: Overlay 包名

**返回值**:
- `true`: 禁用成功
- `false`: 禁用失败

**示例**:
```kotlin
val overlayHelper = ResourceOverlayHelper(context)
val success = overlayHelper.disableOverlay("com.example.skin.overlay")
```

##### getOverlayInfo

获取 Overlay 的状态信息。

```kotlin
fun getOverlayInfo(overlayPackage: String): OverlayInfo?
```

**参数**:
- `overlayPackage`: Overlay 包名

**返回值**:
- 成功：Overlay 信息
- 失败：null

**示例**:
```kotlin
val overlayHelper = ResourceOverlayHelper(context)
val info = overlayHelper.getOverlayInfo("com.example.skin.overlay")
info?.let {
    Log.d("Overlay", "Enabled: ${it.isEnabled}, State: ${it.state}")
}
```

---

### ArsApplication

ARS Application 基类，提供自动初始化功能。

#### 使用方式

```kotlin
class MyApplication : ArsApplication() {
    override fun onCreate() {
        super.onCreate()
        // 其他初始化代码
    }
}
```

**注意**:
- 需要在 AndroidManifest.xml 中声明
- 会自动初始化 ArsSkinManager

---

### ArsActivity

ARS Activity 基类，提供自动换肤支持。

#### 使用方式

```kotlin
class MyActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 使用换肤功能
        loadSkin(skinPath)
        resetSkin()
        switchTheme(ThemeMode.DARK)
    }
    
    override fun onThemeChanged(mode: ArsSkinManager.ThemeMode) {
        super.onThemeChanged(mode)
        // 自定义主题变化处理
    }
}
```

#### 方法

##### loadSkin

加载皮肤包（受保护方法）。

```kotlin
protected fun loadSkin(skinPath: String): Boolean
```

##### resetSkin

重置为默认皮肤（受保护方法）。

```kotlin
protected fun resetSkin()
```

##### switchTheme

切换主题模式（受保护方法）。

```kotlin
protected fun switchTheme(mode: ArsSkinManager.ThemeMode)
```

---

### SkinAttribute

皮肤属性管理类，负责解析和应用 View 的可换肤属性。

#### 构造函数

```kotlin
class SkinAttribute(context: Context)
```

#### 方法

##### applySkin

应用皮肤到视图。

```kotlin
fun applySkin(view: View, attrs: AttributeSet?)
```

**参数**:
- `view`: 目标视图
- `attrs`: 属性集

**支持的属性**:
- `background`: 背景资源
- `src`: ImageView 图片源
- `textColor`: TextView 文字颜色
- `drawableLeft/Top/Right/Bottom`: TextView drawable

---

## 最佳实践

### 1. 初始化

在 Application 中初始化：

```kotlin
class MyApp : ArsApplication() {
    override fun onCreate() {
        super.onCreate()
        // ArsSkinManager 已自动初始化
    }
}
```

### 2. 加载皮肤

```kotlin
// 在合适的时机加载皮肤
val skinLoader = SkinLoader(context)
val skinPath = skinLoader.loadFromAssets("skins/theme.apk", "theme.apk")
skinPath?.let {
    ArsSkinManager.getInstance(context).loadSkin(it)
}
```

### 3. 监听主题变化

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
    }
}
```

### 4. 资源定义

在皮肤包中保持资源名称一致：

```xml
<!-- 主应用 res/values/colors.xml -->
<resources>
    <color name="primary">#6200EE</color>
    <color name="background">#FFFFFF</color>
</resources>

<!-- 皮肤包 res/values/colors.xml -->
<resources>
    <color name="primary">#FF0000</color>
    <color name="background">#FFF5F5</color>
</resources>
```

---

## 注意事项

1. **API 级别**: 确保目标设备运行 Android 14 或更高版本
2. **权限**: 从外部存储加载皮肤包需要存储权限
3. **资源命名**: 皮肤包中的资源名称必须与主应用保持一致
4. **性能**: 主题切换会触发 Activity 重建，注意保存状态
5. **内存**: 注意及时注销监听器，避免内存泄漏
