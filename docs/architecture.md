# ETKN Mobile 架构

单模块 `:app`，无 DI 框架（`AppGraph` 手工装配少量长生命周期单例，冷启动无反射开销）。

```
xyz.etkn.mobile
├── AppGraph / EtknApp / MainActivity     装配、Coil ImageLoader、Compose 根
├── data
│   ├── net        HttpClients（共享连接池：api/probe/images/media/events）、ApiClient（JSON 规则）、Json 容错读取
│   ├── api        EtknApi（/api/tv 全部路由）、ServerProbe（ETKN 身份识别）、ImageUrls（图片 URL 规则与尺寸分桶）
│   ├── model      Item / MediaSource / MediaStream / Chapter / UserData …（JsonObject 手工映射，类型异常只降级字段）
│   ├── session    SessionManager（登录/恢复/切换/过期）、SessionStore（DataStore）、SecureBox（Keystore AES-GCM）
│   ├── discover   影视探索模型（DiscoverItem/Season/Filter、DiscoverStore 跨页传递）、CloudResources（共享池/re0/频道资源解析，移植自 TV）
│   ├── update     Releases（GitHub 发行解析、版本比较、ABI 选包）、UpdateManager（检查、镜像下载、SHA-256 校验、安装）
│   └── settings   AppSettings（主题、配色来源、首页推荐、解码、片头片尾、倍速、自动下一集；缓存服务器主题色）；UiScale（页面大小，同步 SharedPreferences）
├── player         danmaku/（DanmakuTimeline/Layout 解析与分道、DanmakuFeed 120 s 窗口拉取、DanmakuView 位图缓存绘制）
│                  PlayerActivity（横屏、沉浸、画中画）、VideoSession（ExoPlayer + 上报 + 下一集 + 跳过）、PlayerScreen（画面与浮层）、PlayerControls、PlayerGestures、PlayerSheets + PlayerPanel（右侧独立窗口面板）、PlayerFactory、PlaybackAddress
├── music          MusicConnection（共享 ExoPlayer、ResolvingDataSource 按需解析、上报）、MusicService（MediaSession 后台播放）、Lyrics（LRC）
└── ui
    ├── theme      MaterialExpressiveTheme + HCT 种子配色 + 8 档形状 + 语义 Tokens
    ├── components Artwork / Glass(Haze) / Cards / Sections / Hero / States
    ├── nav        Routes（类型安全）、AppNav、MainShell（外壳与迷你播放器）、NavGraph（目的地 + 转场 + PageFrame）、Dock（手机悬浮玻璃底栏，色块包住图标与文字）；平板 WideNavigationRail
    └── screens    connect · home · library · search · detail · person · music · me
```

## 关键数据流

- **会话**：`SessionManager.state`（Restoring / SignedOut / SignedIn）驱动根界面；切换账号时 `MainShell` 以账号 key 重建，旧页面与请求一起销毁；任何 401 经 `EtknApi` 回调 `expire()`，仅当 generation 与当前会话一致才生效。
- **列表分页**：`PagedViewModel` 以服务端实际返回条数推进 `StartIndex`，按 `TotalRecordCount` 或空页结束，失败停住等待重试（不自动循环）。
- **首页 Hero**：`PersonalRecommendations?Stream=true&Limit=1` 逐条拉取并实时追加（首条到达即渲染），跳过无背景图项；推荐不可用时回退各库最新。
- **视频播放**：PlaybackInfo → 服务端 `DirectStreamUrl`（同源校验）→ ExoPlayer。外挂字幕作为 SubtitleConfiguration 侧载；音轨/字幕名称优先使用服务端 MediaStreams 标题。上报 Playing / 10 s Progress / Stopped，Stopped 走应用级协程。
- **音乐**：队列项 URI 为 `etkn-music://track/<id>`，在 ExoPlayer 加载线程通过 PlaybackInfo 解析为带 `X-Emby-Token/UserId/PlaySessionId/MediaSourceId` 的直链并缓存；`MediaSessionService` 提供通知、锁屏与蓝牙控制；歌词 GET 为空才 POST 在线补取。

- **弹幕**：PlaybackInfo / 上报回复里的 `Danmaku.Available` 决定是否显示入口；本机开关开启且服务可用时，`DanmakuFeed` 按播放位置拉 `I/Danmaku?local=true&session=&start=`（120 s 窗口、提前 40 s 预取、暂停不拉、busy/unavailable 15 s 重试、stale 1 s 重试），上报附 `DanmakuActive`。`DanmakuView` 跟随媒体时钟逐帧绘制，同一文本只排版一次。样式（行数/密度/字号/不透明度/速度/跟随主题色）存本机，「我的 → 弹幕」与播放器内面板共用。
- **玻璃层**：Haze 不会采样自身所在的祖先源，所以页面内悬浮栏（首页顶栏、详情顶栏、列表顶栏、搜索框）各用页面级 `HazeState` 包住自己的滚动内容；Dock/迷你播放器继续采样整个 NavHost。

- **影视探索**：`auth/check_status` 的 `user.can_access_discovery` 决定是否出现「探索」（手机底栏取代「搜索」位，搜索仍在首页顶栏；平板侧栏全部显示）。浏览 `D/query/{movie|series}` 按页（≤500）无限滚动，筛选（排序/类型/年代/评分/隐藏已入库）与 TMDb 搜索走同一请求体；详情补全 `D/series`、`D/media/{kind}/credits`，可订阅影片 / 单季 / 全部缺失季（逐季提交、首错停止）；人物页读 `D/person/{id}/credits`。云资源三源并行搜索、分别显示失败，获取前必须确认（可能消耗积分），只提交不重试；获取进度跟踪（TV 的 AcquireTracker 通知）未移植。
- **应用更新**：启动时（可关）请求 `api.github.com/repos/Alumos/etkn-mobile/releases/latest`，失败则经镜像读该发行的 `SHA256SUMS.txt`。按本机 ABI 选包，默认经 `gh-proxy.com` 加速、失败自动换 GitHub 直连（可手动换源），Android 10+ 写入 MediaStore 下载目录（8–9 申请存储权限写公共 Download），边下边算 SHA-256 并与 GitHub 摘要比对；失败/取消即删除未完成文件。安装走系统安装器（首次需允许“安装未知应用”），签名由系统校验与旧版一致。外部请求用不带任何 ETKN 凭据的独立 OkHttp 客户端。
- **自适应布局**：`WindowLayout`（Compact <600 / Medium / Expanded ≥840 dp）。平板：侧栏（≥840 展开带标签）、首页 Hero 改为横幅并左对齐文案、海报与卡片加宽、网格列宽加大、详情头图改横幅、「我的」与设置居中限宽 720 dp、迷你播放器靠右限宽。
- **返回手势**：清单启用 `enableOnBackInvokedCallback`；NavHost 的 pop 转场即预测性返回的预览动画。每个目的地包在 `PageFrame` 里：不透明背景 + 随转场进度变化的圆角裁剪，避免透明页面缩小时与下层页面叠在一起。
- **页面大小**：`UiScale` 在 `attachBaseContext` 用 `applyOverrideConfiguration` 只覆盖 `densityDpi`，对话框、底部面板、菜单等独立窗口同样生效；改设置后 `recreate()`。窗口尺寸一律用 `windowSizeDp()`（像素 / 当前密度），不用 `Configuration.screenWidthDp`。
- **播放器面板**：音轨/字幕/倍速/版本/选集/弹幕都在右侧独立窗口（`ComponentDialog`）中显示；与长按菜单同一套玻璃配方（玻璃色调 + 细描边 + 浅色文字，轻微压暗）。Android 12+ 由系统合成器做窗口背景模糊（可模糊 SurfaceView 视频），不支持时退化为高不透明度面板。
- **蓝光原盘 ISO**：`player/bluray`（移植自 ETKN TV）。`BlurayMediaSourceFactory` 按 `BlurayTag` 走原盘（`PlaybackAddress.isDisc`：`Container`/`Path` 的 iso 标记或服务端原盘探测的 `BlurayPlaylist`/`TransportStreamId`；普通解析报容器不支持时再按原盘重试一次），`BlurayInput` 以 HTTP Range 读原文件，CI 构建的 `libetkn_bluray.so`（libbluray + libudfread + 仅解封装的 FFmpeg）选正片并拆出 MPEG-TS 流，`DiscExtractor` 只暴露主视频（PID 0x1011，丢弃杜比视界增强层与副视频）交给 Media3 硬件解码，轨道名称与默认音轨按 `TransportStreamId` 对应服务端；音频解码器无法启动时自动换音轨，其他解码失败在错误里写明格式与解码器；原盘关闭后播放线程不再调用原生句柄；杜比视界 profile 7 映射为 HEVC 基础层（普通文件同样适用）。
- **条目菜单**：`ui/actions`。`ItemMenu` 在外壳最上层：海报卡片 / 继续观看卡片长按、详情页“更多”打开；毛玻璃菜单从卡片位置弹出（同窗口 Haze 模糊），刷新/删除/编辑元数据/编辑图片/提取缩略图按 Web 端规则与权限显示。变化经 `AppGraph.itemChanges` 广播，列表就地修补、首页静默重载、详情页重载或退出。
- **毛玻璃**：同窗口浮层（顶栏、底栏、菜单、提示）用 Haze；对话框与底部面板是独立窗口，Android 12+ 用 `GlassDialog` / `GlassSheet`（`FLAG_BLUR_BEHIND` 模糊背后整屏 + 半透明容器）。人物、音乐列表、设置、账号页改为悬浮玻璃标题栏。
- **导航**：`visibleTabs()` 决定底栏/侧栏项目（用户可选，首页固定；手机底栏最多 5 项；探索需权限）；宽屏可选侧边或底部。
- **搜索**：库内无结果且有探索权限时，并发查询 TMDb 电影与剧集（`SearchViewModel`）。

## 性能要点

- 所有 Lazy 列表/网格使用稳定 key 与 contentType；Hero 视差只用 `graphicsLayer`，滚动不触发重新布局。
- 图片宽度按显示尺寸换算物理像素并对齐 10 个桶（160–1920），Coil 内存 22% + 磁盘 384 MB；共享 OkHttp 连接池（HTTP/2）。
- 播放器缓冲 20–90 s、起播 1.2 s；UI 时钟 4 Hz 轮询，控件 4 s 自动隐藏；Compose 关闭源码信息、release 开启 R8 与资源裁剪，按 ABI 拆包。

## 未纳入首版（后续）

获取进度跟踪与入库通知、转码协助、字幕翻译与偏移、寻址缩略图、蓝光 ISO 专用播放、Home/Events SSE 实时失效、入库通知、共享偏好写回、局域网自动发现、应用内更新。
