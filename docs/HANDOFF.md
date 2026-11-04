# PB-RAG 当前交接

> 这里只记录当前事实、风险和下一步，不保存聊天历史。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一阶段数据地基已完成，正在进入 Markdown 上传与异步入库纵向链路。

外部 PostgreSQL、pgvector 与 Redis 已部署，后端曾由项目发起人确认可以正常启动。当前代码后续又调整了 MyBatis 分层，尚未重新编译验证。

## 已完成

- Spring Boot 4.1.2、Java 21、Spring AI 2.0.1 后端骨架；
- Vue 3、TypeScript、Vite、Pinia、TDesign 前端骨架；
- PostgreSQL、pgvector、Redis 外部连接配置；
- Flyway V1 启用 vector，V2 创建 Workspace、知识库、文档、版本、Chunk 和入库 Job；
- Workspace 与知识库创建、列表、详情 API；
- 统一 `ApiResponse<T>`、404、409 和参数错误响应；
- 入库 Job 状态机及其领域测试；
- MyBatis-Plus 与标准 `Controller → Service → ServiceImpl → Mapper → Mapper.xml` 分层；
- 单用户范围：暂缓多租户、成员关系和 RBAC，Workspace 仅作为知识资源分组。

## 当前关键决策

- Spring AI 只提供模型、Embedding、工具 Schema、向量基线与观测；Agent Runtime、Production RAG、任务状态、引用和评测由本项目实现；
- Flyway 是唯一 DDL 管理方式；MyBatis-Plus 不自动建表；
- 简单写入可使用 `BaseMapper`，定制查询及 pgvector、FTS、RRF、`SKIP LOCKED`、批处理 SQL 放在 Mapper XML；
- PostgreSQL Job 表是任务事实来源，Redis 只负责唤醒、取消标记和限流；
- 首期在 Java 进程内解析 Markdown 和普通 PDF；
- 不实现多租户和 RBAC，但保留 `workspace_id` 与复合外键确保资源归属一致。

## 当前风险

- 最新 Service/Mapper/XML 重构按要求没有运行 Maven 编译；
- Flyway V2 尚未纳入自动化 PostgreSQL 集成测试；
- 真实模型、Embedding 与 Rerank 供应商尚未确定；
- RAG 固定评测数据集尚未建立；
- 当前没有用户级授权，只适合单用户或由部署边界保护的环境。

## 下一步

实现 Markdown 上传与入库 Job 原子创建：

1. 定义对象存储接口和本地文件实现；
2. 流式计算 SHA-256，限制文件类型和大小；
3. 在同一事务中创建 Document、DocumentVersion 与 IngestionJob；
4. 重复内容返回已有版本或任务，不生成重复数据；
5. 上传接口快速返回 Job ID；
6. 增加 PostgreSQL Flyway、资源归属和幂等集成测试。

## 验证状态

- `git diff --check`：已通过；
- Mapper XML：已完成 XML 语法解析检查；
- Maven 编译/测试：遵照项目发起人要求，本轮未执行。
