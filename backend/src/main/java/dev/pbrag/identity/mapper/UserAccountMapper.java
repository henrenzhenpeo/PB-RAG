package dev.pbrag.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.identity.domain.UserAccount;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户账号数据访问接口；定制查询和更新时间语义由 XML 明确表达。 */
@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
	/** 按创建时间和 ID 的稳定顺序查询全部用户。 */
	List<UserAccount> selectAllOrdered();
	/** 按规范化邮箱查询用户账号。 */
	UserAccount selectByEmail(@Param("email") String email);
	/** 按会话令牌摘要查询尚未过期的用户账号。 */
	UserAccount selectByActiveSession(@Param("tokenHash") String tokenHash);
	/** 更新用户展示名称并刷新更新时间。 */
	int updateDisplayName(@Param("id") UUID id, @Param("displayName") String displayName);
	/** 更新用户邮箱、名称及可选密码摘要。 */
	int updateAccount(@Param("id") UUID id, @Param("email") String email,
			@Param("displayName") String displayName, @Param("passwordHash") String passwordHash);
}
