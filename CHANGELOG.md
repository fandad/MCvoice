# MCvoice 更新说明 / Changelog

## 0.2.3

English:

- Clarified the auto-read toggle label on the main config screen.
- Added a B-key shortcut to toggle auto-read; it shares the same state and feedback messages as `/mcvoice auto`.
- Edge direct connection now retries automatically and falls back to Microsoft's regional HTTP endpoint when direct stays unavailable; empty-audio responses are reported instead of failing silently.
- Reduced memory pressure while speaking: speech requests are capped when queued, extra-long text is synthesized in segments, and MP3 decoding no longer keeps full duplicate copies of the audio.
- All four builds (26.x, 1.21.11, 1.21.8, 1.21.1) are updated to 0.2.3.

中文：

- 配置页一级菜单的自动朗读选项改名为“自动朗读输入聊天框的文字”。
- 新增 B 键自动朗读开关，与配置页开关和 `/mcvoice auto` 状态同步，提示文字一致。
- Edge 直连失败会自动重试，连续失败时自动回退微软区域 HTTP 线路；空音频会明确报错，不再静默失败。
- 说话时内存优化：请求排队有上限、超长文本分段合成、MP3 解码不再保留整段重复副本。
- 26.x、1.21.11、1.21.8、1.21.1 四个版本同步更新到 0.2.3。

## 0.2.2

English:

- Added the Chinese voices Chaowen (male) and Xiao Ya to the Sherpa model download section.
- These voices use Piper `phoneme_type=pinyin` models repackaged by Sherpa-onnx, so no separate g2pW runtime is needed.
- Sherpa models now load matching `phone.fst`, `date.fst`, and `number.fst` automatically for better number, date, and phone-number reading.
- This release includes jars for 26.x, 1.21.11, 1.21.8, and 1.21.1.
- 0.2.2 hotfix: Clarified the auto-read toggle label on the main config screen.

中文：

- Sherpa 下载区新增超文（男声）和小雅两套中文声线。
- 这两套模型采用 Sherpa-onnx 打包的 Piper `phoneme_type=pinyin` 格式，不需要单独安装 g2pW 运行时。
- Sherpa 引擎会自动加载 `phone.fst`、`date.fst`、`number.fst`，改善数字、日期和电话号朗读。
- 本版本包含 26.x、1.21.11、1.21.8、1.21.1 四个游戏版本。
- 0.2.2 热更：配置页自动朗读选项改名为“自动朗读输入聊天框的文字”。

## 0.2.1

English:

- Model download states now persist across opening and closing the download screen.
- Piper downloads show the current mirror and failure reason, and keep `.part` resumes after failures or closing the game.
- Fixed local playback stuttering in Plasmo Voice when "hear myself" is enabled.

中文：

- 模型下载状态跨页面共享，切走再回来不会丢失。
- Piper 下载显示当前源和失败原因，失败或退出后保留断点续传。
- 修复 Plasmo Voice 开启“让自己也听到”时的本地回放卡顿。

## 0.2.0

English:

- Added Minecraft 1.21.1 support.
- Made Simple Voice Chat and Plasmo Voice optional; Fabric API and Mod Menu remain required.
- Added SVC/PV connection state to the config screen with local-only warnings.
- Improved responsive layout and scrolling for config, speech, and model screens.

中文：

- 新增 Minecraft 1.21.1 独立版本。
- Simple Voice Chat 和 Plasmo Voice 改为可选；Fabric API 和 Mod Menu 仍为必装。
- 配置页新增 SVC/PV 连接状态，未连接时提示仅本地生效。
- 改善各界面的窗口缩放适配和滚动。
