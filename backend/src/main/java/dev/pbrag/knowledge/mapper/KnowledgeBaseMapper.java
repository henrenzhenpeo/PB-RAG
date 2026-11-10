package dev.pbrag.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.knowledge.domain.KnowledgeBase;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 知识库数据访问接口；Workspace 资源条件在 XML SQL 中显式表达。 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

	/** 查询指定 Workspace 中的全部知识库。 */
	List<KnowledgeBase> selectAllByWorkspaceId(@Param("workspaceId") UUID workspaceId);

	/** 在 Workspace 作用域内按 ID 查询知识库。 */
	KnowledgeBase selectByWorkspaceIdAndId(
			@Param("workspaceId") UUID workspaceId,
			@Param("id") UUID id);

	/** 对知识库行加锁并验证其 Workspace 归属。 */
	UUID lockByWorkspaceIdAndId(
			@Param("workspaceId") UUID workspaceId,
			@Param("id") UUID id);
}
