package dev.pbrag.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.identity.domain.Workspace;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** Workspace 数据访问接口；普通写入复用 BaseMapper，自定义查询由 XML 实现。 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<Workspace> {

	/** 按创建时间和 ID 的稳定顺序查询全部 Workspace。 */
	List<Workspace> selectAllOrdered();
}
