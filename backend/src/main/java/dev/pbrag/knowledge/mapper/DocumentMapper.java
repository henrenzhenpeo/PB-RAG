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

	/** 查询知识库文档、当前版本及其入库任务的列表投影。 */
	List<DocumentListItem> selectAllByKnowledgeBase(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId);

	/** 在 Workspace 约束下切换文档当前版本。 */
	int updateCurrentVersion(
			@Param("workspaceId") UUID workspaceId,
			@Param("documentId") UUID documentId,
			@Param("versionId") UUID versionId);
}
