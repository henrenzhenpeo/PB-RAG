-- 为数据库对象补充中文业务说明。COMMENT 只修改 PostgreSQL 元数据，不改变表结构和现有数据。

COMMENT ON TABLE workspaces IS '知识资源的逻辑分组；首期不是租户或用户授权边界。';
COMMENT ON COLUMN workspaces.id IS 'Workspace 业务 UUID，由应用生成。';
COMMENT ON COLUMN workspaces.name IS 'Workspace 展示名称，全局唯一。';
COMMENT ON COLUMN workspaces.created_at IS '记录创建时间，TIMESTAMPTZ 保存绝对时间。';
COMMENT ON COLUMN workspaces.updated_at IS '记录最后更新时间，TIMESTAMPTZ 保存绝对时间。';

COMMENT ON TABLE knowledge_bases IS 'Workspace 下的知识库，用于组织文档并限定检索范围。';
COMMENT ON COLUMN knowledge_bases.id IS '知识库业务 UUID，由应用生成。';
COMMENT ON COLUMN knowledge_bases.workspace_id IS '所属 Workspace；与下级资源共同构成资源作用域。';
COMMENT ON COLUMN knowledge_bases.name IS '知识库展示名称，在同一 Workspace 内唯一。';
COMMENT ON COLUMN knowledge_bases.description IS '知识库说明，可为空。';
COMMENT ON COLUMN knowledge_bases.created_at IS '知识库创建时间。';
COMMENT ON COLUMN knowledge_bases.updated_at IS '知识库最后更新时间。';

COMMENT ON TABLE documents IS '逻辑文档身份；文件内容、解析版本和 Chunk 归属于不可变的 document_versions。';
COMMENT ON COLUMN documents.id IS '文档业务 UUID，由应用生成。';
COMMENT ON COLUMN documents.workspace_id IS '文档所属 Workspace，用于数据库层资源作用域约束。';
COMMENT ON COLUMN documents.knowledge_base_id IS '文档所属知识库。';
COMMENT ON COLUMN documents.display_name IS '面向用户显示的原始文件名。';
COMMENT ON COLUMN documents.media_type IS '文档媒体类型，例如 text/markdown 或 application/pdf。';
COMMENT ON COLUMN documents.current_version_id IS '当前生产检索版本；新版本仅在入库成功后切换到此字段，处理中或失败时继续指向旧版本。';
COMMENT ON COLUMN documents.created_at IS '逻辑文档创建时间。';
COMMENT ON COLUMN documents.updated_at IS '逻辑文档最后更新时间，包括当前版本切换时间。';

COMMENT ON TABLE document_versions IS '文档的不可变内容版本，绑定内容摘要、原文件存储位置和解析器版本。';
COMMENT ON COLUMN document_versions.id IS '文档版本业务 UUID，由应用生成。';
COMMENT ON COLUMN document_versions.workspace_id IS '版本所属 Workspace，用于复合外键保证资源作用域一致。';
COMMENT ON COLUMN document_versions.document_id IS '所属逻辑文档 ID。';
COMMENT ON COLUMN document_versions.version_number IS '文档内从 1 开始递增的版本号。';
COMMENT ON COLUMN document_versions.content_sha256 IS '原始上传文件的 SHA-256 十六进制摘要，用于内容幂等和版本复现。';
COMMENT ON COLUMN document_versions.storage_key IS '原始文件在对象存储中的键，不是面向用户的文件路径。';
COMMENT ON COLUMN document_versions.parser_version IS '实际生成解析结果的解析器及版本；解析前可为空。';
COMMENT ON COLUMN document_versions.created_at IS '不可变版本创建时间。';

COMMENT ON TABLE chunks IS '从某个文档版本确定性切分出的检索单元，保存正文、稳定来源位置和语义向量。';
COMMENT ON COLUMN chunks.id IS 'Chunk 业务 UUID；入库重建时可能重新生成，历史引用另存版本与来源快照。';
COMMENT ON COLUMN chunks.workspace_id IS 'Chunk 所属 Workspace，用于资源作用域和复合外键约束。';
COMMENT ON COLUMN chunks.document_version_id IS 'Chunk 来源的不可变文档版本 ID。';
COMMENT ON COLUMN chunks.sequence_number IS 'Chunk 在同一文档版本内从 0 开始的稳定顺序号。';
COMMENT ON COLUMN chunks.content IS '供 BM25、Rerank 和模型上下文使用的 Chunk 正文；视为不可信数据。';
COMMENT ON COLUMN chunks.start_offset IS 'Chunk 在解析后规范化文本中的起始字符偏移，包含起点。';
COMMENT ON COLUMN chunks.end_offset IS 'Chunk 在解析后规范化文本中的结束字符偏移，不包含终点。';
COMMENT ON COLUMN chunks.source_position IS '稳定来源位置 JSON；可包含 Markdown 行号、标题路径、PDF 页码和分块器版本。';
COMMENT ON COLUMN chunks.embedding IS 'Chunk 的 1024 维语义向量，由 qwen3.7-text-embedding-flash 基线模型生成；解析分片后、向量化前可为空。';
COMMENT ON COLUMN chunks.created_at IS '当前 Chunk 记录创建时间。';

COMMENT ON TABLE ingestion_jobs IS '文档版本异步入库任务的事实来源，记录处理阶段、重试、租约、取消和失败信息。';
COMMENT ON COLUMN ingestion_jobs.id IS '入库任务业务 UUID，由应用生成。';
COMMENT ON COLUMN ingestion_jobs.workspace_id IS '任务所属 Workspace，用于查询和条件更新的资源作用域。';
COMMENT ON COLUMN ingestion_jobs.document_version_id IS '本任务处理的文档版本；每个版本最多一个入库任务。';
COMMENT ON COLUMN ingestion_jobs.status IS '任务生命周期状态：PENDING、RUNNING、RETRY_WAIT、COMPLETED、FAILED 或 CANCELLED。';
COMMENT ON COLUMN ingestion_jobs.stage IS '当前流水线阶段：PENDING、PARSING、CHUNKING、EMBEDDING、INDEXING 或 COMPLETED；与 status 分开表示任务是否运行和运行到哪里。';
COMMENT ON COLUMN ingestion_jobs.attempt_count IS '已领取执行的次数；每次从 PENDING 被 Worker 成功领取时加 1。';
COMMENT ON COLUMN ingestion_jobs.max_attempts IS '允许的最大执行次数，达到后可重试错误进入 FAILED。';
COMMENT ON COLUMN ingestion_jobs.available_at IS '任务最早可被领取的时间；RETRY_WAIT 使用它实现退避延迟。';
COMMENT ON COLUMN ingestion_jobs.lease_owner IS '当前持有任务租约的 Worker 实例标识；非 RUNNING 状态通常为空。';
COMMENT ON COLUMN ingestion_jobs.lease_expires_at IS '当前 Worker 租约到期时间；过期任务可被恢复，防止 Worker 崩溃后永久卡住。';
COMMENT ON COLUMN ingestion_jobs.error_code IS '稳定、可供程序判断的错误或取消代码，例如 CLIENT_CANCELLED、WORKER_LEASE_EXPIRED。';
COMMENT ON COLUMN ingestion_jobs.error_message IS '受长度与敏感数据约束的人类可读失败说明，不保存文档正文或密钥。';
COMMENT ON COLUMN ingestion_jobs.created_at IS '入库任务创建时间。';
COMMENT ON COLUMN ingestion_jobs.updated_at IS '任务状态、阶段、租约或错误信息最后更新时间。';

COMMENT ON TABLE app_users IS '应用登录账号；当前用户不构成 Workspace 资源隔离边界。';
COMMENT ON COLUMN app_users.id IS '用户业务 UUID，由应用生成。';
COMMENT ON COLUMN app_users.email IS '登录邮箱，唯一且用于账号识别。';
COMMENT ON COLUMN app_users.display_name IS '用户展示名称。';
COMMENT ON COLUMN app_users.password_hash IS '密码的单向哈希结果，绝不能作为明文密码展示或记录到日志。';
COMMENT ON COLUMN app_users.created_at IS '用户账号创建时间。';
COMMENT ON COLUMN app_users.updated_at IS '用户资料或密码哈希最后更新时间。';

COMMENT ON TABLE user_sessions IS '用户登录会话；数据库只保存会话令牌的 SHA-256 摘要。';
COMMENT ON COLUMN user_sessions.token_hash IS '会话令牌的 SHA-256 十六进制摘要，主键；不是可直接登录的原始 Token。';
COMMENT ON COLUMN user_sessions.user_id IS '会话所属用户 ID。';
COMMENT ON COLUMN user_sessions.expires_at IS '会话过期时间，超过后不得用于认证。';
COMMENT ON COLUMN user_sessions.created_at IS '登录会话创建时间。';

COMMENT ON TABLE chat_sessions IS '某用户在指定 Workspace 和知识库范围内的持久化问答会话。';
COMMENT ON COLUMN chat_sessions.id IS '问答会话业务 UUID，由应用生成。';
COMMENT ON COLUMN chat_sessions.workspace_id IS '会话所属 Workspace。';
COMMENT ON COLUMN chat_sessions.knowledge_base_id IS '本会话固定的知识库检索范围。';
COMMENT ON COLUMN chat_sessions.user_id IS '拥有该会话历史的登录用户 ID。';
COMMENT ON COLUMN chat_sessions.title IS '会话展示标题，通常由首个问题截取生成。';
COMMENT ON COLUMN chat_sessions.created_at IS '会话创建时间。';
COMMENT ON COLUMN chat_sessions.updated_at IS '会话最近一轮消息或状态变化时间，用于列表排序。';

COMMENT ON TABLE chat_messages IS '问答会话中的用户消息和助手消息；同一 turn_id 对应一问一答。';
COMMENT ON COLUMN chat_messages.id IS '消息业务 UUID，由应用生成。';
COMMENT ON COLUMN chat_messages.workspace_id IS '消息所属 Workspace，用于复合外键和作用域查询。';
COMMENT ON COLUMN chat_messages.session_id IS '消息所属问答会话 ID。';
COMMENT ON COLUMN chat_messages.turn_id IS '一轮问答关联 ID；同轮 USER 与 ASSISTANT 消息共享该值。';
COMMENT ON COLUMN chat_messages.role IS '消息角色：USER 或 ASSISTANT。';
COMMENT ON COLUMN chat_messages.status IS '消息终态或流式状态：COMPLETED、STREAMING、INTERRUPTED。用户消息通常直接为 COMPLETED。';
COMMENT ON COLUMN chat_messages.content IS '用户问题或助手回答正文；助手流式生成期间可暂为空，完成或中断时一次性持久化。';
COMMENT ON COLUMN chat_messages.error_code IS '助手消息中断的稳定原因代码，例如 CLIENT_CANCELLED、SERVER_RESTARTED；正常完成为空。';
COMMENT ON COLUMN chat_messages.created_at IS '消息创建时间。';
COMMENT ON COLUMN chat_messages.updated_at IS '消息正文、状态或错误信息最后更新时间。';

COMMENT ON TABLE message_citations IS '助手消息实际使用的证据快照，绑定 Chunk、文档版本和稳定来源位置。';
COMMENT ON COLUMN message_citations.id IS '引用记录业务 UUID，由应用生成。';
COMMENT ON COLUMN message_citations.workspace_id IS '引用所属 Workspace，用于复合外键保证作用域一致。';
COMMENT ON COLUMN message_citations.message_id IS '引用所属的 ASSISTANT 消息 ID。';
COMMENT ON COLUMN message_citations.ordinal IS '引用在回答中的编号，从 1 开始，对应 [1]、[2] 等标记。';
COMMENT ON COLUMN message_citations.chunk_id IS '被引用的 Chunk ID。';
COMMENT ON COLUMN message_citations.document_id IS '被引用的逻辑文档 ID；作为回答时文档身份快照保存。';
COMMENT ON COLUMN message_citations.document_version_id IS '被引用的不可变文档版本 ID，防止文档更新后历史引用漂移。';
COMMENT ON COLUMN message_citations.document_name IS '回答生成时的文档展示名称快照。';
COMMENT ON COLUMN message_citations.sequence_number IS '被引用 Chunk 在文档版本内的顺序号，从 0 开始。';
COMMENT ON COLUMN message_citations.source_position IS '回答生成时的来源位置 JSON 快照，例如行号、页码、标题路径。';
COMMENT ON COLUMN message_citations.excerpt IS '发送给前端展示的受限长度原文摘录，不等同于完整 Chunk 正文。';
COMMENT ON COLUMN message_citations.similarity IS '最终 CONTEXT 阶段沿用的排序分数；可能是 RRF 或 Rerank 分数，不能跨策略直接比较。';
COMMENT ON COLUMN message_citations.created_at IS '引用快照创建时间。';

COMMENT ON TABLE retrieval_runs IS '一次 Production RAG 检索运行，保存资源范围、问题、配置、实际最终阶段和整体结果。';
COMMENT ON COLUMN retrieval_runs.id IS '检索运行业务 UUID，由应用生成。';
COMMENT ON COLUMN retrieval_runs.workspace_id IS '检索限定的 Workspace。';
COMMENT ON COLUMN retrieval_runs.knowledge_base_id IS '检索限定的知识库。';
COMMENT ON COLUMN retrieval_runs.question IS '本次检索使用的规范化用户问题。';
COMMENT ON COLUMN retrieval_runs.strategy IS '检索策略标识，例如 HYBRID_RRF_RERANK。';
COMMENT ON COLUMN retrieval_runs.status IS '运行状态：RUNNING、COMPLETED 或 FAILED。降级但成功的运行仍为 COMPLETED。';
COMMENT ON COLUMN retrieval_runs.embedding_model IS '本次 Query Embedding 实际配置的模型标识。';
COMMENT ON COLUMN retrieval_runs.rerank_model IS '本次配置的 Rerank 模型标识；未启用时可表示禁用或为空。';
COMMENT ON COLUMN retrieval_runs.configuration IS '本次实际生效的检索参数 JSON，包括 Top-K、阈值、RRF 常量、上下文预算和开关。';
COMMENT ON COLUMN retrieval_runs.latency_ms IS '从检索运行开始到完成或失败的总耗时，单位毫秒。';
COMMENT ON COLUMN retrieval_runs.error_code IS '整次检索失败的稳定错误代码；成功或仅降级时通常为空。';
COMMENT ON COLUMN retrieval_runs.error_message IS '整次检索失败的人类可读说明，限制长度且不得保存敏感值。';
COMMENT ON COLUMN retrieval_runs.created_at IS '检索运行创建时间。';
COMMENT ON COLUMN retrieval_runs.completed_at IS '检索进入 COMPLETED 或 FAILED 终态的时间。';
COMMENT ON COLUMN retrieval_runs.final_stage IS '成功运行实际采用的最终排序阶段：FUSION 或 RERANK；CONTEXT 只负责预算裁剪。';
COMMENT ON COLUMN retrieval_runs.degradation_code IS '成功但发生降级时的原因，例如 RERANK_TRANSIENT_FAILURE；无降级时为空。';

COMMENT ON TABLE retrieval_candidates IS '检索流水线各阶段的有序候选快照，用于调试、审计和评测。';
COMMENT ON COLUMN retrieval_candidates.id IS '阶段候选记录 UUID，由应用生成。';
COMMENT ON COLUMN retrieval_candidates.retrieval_run_id IS '所属检索运行 ID。';
COMMENT ON COLUMN retrieval_candidates.workspace_id IS '候选所属 Workspace，必须与检索运行一致。';
COMMENT ON COLUMN retrieval_candidates.stage IS '候选所在阶段：VECTOR、KEYWORD、FUSION、RERANK 或 CONTEXT。';
COMMENT ON COLUMN retrieval_candidates.chunk_id IS '候选 Chunk ID；历史追踪刻意不对 Chunk 建删除外键，避免重建 Chunk 阻塞入库恢复。';
COMMENT ON COLUMN retrieval_candidates.document_id IS '候选所属逻辑文档 ID。';
COMMENT ON COLUMN retrieval_candidates.document_version_id IS '候选来源的不可变文档版本 ID。';
COMMENT ON COLUMN retrieval_candidates.rank IS '候选在该检索运行和阶段内从 1 开始的排名。';
COMMENT ON COLUMN retrieval_candidates.score IS '该阶段自己的排序分数：向量相似度、BM25、RRF 或 Rerank 分数；不同阶段不可直接横向比较。';
COMMENT ON COLUMN retrieval_candidates.metadata IS '阶段特有元数据 JSON，例如 vectorRank、keywordRank、fusionRank、sourceStage 和模型标识。';
COMMENT ON COLUMN retrieval_candidates.created_at IS '阶段候选快照创建时间。';

COMMENT ON TABLE retrieval_stage_summaries IS '每个检索阶段的执行摘要；即使零候选、跳过或降级也必须保存。';
COMMENT ON COLUMN retrieval_stage_summaries.retrieval_run_id IS '所属检索运行 ID，与 stage 共同构成主键。';
COMMENT ON COLUMN retrieval_stage_summaries.workspace_id IS '阶段摘要所属 Workspace，必须与检索运行一致。';
COMMENT ON COLUMN retrieval_stage_summaries.stage IS '阶段名称：VECTOR、KEYWORD、FUSION、RERANK 或 CONTEXT。';
COMMENT ON COLUMN retrieval_stage_summaries.status IS '阶段结果：COMPLETED、SKIPPED 或 DEGRADED。';
COMMENT ON COLUMN retrieval_stage_summaries.candidate_count IS '该阶段过滤后并实际持久化的候选数量。';
COMMENT ON COLUMN retrieval_stage_summaries.latency_ms IS '该阶段执行耗时，单位毫秒。';
COMMENT ON COLUMN retrieval_stage_summaries.error_code IS '阶段跳过或降级的稳定原因代码，例如 RERANK_DISABLED；正常完成为空。';
COMMENT ON COLUMN retrieval_stage_summaries.metadata IS '阶段级配置与观测 JSON，例如阈值、过滤前候选数、RRF 常量、模型和回退阶段。';
COMMENT ON COLUMN retrieval_stage_summaries.created_at IS '阶段摘要创建时间。';
