# 平台签名应用 RRO 支持 — 方案对比

> **背景**：AR 换肤框架需要支持系统签名的应用通过真 RRO（Runtime Resource Overlay）机制应用皮肤。ARS 应用使用平台签名打包，但安装路径不在 `/system/priv-app/`，导致 `CHANGE_OVERLAY_PACKAGES` 权限被拒绝。

---

## 问题根因

```xml
<!-- frameworks/base/core/res/AndroidManifest.xml -->
<permission android:name="android.permission.CHANGE_OVERLAY_PACKAGES"
    android:protectionLevel="signature|privileged" />
```

`signature|privileged` 语义：**同时满足** `signature` **且** `privileged` 才授予。

| 条件 | ARS 应用（平台签名，data 分区） |
|------|:-:|
| ✅ 平台签名 → `signature` 满足 | ✅ |
| ❌ 不在 `/system/priv-app/` → `privileged` 不满足 | ❌ |

**结果**：即使是平台签名应用，也无法调用 `OverlayManager` API。

---

## 方案对比

### 方案 A：OMS 权限检查加签名 fallback（推荐 ⭐）

**改动范围**：1 个文件，1 个方法

```java
// frameworks/base/services/core/java/com/android/server/om/OverlayManagerService.java

private void enforceChangeOverlayPermission(String message) {
    // 1. 先走标准权限检查（priv-app 路径通过）
    if (mContext.checkCallingOrSelfPermission(
            android.Manifest.permission.CHANGE_OVERLAY_PACKAGES)
            == PackageManager.PERMISSION_GRANTED) {
        return;
    }

    // 2. 唯一修改：平台签名即放行
    int callingUid = Binder.getCallingUid();
    PackageManager pm = mContext.getPackageManager();
    String[] pkgs = pm.getPackagesForUid(callingUid);
    if (pkgs != null) {
        for (String pkg : pkgs) {
            if (pm.checkSignatures(pkg, "android")
                    == PackageManager.SIGNATURE_MATCH) {
                return;
            }
        }
    }

    throw new SecurityException("Access denied: " + message);
}
```

| 维度 | 评价 |
|------|------|
| **侵入性** | 🟢 极小 — 不改 Manifest，不拆权限，不加新 API |
| **安全性** | 🟢 等价 — 只有平台签名应用可绕过 |
| **源码冲突** | 🟢 低 — AOSP 升级时冲突概率极低 |
| **适用范围** | 🟡 仅平台签名应用（ARS 目前足够了） |
| **AOSP 合规** | 🟡 非标准修改，但属 ROM 定制合理范围 |

---

### 方案 B：拆成两个独立权限

**思路**：把 `CHANGE_OVERLAY_PACKAGES` 拆成：
- `CHANGE_OVERLAY_PACKAGES` — `signature` 级别（平台签名即可）
- `CHANGE_OVERLAY_PACKAGES_PRIVILEGED` — `signature|privileged`（保留原行为）

```xml
<!-- 新权限 -->
<permission android:name="android.permission.CHANGE_OVERLAY_PACKAGES"
    android:protectionLevel="signature" />

<!-- 原权限改名 -->
<permission android:name="android.permission.CHANGE_OVERLAY_PACKAGES_PRIVILEGED"
    android:protectionLevel="signature|privileged" />
```

OMS 中两个权限都接受。

| 维度 | 评价 |
|------|------|
| **侵入性** | 🔴 大 — 要改 `AndroidManifest.xml`、`PackageManagerService` 权限同步、`core/res` 编译链 |
| **安全性** | 🟢 精确 — 细粒度控制 |
| **源码冲突** | 🔴 高 — 权限定义在 `core/res`，AOSP 升级几乎必然冲突 |
| **适用范围** | 🟢 可扩展 — 未来可给不同签名级别应用分配不同权限 |
| **AOSP 合规** | 🔴 改动 `core/res` 是 ROM 定制中最危险的操作 |

---

### 方案 C：降级 `protectionLevel`

**思路**：直接改成 `signature`：

```xml
<permission android:name="android.permission.CHANGE_OVERLAY_PACKAGES"
    android:protectionLevel="signature" />  <!-- 去掉 privileged -->
```

| 维度 | 评价 |
|------|------|
| **侵入性** | 🟡 中 — 只改一行，但在 `core/res` |
| **安全性** | 🟡 降低 — 去掉了 priv-app 路径的额外保护 |
| **源码冲突** | 🟡 中 — 一行冲突，但容易处理 |
| **适用范围** | 🟢 与 A 相同 |
| **AOSP 合规** | 🔴 放宽了 Android 安全模型，审计风险 |

---

### 方案 D：新增系统 Service + 自定义权限

**思路**：新建 `ArsOverlayManagerService`，注册自定义权限 `com.android.ars.MANAGE_OVERLAYS`，通过这个服务间接调用 OMS。

```xml
<!-- 新增 -->
<permission android:name="com.android.ars.MANAGE_OVERLAYS"
    android:protectionLevel="signature" />
```

| 维度 | 评价 |
|------|------|
| **侵入性** | 🔴 大 — 新增 Service、权限、API |
| **安全性** | 🟢 好 — 独立权限，不影响现有模型 |
| **源码冲突** | 🟢 极低 — 新增代码，不修改现有文件 |
| **适用范围** | 🟢 最灵活 — 可扩展到非平台签名应用 |
| **AOSP 合规** | 🟢 不修改 AOSP 文件 |
| **开发量** | 🔴 需要完整实现 Service + Binder + SDK API |

---

## 决策矩阵

|  | 方案 A<br>fallback | 方案 B<br>拆权限 | 方案 C<br>降级 | 方案 D<br>新 Service |
|------|:-:|:-:|:-:|:-:|
| 改动量 | ⭐ 1 方法 | 5+ 文件 | ⭐ 1 行 | 🔴 完整实现 |
| 安全性 | ✅ | ✅ | ⚠️ | ✅ |
| AOSP 兼容 | ✅ | 🔴 | 🔴 | ✅ |
| 未来扩展 | ⚠️ | ✅ | ⚠️ | ✅ |
| 当下够用 | ✅ | — | ✅ | — |

---

## 推荐

**方案 A**，理由：

1. ARS 的需求明确：**只支持平台签名应用**。方案 A 的 fallback 检查精确对应这个需求。
2. 改动量最小，仅在一个方法内加 ~10 行。AOSP 升级时几乎零冲突。
3. 不修改 `AndroidManifest.xml`、不引入新权限/新 Service，风险最低。
4. Google 自己在 `enforceShellRestriction` 等检查中也有类似的 fallback 模式，有 AOSP 先例。

如果未来需要支持非平台签名应用，再升级到方案 D（独立 Service）也不迟。
