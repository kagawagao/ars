## 6. 全部 UI 模式支持矩阵

ARS 覆盖 Android 常见的全部 UI 呈现模式。以下是完整的支持和处理方式。

### 🟢 自动支持（零代码）

| 模式 | 基类 | 机制 |
|------|------|------|
| **Activity** | `ArsActivity` | `attachBaseContext` 注入 `SkinContextWrapper`；`Factory2` 拦截 XML inflate；皮肤切换时 BFS 走 `decorView` |
| **Fragment** | `ArsFragment` | `onAttach` 包装 Context；`onCreateView` 安装 Factory2；皮肤切换时走 Fragment 的 rootView |
| **DialogFragment** | `ArsDialogFragment` | `onCreateDialog` 包装 Dialog Context；安装 Factory2 到 Dialog 的 LayoutInflater；皮肤切换时走 Dialog 的 `decorView` |
| **BottomSheetDialogFragment** | `ArsDialogFragment` | 继承自 DialogFragment，自动继承所有行为 |
| **RecyclerView** | 无（任何 Activity/Fragment 中） | ViewHolder 创建使用宿主 Context（已包装）；`LayoutInflater.from(parent.context)` 自动经过 Factory2 链 |
| **ViewPager / ViewPager2** | 无 | 内部 Fragment 使用 `ArsFragment` 即可 |
| **ViewStub** | 无 | 延迟 inflate 时使用父 View 的 LayoutInflater（Factory2 已安装） |
| **`<include>` / `<merge>`** | 无 | 标准 LayoutInflater 处理，Factory2 链正常拦截 |
| **DataBinding / ViewBinding** | 无 | 内部使用 `LayoutInflater`，Factory2 已安装 |
| **Navigation Component** | 无 | Fragment 事务中的 Fragment 使用其各自的基类 |
| **自定义 View（XML inflate）** | 无 | `SkinLayoutInflater` 自动记录所有属性绑定 |
| **自定义 View（代码 new）** | 无 | 使用宿主 Context 的 `Resources`（已包装），颜色/drawable 查询自动返回皮肤值 |

### 🟡 半自动（需手动包装 Context）

| 模式 | 方法 | 代码 |
|------|------|------|
| **AlertDialog** | 包装 Context | `AlertDialog.Builder(ArsSkinEngine.wrapContext(ctx))` |
| **MaterialAlertDialogBuilder** | 同上 | 同上 |
| **PopupWindow** | 包装 Context + inflate | `LayoutInflater.from(wrappedCtx).inflate(...)` |
| **Dialog（手动）** | 包装 Context | `Dialog(wrappedCtx)` |
| **动态添加 View** | 代码 new 或 inflate | `Button(wrappedCtx)` 或 `LayoutInflater.from(wrappedCtx).inflate(...)` |

### 🔴 不支持

| 模式 | 原因 |
|------|------|
| **Toast** | 系统级 Window，Context 不可注入 |
| **Snackbar** | 使用 overlay Window；内容 View 继承宿主 Context 但内部 inflate 不经过应用 LayoutInflater |
| **Notification** | 独立进程，使用 RemoteViews |
| **AppWidget** | 独立进程，使用 RemoteViews |
| **WebView** | 自渲染引擎，不走 Android Resources |
| **SurfaceView / TextureView** | 独立渲染表面，Canvas/GL 绘制不经过 Resources |
| **Spinner 下拉 / AutoCompleteTextView 下拉** | 系统创建的 PopupWindow，不经过应用 LayoutInflater |
| **Jetpack Compose** | 完全不同的渲染管线（MaterialTheme/CompositionLocal）— 计划在 `ars-compose` 模块中支持 |

### 特殊场景详解

#### PopupWindow 完整示例

```kotlin
fun showSkinnedPopup(anchor: View) {
    // 1. 获取皮肤感知 Context
    val ctx = ArsSkinEngine.wrapContext(anchor.context)

    // 2. 用包装后的 Context inflate 布局 — 所有资源引用自动使用皮肤值
    val contentView = LayoutInflater.from(ctx).inflate(R.layout.popup_menu, null)

    // 3. 创建 PopupWindow
    PopupWindow(contentView, WRAP_CONTENT, WRAP_CONTENT).apply {
        isOutsideTouchable = true

        // 4. 显示 — 内容已换肤，无需额外操作
        showAsDropDown(anchor)
    }

    // 注意：如果在 PopupWindow 显示期间发生了皮肤切换，
    // PopupWindow 内的 View 不会自动更新（它们不在 Activity 的 decorView 树中）。
    // 解决方案：在 PopupWindow 关闭前注册为 SkinChangeListener，
    // 在回调中 walk popup 的 contentView。
}
```

#### AlertDialog 完整示例（含 EditText 状态保留）

```kotlin
fun showSkinnedDialog() {
    val ctx = ArsSkinEngine.wrapContext(requireContext())
    val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_input, null)

    val dialog = AlertDialog.Builder(ctx)
        .setTitle("输入")
        .setView(view)
        .setPositiveButton("确定", null)
        .create()

    dialog.show()

    // 如果在 Dialog 显示期间皮肤切换了，Dialog 内的 View 不会自动更新。
    // 需要手动监听：
    val listener = SkinChangeListener { _, _ ->
        dialog.window?.decorView?.let {
            com.kagawagao.ars.internal.ArsViewTreeWalker.walk(it, ArsSkinEngine)
        }
    }
    ArsSkinEngine.registerSkinChangeListener(listener)

    dialog.setOnDismissListener {
        ArsSkinEngine.unregisterSkinChangeListener(listener)
    }
}
```

#### RecyclerView 注意事项

```kotlin
// ✅ RecyclerView 自动支持换肤 — 以下场景都自动处理：
// - Adapter.onCreateViewHolder 中的 LayoutInflater.from(parent.context).inflate()
// - ItemView 中的 android:textColor="@color/..." 属性
// - ImageView 的 android:src="@drawable/..."

// ⚠️ 唯一例外：如果在 Adapter 中使用了非 XML 方式设置颜色：
override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    // ❌ 字面量颜色 — 不会自动换肤
    holder.textView.setTextColor(Color.RED)

    // ✅ 引用资源 ID — SkinResources 会拦截
    holder.textView.setTextColor(
        holder.itemView.context.resources.getColor(R.color.text_primary, null)
    )
}
```

#### 自定义 View 开发指南

```kotlin
// ✅ 总是通过 Context 的 Resources 获取颜色/drawable
class MyCustomView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private fun updateColors() {
        // ✅ 自动换肤 — SkinResources 拦截
        paint.color = context.resources.getColor(R.color.custom_fg, null)
        bg = context.resources.getDrawable(R.drawable.custom_bg, null)
    }
}

// ❌ 缓存颜色值 — 皮肤切换后不会更新
class BadCustomView : View {
    private val cachedColor = resources.getColor(R.color.primary, null)
    // cachedColor 在皮肤切换后仍然是旧值
}
```

#### trackTint 等无内置 Handler 的属性

`trackTint` 等属性无内置 Handler，需用户注册自定义 Handler：

```kotlin
// 在 Application.onCreate() 中
ArsSkinEngine.registerAttributeHandler("trackTint") { view, resId, resources ->
    (view as? SeekBar)?.let {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            it.progressTintList = resources.getColorStateList(resId, null)
        }
    }
}
```