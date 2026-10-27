package org.dpdns.zerodep.flow.core.db;

import com.easy.query.core.basic.jdbc.executor.internal.merge.result.StreamResultSet;
import com.easy.query.core.basic.jdbc.executor.internal.props.JdbcProperty;
import com.easy.query.core.basic.jdbc.types.EasyParameter;
import com.easy.query.core.basic.jdbc.types.handler.JdbcTypeHandler;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 时间列：引擎里是 {@link Instant}，库里是 UTC 的 DATETIME(3)（0001 的"UTC 时间"）。按本地时间读写，不经 JVM 与连接的时区换算。
 */
final class InstantUtcHandler implements JdbcTypeHandler {
    @Override
    public Object getValue(JdbcProperty property, StreamResultSet resultSet) throws SQLException {
        LocalDateTime value = resultSet.getObject(property.getJdbcIndex(), LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    @Override
    public void setParameter(EasyParameter parameter) throws SQLException {
        Instant value = (Instant) parameter.getValue();
        parameter.getPs().setObject(parameter.getIndex(), LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
}
