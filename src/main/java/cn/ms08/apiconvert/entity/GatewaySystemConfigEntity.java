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
 * 网关系统配置实体，用于保存路由策略、失败切换等运行期可调整的全局配置。
 */
@Getter
@Setter
@TableName("gateway_system_config")
public class GatewaySystemConfigEntity {

    /**
     * 配置键，作为稳定主键使用，避免新增配置项时反复调整表结构。
     */
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;
    /**
     * 配置值，由业务服务按配置键解析为枚举、数字或字符串。
     */
    private String configValue;
    /**
     * 面向管理员的配置说明，不参与业务判断。
     */
    private String description;
    /**
     * 配置创建时间，由项目时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 配置最近更新时间，由项目时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
