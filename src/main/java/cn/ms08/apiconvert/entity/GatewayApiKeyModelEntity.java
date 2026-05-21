package cn.ms08.apiconvert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 网关密钥模型授权实体，用于限制密钥只能调用指定对外模型名。
 */
@Getter
@Setter
@TableName("gateway_api_key_model")
public class GatewayApiKeyModelEntity {

    /**
     * 授权记录主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属网关密钥 ID。
     */
    private Long apiKeyId;
    /**
     * 允许调用的网关对外模型名。
     */
    private String publicModel;
    /**
     * 授权记录创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
