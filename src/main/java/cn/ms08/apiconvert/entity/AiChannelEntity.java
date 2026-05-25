package cn.ms08.apiconvert.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 渠道主表实体，整合原供应商、端点和凭证配置。
 */
@Getter
@Setter
@TableName("ai_channel")
public class AiChannelEntity {

    /**
     * 渠道主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 渠道编码，路由和模型映射使用的稳定标识。
     */
    private String code;
    /**
     * 渠道名称。
     */
    private String name;
    /**
     * 供应商类型（ProviderType），例如 OPENAI_COMPATIBLE、ANTHROPIC、OPENAI_RESPONSES、GEMINI。
     */
    private String type;
    /**
     * 上游基础地址。
     */
    private String baseUrl;
    /**
     * 对话或消息请求路径。
     */
    private String chatPath;
    /**
     * 视频生成请求路径，用于 OpenAI Videos API 或兼容供应商的自定义路径。
     */
    private String videoPath;
    /**
     * 图片生成请求路径，用于 OpenAI Images API 或兼容供应商的自定义路径。
     */
    private String imagePath;
    /**
     * 模型列表请求路径。
     */
    private String modelsPath;
    /**
     * 上游 API Key，返回前必须脱敏，日志中必须脱敏。
     */
    private String apiKey;
    /**
     * 鉴权模式：API_KEY 使用 apiKey，AUTH_FILE/OAUTH 使用 auth-dir 中的授权文件。
     */
    private String authMode;
    /**
     * 相对 auth-dir 的授权文件路径，接口响应和日志中不得包含文件内容。
     */
    private String authFilePath;
    /**
     * 授权状态：NOT_CONFIGURED、AUTHORIZED、EXPIRED、ERROR。
     */
    private String authStatus;
    /**
     * 脱敏后的授权身份摘要，例如邮箱或账号 ID。
     */
    private String authSubject;
    /**
     * access token 过期时间，用于管理端展示和路由前快速判断。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime authExpiresAt;
    /**
     * 路由权重，加权模式下数值越高分配流量越多。
     */
    private Integer priority;
    /**
     * 凭证状态，例如 ACTIVE 或 DISABLED。
     */
    private String status;
    /**
     * 渠道是否启用。
     */
    private Boolean enabled;
    /**
     * 最近一次上游错误，仅用于排查，不应包含未脱敏密钥。
     */
    private String lastError;
    /**
     * 最近使用时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastUsedAt;
    /**
     * 渠道创建时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 渠道最近更新时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
