package dev.pbrag.identity.service;

import java.util.UUID;

/** 账号认证应用服务，对 HTTP 层屏蔽密码存储、会话持久化等实现细节。 */
public interface UserAccountService {

	/** 注册账号并签发首个登录会话。 */
	Session register(String email, String displayName, String password);
	/** 校验账号密码并签发登录会话。 */
	Session login(String email, String password);
	/** 根据会话令牌查询当前登录用户。 */
	User current(String token);
	/** 修改指定用户的展示名称并返回最新资料。 */
	User update(UUID id, String displayName);
	/** 使指定会话令牌立即失效。 */
	void logout(String token);

	record User(UUID id, String email, String displayName) { }

	record Session(String token, User user) { }
}
