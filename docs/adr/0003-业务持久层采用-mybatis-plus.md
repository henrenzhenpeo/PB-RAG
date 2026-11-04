# ADR 0003：业务持久层采用 MyBatis-Plus

- 状态：已接受
- 日期：2026-11-04
- 替代范围：ADR 0001 中“主持久化使用 Spring Data JDBC 或 `JdbcTemplate`”的实现选择

## 背景

第一批 Workspace 与知识库代码使用 Spring Data JDBC。项目发起人希望普通业务 CRUD 采用更熟悉的 MyBatis-Plus 风格，同时保留 PostgreSQL、pgvector 和显式 SQL 的工程学习价值。

MyBatis-Plus 已提供 Spring Boot 4 Starter，并正式支持 PostgreSQL。项目仍处于第一批 CRUD 阶段，切换成本较低。

## 决定

- 普通业务 CRUD、分页与条件构造使用 MyBatis-Plus；
- 业务模块采用 `Controller → Service 接口 → ServiceImpl → Mapper → Mapper.xml` 分层；
- Controller 只依赖 Service 接口，ServiceImpl 负责事务和业务规则，不能直接在 Controller 中调用 `BaseMapper`；
- Mapper 可以继承 `BaseMapper` 复用单表 CRUD，自定义查询统一在同名 `Mapper.xml` 中维护；
- Workspace 内资源查询显式包含 `workspace_id`，用于资源分组与数据一致性；
- pgvector、PostgreSQL FTS、RRF、`SKIP LOCKED`、批量 Upsert 等复杂路径使用自定义 Mapper SQL；
- Flyway 仍是唯一 DDL 与数据库版本管理机制；MyBatis-Plus 不负责建表；
- 首期不启用 TenantLine；按照 ADR 0004，Workspace 不是租户边界，业务查询和数据库复合外键保留 Workspace 条件以防止资源错误关联；
- 版本锁定为 MyBatis-Plus `3.5.18.1`，使用 `mybatis-plus-spring-boot4-starter`。

## 理由

MyBatis-Plus 减少普通 CRUD 的样板代码，符合项目发起人的 Java 企业开发经验。Service 接口与 ServiceImpl 保留稳定的 Spring 业务分层；Mapper XML 让复杂检索 SQL、执行计划、资源条件、向量算子和任务锁语义保持明确。当前项目不再额外增加 Repository 包，避免 Repository 与 Mapper 职责重叠。

## 负面影响

- 项目增加一个框架依赖及其升级成本；
- Wrapper 不能替代复杂 SQL，持久层仍会同时存在生成 SQL 与自定义 SQL；
- 若滥用 `selectById`，可能造成 Workspace 资源归属混乱；代码评审和一致性测试必须检查这一点；
- PostgreSQL 特有类型（vector、JSONB）需要自定义 TypeHandler 或自定义 Mapper SQL。

## 验证

- Workspace 与知识库写入使用 BaseMapper，定制查询使用 Mapper XML；
- 资源 ID 与路径中的 Workspace 不匹配时返回不存在；
- Flyway 在空库完成全部迁移；
- 后续对向量、FTS 和任务领取 SQL执行真实 PostgreSQL 集成测试。
