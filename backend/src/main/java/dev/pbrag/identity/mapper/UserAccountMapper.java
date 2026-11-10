package dev.pbrag.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.identity.domain.UserAccount;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户账号数据访问接口；定制查询和更新时间语义由 XML 明确表达。 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
	/** 按规范化邮箱查询用户账号。 */
	UserAccount selectByEmail(@Param("email") String email);
	/** 按会话令牌摘要查询尚未过期的用户账号。 */
	UserAccount selectByActiveSession(@Param("tokenHash") String tokenHash);
	/** 更新用户展示名称并刷新更新时间。 */
	int updateDisplayName(@Param("id") UUID id, @Param("displayName") String displayName);
}
