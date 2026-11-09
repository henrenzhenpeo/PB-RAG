package dev.pbrag.platform.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * 在 Java UUID 与 PostgreSQL uuid 类型之间转换。
 *
 * <p>PostgreSQL JDBC 将 uuid 暴露为 JDBC OTHER；MyBatis 没有内置 UUID
 * TypeHandler，因此显式 ResultMap 和参数绑定需要在项目中统一注册。</p>
 */
@MappedTypes(UUID.class)
@MappedJdbcTypes(value = JdbcType.OTHER, includeNullJdbcType = true)
public class PostgreSqlUuidTypeHandler extends BaseTypeHandler<UUID> {

	@Override
	public void setNonNullParameter(
			PreparedStatement preparedStatement,
			int index,
			UUID parameter,
			JdbcType jdbcType) throws SQLException {
		preparedStatement.setObject(index, parameter, Types.OTHER);
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
		return resultSet.getObject(columnName, UUID.class);
	}

	@Override
	public UUID getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
		return resultSet.getObject(columnIndex, UUID.class);
	}

	@Override
	public UUID getNullableResult(
			CallableStatement callableStatement,
			int columnIndex) throws SQLException {
		return callableStatement.getObject(columnIndex, UUID.class);
	}
}
