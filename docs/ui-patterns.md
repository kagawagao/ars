# ARS 全部 UI 模式换肤支持矩阵

完整覆盖 Android 平台上一切 UI 呈现模式的换肤方案。每种模式配有**完整可运行的代码示例**。

---

## 模式总览

| # | 模式 | 支持级别 | 基类/方法 | 皮肤切换时自动刷新 |
|---|------|----------|-----------|:---:|
| 1 | Activity | 🟢 自动 | `ArsActivity` | ✅ |
| 2 | Fragment | 🟢 自动 | `ArsFragment` | ✅ |
| 3 | DialogFragment | 🟢 自动 | `ArsDialogFragment` | ✅ |
| 4 | BottomSheetDialogFragment | 🟢 自动 | `ArsDialogFragment` | ✅ |
| 5 | Dialog（手动） | 🟢 自动 | `ArsDialog` | ✅ |
| 6 | AlertDialog | 🟢 自动 | `ArsDialog` / `createSkinnedAlertDialog()` | ✅ |
| 7 | PopupWindow | 🟢 自动 | `ArsPopupWindow` | ✅ |
| 8 | Toast | 🟡 半自动 | `ArsToast.showText()` | ❌ |
| 9 | Snackbar | 🟢 自动 | `ArsSnackbar.make().showSkinned()` | ✅ |
| 10 | RecyclerView | 🟢 自动 | 无（自动） | ✅ |
| 11 | ViewPager / ViewPager2 | 🟢 自动 | 无（`ArsFragment`） | ✅ |
| 12 | ViewStub | 🟢 自动 | 无（自动） | ✅ |
| 13 | `<include>` / `<merge>` | 🟢 自动 | 无（标准 LayoutInflater） | ✅ |
| 14 | DataBinding / ViewBinding | 🟢 自动 | 无（使用 LayoutInflater） | ✅ |
| 15 | Navigation Component | 🟢 自动 | 无（Fragment 基类） | ✅ |
| 16 | Spinner / AutoCompleteTextView 下拉 | 🟡 半自动 | `ArsSpinnerAdapter.wrap()` | 手动 |
| 17 | 自定义 View（XML inflate） | 🟢 自动 | 无（`SkinLayoutInflater`） | ✅ |
| 18 | 自定义 View（代码 new） | 🟢 自动 | 无（`SkinResources`） | ✅ |
| 19 | 动态添加 View | 🟢 自动 | `SkinLayoutInflater` / `refreshSkin()` | ✅ |
| 20 | Notification | 🔴 不支持 | — | — |
| 21 | AppWidget | 🔴 不支持 | — | — |
| 22 | WebView | 🔴 不支持 | — | — |
| 23 | SurfaceView / TextureView | 🔴 不支持 | — | — |
| 24 | Jetpack Compose | 🔴 计划中 | `ars-compose`（未来模块） | — |

---

## 🟢 自动支持（零代码或一行代码）

### 1. Activity

```kotlin
class MainActivity : ArsActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // ✅ 所有 View 自动换肤，不需要任何额外代码
    }
}
```

**原理：** `attachBaseContext` 注入 `SkinContextWrapper` → `onCreate` 安装 `SkinLayoutInflater` Factory2 → 皮肤切换时 BFS 走 `decorView`。

---

### 2. Fragment

```kotlin
class HomeFragment : ArsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
}
```

**原理：** `onAttach` 包装 Context → `onCreateView` 安装 Factory2 → `onSkinChanged` 走路由 `fragmentRootView`。

---

### 3. DialogFragment

```kotlin
class PickerDialog : ArsDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_picker, container, false)
    }
}
```

**原理：** `onCreateDialog` 用 `SkinContextWrapper` 创建 Dialog，安装 Factory2 → 皮肤切换时走 `dialog.window.decorView`。

---

### 4. BottomSheetDialogFragment

```kotlin
class FilterSheet : ArsDialogFragment() {  // ← 继承 ArsDialogFragment 即可
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_filter, container, false)
    }
}
```

**原理：** `BottomSheetDialogFragment` 是 `DialogFragment` 的子类，自动继承 `ArsDialogFragment` 全部行为。

---

### 5. Dialog（手动创建）

```kotlin
class SettingsDialog(context: Context) : ArsDialog(context, R.style.MyDialogTheme) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_settings)

        // 可选：在皮肤切换后做额外处理
    }

    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        // View 树已更新完毕，这里处理非 View 逻辑
        updateStatusBar()
    }
}

// 使用
val dialog = SettingsDialog(this)
dialog.show()
// ✅ 显示期间皮肤切换 → 自动刷新
```

**原理：** `ArsDialog` 构造时自动 `wrapContext()` → `onCreate` 安装 Factory2 → `onStart/onStop` 注册/注销 `SkinChangeListener`。

---

### 6. AlertDialog

```kotlin
// 方式一：使用 ArsDialog 替代（推荐，完全自动）
class ConfirmDialog(context: Context) : ArsDialog(context) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_confirm)
        // 如果需要 AlertDialog 风格（标题+按钮），在布局中自行实现
    }
}

// 方式二：手动 wrapContext（AlertDialog 保留标题和按钮）
val ctx = ArsSkinEngine.wrapContext(requireContext())
val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_content, null)

val dialog = AlertDialog.Builder(ctx)
    .setTitle("确认")
    .setView(view)
    .setPositiveButton("确定") { _, _ -> /* ... */ }
    .setNegativeButton("取消", null)
    .create()

dialog.show()

// ⚠️ 如果在 Dialog 显示期间皮肤切换了，Dialog 内的 View 不会自动更新。
// 解决方案一：用 ArsDialog 替代（推荐）
// 解决方案二：手动注册监听器
val listener = SkinChangeListener { _, _ ->
    dialog.window?.decorView?.let {
        com.kagawagao.ars.internal.ArsViewTreeWalker.walk(it, ArsSkinEngine)
    }
}
ArsSkinEngine.registerSkinChangeListener(listener)
dialog.setOnDismissListener { ArsSkinEngine.unregisterSkinChangeListener(listener) }
```

---

### 7. PopupWindow

```kotlin
// 方式一：ArsPopupWindow（推荐，自动刷新）
val popup = ArsPopupWindow(requireContext()).apply {
    contentView = LayoutInflater.from(skinContext).inflate(R.layout.popup_menu, null)
    width = ViewGroup.LayoutParams.WRAP_CONTENT
    height = ViewGroup.LayoutParams.WRAP_CONTENT
    isOutsideTouchable = true
    showAsDropDown(anchor)
    // ✅ 皮肤切换 → 自动刷新，dismiss → 自动注销监听
}

// 方式二：手动包装（不自动刷新）
val ctx = ArsSkinEngine.wrapContext(requireContext())
val contentView = LayoutInflater.from(ctx).inflate(R.layout.popup_menu, null)
PopupWindow(contentView, WRAP_CONTENT, WRAP_CONTENT).apply {
    showAsDropDown(anchor)
    // ⚠️ 皮肤切换时不会自动更新
}
```

---

### 8. Toast

```kotlin
// 简单文字 Toast（API < 30 时使用皮肤颜色）
ArsToast.showText(this, "操作成功", Toast.LENGTH_SHORT)

// 自定义布局 Toast（仅 API < 30）
val toast = ArsToast.showCustom(this, R.layout.toast_skinned, Toast.LENGTH_SHORT)
```

**限制：** Toast 窗口由系统管理，不在任何 View 树中。皮肤切换时正在显示的 Toast 不会更新。Android 11+ 限制自定义 Toast 视图。

---

### 9. Snackbar

```kotlin
// 简单 Snackbar（颜色取创建时的皮肤值）
ArsSnackbar.make(rootView, "消息已发送", Snackbar.LENGTH_SHORT).show()

// 自动刷新 Snackbar（皮肤切换时自动更新）
ArsSnackbar.make(rootView, "皮肤已切换", Snackbar.LENGTH_SHORT)
    .showSkinned()  // ✅ 注册 SkinChangeListener，dismiss 时自动注销

// 手动刷新已显示的 Snackbar
ArsSnackbar.refreshSkin(existingSnackbar)
```

**原理：** `showSkinned()` 通过 `BaseTransientBottomBar.BaseCallback` 在 Snackbar 显示/隐藏时注册/注销 `SkinChangeListener`，回调中走 Snackbar 内容 View 的树。

---

### 10. RecyclerView

```kotlin
// ✅ 零配置。RecyclerView 自动支持换肤：
// - Adapter.onCreateViewHolder 中 LayoutInflater.from(parent.context) 自动使用皮肤 Context
// - XML 中的 android:textColor="@color/..." 自动注册皮肤绑定
// - 皮肤切换时，BFS walk 覆盖所有可见 item 的 View

class MyAdapter : RecyclerView.Adapter<MyAdapter.VH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        // parent.context 已被 SkinContextWrapper 包装
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        // ✅ 引用资源 ID — 皮肤切换自动更新
        holder.textView.text = item.title

        // ❌ 字面量颜色 — 不会换肤
        // holder.textView.setTextColor(Color.RED)

        // ✅ 用 resources.getColor() — 皮肤切换后自动换肤
        holder.textView.setTextColor(
            holder.itemView.context.resources.getColor(R.color.text_primary, null)
        )
    }
}
```

---

### 11. ViewPager / ViewPager2

```kotlin
// ✅ ViewPager 中的 Fragment 使用 ArsFragment 即可自动换肤
class PagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment = TabFragment()
}

class TabFragment : ArsFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_tab, container, false)
    }
}
```

---

### 12. ViewStub

```kotlin
// ✅ ViewStub inflate 时使用父 View 的 LayoutInflater（Factory2 已安装）
// 无需额外配置

val stub = findViewById<ViewStub>(R.id.stub_extra)
val inflatedView = stub.inflate()  // 自动注册皮肤绑定 + 立即应用当前皮肤
```

---

### 13. `<include>` / `<merge>` 标签

```kotlin
// ✅ 标准 LayoutInflater 处理，Factory2 链正常拦截所有子 View
// 无需额外配置
```

```xml
<!-- activity_main.xml -->
<include layout="@layout/toolbar" />
<include layout="@layout/content" />
```

---

### 14. DataBinding / ViewBinding

```kotlin
// ✅ DataBinding 和 ViewBinding 内部都用 LayoutInflater，Factory2 已安装
class MainActivity : ArsActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // ✅ 所有绑定 View 自动换肤
    }
}
```

---

### 15. Navigation Component

```kotlin
// ✅ Fragment 事务中的 Fragment 继承 ArsFragment 即可
// 无需额外配置
```

---

### 16. Spinner / AutoCompleteTextView 下拉列表

```kotlin
// 用 ArsSpinnerAdapter 包装原始适配器
val originalAdapter = ArrayAdapter.createFromResource(
    this, R.array.options, android.R.layout.simple_spinner_item
)
spinner.adapter = ArsSpinnerAdapter.wrap(originalAdapter, this)

// 启用皮肤切换时下拉项自动刷新（需要在下拉打开/关闭时手动调用）
spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        (parent?.adapter as? ArsSpinnerAdapter<*>)?.startTrackingSkinChanges()
    }
    override fun onNothingSelected(parent: AdapterView<*>?) {}
}
```

**限制：** Spinner 下拉使用系统创建的 PopupWindow，其 LayoutInflater 不经过 SkinLayoutInflater。只有 View 属性通过 SkinResources 的重写方法才能生效。dropdown 中的 View 属性（如 `android:textColor`）需要通过 `SkinResources.getColorStateList()` 间接生效。

---

### 17. 自定义 View（XML 布局中 inflate）

```kotlin
// ✅ SkinLayoutInflater 自动记录 Android namespace 支持的属性
// 无需额外代码

class GradientBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    // background 属性自动被 SkinLayoutInflater 拦截，皮肤切换时自动更新
}
```

```xml
<com.example.GradientBackgroundView
    android:background="@drawable/bg_gradient" />
<!-- ✅ background 自动换肤 -->
```

---

### 18. 自定义 View（代码 new）

```kotlin
// ✅ 使用宿主 Context 的 Resources（已由 SkinContextWrapper 包装）
class CustomBadge(context: Context) : View(context) {
    override fun onDraw(canvas: Canvas) {
        // ✅ 颜色自动取皮肤值
        paint.color = context.resources.getColor(R.color.badge_bg, null)
        canvas.drawCircle(...)

        // ❌ 缓存颜色值在字段中 — 皮肤切换后不会更新
        // private val cachedColor = resources.getColor(R.color.badge_bg, null)
    }
}

// 皮肤切换后触发重绘
override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
    invalidate()  // onDraw 重新调用，取到新皮肤颜色
}
```

---

### 19. 动态添加 View

```kotlin
// ✅ a) XML inflate（自动）
val ctx = requireContext()  // 已在 ArsActivity/ArsFragment 中包装
val newView = LayoutInflater.from(ctx).inflate(R.layout.widget_extra, parent, false)
// SkinLayoutInflater 自动注册皮肤绑定 + 立即应用当前皮肤
parentLayout.addView(newView)

// ✅ b) 代码 new + inflate（自动）
val ctx = ArsSkinEngine.wrapContext(requireContext())
val cardView = MaterialCardView(ctx).apply {
    // SkinResources 拦截 getDrawable/getColor，取皮肤值
    background = ctx.resources.getDrawable(R.drawable.card_bg, null)
}
parentLayout.addView(cardView)

// ✅ c) 皮肤切换后刷新动态 View
// 如果 View 通过 XML inflate 创建 → SkinLayoutInflater 已注册 → 自动刷新
// 如果 View 通过代码创建 → 调用 refreshSkin()
refreshSkin()  // BFS 走 Activity/Fragment 的 View 树
```

---

## 🟡 半自动（需一行包装代码）

### Toast

详见 [Toast 章节](#8-toast)。

### Spinner 下拉列表

详见 [Spinner 章节](#16-spinner--autocompletetextview-下拉列表)。

---

## 🔴 不支持（含原因和替代方案）

### 20. Notification

**原因：** Notification 使用 `RemoteViews`，运行在系统进程（SystemUI），不经过应用的 `Resources` 或 `LayoutInflater`。

**替代方案：**
```kotlin
// 在创建 Notification 前，根据当前皮肤选择不同的资源
val isDark = ArsSkinEngine.currentThemeMode == SkinPackage.ThemeMode.DARK
val accentColor = if (activeSkin != null) R.color.skin_accent else R.color.app_accent

NotificationCompat.Builder(this, CHANNEL_ID)
    .setSmallIcon(if (isDark) R.drawable.ic_notify_dark else R.drawable.ic_notify_light)
    .setColor(ContextCompat.getColor(this, accentColor))
    .build()
```

---

### 21. AppWidget

**原因：** AppWidget 运行在 Launcher 进程，通过 `RemoteViews` 渲染。`Resources` 拦截在此完全不生效。

**替代方案：**
```kotlin
// 在 AppWidgetProvider.onUpdate 中根据当前皮肤选择不同布局
fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    val isDark = ArsSkinEngine.currentThemeMode == SkinPackage.ThemeMode.DARK
    val layoutId = if (isDark) R.layout.widget_dark else R.layout.widget_light

    val views = RemoteViews(context.packageName, layoutId)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
```

---

### 22. WebView

**原因：** WebView 渲染 Web 内容（HTML/CSS/JS），不走 Android `Resources` 栈。WebView 内部的颜色/drawable 是 CSS，而非 `@color/` 引用。

**替代方案 — JS Bridge 注入 CSS 变量：**
```kotlin
class SkinnedWebViewActivity : ArsActivity() {
    private lateinit var webView: WebView

    override fun onSkinApplied(previous: SkinPackage?, current: SkinPackage?) {
        injectSkinCss(webView)
    }

    private fun injectSkinCss(webView: WebView) {
        val primaryColor = resources.getColor(R.color.primary, null)
        val bgColor = resources.getColor(R.color.background, null)
        val css = """
            :root {
                --color-primary: #${Integer.toHexString(primaryColor and 0xFFFFFF)};
                --color-background: #${Integer.toHexString(bgColor and 0xFFFFFF)};
            }
        """.trimIndent()
        webView.evaluateJavascript("""
            (function() {
                var style = document.getElementById('ars-skin');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'ars-skin';
                    document.head.appendChild(style);
                }
                style.textContent = `$css`;
            })()
        """.trimIndent(), null)
    }
}
```

---

### 23. SurfaceView / TextureView

**原因：** SurfaceView 和 TextureView 的渲染在独立 Surface 上进行（Canvas 或 OpenGL），不经过 `Resources` 或 `LayoutInflater`。所有颜色和图片都在画布绘制时直接指定。

**替代方案：**
```kotlin
class SkinnedSurfaceView(context: Context) : SurfaceView(context) {
    // 通过 SkinChangeListener 在皮肤切换时重新绘制
    init {
        ArsSkinEngine.registerSkinChangeListener { _, _ ->
            // 触发重绘，从 context.resources 重新取颜色
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        // 每次绘制时从 Context 取颜色（而非缓存）
        paint.color = context.resources.getColor(R.color.surface_bg, null)
        // ... 绘制
    }
}
```

---

### 24. Jetpack Compose

**原因：** Compose 使用完全不同的渲染管线（`CompositionLocal` / `MaterialTheme`）。View 体系的 `LayoutInflater.Factory2`、`Resources` 子类拦截、`ViewTreeWalker` 在 Compose 中全部不适用。

**计划：** `ars-compose` 独立模块 — 提供 `ArsMaterialTheme` Composable，通过 `CompositionLocal` 注入皮肤颜色。

---

## 快速决策指南

**我应该用什么基类？**

```
需求                                   → 基类
═══════════════════════════════════════════════════════════
标准 Activity                           → ArsActivity
标准 Fragment                           → ArsFragment
全屏 Dialog / 底部 Sheet               → ArsDialogFragment
简单弹窗（标题+按钮）                   → ArsDialog
下拉菜单 / 工具提示                     → ArsPopupWindow
列表下拉（Spinner/AutoComplete）       → ArsSpinnerAdapter.wrap()
临时消息                                → ArsSnackbar.make().showSkinned()
短提示                                  → ArsToast.showText()
```

**皮肤切换时是否需要额外刷新？**

| 如果我用了... | 皮肤切换时自动刷新？ |
|---------------|:---:|
| ArsActivity / ArsFragment / ArsDialogFragment | ✅ 引擎全局 walk |
| ArsDialog | ✅ onSkinChanged 走 decorView |
| ArsPopupWindow | ✅ onSkinChanged 走 contentView |
| ArsSnackbar.showSkinned() | ✅ onSkinChanged 走 contentLayout |
| ArsSpinnerAdapter + startTrackingSkinChanges | ✅ onSkinChanged 走 dropdown |
| AlertDialog + ArsSkinEngine.wrapContext() | ❌ 需手动注册 SkinChangeListener |
| PopupWindow + ArsSkinEngine.wrapContext() | ❌ 需手动注册 SkinChangeListener |
| Toast | ❌ 不支持（系统 Window） |
| Notification / AppWidget | ❌ 独立进程 |
| WebView | ❌ 需 JS Bridge 注入 |
