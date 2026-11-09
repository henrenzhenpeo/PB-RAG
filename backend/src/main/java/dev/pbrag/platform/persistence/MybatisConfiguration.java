package dev.pbrag.platform.persistence;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import java.util.UUID;
import org.apache.ibatis.type.JdbcType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 项目级 MyBatis 类型映射配置。 */
@Configuration(proxyBeanMethods = false)
public class MybatisConfiguration {

	/**
	 * 在 Mapper XML 解析前注册 UUID 映射，供显式 ResultMap、BaseMapper 和
	 * 自定义 SQL 共同使用。
	 */
	@Bean
	ConfigurationCustomizer postgreSqlUuidTypeHandlerCustomizer() {
		return configuration -> configuration.getTypeHandlerRegistry().register(
				UUID.class,
				JdbcType.OTHER,
				PostgreSqlUuidTypeHandler.class);
	}
}
