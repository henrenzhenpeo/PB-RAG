package dev.pbrag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.knowledge.domain.Document;
import dev.pbrag.knowledge.domain.DocumentListItem;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文档数据访问接口，包含当前版本切换及知识库作用域列表查询。 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
	/** 在完整资源作用域内锁定文档，串行化版本号分配与当前版本切换。 */
	Document lockByScope(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("documentId") UUID documentId);

	/** 查询知识库文档、当前生产版本及最新版本入库任务的列表投影。 */
	List<DocumentListItem> selectAllByKnowledgeBase(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId);

	/** 在 Workspace 约束下切换文档当前版本。 */
	int updateCurrentVersion(
			@Param("workspaceId") UUID workspaceId,
			@Param("documentId") UUID documentId,
			@Param("versionId") UUID versionId);
}
