package cn.ms08.apiconvert.service.auth;

import cn.ms08.apiconvert.config.GatewayProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 解析并创建 auth-dir 根目录，保证授权文件路径只能落在受控目录内。
 */
@Service
public class AuthStorageService {

    private final GatewayProperties properties;

    public AuthStorageService(GatewayProperties properties) {
        this.properties = properties;
    }

    public Path rootDirectory() {
        String configured = properties.getAuth() == null ? null : properties.getAuth().getStorageDir();
        Path root;
        if (StringUtils.hasText(configured)) {
            root = Paths.get(configured);
        } else if ("mysql".equalsIgnoreCase(properties.getDatabase().getType())) {
            root = Paths.get("/opt/data/auth-dir");
        } else {
            Path sqlite = Paths.get(properties.getDatabase().getSqlitePath()).toAbsolutePath().normalize();
            Path parent = sqlite.getParent() == null ? Paths.get(System.getProperty("user.dir")) : sqlite.getParent();
            root = parent.resolve("auth-dir");
        }
        return root.toAbsolutePath().normalize();
    }

    public Path resolveRelative(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("auth file path is empty");
        }
        Path root = rootDirectory();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid auth file path");
        }
        return resolved;
    }

    public Path channelFile(String providerType, String channelCode) throws Exception {
        Path root = rootDirectory();
        Path directory = root.resolve(safeName(providerType));
        Files.createDirectories(directory);
        return directory.resolve(safeName(channelCode) + ".json").normalize();
    }

    public String toRelative(Path path) {
        return rootDirectory().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String safeName(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("auth path segment is empty");
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
