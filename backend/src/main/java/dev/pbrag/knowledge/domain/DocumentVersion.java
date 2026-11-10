package dev.pbrag.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 不可变文档版本，绑定内容摘要、对象存储键与解析器版本。 */
@TableName("document_versions")
public class DocumentVersion {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private UUID documentId;
	private int versionNumber;
	private String contentSha256;
	private String storageKey;
	private String parserVersion;
	private OffsetDateTime createdAt;

	/** 供 MyBatis 反射恢复数据库中的文档版本。 */
	protected DocumentVersion() {
	}

	/** 创建与内容摘要及对象存储位置绑定的新文档版本。 */
	public DocumentVersion(
			UUID id, UUID workspaceId, UUID documentId, int versionNumber,
			String contentSha256, String storageKey) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.documentId = documentId;
		this.versionNumber = versionNumber;
		this.contentSha256 = contentSha256;
		this.storageKey = storageKey;
	}

	/** 返回版本 ID。 */
	public UUID id() { return id; }
	/** 返回版本所属 Workspace ID。 */
	public UUID workspaceId() { return workspaceId; }
	/** 返回版本所属文档 ID。 */
	public UUID documentId() { return documentId; }
	/** 返回文档内递增的版本号。 */
	public int versionNumber() { return versionNumber; }
	/** 返回原始内容的 SHA-256 摘要。 */
	public String contentSha256() { return contentSha256; }
	/** 返回原始内容在对象存储中的键。 */
	public String storageKey() { return storageKey; }
	/** 返回生成当前解析结果的解析器版本。 */
	public String parserVersion() { return parserVersion; }
	/** 返回版本创建时间。 */
	public OffsetDateTime createdAt() { return createdAt; }
	/** 为 MyBatis 返回版本 ID。 */
	public UUID getId() { return id; }
	/** 为 MyBatis 返回 Workspace ID。 */
	public UUID getWorkspaceId() { return workspaceId; }
	/** 为 MyBatis 返回文档 ID。 */
	public UUID getDocumentId() { return documentId; }
	/** 为 MyBatis 返回版本号。 */
	public int getVersionNumber() { return versionNumber; }
	/** 为 MyBatis 返回内容摘要。 */
	public String getContentSha256() { return contentSha256; }
	/** 为 MyBatis 返回对象存储键。 */
	public String getStorageKey() { return storageKey; }
	/** 为 MyBatis 返回解析器版本。 */
	public String getParserVersion() { return parserVersion; }
	/** 为 MyBatis 返回创建时间。 */
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
