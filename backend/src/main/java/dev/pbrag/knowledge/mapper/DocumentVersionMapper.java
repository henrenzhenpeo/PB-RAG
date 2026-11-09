package dev.pbrag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.knowledge.domain.DocumentVersion;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {

	DocumentVersion selectByKnowledgeBaseAndHash(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("contentSha256") String contentSha256);
}
