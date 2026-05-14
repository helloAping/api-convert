package cn.ms08.apiconvert.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网关密钥实体，保存外部工具调用 OpenAI/Anthropic 兼容接口时携带的密钥。
 */
@Getter
@Setter
@TableName("gateway_api_key")
public class GatewayApiKeyEntity {

    /**
     * 网关密钥主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 管理端展示的密钥名称。
     */
    private String name;
    /**
     * 外部工具实际携带的明文密钥；仅管理端返回，日志和普通展示必须脱敏。
     */
    private String rawKey;
    /**
     * 外部工具实际携带密钥的 SHA-256 哈希，用于鉴权快速匹配。
     */
    private String apiKeyHash;
    /**
     * 脱敏展示值，例如 sk-****abcd；仅用于兼容历史响应，不能用于鉴权。
     */
    private String keyPreview;
    /**
     * 密钥状态，只有 ACTIVE 可以调用 OpenAI/Anthropic 兼容接口。
     */
    private String status;
    /**
     * 密钥剩余额度；为空表示不限制总额度，便于兼容历史密钥。
     */
    private BigDecimal quotaBalance;
    /**
     * 滑动窗口内最多可消耗的额度；为空表示不启用周期限制。
     */
    private BigDecimal quotaLimit;
    /**
     * 滑动窗口长度数值，配合 quotaWindowUnit 使用。
     */
    private Integer quotaWindowValue;
    /**
     * 滑动窗口单位，支持 HOUR、DAY、MONTH。
     */
    private String quotaWindowUnit;
    /**
     * 密钥创建时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 密钥最近更新时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
