package dev.pbrag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.knowledge.domain.Document;
import dev.pbrag.knowledge.domain.DocumentListItem;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

	List<DocumentListItem> selectAllByKnowledgeBase(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId);

	int updateCurrentVersion(
			@Param("workspaceId") UUID workspaceId,
			@Param("documentId") UUID documentId,
			@Param("versionId") UUID versionId);
}
