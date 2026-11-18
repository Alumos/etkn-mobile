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
│   └── settings   AppSettings（主题、配色来源、解码、片头片尾、倍速、自动下一集；缓存服务器主题色）
├── player         PlayerActivity（横屏、沉浸、画中画）、VideoSession（ExoPlayer + 上报 + 下一集 + 跳过）、PlayerScreen（手势与控件）、PlayerFactory、PlaybackAddress
├── music          MusicConnection（共享 ExoPlayer、ResolvingDataSource 按需解析、上报）、MusicService（MediaSession 后台播放）、Lyrics（LRC）
└── ui
    ├── theme      MaterialExpressiveTheme + HCT 种子配色 + 8 档形状 + 语义 Tokens
    ├── components Artwork / Glass(Haze) / Cards / Sections / Hero / States
    ├── nav        Routes（类型安全）、AppNav、MainShell（手机悬浮玻璃 ShortNavigationBar / 平板 WideNavigationRail + 迷你播放器）
    └── screens    connect · home · library · search · detail · person · music · me
```

## 关键数据流

- **会话**：`SessionManager.state`（Restoring / SignedOut / SignedIn）驱动根界面；切换账号时 `MainShell` 以账号 key 重建，旧页面与请求一起销毁；任何 401 经 `EtknApi` 回调 `expire()`，仅当 generation 与当前会话一致才生效。
- **列表分页**：`PagedViewModel` 以服务端实际返回条数推进 `StartIndex`，按 `TotalRecordCount` 或空页结束，失败停住等待重试（不自动循环）。
- **首页 Hero**：`PersonalRecommendations?Stream=true&Limit=1` 逐条拉取并实时追加（首条到达即渲染），跳过无背景图项；推荐不可用时回退各库最新。
- **视频播放**：PlaybackInfo → 服务端 `DirectStreamUrl`（同源校验）→ ExoPlayer。外挂字幕作为 SubtitleConfiguration 侧载；音轨/字幕名称优先使用服务端 MediaStreams 标题。上报 Playing / 10 s Progress / Stopped，Stopped 走应用级协程。
- **音乐**：队列项 URI 为 `etkn-music://track/<id>`，在 ExoPlayer 加载线程通过 PlaybackInfo 解析为带 `X-Emby-Token/UserId/PlaySessionId/MediaSourceId` 的直链并缓存；`MediaSessionService` 提供通知、锁屏与蓝牙控制；歌词 GET 为空才 POST 在线补取。

## 性能要点

- 所有 Lazy 列表/网格使用稳定 key 与 contentType；Hero 视差只用 `graphicsLayer`，滚动不触发重新布局。
- 图片宽度按显示尺寸换算物理像素并对齐 10 个桶（160–1920），Coil 内存 22% + 磁盘 384 MB；共享 OkHttp 连接池（HTTP/2）。
- 播放器缓冲 20–90 s、起播 1.2 s；UI 时钟 4 Hz 轮询，控件 4 s 自动隐藏；Compose 关闭源码信息、release 开启 R8 与资源裁剪，按 ABI 拆包。

## 未纳入首版（后续）

影视探索 / 订阅 / 资源获取（`/api/tv/discover`）、转码协助、弹幕、字幕翻译与偏移、寻址缩略图、蓝光 ISO 专用播放、Home/Events SSE 实时失效、入库通知、共享偏好写回、局域网自动发现、应用内更新。
