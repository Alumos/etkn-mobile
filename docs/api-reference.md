# ETKN 客户端接口说明（移植参考）

本文列出 ETKN Mobile 当前**实际调用**的全部服务端接口，供移植 Windows 等其他客户端时参考。

- 来源是本仓库源码（2026-11-18，v0.5.0）。契约与 ETKN TV 客户端一致，TV 仓库 `docs/etkn-mobile-api.md` 是更早的观察记录。
- 括号里的文件路径相对于 `app/src/main/java/xyz/etkn/mobile/`。

## 1. 通用约定

### 1.1 地址与路由
- 业务接口一律使用 `/api/tv/...` 专用路由，**不要**回退到通用 Emby/Jellyfin 路由（如 `/Items`、`/Videos/{id}/stream`）。
- 不要用 ID 拼接播放地址：视频、音乐、字幕地址都由服务端在 PlaybackInfo 里给出。
- 例外只有三处：
  - 连接识别用的 `/api/health`、`/api/system/tv-discovery`；
  - 音乐封面代理 `/api/image_proxy`；
  - 影视探索的 `/api/tv/discover/*`（仍在 `/api/tv` 下）。
- 服务器地址规范化（`data/api/ServerProbe.kt`）：
  - 没有 scheme 时补 `http://`；
  - 拒绝带 userinfo、query、fragment 的地址；
  - 去掉末尾的 `/`。

### 1.2 请求头（`data/net/ApiClient.kt`）
| 头 | 值 |
|---|---|
| `Accept` | `application/json` |
| `User-Agent` | `{ClientName}/{Version} (Android)`，Windows 可改为 `(Windows)` |
| `X-Emby-Authorization` | `MediaBrowser Client="ETKN Mobile", Device="<设备名>", DeviceId="<稳定设备 ID>", Version="<版本>"` |
| `X-Emby-Token` | 登录令牌（未登录时不发） |
| `Cookie` | `etkn_session=<令牌>`（与 TV 客户端一致，和上一行同时发送） |

- 请求体都是 `application/json; charset=utf-8`。没有请求体的 POST/PUT 发 `{}`。
- Windows 端应换成自己的客户端名（例如 `ETKN Windows`）。如果服务端按客户端名做限制，需要与服务端确认。

### 1.3 响应处理
- **不要跟随重定向**：3xx 视为“地址错误，请使用最终地址登录”。
- 空响应体当作 `{}`；顶层是数组时包成 `{"Items": [...]}`。
- 列表字段的兼容读取顺序：`Items` → `results` → `resources` → `items` → `data`。如果该键的值是对象，就在对象里继续按同样顺序找（`data/net/Json.kt` 的 `rows()`）。
- 响应体大于 8 MB 视为异常。
- 状态码处理：

  | 状态 | 处理 |
  |---|---|
  | `401` | 令牌失效：清除该账号的会话，回到登录页 |
  | `404`（路径以 `/api/tv/` 开头） | 服务端版本过旧，提示“请先更新 ETKN 服务端” |
  | 其他 `≥400` | 读取 `detail` 字段作为错误文案 |

- 字段读取要宽松：缺失、类型不符时一律回退到默认值，与 TV 的 `optString` 行为相同。
- 时间单位是 **ticks**，1 ms = 10 000 ticks。

### 1.4 URL 编码
- 查询参数值使用表单式编码（Java `URLEncoder`，UTF-8，空格编码为 `+`）。
- 值为 null 的参数直接省略。

## 2. 连接与识别（不带令牌，`data/api/ServerProbe.kt`）

按顺序执行：

| # | 方法与路径 | 判定 |
|---|---|---|
| 1 | `GET {addr}/api/health` | `status == "ok"` |
| 2 | `GET {addr}/api/tv/System/Info/Public` | `ProductName == "ETKN"` 且 `TvApiVersion == 1`；记下 `Id`（实例 ID）和 `ServerName` |
| 3 | `GET {addr}/api/system/tv-discovery` | 可选。`ProductName` 必须是 `"ETKN"`。读取 `MediaServerAddress`、`ApiPort`（默认 5257）、`MediaServerPort` |

第 3 步之后的候选媒体地址规则：

- `MediaServerAddress` 与输入地址同主机：直接用它作为候选。
- 否则：只有输入端口等于 `ApiPort` 时，才用 `MediaServerPort` 换掉端口，作为候选。
- 有候选时，对候选重新执行第 1、2 步，并且它返回的 `Id` 必须与原地址的 `Id` 一致，否则拒绝。
- 用户输入的就是媒体地址时，不需要切换。

## 3. 账号（`data/api/EtknApi.kt` → Account；`data/session/SessionManager.kt`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tv/Users/AuthenticateByName` | 请求体 `{"Username","Pw"}`，不带令牌。返回 `AccessToken`、`User`（用户对象）、`PrivateAccount`（顶层布尔） |
| GET | `/api/tv/Users/Me` | 校验令牌，返回用户对象 |
| GET | `/api/tv/Users/Me/AccountPreferences` | 读取 `remember_admin_account` |
| POST | `/api/tv/Sessions/Logout` | 退出（失败时忽略） |
| GET | `/api/tv/Users/Me/ClientPreferences` | 服务端外观：`theme_color`（种子色）、`mode`（深/浅色）；`playback_preferences{preferences, global_preferences, effective_preferences, hide_watched_latest}` |
| POST | `/api/tv/Users/Me/PlaybackPreferences` | 一次一个键：`audio_lang_priority`（`chi/yue/original/eng/jpn/kor`）/ `subtitle_priority`（`effect/chs/cht/chs_eng/…/original/eng/jpn/kor`）为有序数组，`null` = 跟随全局；`hide_watched_latest` 布尔。之后重读 ClientPreferences。服务端据此给出 PlaybackInfo 的 `DefaultAudioStreamIndex / DefaultSubtitleStreamIndex`（客户端在两个内核上都按它选初始轨道，`-1` = 关闭字幕），并在最新列表去掉已看 |
| GET | `/api/tv/auth/check_status` | 读取 `user.can_access_discovery`，决定是否显示影视探索 |

**用户对象字段：**
- `Id`、`Name`
- `Policy.IsAdministrator`
- `PrivateAccount`
- `TvPermissions.CanRefreshMetadata`、`TvPermissions.CanDeleteMedia`

**令牌保存规则（必须遵守）：**
- 私密账号：令牌只放内存。
- 管理员：只有在 `remember_admin_account` 为真时才持久化。
- 其他账号：加密后持久化。Android 用 Keystore AES-GCM，Windows 建议用 DPAPI 或 Credential Manager。
- 不要把令牌、密码或带令牌的 URL 写进日志。

## 4. 首页与媒体库

下文 `U` = `/api/tv/Users/{userId}`。

**列表字段常量 `LIST_FIELDS`**：
`PrimaryImageAspectRatio,Overview,CommunityRating,TMDbRating,DoubanRating,IMDbRating,CriticRating,ProductionYear,RecursiveItemCount,UserData`

| 方法 | 路径与参数 | 用途 |
|---|---|---|
| GET | `U/Views` | 媒体库列表（带 `CollectionType`） |
| GET | `U/Items/Resume?Limit=24&Fields=Overview` | 继续观看 |
| GET | `U/Items/Latest?ParentId=&IncludeItemTypes=&Limit=24` | 各库最新入库 |
| GET | `U/Items/PersonalRecommendations?Stream=true&Limit=1&Fields=Overview&ShuffleSeed=&Cursor=` | 首页轮播推荐。逐条拉取，返回 `Items` 和 `NextCursor`（字符串，用于下一页） |
| GET | `U/Items?Recursive=true&ParentId=&IncludeItemTypes=&Limit=&StartIndex=&SortBy=&SortOrder=&Fields=LIST_FIELDS[&Filters=IsFavorite][&Genres=]` | 库内海报墙，返回 `Items` 和 `TotalRecordCount` |
| GET | `U/Items?ParentId=` | 文件夹子项 |
| GET | `U/Browse?Limit=&StartIndex=&SortBy=DateCreated&SortOrder=Descending&Filters=IsFavorite` | 收藏 |
| GET | `U/Items?Recursive=true&IncludeItemTypes=Movie,Series&SearchTerm=&SortBy=SortName&Limit=&StartIndex=&Fields=…` | 库内搜索 |
| GET | `U/Libraries/{libraryId}/Collections?Limit=200` | 库内合集（库页「合集」，仅 path 来源的库有结果）；成员用 `U/Items?ParentId=<合集ID>` |
| GET | `U/Items/{id}/Collections?Limit=60` | 电影所属合集（详情「所属合集」） |
| GET | `U/Facets?ParentId=&Kind={Types\|Networks\|Companies}` | 分类卡片：`Items[]{Id, Name, Kind(Genres/Tags/Networks/Companies), Value, Type=TvFacet, ImageTags.Primary}` |
| GET | `U/Browse?ParentId=&{Kind}={Value}&Limit=&StartIndex=&SortBy=&SortOrder=&Fields=LIST_FIELDS` | 一张分类卡片下的影视 |
| GET | `U/FacetArtwork?ParentId=&Kind=&Value=&tag=&maxWidth=` | 分类封面（二进制图） |
| POST | `U/FacetArtwork?ParentId=&Kind=&Value=[&Action=icon]` | 生成类型/标签封面，或为电视网/出品公司拉取标志（`Action=icon`）；管理员或可刷新元数据的账号，逐张提交 |
| POST | `/api/tv/Items/{libraryId}/Scan` | 扫描媒体库，`{}`；仅管理员，且库 `SourceType` 为 `path` 或 `list` |
| POST | `U/Items/{id}/HideFromResume` | 从继续观看中移除 |

**`SortBy` 可选值**：
- `DateCreated`、`PremiereDate`、`SortName`、`ProductionYear`、`DatePlayed`、`Random`；
- 评分类：`TMDbRating`、`DoubanRating`、`IMDbRating`、`CriticRating`。
- `Random` 时固定发 `SortOrder=Ascending`。

**`IncludeItemTypes` 按库类型映射**：

| `CollectionType` | `IncludeItemTypes` |
|---|---|
| `movies` | `Movie` |
| `tvshows` | `Series` |
| `homevideos` | `Video` |
| `boxsets` | `BoxSet` |
| `musicvideos` | `MusicVideo` |
| 其他 | `Movie,Series,Video` |

## 5. 详情、剧集与用户数据

| 方法 | 路径与参数 | 用途 |
|---|---|---|
| GET | `U/Items/{id}[?Fields=MediaSources,MediaStreams,Overview]` | 条目详情 |
| GET | `/api/tv/Items/{id}/Similar?UserId=&Limit=20` | 相似推荐 |
| GET | `/api/tv/Persons/{id}?UserId=` | 演职人员 |
| GET | `/api/tv/Persons/{id}/Items?UserId=&IncludeItemTypes=Movie,Series&StartIndex=&Limit=40` | 人物作品 |
| GET | `/api/tv/Shows/{seriesId}/Seasons?UserId=` | 季列表 |
| GET | `/api/tv/Shows/{seriesId}/PlaybackTarget?UserId=&Fields=MediaSources,MediaStreams,Overview[&SeasonId=]` | 剧集“继续/开始播放”的目标集，取 `Items[0]` |
| GET | `/api/tv/Shows/{seriesId}/EpisodePage?UserId=&Limit=&SeasonId=&AnchorId=&StartIndex=` | 分页剧集。返回 `Items`、`StartIndex`、`NextStartIndex`、`TotalRecordCount`；`AnchorId` 用于定位到某一集所在的页 |
| GET | `/api/tv/Items/{id}/NextEpisode?UserId=` | 下一集，取 `Items[0]` |
| POST / DELETE | `U/FavoriteItems/{id}` | 收藏 / 取消收藏 |
| POST / DELETE | `U/PlayedItems/{id}` | 标记已看 / 未看 |

**条目对象主要字段**（`data/model/Models.kt` → `Item.from`）：
- 基本信息：`Id`、`Name`、`Type`、`CollectionType`、`Overview`、`Taglines`、`ProductionYear`、`PremiereDate`、`EndDate`、`OfficialRating`
- 评分：`CommunityRating`、`CriticRating`、`TMDbRating`、`DoubanRating`、`IMDbRating`
- 时长与分类：`RunTimeTicks`、`Genres`、`Studios[].Name`
- 剧集层级：`SeriesId`、`SeriesName`、`SeasonId`、`SeasonName`、`ParentIndexNumber`、`IndexNumber`、`IndexNumberEnd`
- 图片：`ImageTags{Primary,Thumb,Logo}`、`BackdropImageTags`、`ParentBackdropItemId` / `ParentBackdropImageTags`、`ParentLogoItemId` / `ParentLogoImageTag`、`ParentThumbItemId` / `ParentThumbImageTag`、`SeriesPrimaryImageTag`
- 用户数据：`UserData{Played,IsFavorite,PlaybackPositionTicks,PlayCount}`
- 关联：`People[]{Id,Name,Role,Type,PrimaryImageTag}`、`MediaSources[]`、`ProviderIds`
- 状态：`ChildCount`、`RecursiveItemCount`、`IsFolder`、`LocationType`（`Virtual` 表示缺集）、`Status`

## 5.1 媒体管理（长按菜单 / 详情页“更多”）

权限来自用户对象：
- 管理员（`Policy.IsAdministrator`）可用全部操作；
- `TvPermissions.CanRefreshMetadata` 只能刷新；
- `TvPermissions.CanDeleteMedia` 只能删除。

管理操作只对本地条目（`Id` 形如 `item-<数字>`）开放。服务端会对每个请求重新校验权限，返回 403 时直接提示用户。服务端实现见 ETKvNext 的 `api/tv.py`、`api/tv_management.py`、`api/web_client.py`。

| 方法 | 路径与参数 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/tv/Users/Me` | — | 每次打开菜单时刷新一次权限 |
| POST | `/api/tv/Items/{id}/Refresh?Action={replace_metadata\|replace_images\|replace_metadata_images}` | 刷新 | 返回 `submitted`、`RefreshRunId`；如果有 `reason=task_already_running`，表示已有刷新任务在运行 |
| GET | `/api/tv/Items/{id}/Refresh/{runId}` | 刷新 | 返回 `{RefreshRunId, status, finished}`。客户端每 5 秒查询一次，最多 10 分钟 |
| DELETE | `/api/tv/Items/{id}` | 删除 | 联动删除全部版本（剧集会删除所有季和分集），返回 204 |
| DELETE | `/api/tv/Items/{id}/Versions/{versionId}` | 删除 | 只删除一个版本 |
| POST | `/api/tv/Manage/Items/preview-thumbnails` | 管理员 | 请求体 `{"media_item_ids": ["item-…"]}`，用于提取进度条预览缩略图；已经有缩略图的会跳过 |
| GET | `/api/tv/Manage/RatingOptions` | 管理员 | 自定义分级选项 `items[]{label,value}`。保存时传 `value`，界面显示 `label` |
| PUT | `/api/tv/Items/{id}/Metadata` | 管理员 | 请求体 `{name, overview, genres[], tags[], custom_rating, douban_id, tmdb_rating, douban_rating}`；评分范围 0–10，留空时传 null |
| GET | `/api/tv/Items/{id}/RemoteImages?include_all_languages=` | 管理员 | 在线候选图 `items[]{type, url, thumbnail_url, source_url, source_provider, language, width, height}`；`url` 和 `thumbnail_url` 是同源的 `/api/image_proxy?...` 地址 |
| POST | `/api/tv/Items/{id}/RemoteImages` | 管理员 | 选定候选图，请求体 `{url: source_url, image_type: primary\|backdrop\|logo, source_provider, language}` |
| POST | `/api/tv/Items/{id}/Images/Upload?image_type={Primary\|Backdrop\|Logo}&file_name=` | 管理员 | 上传本地图片：请求体直接是图片字节，`Content-Type` 为图片 MIME |

**删除流程：**
1. 先请求 `GET U/Items/{id}`，从 `Versions[]` 读取版本列表；没有这个字段时，改用 `MediaSources[]` 里 `Id` 形如 `version-N` 的项，去掉前缀后就是版本 ID。
2. 有多个版本时，让用户选择删除“全部版本”还是其中一个。
3. 确认框要写明删除范围：整部剧（含全部季和分集）、本季（含全部分集），或该条目的全部版本。
4. 默认选中“取消”。

**编辑元数据：** 需要先读取 `GET U/Items/{id}` 的完整字段：`Name`、`Overview`、`CustomRatingCode` / `CustomRating`、`TMDbRating` / `CommunityRating`、`DoubanRating`、`DoubanId` / `ProviderIds.Douban`、`Genres`、`Tags`。

**同一个页面里的变化：** 收藏、标记已看、移出继续观看、删除，以及编辑完成后，客户端都会广播一次条目变化。各列表就地删除该行或重新读取该行，详情页重新加载；如果是当前条目被删除，就退出详情页。

## 6. 播放

### 6.1 获取播放信息
`POST /api/tv/Items/{id}/PlaybackInfo?UserId=`，请求体 `{}`。音乐请求不带 `UserId`。

**返回：**
- `PlaySessionId`
- `MediaSources[]`
- `Danmaku.Available`（可选，见第 7 节）

**`MediaSources[]` 字段：**
- 基本：`Id`、`Name`、`Path`、`Container`、`DirectStreamUrl`、`RunTimeTicks`、`Size`、`Bitrate`
- 默认轨：`DefaultAudioStreamIndex`、`DefaultSubtitleStreamIndex`
- `MediaStreams[]`：`Index`、`Type`、`Codec`、`Language`、`Title`、`DisplayTitle`、`IsDefault`、`IsExternal`、`DeliveryUrl`、`Width`、`Height`、`Channels`、`BitRate`、`VideoRange`、`VideoRangeType`、`ColorTransfer`、`Profile`
- `Chapters[]`：`StartPositionTicks`、`Name`、`MarkerType`、`ChapterIndex`、`ImageTag`、`ImagePath`

多版本：每个 `MediaSource` 是一个版本，客户端在其中切换（`Name` 为空时用分辨率和 HDR 生成标签）。

片头片尾：章节的 `MarkerType` 标出片头 / 片尾区间，客户端据此显示“跳过”按钮或自动跳过（`player/VideoSession.kt` 的 `segmentAt`）。

### 6.2 播放地址规则（`player/PlaybackAddress.kt`）

**视频：**
- 使用 `DirectStreamUrl`：相对路径拼到服务器地址上；绝对地址必须**同源**（scheme、host、port 都相同），否则拒绝。
- ISO 例外：原盘（判定见下）且 `Path` 含 `.iso` 时改用 `Path`；其余原盘（如 115 STRM）仍用 `DirectStreamUrl`。地址与服务器同源时补上 `X-Emby-Token` 和 `UserId` 查询参数。

**音乐：**
- 以上述地址为基础，再设置查询参数 `X-Emby-Token`、`UserId`、`PlaySessionId`、`MediaSourceId`。
- 播放到这首曲目时才请求 PlaybackInfo，不要整张专辑预先请求。

**蓝光原盘 ISO：**
- 判定为原盘的条件（任一）：`Container == iso`；`Path` 含 `.iso`；有 `BlurayPlaylist`；任一 `MediaStreams[]` 带 `TransportStreamId`。后两项来自服务端的原盘探测，只出现在 `/api/tv` 的 PlaybackInfo 里。
- 115 STRM 原盘：`Container` 是 `strm`，`Path` 是 `/api/p115/play/{PickCode}` 形式的 STRM 目标（主机为服务端 `service_ip`，外网未必可达），都不带 `.iso`。地址用同源的 `DirectStreamUrl`（`/api/tv/Videos/{id}/original.strm`，由服务端 302 到网盘直链）。
- 本客户端的兜底：普通解析报 `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` 时，对同一地址再按原盘打开一次。
- 客户端用 HTTP `Range` 随机读取原文件，存储端必须返回 `206` 和 `Content-Range`。
- `MediaSources[].BlurayPlaylist`：服务端探测到的正片播放列表编号，可选；没有时由 libbluray 自己选正片。
- `MediaStreams[].TransportStreamId`：该音轨/字幕/视频在原盘 MPEG-TS 里的 PID（服务端从 CLPI 或 M2TS 采样得到）。客户端按 PID 把服务端的轨道名称、默认音轨（`DefaultAudioStreamIndex`）对应到实际解出的轨道，不要按顺序对应；TrueHD 与其 AC3 内核共用一个 PID，需再按编码区分。
- `MediaStreams[].Width/Height/Channels`（同一 PID）可作为原盘轨道参数的兜底：只做解封装的 FFmpeg 拿不到画面尺寸，本客户端先从码流 SPS 解析，解析不到再用服务端值；部分硬件解码器（如高通）拒绝 0×0 的视频格式。
- 杜比视界 profile 7 原盘同时含基础层（主视频 PID `0x1011`）和增强层（`0x1015`），服务端探测结果里两者都是 `hevc` 视频流；只播放主视频，不要把增强层当作可选视频轨。
- 本仓库的做法：libbluray 加 libudfread 读取原盘，只做解封装的 FFmpeg 拆出流，交给 Media3 硬件解码；见 `third_party/bluray`。
- 加密原盘和 BD-J 菜单不支持。
- 杜比视界 profile 7（双层）只解码 HDR10 基础层。

**外挂字幕：**
- 来源是 `IsExternal == true` 的流的 `DeliveryUrl`，必须同源。
- 去掉 `UserId` 参数，设置 `X-Emby-Token` 查询参数。

### 6.3 播放上报（视频与音乐共用）

| 时机 | 请求 |
|---|---|
| 开始 | `POST /api/tv/Sessions/Playing` |
| 播放中，每 10 秒 | `POST /api/tv/Sessions/Playing/Progress` |
| 结束 | `POST /api/tv/Sessions/Playing/Stopped` |

**视频请求体：**
```json
{
  "ItemId": "", "UserId": "", "MediaSourceId": "", "PlaySessionId": "",
  "PositionTicks": 0, "RunTimeTicks": 0, "IsPaused": false, "CanSeek": true,
  "PlayMethod": "DirectStream", "Client": "ETKN Mobile", "TranscodeSessionId": "",
  "PlayedToCompletion": true,
  "DanmakuActive": true
}
```
- `PlayedToCompletion` 只在 Stopped、并且播完或跳过片尾时发送。
- `DanmakuActive` 只在非 Stopped、并且弹幕可用且已开启时发送。

**音乐请求体：** `ItemId`、`UserId`、`MediaSourceId`、`PlaySessionId`、`PositionTicks`、`IsPaused`、`PlayedToCompletion`。

**要点：**
- Stopped 上报要在应用级任务里发出，不能因为关闭播放窗口而丢失。
- 上报的回复里可能带 `Danmaku.Available`，用来实时更新弹幕是否可用。

### 6.4 进度预览图与字幕偏移

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tv/Items/{id}/PreviewThumbnails?MediaSourceId=` | 拖动进度条的预览图清单：`MediaSourceId, AspectRatio, IntervalSeconds, Thumbnails[]{Index, PositionTicks, ImageTag}`；未提取时 204（空体）。帧图：`GET /api/tv/Items/{id}/PreviewThumbnails/{index}?MediaSourceId=&tag=<ImageTag>`（JPEG，同源带令牌） |
| POST | `/api/tv/Items/{id}/{mediaSourceId}/SubtitleOffsets` | 保存字幕偏移 `{SubtitleKey, OffsetMs}`（−600000…600000，0 = 删除），Web/TV 共用。键与已存值在 PlaybackInfo 的字幕流上：`SubtitleOffsetKey, SubtitleOffsetMs`；`IsBurnedIn` 的字幕不能偏移。客户端只在用户点「保存」时提交；当前只有 mpv 内核能平移字幕（`sub-delay`），ExoPlayer 播放时不显示偏移控件 |

## 7. 弹幕

| 方法 | 路径与参数 | 说明 |
|---|---|---|
| GET | `/api/tv/System/Danmaku` | 服务端弹幕总开关：`Available`（客户端只读） |
| GET | `/api/tv/Items/{id}/Danmaku?local=true&session={PlaySessionId}&start={ms}` | 从 `start` 开始一个 120 秒窗口的弹幕。返回 `enabled`、`status`、`comments[]`（解析见 `player/danmaku/Danmaku.kt`） |

拉取节奏：
- 提前 40 秒预取下一个窗口。
- 失败后按 15 秒、1 秒重试。
- 跳转进度后重新从当前位置拉取。

## 8. 音乐

下文 `M` = `U/Music`。

| 方法 | 路径与参数 | 说明 |
|---|---|---|
| GET | `M?ParentId=&Key=&Tab=recommended&SortBy=Name&SortOrder=Ascending&FrequentLimit=12` | 音乐首页，返回 `Latest`、`Recent`、`Frequent` |
| GET | `M?ParentId=&Tab=&Key=&SortBy=&SortOrder=[&Tracks=1]&StartIndex=&Limit=60` | 分类列表。`Tab` 取 `albums`、`artists`、`songs`、`playlists` 等；`Key` 指定进入某个专辑、歌手或文件夹。返回 `Items`、`Folders`、`TotalRecordCount`、`Title`、`Artist.Overview` |
| GET | `M?ParentId=&Tab=playlists&Key=&SortBy=Name&SortOrder=Ascending` | 歌单，返回 `Playlists[]{Key,Name,Count}` |
| POST | `M/Playlists` | 新建歌单，请求体 `{"Name"}`（最多 80 字），返回 `Key` |
| DELETE | `M/Playlists/{key}` | 删除歌单 |
| POST / DELETE | `M/Playlists/{key}/Items/{itemId}` | 加入 / 移出歌单 |
| GET，必要时 POST | `/api/tv/Music/{id}/Lyrics?MediaSourceId=` | 歌词，返回 `text`。GET 得到空文本时，才 POST 同一路径触发在线查找 |

不带 `Key` 浏览 `albums`、`artists`、`songs` 时，用 `SortBy=DateCreated&SortOrder=Descending`；其余情况用 `Name` 升序。

## 9. 影视探索（TMDb；需要 `can_access_discovery`）

下文 `D` = `/api/tv/discover`。

`kind` 的取值：

| | 路径片段 | `media_type` |
|---|---|---|
| 电影 | `movie` | `movie` |
| 剧集 | `series` | `tv` |

| 方法 | 路径与参数 | 说明 |
|---|---|---|
| GET | `D/options?media_type={movie\|series}` | 筛选项，`genres[]`、`countries[]`、`languages[]`、`keywords[]`、`studios[]`、`ratings[]`，每项 `{label,value}` |
| GET | `D/people?q={姓名}` | 演员/导演选择器的人物搜索。返回 `items[]{id,name,profile_path,department,known_for}`（TMDb 热度序，最多 20，`department` 为 `Acting`/`Directing` 等）。客户端：未点选结果就确认时，取同名且部门匹配者，否则取该部门第一位 |
| POST | `D/query/{movie\|series}` | 海报墙或片名搜索（请求体见下）。返回 `results[]`、`total_pages`（最多 500） |
| GET | `D/series/{tmdbId}` | 剧集详情，含 `seasons[]{season_number,name,air_date,episode_count,poster_path,in_library,subscription_status}` |
| GET | `D/media/{movie\|series}/{tmdbId}/credits` | 演职员，含 `cast[]{id,name,character,profile_path}` |
| GET | `D/person/{personId}/credits` | 人物与作品：`person{name,biography,profile_path}`、`works[]` |
| POST | `D/subscribe` | 订阅电影或单季（请求体见下）。返回 `demand_id` |
| GET | `D/shared-resources?tmdb_id=&item_type={movie\|tv\|season}&q={片名}&limit=100` | 共享池资源。可能带 `versions[]`，客户端按版本分组 |
| GET | `D/re0-resources?tmdb_id=&media_type=&require_complete=false[&season=]` | re0 资源 |
| GET | `D/channel-resources?tmdb_id=&media_type=&q=&year=[&season_number=]` | 频道资源 |
| POST | `D/shared-resources/acquire{W}` | 请求体 `{"source": <共享池行>}` |
| POST | `D/channel-resources/acquire{W}` | 请求体 `{"resource": <频道行>}` |
| POST | `D/re0-resources/acquire{W}` | 请求体 `{"slug","title","tmdb_id","media_type"[, "season_number"]}` |

- `{W}` = `?TmdbId=&MediaType={movie|series}[&SeasonNumber=]`。
- 三个 acquire 接口都返回 `workflow_run_id`。

**`query` 请求体：**
```json
{
  "page": 1, "query": "片名或空", "sort_by": "popularity.desc", "hide_in_library": false,
  "with_genres": "16,35", "release_date_gte": "2020-01-01", "release_date_lte": "2029-12-31",
  "vote_average_gte": 7.0, "keywords": [], "studios": []
}
```
- `sort_by` 可选：`popularity.desc|asc`、`vote_average.desc`。
- 按日期排序时，电影用 `primary_release_date.desc|asc`，剧集用 `first_air_date.desc|asc`。
- 没有用到的筛选项不发。

**`subscribe` 请求体：**
```json
{
  "tmdb_id": "", "name": "片名[ 第 N 季]", "entity_type": "movie|season",
  "poster_path": "", "release_date": "", "first_air_date": "", "priority": 50,
  "season_number": 1
}
```
- `season_number` 只在订阅单季时发送。
- “订阅缺失季”是对每个可订阅的季逐一调用，遇到错误就停下，已成功的保留。

**探索条目字段：**
- `id`、`title` / `name`、`media_type`
- `poster_path`、`backdrop_path`（拼成 `https://image.tmdb.org/t/p/{w342|w780|original}{path}`）
- `overview`、`tagline`、`vote_average`、`release_date` / `first_air_date`
- `in_library`、`media_item_id`（已入库时对应的媒体库 ID，可直接打开播放）、`subscription_status`

**规则：**
- 订阅和获取资源前都必须让用户确认，获取可能消耗服务端积分。
- 不要自动重试，也不要预取 acquire 接口。
- 三个资源来源要并发搜索，并分别显示各自的结果或失败原因。

## 9.1 变更推送与入库通知（`data/events`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tv/Users/{uid}/Home/Events[?ItemId=]` | **SSE**（`Accept: text/event-stream`，`X-Emby-Token`，禁止跳转，读超时 25 s）。首页读 `event: home` 的 `{revision}`，变化即静默重载；详情带 `ItemId`，读 `event: items` 的 `{Initial, Updated, Added, Removed, Versions}`，非初始即重载。`event: auth-expired` 或 401 = 会话失效。前台连接，断线 3 s 重连 |
| GET | `/api/tv/Users/{uid}/IngestUpdates?After={cursor}` | 入库通知：`Items[]{Id, VersionId, EventId, Outcome(ingested/replaced/version_added), Name, SeriesName, Type}`、`Cursor`、`Initial`、`HasMore`。账号首次只记游标；每轮最多 5 页；按 `EventId` 去重 |
| GET | `D/acquire-status/{runId}` | 已确认的获取（`workflow_run_id`）结果：`finished, failed, title, error, ingest_items[]`（同上的行）。本机跟踪 7 天，完成即停 |

客户端在前台每分钟检查一次入库与获取结果，逐条发系统通知（点按打开条目）；后台不运行。

## 10. 图片（`data/api/ImageUrls.kt`）

| 用途 | URL |
|---|---|
| 条目图片 | `{server}/api/tv/Items/{id}/Images/{Primary\|Thumb\|Backdrop\|Logo}?maxWidth={桶}&quality=90&tag={tag}` |
| 用户头像 | `{server}/api/tv/Users/{userId}/Images/Primary?maxWidth=160&tag=` |
| 章节缩略图 | `{server}/api/tv/Items/{id}/Images/Chapter/{index}?maxWidth=&quality=85&tag=`；如果 `ImagePath` 以 `/api/` 开头，就直接用它 |
| 音乐封面 | 有 `ArtworkHash` 时：`{server}/api/image_proxy?cache={hash}&maxWidth=`（歌手再加 `&variantGroup=avatar`） |

**宽度桶**：160、240、320、480、640、800、1080、1280、1600、1920，取第一个不小于实际像素宽度的值。这样不同页面能共用同一张缓存图。

**回退顺序：**
- 剧集单集：剧集主图 → 本身主图 → 季主图。
- 横图：本身 Thumb → 单集主图 → 父级 Thumb → 背景图 → 主图。

**图片鉴权：**
- 只对与当前 ETKN 服务器同源的图片请求附加 `X-Emby-Token` 头。
- 跳转到其他源时必须是 HTTPS。

## 11. 应用更新（与 ETKN 服务端无关）

- `GET https://api.github.com/repos/Alumos/etkn-mobile/releases/latest`：读取 `tag_name`、`body`、`assets[]{name,browser_download_url,size,digest}`，其中 `digest` 形如 `sha256:<hex>`。
- 上面失败时，读取 `https://github.com/Alumos/etkn-mobile/releases/latest/download/SHA256SUMS.txt`。
- 下载默认加前缀 `https://gh-proxy.com/`，失败时改用 GitHub 直连。下载后必须校验 SHA-256。
- 这类外部请求**不得**携带 ETKN 令牌或账号信息。
- Windows 端如果另开仓库，按同样的方式替换仓库名和资产命名规则（`Releases.kt`）。

## 12. Windows 移植要点

- 同一个 ETKN 实例只需要一个 HTTP/2 连接池，所有 `/api/tv` 请求共用。
- 每个 API 调用都绑定发起时的会话。切换账号后，要丢弃旧会话的返回结果（`ActiveSession.generation`）。
- 播放器需要支持：
  - 直链播放；
  - 外挂字幕（SRT、ASS/SSA、VTT 等）；
  - 多音轨；
  - 能读取章节 `MarkerType` 来跳过片头片尾；
  - 弹幕叠加（可参考 `DanmakuLayout` 的轨道分配算法）。
- 首页轮播是“流式”的：先显示第一条，后续条目边到边追加（`HomeViewModel.loadHero`）。
