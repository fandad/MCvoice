# MCvoice 更新说明 / Changelog

## 0.2.7

English:

- Added English interface translations (`en_us.json`) and moved the previously hardcoded Chinese UI text into translation keys: back buttons, volume and range labels, voice names in the picker, connection-state texts, the whole external TTS service screen, the speech screen's history and linger labels, the whole model download screen, the Simple Voice Chat volume category, and the mod metadata shown in Mod Menu. Both language files now hold 227 keys with no difference in the key sets. Players whose client language is not Chinese no longer see raw translation keys or untranslated Chinese text.
- The English interface carries two small grey notes: the first says the UI was only ever meant to be Chinese and that most model download sources are inside mainland China; the second (in parentheses) says that plenty of interfaces and built-in engines are left open for players who have their own model or prefer their own approach - open the mod folder, or configure the model you want in Advanced settings.
- The audio output screen shows a hint under the external volume slider: that volume mainly follows the system settings, so changing it there may have no effect.
- New audio output settings (Advanced settings -> Audio output): every spoken line can additionally be sent to a chosen output device. The typical use is a virtual audio cable (VB-CABLE / VoiceMeeter): the mod writes into the cable input, and other applications (voice chat, recording, streaming) select the matching cable output as their microphone, so what you type in game becomes a microphone input there. Three output modes (in game only = default, external device only, both), an independent volume slider for the external copy (0-200%), and a device list that is rescanned on every visit, detects virtual cables, marks them as recommended and lists them first.
- Every control in the new screen has a bilingual tooltip, and the list never overflows the screen.
- The default behaviour is unchanged: the output mode defaults to "in game only", in which the mod never opens an output device at all.
- Fixed: registering the Simple Voice Chat volume category could fail, which aborted the rest of the connection handler and left the local playback channel uncreated - the symptom was hearing nothing at all in game. The channel is now created first and category registration is isolated.
- Fixed: the selected output device was not always the device that actually received the audio (Java Sound resolves global line lookups by provider order); lines are now opened on the selected mixer.
- Fixed: stuttering speech. The playback loop did its per-frame work and then slept a fixed 20 ms, so every frame took longer than the 20 ms of audio it carried and the audio engine was permanently starved. The loop now paces against an absolute deadline: measured 26.7 ms -> 20.0 ms per frame.
- Fixed: with "external device only" selected but no device chosen (or the device failing to open) the audio became completely silent; it now falls back to in-game playback and logs the reason.
- Fixed: the two Piper voices (Huayan female and Huayan low) were listed with their raw model id in the voice picker instead of their Chinese name, because the picker looked them up with the full voice id while the name table is keyed by the bare model folder name. The download screen was not affected.
- All four builds (26.x, 1.21.11, 1.21.8, 1.21.1) are updated to 0.2.7.

中文：

- 新增英文界面语言文件（`en_us.json`），并把原先硬编码在代码里的中文界面文案全部改为翻译 key：返回按钮、音量/传播距离标签、声线选择列表里的声线名、连接状态、整个外部 TTS 服务页、说话界面的历史与滞留模式、整个模型下载页、Simple Voice Chat 的音量分类名，以及 Mod Menu 里显示的模组元数据。两个语言文件现在各 225 个键、键集合零差异。非中文客户端不再看到裸键名或未翻译的中文。
- 英文界面下方有两行小灰字：第一行说明界面本来只打算做中文、模型下载源大多在中国大陆境内；第二行（括号内）说明有自己的模型、或想用自己的办法的人，接口和内置引擎都留足了 —— 自己打开模组文件夹，或直接在高级设置里配置想要的模型。
- 音频输出界面在外部音量滑条下方新增提示：这一路音量主要跟随系统设置，在这里调整可能无效。
- 新增音频输出设置（高级设置 → 音频输出设置）：说话时除游戏内播放外，可以再送一路到指定输出设备。典型用法是虚拟声卡（VB-CABLE / VoiceMeeter）：模组写进虚拟声卡的输入端，其他软件（语音、录制、直播）把对应的输出端选作麦克风，于是你在游戏里打的字就能被当成麦克风输入。提供三种输出方式（只在游戏内=默认、只送外部设备、两边都放）、外部那一路的独立音量（0-200%），设备列表每次进入都重新扫描、自动识别虚拟声卡并标注推荐、排在列表最前。
- 新界面的每个控件都有中英双语悬浮注释，内容不会溢出屏幕。
- 默认行为没有变化：输出方式默认"只在游戏内"，这种情况下模组完全不会打开任何输出设备。
- 修复：Simple Voice Chat 的音量分类注册可能失败，导致连接回调后面的代码整段不执行、本地播放通道建不起来，表现为游戏内完全听不到自己的声音。现在先建通道，分类注册单独兜异常。
- 修复：选中的输出设备不一定真的收到声音（Java Sound 的全局取线顺序问题），现在改为在选中的 mixer 上开线。
- 修复：说话卡顿。播放循环原来是"做完本帧的工作再固定睡 20ms"，每帧实际耗时超过它所承载的 20ms 音频，音频引擎持续欠载，加了 PV 发送或设备写入后必然触发。现在按绝对时间对齐，实测每帧 26.7ms → 20.0ms。
- 修复：选择"只送外部设备"但没选设备（或设备打不开）时声音会彻底消失；现在会自动回退成游戏内播放，并在日志里写明原因。
- 修复：Piper 的两条声线（花颜女声 / 花颜低配）在声线选择界面显示成原始模型 id 而不是中文名 —— 选择界面是拿带 `piper:` 前缀的完整 id 去查名字表，而表的键是裸模型目录名；下载页不受影响。
- 四个构建（26.x、1.21.11、1.21.8、1.21.1）同步更新到 0.2.7。

## 0.2.6

English:

- Added a voice picker screen: the voice button on the config screen now opens a list of every detected voice instead of cycling through them one by one. The active voice is shown greyed out and cannot be clicked, and multi-speaker models appear as separate entries.
- Added the Kokoro multi-language engine (about 126 MB, 8 Chinese voices) and the Matcha Baker Chinese voice (about 72 MB) to the Sherpa download section.
- The multi-speaker models Hanbing and Eila (804 speakers each) and Xiaoai Style (5 speakers) now expose selected speakers as individual voices, for 12 entries in total.
- Sherpa model downloads now support HTTP Range resume: an interrupted download keeps its partial file and continues from where it stopped instead of starting over. The request timeout was raised from 20 to 30 minutes.
- Download failures are now written to the game log together with the underlying error, instead of only being shown in the UI.
- Reworked the model download screen into two blocks (Piper, then Sherpa / Matcha / Kokoro) laid out as an aligned two-column grid. A yellow notice at the top warns that some models have no reliable download source in mainland China and suggests using a GitHub accelerator.
- Added a scroll bar and fixed the scroll range on the download screen so the bottom controls can always be reached.
- The title of every scrollable screen now scrolls together with its content.
- 1.21.8 is no longer deployed into a game folder; its jar is still built and archived.
- All four builds (26.x, 1.21.11, 1.21.8, 1.21.1) are updated to 0.2.6.

中文：

- 新增声线选择界面：配置页的声线按钮不再逐个点击循环，而是打开声线列表；当前生效的那条显示为灰暗且不可点击，多音色模型会分成多个条目。
- Sherpa 下载区新增 Kokoro 多语言引擎（约 126MB，含 8 个中文音色）与 Matcha Baker 中文声线（约 72MB）。
- 多音色模型寒冰、伊拉（各 804 个说话人）与小爱风格（5 个说话人）现在把选定的说话人展开为独立声线，共 12 条。
- Sherpa 模型下载支持 HTTP Range 断点续传：中断后保留半成品并从断点继续，不再从头重来；请求超时从 20 分钟提高到 30 分钟。
- 下载失败现在会连同具体原因写入游戏日志，不再只在界面上显示。
- 模型下载页重排为上下两块（Piper，然后 Sherpa / Matcha / Kokoro），按两列网格对齐排列；顶部新增黄色提示：部分模型国内没有稳定下载源，建议挂加速器（加速 GitHub 等）。
- 下载页补充滚动条并修复滚动范围，底部按钮与文字现在一定能滚到。
- 所有可滚动页面的标题现在随内容一起滚动。
- 1.21.8 不再部署进游戏目录，jar 仍会构建与归档。
- 26.x、1.21.11、1.21.8、1.21.1 四个版本同步更新到 0.2.6。

## 0.2.5

English:

- Added Cantonese support: a new local voice, Xiao Mei (female), is available in the Sherpa model download section (about 108 MB); no account or login is required to download it.
- Added Northeast Mandarin (Xiaobei) and Shaanxi Mandarin (Xiaoni), plus Sichuan Mandarin (Yunxi), to the free Edge voice list.
- Fixed the Sichuan voice entry, which previously played standard Mandarin; it now maps to a real Sichuan voice.
- Sherpa models now also load a matching `rule.fst` when present, which fixes number and date reading for models shipped with that file instead of `phone`/`date`/`number` FSTs.
- Fixed overlapping buttons on the model download screen; the screen scrolls properly now that the Cantonese entry is present.
- All four builds (26.x, 1.21.11, 1.21.8, 1.21.1) are updated to 0.2.5.

中文：

- 新增粤语支持：Sherpa 模型下载区新增本地声线"小美"（女声，约 108MB），下载不需要账号或登录。
- 免费 Edge 音色新增东北话（小北）、陕西话（小妮）和四川话（云希）三套方言声线。
- 修正四川话音色：原条目实际发出的是普通话，现已指向真正的四川话声线。
- Sherpa 引擎在模型自带 `rule.fst` 时也会自动加载，修复了用该文件替代 `phone`/`date`/`number` 的模型的数字与日期朗读。
- 修复模型下载页按钮重叠问题；加入粤语条目后页面可正常滚动。
- 26.x、1.21.11、1.21.8、1.21.1 四个版本同步更新到 0.2.5。

## 0.2.4

English:

- Speech-screen history entries are now clickable to say that text again; the last clicked entry is highlighted with a border, and the full text is shown on hover.
- Added a linger mode toggle on the speech screen (off by default): unsent text is kept when you leave the screen and restored when you return; after a sentence is sent, the screen closes as usual.
- Added scroll bars on the right side of the speech screen and all scrollable menu screens; speech history opens at the newest entry at the bottom and keeps up to 50 entries.
- History storage is unchanged; replaying a history entry does not append a duplicate entry.

中文：

- 说话界面历史记录现在可以点击再次朗读；最近点击的那条会用边框标出，悬停可看完整原文。
- 说话界面新增滞留模式开关（默认关闭）：退出界面时未发送的文字会保留，下次打开自动恢复；句子发出后界面照常关闭。
- 说话界面和各可滚动的菜单界面右侧新增滚动条；说话界面历史默认显示到最新一条（最下面），仍按原上限保留 50 条。
- 历史记录存储方式不变；点击历史重读不会往历史尾部重复添加。

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
