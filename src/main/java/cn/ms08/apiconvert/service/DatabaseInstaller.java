package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.config.GatewayProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * 数据库安装与增量升级器，首次安装执行完整 schema，后续只按版本执行迁移 SQL。
 */
@Component
@Order(0)
public class DatabaseInstaller implements CommandLineRunner {

    /**
     * 当前结构版本；升级时必须提供对应版本迁移 SQL，不允许通过重建整库升级。
     */
    private static final int CURRENT_SCHEMA_VERSION = 11;

    /**
     * 获取底层连接以执行 SQL 安装和迁移脚本。
     */
    private final DataSource dataSource;
    /**
     * 查询当前 schema 版本。
     */
    private final JdbcTemplate jdbcTemplate;
    /**
     * 读取数据库类型和是否启用安装器的配置。
     */
    private final GatewayProperties properties;

    /**
     * 注入数据库安装和迁移所需依赖。
     */
    public DatabaseInstaller(DataSource dataSource, JdbcTemplate jdbcTemplate, GatewayProperties properties) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /**
     * 启动时执行首次安装或从当前版本逐个迁移到最新版本。
     */
    @Override
    public void run(String... args) throws Exception {
        if (!properties.getDatabase().isInstallEnabled()) {
            return;
        }
        String type = databaseType();
        if (!schemaVersionTableExists()) {
            executeScript(schemaScript(type));
            return;
        }
        Integer version = currentVersion();
        if (version == null) {
            throw new IllegalStateException("gateway_schema_version exists but has no version row");
        }
        for (int nextVersion = version + 1; nextVersion <= CURRENT_SCHEMA_VERSION; nextVersion++) {
            executeScript(migrationScript(type, nextVersion));
        }
    }

    /**
     * 查询当前数据库中的最大结构版本。
     */
    private Integer currentVersion() {
        return jdbcTemplate.queryForObject("select max(version) from gateway_schema_version", Integer.class);
    }

    /**
     * 根据配置解析数据库类型。
     */
    private String databaseType() {
        String type = properties.getDatabase().getType() == null ? "sqlite" : properties.getDatabase().getType().toLowerCase();
        if (!"sqlite".equals(type) && !"mysql".equals(type)) {
            throw new IllegalStateException("Unsupported database type: " + type);
        }
        return type;
    }

    /**
     * 首次安装脚本只允许创建缺失对象和写入版本，不得删除用户表。
     */
    private String schemaScript(String type) {
        return switch (type) {
            case "mysql" -> "db/schema-mysql.sql";
            case "sqlite" -> "db/schema-sqlite.sql";
            default -> throw new IllegalStateException("Unsupported database type: " + type);
        };
    }

    /**
     * 每个版本的增删改和数据同步都写在对应迁移 SQL 中。
     */
    private String migrationScript(String type, int version) {
        return "db/migration/" + type + "/V" + version + ".sql";
    }

    /**
     * 执行 SQL 脚本；迁移脚本缺失时直接失败，避免跳版本造成结构不一致。
     */
    private void executeScript(String script) throws Exception {
        Resource resource = new ClassPathResource(script);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing database migration script: " + script);
        }
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, resource);
        }
    }

    /**
     * 兼容不同数据库大小写行为，判断版本表是否已经存在。
     */
    private boolean schemaVersionTableExists() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getTables(null, null, "gateway_schema_version", null)) {
                if (resultSet.next()) {
                    return true;
                }
            }
            try (ResultSet resultSet = metaData.getTables(null, null, "GATEWAY_SCHEMA_VERSION", null)) {
                return resultSet.next();
            }
        }
    }
}
