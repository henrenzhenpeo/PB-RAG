package dev.pbrag.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.pbrag.identity.domain.UserSession;
import org.apache.ibatis.annotations.Mapper;

/** 用户会话数据访问接口；简单创建和删除复用 MyBatis-Plus BaseMapper。 */
@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> { }
