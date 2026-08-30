# MC语音

MC语音是面向 Minecraft Fabric 的文字转语音模组，当前提供 26.x、1.21.11、1.21.8 三个 jar。输入文字后，本地生成中文语音，并通过 Simple Voice Chat 或 Plasmo Voice 发给服务器里其他安装了对应语音模组的玩家。

本模组基于 [FlooferLand/ttvoice-mod](https://github.com/FlooferLand/ttvoice-mod) 开发，遵循 GPLv3 许可证。

## 当前功能

- Windows x64 打包，macOS/Linux 会显示不支持提示
- 中文配置界面、说话界面、聊天栏指令
- 自动朗读开关：聊天框发送的文字可直接说出来
- 音量调节：支持 0%-200%
- 传播距离：支持 1-128 格，实际生效受 SVC/PV 服务端和群组规则限制
- 通过 Simple Voice Chat 播放音频
- 支持 Plasmo Voice 服务端桥接，PV 玩家也能听到 TTS
- 支持 Piper 离线中文模型，可一键下载或手动放入多个声线
- 支持 Sherpa-onnx 离线中文模型，提供更多本地声线
- 支持 Windows SAPI 系统声线，不需要额外下载模型
- 支持外部 TTS 命令，可接入 edge-tts、自建脚本或任意能输出 WAV 的工具
- 支持外部 TTS 服务，可选用内置免费 TTS、URL 模板或 OpenAI 兼容接口
- 默认按键：`~` 打开说话界面，`X` 打开配置菜单

## 指令

```text
/mcvoice say <文本>
/mcvoice stop
/mcvoice test
/mcvoice auto
/mcvoice auto on
/mcvoice auto off
/mcvoice volume <0-200>
/mcvoice distance <1-128>
/mcvoice external on
/mcvoice external off
/mcvoice external set <命令>
/mcvoice voice list
/mcvoice voice set <声线ID>
/mcvoice config
```

## Plasmo Voice 适配

要让其他 PV 玩家听到 MCvoice 的 TTS，服务器必须同时安装 MCvoice、Plasmo Voice，并在需要群组时安装 `pv-addon-groups`。客户端只负责本地生成 48kHz/16bit 单声道 PCM，并把音频通过自定义数据包发给服务器；服务器再通过 PV API 广播给其他 PV 玩家。

- 如果玩家在 PV 群组中，使用群组广播源，群组规则优先，忽略距离滑块。
- 如果没有群组，使用 PV 近聊源，并沿用 MCvoice 的距离滑块（1-128 格）。
- Simple Voice Chat 接入保留，SVC 和 PV 可以同时存在。

## 添加 Piper 中文声线

模组不再内置模型，避免 jar 体积过大。

1. 在配置界面点击“下载中文声线”，从国内镜像下载花颜或花颜低配。
2. 也可以打开 `mcvoice/models`，手动放入声线的 `.onnx` 和 `.onnx.json` 两个文件。
3. 重进游戏后，配置界面或 `/mcvoice voice list` 会显示这些声线。

已知可用声线示例：

```text
zh_CN-huayan-medium
zh_CN-huayan-x_low
zh_CN-chaowen-medium
```

## 添加 Sherpa 中文声线

在“下载中文声线”页面右侧下载 Sherpa 模型。模组会自动解压到 `mcvoice/models/sherpa`，无需手动处理压缩包。

也可手动放入模型目录，格式要求为：

```text
mcvoice/models/sherpa/<模型名>/
  model.onnx
  tokens.txt
  lexicon.txt（可选）
```

## Windows SAPI 系统声线

在 Windows 配置界面切换到“系统声线 · 声线名”即可使用系统已安装的语音。SAPI 不依赖模型文件，但需要系统里已经安装对应的中文语音包。

## 外部 TTS

### 外部 TTS 命令

在配置界面的“高级设置”里勾选“使用外部TTS命令”，并填写命令。命令中支持 `{text}` 和 `{file}`：

```text
edge-tts --voice zh-CN-XiaoxiaoNeural --text "{text}" --write-media "{file}"
```

也可以使用环境变量 `MCVOICE_TEXT` 和 `MCVOICE_OUT`，然后命令只负责读取文本并生成 WAV。

### 外部 TTS 服务

在“高级设置”里打开“外部TTS服务设置”，有三种请求方式：

- 免费 TTS（内置）：默认使用微软 Edge 直连，无需 API Key，可直接生成中文语音。
- URL 模板：适合自建 GET 接口。
- OpenAI 兼容：适合 OpenAI 或兼容服务的 `/audio/speech` 接口。

免费 TTS 模式提供线路按钮，可在微软 Edge 直连和 apizero 备用之间切换。微软 Edge 直连最稳定，支持标准中文音色；apizero 备用支持四川话等音色。如果备用线路限流或不可用，模组会自动回退到微软 Edge 直连，避免出现“显示生成成功但没有声音”的情况。
免费模式下会隐藏服务地址、API Key 和模型输入框，音色改为点击切换。

外部 TTS 服务设置里新增“服务输出音量”滑条，范围 0%-200%，三种请求方式都会生效。

URL 模板示例：

```text
http://127.0.0.1:9880?text={text}&voice={voice}
```

OpenAI 兼容地址示例：

```text
https://api.openai.com/v1/audio/speech
```

除免费模式外，其他方式都可以填写 API Key、音色名和模型名。服务支持返回 WAV、常见格式和 MP3。

## 版本历史

### 0.1.0

- 从原 ttvoice 重写为独立的 MCvoice 模组，适配 Minecraft 26.2 / Fabric。
- 中文配置界面、说话界面、聊天栏指令。
- 接入 Piper 中文声线、Windows SAPI 系统声线。
- 通过 Simple Voice Chat 在服务器内播放，其他人安装 SVC 后即可听到。

### 0.1.1

- 只修复一个 bug，不新增功能：修复无声音/配置界面无可选声线的问题。

### 0.1.2

- 修复另一台机器复测时仍无声音的问题。
- 完善模型加载和默认声线识别。

### 0.1.3

- 新增音量调节，支持 0%-200%。
- 新增传播距离设置，支持 1-128 格。
- 新增外部 TTS 命令，可接入 edge-tts、自建脚本或任意能输出 WAV 的工具。

### 0.1.4

- 新增外部 TTS 服务设置界面，支持 URL 模板和 OpenAI 兼容接口。
- 高级设置补充字段说明和鼠标悬停提示。
- 部署脚本同步到 `26.2` 模组目录和 `E:\Bakabot历史\MCvoice` 备份目录。

### 0.1.5

- 新增免费外部 TTS 服务，无需 API Key。
- 免费模式支持切换可用音色。

### 0.1.6

- 免费模式切换为专用输入界面，隐藏不需要填写的字段，音色改为点击切换。
- 配置页增加 `mcvoice/models` 文件夹入口。
- 恢复模型格式识别，新增 Sherpa 模型目录。
- 新增外部 TTS 服务输出音量滑条，三种请求模式都会生效。

### 0.1.7

- 移除内置模型，改为下载页直接下载，jar 体积从约 131MB 降到约 61MB。
- 接入 Sherpa-onnx，新增多个中文本地声线。
- 下载页分为 Piper 和 Sherpa 两组，Sherpa 模型自动解压到 `mcvoice/models/sherpa`。
- 按 Windows x64 打包，macOS/Linux 显示不支持提示。

### 0.1.9

- 模型下载页改为每个模型独立显示状态：未下载、下载中、已下载、下载失败。
- 已下载判断复用模型校验逻辑，避免 `.part` 或空目录被误认为已经下载完成。
- 下载失败后自动清理临时文件、无效模型文件或残缺目录，并恢复为可点击重试。
- 修正 `fabric.mod.json` 许可证字段为 `GPL-3.0-only`。
- 新增 Plasmo Voice 服务端桥接：客户端把 TTS PCM 发给服务器，PV 服务器再广播给其他 PV 玩家。
- PV 群组规则优先；未开群组时沿用现有距离滑条作为近聊距离。
- 工程拆分为 3 个目标 jar，26.x 覆盖 26.1、26.1.1、26.1.2 和 26.2；1.21.11 和 1.21.8 各自独立。
- 新增配置菜单快捷键，默认 `X`；说话界面仍为 `~`。
- 修复 26.x 和 1.21.11 配置键位分类重复注册导致游戏无法启动的问题。

### 0.1.8

- 修复免费 TTS 无声音的问题：默认改为微软 Edge 直连，失效镜像会返回首页 HTML 时自动回退到直连线路。
- apizero 备用线路保留四川话等音色，限流或不可用时也会自动回退到微软 Edge 直连。
- Sherpa 模型解压优先使用 Windows 自带解压器，失败时回退到内置解压，并显示解压进度。

## 构建

本机构建已使用离线依赖：

```powershell
$env:GRADLE_USER_HOME = "E:\GradleCache"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2"
E:\gradle-9.6.1\bin\gradle.bat build --offline --no-daemon --no-watch-fs --no-parallel
```

当前 0.1.9 实际产物为：

```text
mc26/build/libs/mcvoice-0.1.9+26.x.jar
mc12111/build/libs/mcvoice-0.1.9+1.21.11.jar
mc1218/build/libs/mcvoice-0.1.9+1.21.8.jar
```

`mcvoice-0.1.9+26.x.jar` 覆盖 26.1、26.1.1、26.1.2 和 26.2；`1.21.11` 和 `1.21.8` 各自独立。

## 实例目录

部署脚本会把三个同版本 jar 一起复制到：

```text
E:\Bakabot历史\MCvoice\0.1.9实例
```

这个目录用于集中存放和备份同一版本的三个目标 jar。实际启动某个 Minecraft 版本时，只把对应游戏版本的 jar 放进该版本的 `mods` 文件夹，不要把三个 jar 同时塞进同一个游戏实例。

`deploy.ps1` 不再更新 `1.21.11` 游戏文件夹，但仍会生成并备份 `1.21.11` jar。
