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
# 产物：build/libs/plugin-srcset-1.0.0.jar
```

> wrapper 默认使用官方发行包（`gradle-9.4.0-bin.zip`）。
> 如果本机已缓存 `.gradle-cache/gradle-9.4.0-bin.zip`（约 131 MB，未入库），
> 可以把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 临时改成
> `file:///C:/workspace/plugin-srcset/.gradle-cache/gradle-9.4.0-bin.zip` 来加速或离线构建。

## 安装

在 Halo 控制台「插件 → 安装」上传 JAR，或：

```bash
halo plugin install build/libs/plugin-srcset-1.0.0.jar --profile <profile>
```

## 设置项

| 设置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 关闭后不再生成任何 `srcset` |
| `widths` | `400,800,1200,1600` | 逗号分隔，生成 `?width=<值>` 候选；候选越多 CDN 需要生成的变体越多 |
| `sizes` | `(max-width: 800px) 100vw, 768px` | 浏览器据此挑候选，应按主题正文列宽填写（JumpServer 主题正文列宽 768px）|
| `skipExtensions` | 空 | 额外跳过的扩展名 |

## 行为边界（都有实测依据）

只会处理**同源 `/upload/` 附件路径**，并且：

| 情况 | 处理 | 原因 |
| --- | --- | --- |
| `/upload/a.gif` | **始终跳过** | 缩略图接口对 GIF 返回 **403**；而 `srcset` 候选失败时浏览器**不会回退**到 `src`，会直接把图片弄坏 |
| `/upload/a.svg`、`.ico` | 始终跳过 | 无需栅格化缩略图 |
| 已有 `srcset` | 不动 | 尊重主题/编辑器已有声明 |
| 已带 `?width=`/`?height=`/`?size=` | 不动 | 避免二次处理 |
| 绝对 URL（含 CDN 域名） | 不动 | 外部主机不一定支持 Halo 的查询参数；主题 logo 墙用的是绝对地址，恰好不会被误处理 |
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
