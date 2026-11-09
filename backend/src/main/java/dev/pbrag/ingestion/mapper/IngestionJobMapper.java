package dev.pbrag.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IngestionJobMapper extends BaseMapper<IngestionJob> {

	IngestionJob selectByWorkspaceIdAndId(
			@Param("workspaceId") UUID workspaceId,
			@Param("id") UUID id);

	IngestionJob selectByDocumentVersionId(@Param("documentVersionId") UUID documentVersionId);
}
