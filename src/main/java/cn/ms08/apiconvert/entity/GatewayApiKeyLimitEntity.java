package cn.ms08.apiconvert.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 网关密钥限制项实体，按类型保存额度、请求数等可扩展滑动窗口限制。
 */
@Getter
@Setter
@TableName("gateway_api_key_limit")
public class GatewayApiKeyLimitEntity {

    /**
     * 限制项主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 所属网关密钥 ID。
     */
    private Long apiKeyId;
    /**
     * 限制类型，例如 QUOTA 表示额度限制，REQUEST 表示请求数限制。
     */
    private String limitType;
    /**
     * 滑动窗口长度数值。
     */
    private Integer windowValue;
    /**
     * 滑动窗口单位，支持 MINUTE、HOUR、DAY。
     */
    private String windowUnit;
    /**
     * 限制阈值；额度限制表示额度，请求数限制表示次数。
     */
    private BigDecimal limitValue;
    /**
     * 预留扩展配置 JSON，后续新增复杂限制时使用，不能写入密钥明文。
     */
    private String configJson;
    /**
     * 限制项创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 限制项更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
