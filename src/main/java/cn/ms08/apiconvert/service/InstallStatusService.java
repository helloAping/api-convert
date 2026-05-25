package cn.ms08.apiconvert.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 安装状态查询服务，用于健康检查展示当前数据库结构版本。
 */
@Service
public class InstallStatusService {

    /**
     * 当前要求的结构版本；版本 15 支持渠道视频生成和图片生成路径。
     */
    private static final int CURRENT_SCHEMA_VERSION = 15;

    /**
     * 查询 schema 版本表。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 注入 JDBC 查询工具。
     */
    public InstallStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 判断数据库是否已经安装到当前代码要求的结构版本。
     */
    public boolean isInstalled() {
        try {
            Integer version = jdbcTemplate.queryForObject("select max(version) from gateway_schema_version", Integer.class);
            return version != null && version >= CURRENT_SCHEMA_VERSION;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 返回当前数据库中的最大结构版本，查询失败时返回 null。
     */
    public Integer currentVersion() {
        try {
            return jdbcTemplate.queryForObject("select max(version) from gateway_schema_version", Integer.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
