# halo-plugin-srcset

Halo 插件：**为没有 `srcset` 的图片自动补上响应式候选**，候选指向 Halo 自带的缩略图接口
（`/upload/xxx?width=W`），从而让浏览器按视口/DPR 取更小的图。

- 插件 id：`plugin-srcset`
- 要求：**Halo >= 2.26.0**
- 实现：`SrcsetImageTagProcessor implements ElementTagPostProcessor`
  （`run.halo.app.theme.dialect.ElementTagPostProcessor`，Halo 2.20.0 起提供）

因为挂在「元素标签后处理」扩展点上，它看到的是**整页渲染结果**——不只是文章正文，主题模板里直接写的
封面、推荐位、卡片图也一并覆盖（`<img>` 自带 `srcset` 时不动）。

## 构建

```bash
./gradlew build
# 产物：build/libs/plugin-srcset-<version>.jar
```

> wrapper 默认使用官方发行包（`gradle-9.4.0-bin.zip`）。
> 如果本机已缓存 `.gradle-cache/gradle-9.4.0-bin.zip`（约 131 MB，未入库），
> 可以把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 临时改成
> `file:///C:/workspace/plugin-srcset/.gradle-cache/gradle-9.4.0-bin.zip` 来加速或离线构建。

## 安装

在 Halo 控制台「插件 → 安装」上传 JAR，或：

```bash
halo plugin install build/libs/plugin-srcset-<version>.jar --profile <profile>
```

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 关闭后不再生成任何 `srcset` |
| `widths` | `400,800,1200,1600` | 逗号分隔，生成 `?width=<值>` 候选；候选越多 CDN 需要生成的变体越多 |
| `reencodedWidths` | `400,800` | WebP 专用（见下）；**留空或填错都回退到这个默认值**，想让 WebP 用全部宽度就把 `widths` 原样再填一遍 |
| `sizes` | `(max-width: 800px) 100vw, 768px` | 浏览器据此挑候选，应按主题正文列宽填写（JumpServer 主题正文列宽 768px）|
| `skipExtensions` | 空 | 额外跳过的扩展名 |

### 为什么 WebP 的候选更窄

缩略图接口**只输出 JPEG**，即使源文件是 WebP（`?format=webp` / `fm` / `output` / `type` / `avif` 全部无效，实测仍返回 `image/jpeg`）。而在大尺寸下，JPEG 会比它替换掉的 WebP **更大**：

```
/upload/jumpserver_aws_console.webp   原图       81 KB  (1672x940 WebP)
                                     ?width=800  44 KB  ← -46% ✓
                                     ?width=1600 140 KB  ← +73% ✗
```

高分屏浏览器按 `sizes` 换算后恰好会挑**最宽**的候选（768px 列 + DPR 2 → 需要 1536px → 选 1600w），于是给 WebP 提供 1600w 反而会让这些页面变重。所以 WebP 默认只给到 800（`reencodedWidths`），PNG/JPEG 保持原格式、任何小于自然尺寸的候选都是净收益，沿用 `widths` 的四个候选。

`reencodedWidths` 留空（或填成无法解析的值）时**回退到内置默认 400,800，而不是回退到 `widths`**：Halo 会给「旧版本里不存在、升级后才出现」的设置项写入空值，如果空值意味着放开限制，从 1.0.0 升级上来的站点会静默地把上面的问题原样带回来。想让 WebP 也用全部宽度，就显式把 `widths` 的值再填一遍。

## 与 Halo 内核的关系（重要）

Halo 从 2.19 起自带 `ThumbnailImgTagPostProcessor`：只要 `<img>` 没有 `srcset`，内核就会为它补上
`?width=` 的候选，`sizes` 用固定的正文列宽 `(max-width: 640px) 94vw, (max-width: 768px) 92vw,
(max-width: 1024px) 88vw, min(800px, 85vw)`。也就是说**两个处理器在做同一件事**，谁先跑谁说了算：
本插件注册得更早，因此正常情况下页面上看到的是本插件的 `sizes` 和宽度列表；一旦本插件跳过某张图，
内核会立刻接手。

这带来两个必须注意的后果：

1. **`sizes` 是「正文列宽」，对 logo 墙这类小图是错的。** 一个实际只显示 ~150px 的 logo，浏览器会
   按 768px（DPR 2 时按 1536px）去挑候选，于是下载 800w/1600w 的重图 —— 实测某首页因此从
   0.92 MB 涨到 2.25 MB（DPR 2）。
2. **光「跳过」不够**，必须同时挡住内核，否则跳过的图会被内核用同样错误的 `sizes` 接管。

所以主题可以给这类图片加标记：

```html
<img src="/upload/logo.png" data-srcset="off" />
```

插件看到后不会生成候选，而是写一个**只有原图的单候选 srcset**（`srcset="/upload/logo.png 1x"`）。
浏览器在任何 DPR 下都取原图（等于不优化），而内核看到 `srcset` 已存在也会放手。JumpServer 主题的
logo 墙（`partials/logo-wall.html`）就是这么标记的。

另外，`sizes` 的取值优先级为：图片自己的 `sizes` 属性（不覆盖）> 图片的 `width` 属性（≤ 1024 时按
`{width}px` 处理，更大的值视为 CLS 用的固有尺寸提示，忽略）> 设置里的 `sizes`。

## 行为边界（都有实测依据）

只会处理**同源 `/upload/` 附件路径**，并且：

| 情况 | 处理 | 原因 |
| --- | --- | --- |
| `data-srcset="off"` | 只写 `srcset="<原图> 1x"` | 小图（logo 墙）用任何候选都更重；同时挡住内核接管 |
| `/upload/a.gif` | **始终跳过** | 缩略图接口对 GIF 返回 **403**；而 `srcset` 候选失败时浏览器**不会回退**到 `src`，会直接把图片弄坏 |
| `/upload/a.svg`、`.ico` | 始终跳过 | 无需栅格化缩略图 |
| 已有 `srcset` | 不动 | 尊重主题/编辑器已有声明 |
| 已带 `?width=`/`?height=`/`?size=` | 不动 | 避免二次处理 |
| 绝对 URL（含 CDN 域名） | 不动 | 外部主机不一定支持 Halo 的查询参数 |
| `/assets/**` 等主题静态资源 | 不动 | 只认 `/upload/` |
| 带 `#fragment` | 不动 | 无法安全地追加查询参数 |

实测（Halo 2.26 站点）：

```
/upload/x.webp                     2503x1285   56 KB
/upload/x.webp?width=800            800x410    19 KB   ← -66%
/upload/x.webp?width=1200          1200x616    37 KB
/upload/x.gif?width=800                         403     ← 所以必须跳过 GIF
/upload/x.png?height=400           2567x1349  255 KB    ← height 对 PNG 无效
/upload/x.webp?size=800            2503x1285   56 KB    ← size 参数被忽略
```

参数口径：**只有 `width` 可靠**。另外 WebP 走缩略图后输出的是 JPEG（透明通道需留意，可用
`skipExtensions: webp` 关掉）。

## 测试

```bash
./gradlew test
```

`ThumbnailCandidatesTest` 覆盖了上面每一条边界（GIF/绝对 URL/已带尺寸/无扩展名等）。

## 许可

GPL-3.0
