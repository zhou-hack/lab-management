package com.labmanage.common.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Instant 与 UTC LocalDateTime 的显式 JDBC 读写转换
 * <p>
 * MySQL DATETIME(3) 列本身无时区语义，统一由本处理器按 UTC 执行
 * Instant ↔ LocalDateTime 转换，不依赖驱动时区参数猜测字段时区；
 * 通过 mybatis-plus.type-handlers-package 注册，覆盖内置 Instant 映射。
 */
@MappedTypes(Instant.class)
public class InstantUtcTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, LocalDateTime.ofInstant(parameter, ZoneOffset.UTC));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toInstant(rs.getObject(columnName, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toInstant(rs.getObject(columnIndex, LocalDateTime.class));
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toInstant(cs.getObject(columnIndex, LocalDateTime.class));
    }

    private Instant toInstant(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
    }
}
