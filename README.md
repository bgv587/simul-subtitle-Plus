# SIMSUB Plus
基于 [J1nxi-youmi/simul-subtitle-diy](https://github.com/J1nxi-youmi/simul-subtitle-diy) 的实时字幕，安卓 + Windows 双端

## 下载

两个平台的文件挂在**同一个页面**的 Assets 里（[最新构建](../../releases/tag/plus-latest)），按文件名区分：

### 安卓 —— `SimulSub-Plus<版本号>.apk`（约 251 MB）

到 [下载页](../../releases/tag/plus-latest) 的 Assets 里取 `SimulSub-Plus….apk`

需要 Android 10 以上、arm64。装完在 App 里就能检查更新。

### Windows —— `Windows-SimulSub-Plus<版本号>-x64.zip`（约 215 MB）

到同一个页面的 Assets 里取 `Windows-SimulSub-Plus…-x64.zip`

解压到英文路径后双击 `SimulSub.exe`。没有代码签名，首次运行会弹 SmartScreen，点「更多信息 → 仍要运行」。
桌面版没有应用内更新，升级要重新下载。

## 特点

两端相同：

- **识别**：SenseVoice，中/英/日/韩/粤一部模型全认，可锁语种也可自动判断
- **翻译**：填自己的 OpenAI 兼容接口。Qwen-MT 走原生协议，语言和术语用结构化参数传，不塞提示词
- **免费翻译**：「设置 → 翻译」的 LLM API 卡片里点「使用 B 站 Index 免费翻译」（`index-translate.bilibili.com` + `Index-Translate-35B-A3B`），不用 key、不用注册，自动带上必需的 User-Agent。没预算或只想先试试就用它，效果不如付费模型
- **同语种不装失败**：字幕语言选中文、听到的本来就是中文时，接口会拒译同语种——这时直接上屏听到的原文，不再写成「[翻译失败]」
- **会议记录**：桌面端按「开始字幕 → 停止字幕」存成一段，本次运行留最近 50 段，可导出 Markdown / 纯文本 / SRT / VTT / JSON（暂停再继续仍算同一段）
- **术语**：只把这一句里真的出现的词发过去，没出现的不发（否则模型会硬塞进译文）
- **残留假名**：译文里还剩成串平假名或片假名时（如「别说是アルバイト」「…像廉んかちゃん那样」），自动带提示重译一次
- **译文检查**：可选，用自己的 LLM 逐句复核，发现译反、漏否定、直译比喻就带提示重译；可单独配地址和 key
- **断句**：停顿切段、单句最长都能调——调小出字快但句子碎，调大句子完整但更晚
- **字幕**：边说边出草稿（灰字会就地修正），或说完再出；可同时显示原文和用时
- **配色**：7 套预设，也能自己配——原文、译文、背景三个颜色随便改，写好保存就用；草稿行和用时行跟着译文颜色自动调暗，深色译文自动去掉阴影
- **暂停 / 继续**：工具栏、App 首页（手机端还有通知栏）都能暂停和继续。暂停时说到一半的句子立即定稿，之后的声音不再识别，字幕窗口和会话记录都保留；继续时不用重新加载模型，同一段会话记录也不会被切开
- **隐私**：声音只在本机识别，不上传；翻译只发识别出的文字

只有安卓有：应用内检查更新、覆盖安装保留配置。

## 用起来

两端流程一样：

1. 装好上面的包
2. **只需要填 API Key**：设置 → 翻译 → LLM API → 粘贴 key → 点「测试连接」
   - 地址（`https://dashscope.aliyuncs.com/compatible-mode/v1`）和模型（`qwen-flash`）已经填好了
   - 用别的服务商时再改这两项
   - 一个 key 都不想填：点同一页的「B站 Index 免费」，直接用它翻译
3. 选「识别语种」（默认日语）→ 回首页点「开始字幕」→ 选要听的 App / 直接播放
4. 安卓更新：设置 → 页面 → 检查更新

默认配置是实测出来的（同一场直播、同一套提示词）：

| 模型 | 翻译 p50 | 端到端 p50 | 上下文 |
|---|---|---|---|
| **`qwen-flash` + 上文 2 句**（默认） | 346 ms | **833 ms** | ✅ |
| `qwen-mt-plus` | 268 ms | 1370 ms | ❌ |
| `qwen3.7-plus` | 1046 ms | 2684 ms | ✅ |

Windows 上因为 CPU 更强，实测端到端中位 **554 ms**（识别 55 ms、翻译 324 ms）。

成本：一场 1 小时直播约 ¥0.1~0.2（`qwen-flash` 前缀缓存命中率 56~66%）。

## 已知限制

- 识别语种选「自动」时，短句容易判错语种（实测 52 句里 19 句被判成中文或英文）。纯日语直播请选「日语」
- 游戏里的玩家名、技能名仍会认错；术语表能修译文，修不了识别
- 直播黑话容易连锁出错：识别听错 → 翻译照着错（如 `エフエッ` 被当成人名「FFJ」）
- 安卓的应用内更新会依次尝试 github.com、gh-proxy、ghfast、ghproxy、gh.llkk 五个源，都不通时只能手动下载
- 选「粤语」时识别正常，但翻译的源语言会退回「自动」（交给翻译模型自己判断，效果不受影响）
- Windows 版没有应用内更新；`sysaudio` 抓的是默认输出设备，正在用的耳机/音箱如果不是默认设备可能抓不到声音


- 每个版本的改动见 [CHANGELOG.md](CHANGELOG.md)

## 致谢

感谢 [KorenKrita](https://github.com/KorenKrita) 的 [simul-subtitle](https://github.com/KorenKrita/simul-subtitle)。J1nxi-youmi的https://github.com/J1nxi-youmi/simul-subtitle-diy。

悬浮字幕、音频内录、VAD 断句、草稿/定稿字幕、BYOK 翻译、术语与场景、桌面端……这套东西的骨架全是他一个人搭的。没有他的工作，这个 diy 版根本不存在——我只是在他的成果上换了自己更习惯的识别模型，改了几处交互和译文检查的实现。

也感谢 [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)（本地推理）、[SenseVoice](https://github.com/FunAudioLLM/SenseVoice)（多语种识别）、[Silero VAD](https://github.com/snakers4/silero-vad)（断句）。

## 许可

沿用 [LICENSE](LICENSE)。
