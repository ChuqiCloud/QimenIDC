package com.chuqiyun.proxmoxveams.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @Author: 星禾
 * @Description: 凭据字段的 MyBatis 处理器，写入时加密、读取时在服务内部解密
 * @DateTime: 2026/10/17 09:46
 *
 * 旧明文由读取路径兼容，便于部署迁移脚本逐步转换历史数据。
 */
public class EncryptedPasswordTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, StoredPasswordCipher.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return decrypt(cs.getString(columnIndex));
    }

    private String decrypt(String value) throws SQLException {
        try {
            return StoredPasswordCipher.decrypt(value);
        } catch (IllegalArgumentException e) {
            throw new SQLException("读取凭据字段失败", e);
        }
    }
}
