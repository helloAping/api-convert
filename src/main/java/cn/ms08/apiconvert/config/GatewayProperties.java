package cn.ms08.apiconvert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关业务配置，集中承载数据库安装、鉴权和管理端账号等可外部覆盖的参数。
 */
@ConfigurationProperties(prefix = "api-convert")
public class GatewayProperties {

    /**
     * 数据库安装和连接相关配置。
     */
    private Database database = new Database();
    /**
     * 网关鉴权和管理端登录配置。
     */
    private Security security = new Security();

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    /**
     * 数据库配置，SQLite 默认文件位于应用启动目录，可通过环境变量指定完整文件路径。
     */
    public static class Database {
        /**
         * 数据库类型，支持 sqlite 或 mysql。
         */
        private String type = "sqlite";
        /**
         * SQLite 数据库文件路径，只在未显式设置 spring.datasource.url 时参与默认 URL 拼接。
         */
        private String sqlitePath = System.getProperty("user.dir") + "/api-convert.db";
        /**
         * 是否在启动时自动安装或升级数据库结构。
         */
        private boolean installEnabled = true;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSqlitePath() {
            return sqlitePath;
        }

        public void setSqlitePath(String sqlitePath) {
            this.sqlitePath = sqlitePath;
        }

        public boolean isInstallEnabled() {
            return installEnabled;
        }

        public void setInstallEnabled(boolean installEnabled) {
            this.installEnabled = installEnabled;
        }
    }

    public static class Security {
        private boolean enabled = true;
        private Admin admin = new Admin();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Admin getAdmin() {
            return admin;
        }

        public void setAdmin(Admin admin) {
            this.admin = admin;
        }
    }

    public static class Admin {
        private String username = "admin";
        private String password = "admin123";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

}
