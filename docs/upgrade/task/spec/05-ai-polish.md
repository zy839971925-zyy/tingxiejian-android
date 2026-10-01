# 05 — AI 整理稿

新增真正的“整理文字”，不是只靠“问 AI”。

保留三层：
- raw/streaming
- final faithful transcript
- polished transcript

Final 原文永远不可被 AI 覆盖。

AI 整理只允许：
- 去明显口癖/无意义重复；
- 修明显病句；
- 轻微调整语序；
- 合理补标点。

禁止：
- 添加录音里没有的信息；
- 总结/扩写/推测；
- 改变观点；
- 擅自改数字、日期、金额、百分比、人名、地名、用户热词、专业专名。

优先结构化 segment edits：`segment_id + original + polished (+ optional reason)`。
客户端验证 segment id 和 protected entities；解析失败或敏感实体异常时保持原文。

全文页提供“忠实稿 / 整理稿”切换。

默认不自动联网；用户主动整理或明确开启自动整理时才调用已配置 cloud model。

旧 History 必须继续能打开/复制/分享/导出；不要 destructive migration。
