# ETKN Mobile

ETKN 媒体服务器的原生 Android 手机/平板客户端。Kotlin + Jetpack Compose，界面基于 Material 3 Expressive。

## 功能

- 连接：手动填写地址，严格识别 ETKN 实例；多账号、多服务器切换。
- 首页：个性化推荐 Hero 轮播、继续观看、媒体库、各库最新、新专辑。
- 媒体库：海报墙无限滚动，按入库时间 / 名称 / TMDb / 豆瓣 / IMDb / 烂番茄 / 年份 / 随机排序，收藏筛选；收藏夹、搜索、人物作品。
- 详情：剧照头图、Logo、技术参数、多版本切换、季与分集分页、演职员、相似推荐、收藏 / 已看。
- 播放：Media3 ExoPlayer 硬解优先 + FFmpeg（NextLib）兜底，外挂字幕，音轨 / 字幕 / 倍速 / 画面比例，手势（双击快进退、横滑定位、左右竖滑亮度音量、长按 2 倍速），片头片尾跳过、自动下一集、选集面板、画中画，进度与服务器双向同步。
- 音乐：推荐 / 专辑 / 歌手 / 歌曲 / 歌单 / 文件夹，后台播放与媒体通知，同步歌词（LRC），播放队列、随机与循环、歌单管理。
- 外观：跟随服务器主题色（HCT 生成完整配色）或系统壁纸取色，浅色 / 深色 / 跟随系统 / 跟随服务器，纯黑模式，手机悬浮玻璃底栏、平板侧边导航。

## 构建

所有编译在 GitHub Actions 云端完成，见 [`.github/workflows/android.yml`](.github/workflows/android.yml)：推送到 `main` 生成签名 APK（Actions 构件），推送 `v*` 标签发布 Release。开发约束见 [AGENTS.md](AGENTS.md)，架构见 [docs/architecture.md](docs/architecture.md)。

## 许可

GPL-3.0（包含 GPL-3.0 的 NextLib FFmpeg 扩展），见 [LICENSE](LICENSE) 与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
