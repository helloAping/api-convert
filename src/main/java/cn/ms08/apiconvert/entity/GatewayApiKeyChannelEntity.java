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
 * 网关密钥渠道授权实体，用于限制外部工具密钥只能调用指定渠道。
 */
@Getter
@Setter
@TableName("gateway_api_key_channel")
public class GatewayApiKeyChannelEntity {

    /**
     * 授权记录主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 网关密钥 ID，关联 gateway_api_key。
     */
    private Long apiKeyId;
    /**
     * 允许该密钥使用的渠道编码。
     */
    private String channelCode;
    /**
     * 授权记录创建时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
