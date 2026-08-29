# MC语音

MC语音是面向 Minecraft 26.2 Fabric 的客户端文字转语音模组。输入文字后，本地生成中文语音，并通过 Simple Voice Chat 发给服务器里其他安装了 SVC 的玩家。

本模组基于 [FlooferLand/ttvoice-mod](https://github.com/FlooferLand/ttvoice-mod) 开发，遵循 GPLv3 许可证。

## 当前功能

- Windows x64 打包，macOS/Linux 会显示不支持提示
- 中文配置界面、说话界面、聊天栏指令
- 自动朗读开关：聊天框发送的文字可直接说出来
- 音量调节：支持 0%-200%
- 传播距离：支持 1-128 格，实际生效受 SVC 服务端和群组规则限制
- 通过 Simple Voice Chat 播放音频
- 支持 Piper 离线中文模型，可一键下载或手动放入多个声线
- 支持 Sherpa-onnx 离线中文模型，提供更多本地声线
- 支持 Windows SAPI 系统声线，不需要额外下载模型
- 支持外部 TTS 命令，可接入 edge-tts、自建脚本或任意能输出 WAV 的工具
- 支持外部 TTS 服务，可选用内置免费 TTS、URL 模板或 OpenAI 兼容接口
- 默认按键为 `~`，可打开说话界面

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

- 免费 TTS（内置）：默认使用 `https://v1.apizero.cn/api/tts`，无需 API Key。
- URL 模板：适合自建 GET 接口。
- OpenAI 兼容：适合 OpenAI 或兼容服务的 `/audio/speech` 接口。

免费 TTS 模式也支持把服务地址改为 `https://ttsapi.cn`、`https://ttsbox.cn` 或 `https://edge.text-to-speech.cn`。
免费模式下会隐藏 API Key 和模型输入框，音色改为点击切换。

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

## 构建

本机构建已使用离线依赖：

```powershell
$env:GRADLE_USER_HOME = "E:\GradleCache"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.2"
E:\gradle-9.6.1\bin\gradle.bat build --offline
```

产物位于 `build/libs/mcvoice-0.1.7+26.2.jar`。
