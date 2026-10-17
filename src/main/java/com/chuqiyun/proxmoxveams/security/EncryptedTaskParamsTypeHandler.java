package com.chuqiyun.proxmoxveams.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * task.params JSON 列的凭据处理器。
 * 写入数据库时递归加密敏感参数，读取时恢复为业务使用的明文 Map。
 */
public class EncryptedTaskParamsTypeHandler extends BaseTypeHandler<Map<Object, Object>> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<Object, Object> parameter,
                                    JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(TaskPasswordValues.encryptForStorage(parameter)));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new SQLException("写入任务参数 JSON 失败", e);
        }
    }

    @Override
    public Map<Object, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<Object, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<Object, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<Object, Object> parse(String raw) throws SQLException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> parsed = OBJECT_MAPPER.readValue(raw, Map.class);
            return TaskPasswordValues.decrypt(parsed);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new SQLException("读取任务参数 JSON 失败", e);
        }
    }
}
