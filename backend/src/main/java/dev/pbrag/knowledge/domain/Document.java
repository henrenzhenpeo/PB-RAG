package dev.pbrag.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 文档逻辑身份；具体内容与解析结果由可复现的 DocumentVersion 承载。 */
@TableName("documents")
public class Document {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private UUID knowledgeBaseId;
	private String displayName;
	private String mediaType;
	private UUID currentVersionId;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	/** 供 MyBatis 反射恢复数据库中的文档对象。 */
	protected Document() {
	}

	/** 创建尚未写入当前版本信息的新文档实体。 */
	public Document(UUID id, UUID workspaceId, UUID knowledgeBaseId, String displayName, String mediaType) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.knowledgeBaseId = knowledgeBaseId;
		this.displayName = displayName;
		this.mediaType = mediaType;
	}

	/** 返回文档 ID。 */
	public UUID id() { return id; }
	/** 返回文档所属 Workspace ID。 */
	public UUID workspaceId() { return workspaceId; }
	/** 返回文档所属知识库 ID。 */
	public UUID knowledgeBaseId() { return knowledgeBaseId; }
	/** 返回文档展示名称。 */
	public String displayName() { return displayName; }
	/** 返回文档媒体类型。 */
	public String mediaType() { return mediaType; }
	/** 返回当前生效的文档版本 ID。 */
	public UUID currentVersionId() { return currentVersionId; }
	/** 返回文档创建时间。 */
	public OffsetDateTime createdAt() { return createdAt; }
	/** 返回文档最后更新时间。 */
	public OffsetDateTime updatedAt() { return updatedAt; }
	/** 为 MyBatis 返回文档 ID。 */
	public UUID getId() { return id; }
	/** 为 MyBatis 返回 Workspace ID。 */
	public UUID getWorkspaceId() { return workspaceId; }
	/** 为 MyBatis 返回知识库 ID。 */
	public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
	/** 为 MyBatis 返回展示名称。 */
	public String getDisplayName() { return displayName; }
	/** 为 MyBatis 返回媒体类型。 */
	public String getMediaType() { return mediaType; }
	/** 为 MyBatis 返回当前版本 ID。 */
	public UUID getCurrentVersionId() { return currentVersionId; }
	/** 为 MyBatis 返回创建时间。 */
	public OffsetDateTime getCreatedAt() { return createdAt; }
	/** 为 MyBatis 返回更新时间。 */
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
