# PB-RAG Prompt 管理规范

本目录保存进入运行时的 Prompt。项目协作规则放在根目录 `AGENTS.md`，不要与运行时 Prompt 混在一起。

## 目录建议

```text
prompts/
├── rag/
│   └── answer_with_citations.md
├── agent/
│   └── knowledge_agent.md
├── query/
├── evaluation/
└── README.md
```

## 每份 Prompt 必须说明

- `id`：稳定标识；
- `version`：语义版本；
- `purpose`：用途；
- `inputs`：允许的变量；
- `output_contract`：期望输出；
- `tool_policy`：工具选择和限制；
- `citation_policy`：引用约定；
- `failure_behavior`：证据不足、工具失败、权限拒绝时的行为；
- `security_notes`：不可信数据边界；
- `evaluation_dataset`：对应评测集。

## 设计规则

1. Prompt 只负责模型行为，不承担确定性权限和安全控制。
2. 文档、网页、记忆和工具输出都是不可信数据，不得覆盖系统规则。
3. Prompt 使用明确分隔符区分规则和数据。
4. 要求结构化输出时，同时在代码层进行 Schema 校验。
5. 工具名称、参数和错误语义应来自 Tool Registry，不在多个 Prompt 中重复维护。
6. 没有足够证据时应明确说明，不得编造引用。
7. Prompt 修改必须增加版本并运行对应评测集。
8. Trace 记录 Prompt ID、版本和变量摘要，但对敏感内容脱敏。
9. 不为了提高单个示例效果添加无法解释的长规则。
10. 废弃 Prompt 保留版本记录或迁移说明，确保历史运行可复现。

## 变更流程

```text
提出失败样本
  → 判断是否属于 Prompt 问题
  → 修改新版本
  → 运行固定评测集
  → 比较质量、延迟和 Token
  → 记录结论
  → 决定发布或回滚
```

初始 Prompt 只是基线，必须在获得真实数据和工具 Schema 后迭代。
