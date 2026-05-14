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
 * 渠道模型映射实体，保存对外模型名、可选别名和上游真实模型名的对应关系。
 */
@Getter
@Setter
@TableName("ai_channel_model")
public class AiChannelModelEntity {

    /**
     * 模型映射主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 网关对外模型名；普通模型名允许多个渠道重复，路由时会随机选择可用渠道。
     */
    private String publicName;
    /**
     * 用户手动设置的模型别名；别名不可重复，为空表示使用前缀和上游模型名生成对外模型名。
     */
    private String modelAlias;
    /**
     * 所属渠道编码。
     */
    private String channelCode;
    /**
     * 上游真实模型名。
     */
    private String providerModel;
    /**
     * 能力配置 JSON。
     */
    private String capabilitiesJson;
    /**
     * 每 100 万普通输入 token 消耗的额度；为空或 0 表示不按该维度计费。
     */
    private BigDecimal inputQuotaPerMillion;
    /**
     * 每 100 万输出 token 消耗的额度；为空或 0 表示不按该维度计费。
     */
    private BigDecimal outputQuotaPerMillion;
    /**
     * 每 100 万缓存读取输入 token 消耗的额度；为空时缓存读取按普通输入计费。
     */
    private BigDecimal cacheReadQuotaPerMillion;
    /**
     * 模型映射是否启用。
     */
    private Boolean enabled;
    /**
     * 模型映射创建时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    /**
     * 模型映射最近更新时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
