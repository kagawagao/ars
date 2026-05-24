# ARS V2 — Getting Started

**5 分钟将 ARS 换肤框架接入你的 Android 应用。**

## 前提条件

- Android 14+ (API 34)
- Kotlin 1.9+
- Gradle 8.2+

---

## 1. 集成（2 步）

### Step 1: 添加依赖

`settings.gradle.kts`:
```kotlin
include(":ars-core")
```

`app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":ars-core"))
}
```

### Step 2: 继承基类

```kotlin
// Application
class MyApp : ArsApplication()

// Activity
class MainActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}

// Fragment
class MyFragment : ArsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_my, container, false)
    }
}

// DialogFragment
class MyDialog : ArsDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_my, container, false)
    }
}
```

**完成。** 所有 View 的资源查询（`getResources().getColor()` 等）现在都是换肤感知的。

---

## 2. 深浅色主题切换

无需皮肤包即可切换：

```kotlin
class MainActivity : ArsActivity() {
    fun toggleTheme() {
        val newMode = when (ArsSkinEngine.currentThemeMode) {
            SkinPackage.ThemeMode.LIGHT -> SkinPackage.ThemeMode.DARK
            SkinPackage.ThemeMode.DARK -> SkinPackage.ThemeMode.LIGHT
        }
        setSkinThemeMode(newMode)
    }
}
```

在 `res/values/colors.xml` 和 `res/values-night/colors.xml` 中定义颜色资源，框架会自动切换。

---

## 3. 加载皮肤包

### 从文件加载

```kotlin
class MainActivity : ArsActivity() {
    fun loadSkin() {
        // switchSkin 是 suspend 函数，在后台线程执行 I/O
        lifecycleScope.launch {
            val result = switchSkin("/sdcard/Download/skin.apk")
            when (result) {
                is SkinResult.Success -> showToast("皮肤已加载")
                is SkinResult.Error -> showToast("加载失败: ${result.error.message}")
            }
        }
    }
}
```

### 从网络下载

```kotlin
lifecycleScope.launch {
    val url = "https://cdn.example.com/skins/holiday.apk"
    val result = ArsSkinEngine.switchSkin(url) // 自动下载 + 校验 + 加载
    // ... 处理结果
}
```

### 重置为默认

```kotlin
lifecycleScope.launch {
    resetSkin()  // 恢复宿主应用原生资源
}
```

---

## 4. 创建皮肤包

皮肤包是仅含资源的 Android APK。

### 结构

```
skin.apk
├── AndroidManifest.xml    ← 必须包含 ARS 元数据
├── resources.arsc
└── res/
    ├── values/
    │   └── colors.xml
    └── values-night/
        └── colors.xml
```

### AndroidManifest.xml 元数据

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.skin.holiday">

    <application>
        <meta-data
            android:name="ars-skin-name"
            android:value="Holiday Theme" />
        <meta-data
            android:name="ars-skin-version"
            android:value="1" />
        <meta-data
            android:name="ars-target-package"
            android:value="com.example.myapp" />
    </application>
</manifest>
```

**资源名称必须与宿主应用完全一致。** ARS 通过名称匹配（而非 ID）查找皮肤资源。

---

## 5. 高级用法

### 自定义属性处理器

```kotlin
// 在 Application.onCreate() 中注册
ArsSkinEngine.registerAttributeHandler("cornerRadius") { view, resId, resources ->
    (view as? MyCustomView)?.cornerRadius = resources.getDimension(resId)
}
```

### 内置支持属性（13 个）

| 属性 | 目标 View | 说明 |
|------|-----------|------|
| `background` | View | 背景色/图 |
| `textColor` | TextView | 文字颜色 |
| `textColorHint` | TextView | 提示文字颜色 |
| `textSize` | TextView | 文字大小 |
| `src` | ImageView | 图片源 |
| `tint` | ImageView | 图片着色 |
| `progressTint` | ProgressBar | 进度条着色 |
| `thumbTint` | SeekBar | 滑块着色 |
| `buttonTint` | CompoundButton | 按钮着色 |
| `drawableStart` | TextView | 左侧图标(RTL) |
| `drawableEnd` | TextView | 右侧图标(RTL) |
| `drawableTop` | TextView | 顶部图标 |
| `drawableBottom` | TextView | 底部图标 |

### 排除特定 View

```xml
<ImageView
    android:tag="@id/ars_skip_skinning"
    android:src="@drawable/logo" />
```

`ars_skip_skinning` 标签会排除该 View 及其全部子 View。

### 皮肤预览

```kotlin
// 临时应用皮肤，不持久化选择
ArsSkinEngine.previewSkin("/path/to/skin.apk")

// 用户点击「撤销」时恢复
ArsSkinEngine.cancelPreview()
```

### 引擎诊断

```kotlin
val diag = ArsSkinEngine.getDiagnostics()
Log.d("ARS", """
    活跃皮肤: ${diag.activeSkinName}
    主题模式: ${diag.themeMode}
    已注册View: ${diag.registeredViewCount}
    上次切换耗时: ${diag.lastSwitchDurationMs}ms
""".trimIndent())
```

### 监听皮肤切换

```kotlin
ArsSkinEngine.registerSkinChangeListener { previous, current ->
    // 所有 View 已更新完毕，在这里做额外处理
    Log.d("ARS", "皮肤从 ${previous?.name} 切换到 ${current?.name}")
}
```

---

## 6. 全部 UI 模式支持

ARS 覆盖 Android 全部 UI 呈现模式。**17 种自动 + 4 种半自动 + 明确的不支持说明**：

| 类别 | 模式 | 支持方式 |
|------|------|----------|
| 🟢 自动 | Activity / Fragment / DialogFragment / BottomSheet | 继承对应基类 |
| 🟢 自动 | Dialog / AlertDialog / PopupWindow | `ArsDialog` / `ArsPopupWindow` |
| 🟢 自动 | Snackbar | `ArsOverlaySkin.autoRefresh()` |
| 🟢 自动 | RecyclerView / ViewPager / ViewStub / `<include>` / DataBinding / ViewBinding / 自定义View | 零配置自动 |
| 🟡 半自动 | Toast / Spinner下拉 / 动态添加View | 一行包装代码 |
| 🔴 不支持 | Notification / AppWidget / WebView / SurfaceView / Compose | 独立进程/渲染管线，含替代方案 |

**详见：[全部 UI 模式支持矩阵](docs/ui-patterns.md)** — 24 种模式完整代码示例、快速决策指南。

### AlertDialog

```kotlin
// 方式一：ArsDialog（推荐）
class MyDialog(context: Context) : ArsDialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_my)
    }
}

// 方式二：wrapContext
val ctx = ArsSkinEngine.wrapContext(requireContext())
val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_content, null)
AlertDialog.Builder(ctx).setView(view).show()
```

### PopupWindow

```kotlin
// 方式一：ArsPopupWindow（推荐，自动刷新）
val popup = ArsPopupWindow(requireContext()).apply {
    contentView = LayoutInflater.from(skinContext).inflate(R.layout.popup_menu, null)
    showAsDropDown(anchor)
}
```

### Snackbar

```kotlin
val snackbar = Snackbar.make(view, "消息", Snackbar.LENGTH_SHORT).apply { show() }
// 皮肤切换后手动刷新 Snackbar 内容
val listener = ArsOverlaySkin.autoRefresh(snackbar.view)
// dismiss 时记得: ArsSkinEngine.unregisterSkinChangeListener(listener)
```

### Toast

```kotlin
ArsToast.showText(this, "操作成功", Toast.LENGTH_SHORT)
```

### Spinner 下拉

```kotlin
val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
spinner.adapter = ArsSpinnerAdapter.wrap(adapter, this)
```

### 动态添加 View

```kotlin
val ctx = ArsSkinEngine.wrapContext(this)
val button = Button(ctx)  // 资源查询自动使用皮肤
parentLayout.addView(button)
refreshSkin()  // 如果之后切换了皮肤
```

---

## 7. 常见问题

### Q: 皮肤不生效？
A: 检查：
1. 皮肤 APK 资源名称是否与宿主完全一致
2. `ars-target-package` 元数据是否匹配宿主包名
3. `ars-skin-version` 是否 = 1（当前框架版本）
4. 调用 `getDiagnostics()` 查看 lastError

### Q: 部分 View 不更新？
A: 确保：
1. 属性在 XML 中引用了 `@color/` 或 `@drawable/` 资源（不是字面量 `#FF0000`）
2. View 没有被 `ars_skip_skinning` 排除
3. 动态创建的 View 调用了 `refreshSkin()`

### Q: 切换皮肤时卡顿？
A: `getDiagnostics().lastSwitchDurationMs` 查看耗时。如果 >500ms：
1. 检查是否注册了过多无用的 View（考虑给不换肤的 View 加 `ars_skip_skinning`）
2. 大量 View 时考虑分批加载

### Q: 支持 Compose 吗？
A: V2 仅支持 View 体系。Compose 使用完全不同的渲染管线，不在此版本范围内。

---

## 项目结构速查

```
ars-core/
├── ArsApplication.kt      ← 继承此类
├── ArsActivity.kt         ← 继承此类
├── ArsFragment.kt         ← 继承此类
├── ArsDialogFragment.kt   ← DialogFragment 继承此类
├── ArsDialog.kt           ← 手动 Dialog 继承此类
├── ArsPopupWindow.kt      ← PopupWindow 继承此类
├── ArsToast.kt            ← Toast 换肤工具
├── ArsSnackbar.kt         ← Snackbar/Overlay 换肤工具
├── ArsSpinnerAdapter.kt   ← Spinner 下拉换肤适配器
├── ArsSkinEngine.kt       ← 核心引擎（单例）
├── SkinPackage.kt         ← 皮肤包数据类
├── SkinResult.kt          ← 操作结果包装
├── SkinError.kt           ← 错误类型（8种）
├── SkinDiagnostics.kt     ← 诊断数据
├── SkinChangeListener.kt  ← 皮肤切换监听器
├── SkinAttributeHandler.kt ← 自定义属性处理器
└── internal/              ← 内部实现（不导入）
```

---

**更多文档：**
- [API 参考](API.md)
- [实现细节](IMPLEMENTATION.md)
- [需求规格](docs/requirements-v2.md)
- [架构设计](docs/architecture-v2.md)
