# 忠实稿、自动逐段校正与整理稿

本轮遵循用户补充要求：配置可用云端 API Key / 接口后，转写过程中每个已完成的忠实段自动进入独立校正任务；未配置则跳过并明确提示。配置页说明文本会发送到用户选择的服务商。“问 AI”保留为独立问答功能，不充当校正入口。

## 数据与调用边界

- `TranscriptPolisher.polishSegment(Context, JSONObject, List<String>) -> String`：只读取 `segment_id` 与 `final_text`（兼容 `id` / `text`），返回已验证的校正文字；异常时调用方保留原文。方法不修改输入 JSON，不持有 native ASR 或录音资源。
- `SpeechPipeline` 拥有背景 worker、排队、取消、逐段事件与最终保存。一个worker/四个pending，队满明确跳过部分段并保留忠实稿；停止后额外最多等30秒，然后seal禁止迟到写。请求20秒总deadline，支持主动断开取消。成功文字写到对应段的 `polished_text`，`text` / `final_text` 仍是忠实稿。
- `raw_text`（流式稿）、`final_text`（忠实稿）、`polished_text`（整理稿）分别保存；模型与热词元数据仍可与 History JSON 共存。
- 读取旧 History 不写回、不执行 destructive migration。`History.view` 生成分离的内存投影；未校正段采用忠实稿，并在 JSON 导出中保留独立 `final_text`。
- `History.update` 使用同一个经验证的 id 与 `AtomicFile` 更新已有记录；拒绝不存在记录、路径穿越、id 变更，以及既有 raw/final/text/时间戳/发言人/段标识的改写。成功校正不生成新 History id。

## 响应与保护校验

只接受以下严格 JSON，不剥离 Markdown 围栏、不截取 JSON 片段：

```json
{"edits":[{"segment_id":"s1","original":"逐字一致的忠实原文","polished":"逐字一致的忠实原文。"}]}
```

可选 `reason` 最多 512 字。要求段集合完整且恰好出现一次；未知、重复、缺失 id、错误原文、额外字段、非字符串、重复 JSON key（包括转义等价 key）、单引号、未引号 key、尾随逗号、尾随内容均拒绝。JSON 深度最多 16；一次校验最多 32 段；段长度最多 4096 字；响应最多 256 Ki 字符；热词最多 256 个、每个 128 字。

数字序列（含常见日期/金额/百分比格式）、拉丁专名与每个热词的出现次数必须保持一致。对任意中文姓名/地名，不猜测 NER：除了独立的“嗯 / 呃 / 唔”口癖与有限标点/空白之外，其余 Unicode 内容字符必须逐字且顺序一致。内部已有标点边界必须保留；标点只能在这些位置或段边缘改变，以拒绝把“不买”改成“不，买”这一类移动断句。

这是保守的规则校验，不是完整语义事实验证。它有意拒绝多数改词、语序调整和病句修复，避免没有录音证据时修改实体或观点。标点仍可能影响语气；单字口癖也有上下文歧义，因此忠实稿始终保留，用户热词可进一步保护同名文字。未通过、接口错误或不可用时保留忠实段，不覆盖任何源层。

## 全文页与导出

默认展示忠实稿，提供“忠实稿 / 整理稿”切换，标明已校正段数与 fallback。复制、分享、TXT / SRT / JSON 使用当前选中层；按钮、分享标题、导出文件名与 JSON `selected_layer` 明示该层。切换不覆盖 History。文档选择器打开时捕获选中层，与格式一起存入 Activity state；Activity 重建后仍导出选择器打开时的层。“问 AI”继续接收原 History id，保持原忠实问答上下文。

## 验证与限制

`bash design-tools/polish-check.sh` 编译生产 `PolishValidator`、`TranscriptPolisher`、`History`、`Exporter`，使用桌面 org.json 和仅测试范围的 Android/Cloud 边界 stand-in。71 个行为断言覆盖严格响应、实体与内容保护、无 Key 零联网、校正网络读取超时、成功/拒绝无输入修改、旧数据三种文本投影、所选层导出、相同 id 更新、raw/final 不可改写、非法 id / symlink 路径、原子失败回滚与 backup 恢复。

RED：新增类不存在；之后增加热词请求边界测试观察到失败，再增加限制通过；旧整理稿 JSON 保留忠实字段测试观察到失败，再修投影通过。原 `export-check.sh` 与 portal 检查也通过。资源/Java/DEX 在独立临时目录编译通过；存在原有 annotation/deprecated warnings。

Android 真机的文档选择器重建、TalkBack、长文本切换、实际服务商返回、弱网络与取消仍须设备验证；桌面 AtomicFile stand-in 验证调用的 rollback 合同，不冒充设备文件系统/进程死亡验证。
