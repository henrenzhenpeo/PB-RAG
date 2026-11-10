package dev.pbrag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.knowledge.domain.DocumentVersion;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 文档版本数据访问接口，负责内容去重查询与解析器版本记录。 */
@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {

	/** 按知识库和内容摘要查找可复用的文档版本。 */
	DocumentVersion selectByKnowledgeBaseAndHash(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("contentSha256") String contentSha256);

	/** 记录生成该版本解析结果的解析器版本。 */
	int updateParserVersion(
			@Param("workspaceId") UUID workspaceId,
			@Param("id") UUID id,
			@Param("parserVersion") String parserVersion);
}
