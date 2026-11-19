# ETKN Mobile 开发边界

- 原生 Android 手机/平板客户端（Kotlin + Jetpack Compose + Material 3 Expressive），最低 Android 8 / API 26，目标 API 36。包名 `xyz.etkn.mobile`，GitHub 仓库 `Alumos/etkn-mobile`。
- 服务端接口一律走 ETKN 专用 `/api/tv` 路由（接口来源见 ETKN TV 客户端源码与其 `docs/etkn-mobile-api.md`）；不回退到通用 Emby/Jellyfin 路由，不从 ID 拼接播放地址。客户端标识 `ETKN Mobile`。
- 连接时严格识别 ETKN：`/api/health` → `/api/tv/System/Info/Public`（`ProductName=ETKN`, `TvApiVersion=1`）→ `/api/system/tv-discovery`，跨地址候选须校验同一实例 `Id`。
- 令牌：私密账号只在内存；管理员仅在服务端 `remember_admin_account` 开启时持久化；其他账号用 Android Keystore AES-GCM 加密保存。不得打印或记录令牌、密码、带凭证的 URL。
- 图片请求只向当前 ETKN 同源地址附加 `X-Emby-Token`，跨源跳转必须 HTTPS；视频直链使用服务端 `DirectStreamUrl`，普通直链必须同源；外挂字幕同源、去掉 `UserId`、设置 `X-Emby-Token`。
- 播放上报：`Sessions/Playing` → 每 10 秒 `Progress` → `Stopped`（片尾/播完附 `PlayedToCompletion`），停止上报在应用级协程发出，不能随页面销毁丢失。
- UI 统一使用 Material 3 Expressive 组件（`MaterialExpressiveTheme`、`ShortNavigationBar`/`WideNavigationRail`、`ToggleButton` 连接组、`LoadingIndicator` 等）；配色由服务器 `theme_color` 经 HCT（material-color-utilities）生成，或 Android 12+ 壁纸取色。语义色/间距只在 `ui/theme/Tokens.kt` 定义。页面避免不必要的辅助标签与说明。
- 性能：单一 OkHttp 连接池（HTTP/2）、Coil 按显示尺寸分桶请求图片、列表全部带稳定 key/contentType、播放器双内核（`player/VideoSession` 按片源选：原盘与 HDR/杜比视界走 ExoPlayer 硬解直出 HDR，其余走 mpv `player/mpv`，MediaCodec 硬解 + gpu-next；一个内核失败自动换另一个），ExoPlayer 缺解码器时 NextLib FFmpeg 兜底；起播时 PlaybackInfo 在点击播放时即预取、不等详情，302 目标按播放器缓存；音乐曲目地址在加载线程按需解析。

- 应用更新只读公开的 GitHub Release（API 或 `SHA256SUMS.txt`），下载默认经 `gh-proxy.com`、可换 GitHub 直连，必须校验 SHA-256；外部请求不得携带任何 ETKN 令牌或账号信息。
- 全部服务端接口的说明见 `docs/api-reference.md`（供其他平台移植）；新增或改动接口时同步更新。
- 媒体管理（刷新/删除/编辑元数据/编辑图片/提取缩略图）只走 `/api/tv` 与 `/api/tv/Manage`，按 Web 端权限规则显示入口；删除必须二次确认并默认取消，不得批量或自动执行。
- 影视探索仅在 `can_access_discovery` 为真时出现；订阅与云资源获取都必须先经用户确认，不得自动重试或预取获取接口。

## 构建与容器策略

- **禁止在用户本机 Mac 运行 Gradle 或构建 APK。** 编译、单元测试、Lint 与签名全部在 GitHub Actions 官方 Ubuntu runner 上完成（`.github/workflows/android.yml`）。
- 构建 job 不接触签名凭据，产出一次性未签名 APK；签名 job 为全新 runner，不 checkout、不运行 Gradle，仅用 `apksigner` 以固定 PKCS#12 发行密钥签名并校验证书 SHA-256。
- 签名 Secrets：`ETKN_KEYSTORE_BASE64`、`ETKN_KEYSTORE_PASSWORD`、`ETKN_KEY_ALIAS`、`ETKN_KEY_PASSWORD`、`ETKN_SIGNER_SHA256`。私钥离线备份在仓库外私有目录 `~/.etkn-mobile-signing`（0700/0600），不得提交、公开或打印。
- 推送 `v*` 标签会把签名后的分 ABI APK（arm64-v8a / armeabi-v7a / x86_64 / universal）发布到 GitHub Release；versionCode = (1000 + run_number) × 10 + ABI 位。
- 蓝光 ISO 原生库 `libetkn_bluray.so` 只由 CI 用 `scripts/build_bluray_native.py`（NDK r27c、上游源码 SHA-256 校验）构建到被忽略的 `app/src/main/jniLibs/`；JNI 名称与 `third_party/bluray/bluray_jni.cpp` 保持一致，并保留 `proguard-rules.pro` 中的 keep 规则。其中链接 `third_party/dovi`（Rust 静态库，包装 dovi_tool 的 `dolby_vision` crate，`Cargo.lock` 固定依赖），CI 需 `rustup target add` 三个 Android 目标；杜比视界 profile 7 在设备有 profile 8 解码器且屏幕支持杜比视界时转 8.1 播放。
- mpv-android 与 NextLib 共用一套 FFmpeg 9 共享库（`pickFirsts` 取 mpv 的完整版，CI 校验 APK 内 libavcodec 与 mpv AAR 一致）；升级任一方都要保持 FFmpeg 主版本一致。mpv 是进程级单例，同时只能有一个 `MpvPlayer`。
- 研究用的参考仓库（CineIsle、md3e-skill 等）只放在被 `.gitignore` 忽略的 `.ref/`，不参与构建。
- 诊断日志（`data/diagnostics/Diagnostics.kt`）：只记录本应用 UID 的错误级日志（含崩溃堆栈）和 `ETKN*` 标签的关键行（来源、轨道、解码器、格式），带年份时间戳，256 KB × 8 轮转，启动时删除 7 天前的文件；「我的 → 导出日志」按日期与小时段导出，导出前去掉 URL 查询串与令牌类键值。自己写的日志只记主机、状态码、格式与 PID，不记完整播放地址、令牌或账号信息；不要再挂 Media3 `EventLogger` 这类逐事件日志。
- 区分“源码完成 / CI 构建通过 / 真机验证”，未经真机验证的行为不得写成已验证。
