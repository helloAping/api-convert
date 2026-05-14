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
 * 请求日志实体，记录外部工具调用对话接口的协议、路由、耗时和 token 用量。
 */
@Getter
@Setter
@TableName("request_log")
public class RequestLogEntity {

    /**
     * 日志主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 单次网关调用生成的请求编号，用于串联排查日志。
     */
    private String requestId;
    /**
     * 调用方网关密钥 ID；安全关闭时可能为空。
     */
    private Long gatewayApiKeyId;
    /**
     * 外部工具调用协议，例如 openai 或 anthropic。
     */
    private String sourceProtocol;
    /**
     * 对话接口类型，例如 chat_completions 或 messages。
     */
    private String requestType;
    /**
     * 实际承载请求的渠道编码。
     */
    private String providerCode;
    /**
     * 实际承载请求的供应商协议类型。
     */
    private String providerType;
    /**
     * 调用方请求的对外模型名。
     */
    private String publicModel;
    /**
     * 转发到上游时使用的真实模型名。
     */
    private String providerModel;
    /**
     * 是否为流式请求；当前流式未实现也会记录失败日志。
     */
    private Boolean stream;
    /**
     * 请求是否成功完成。
     */
    private Boolean success;
    /**
     * 返回给调用方的 HTTP 状态码。
     */
    private Integer httpStatus;
    /**
     * 从进入网关对话服务到返回或失败的耗时毫秒。
     */
    private Long latencyMs;
    /**
     * 上游返回的输入 token 数，供应商未返回时为空。
     */
    private Integer inputTokens;
    /**
     * 上游返回的缓存读取输入 token 数；前端在输入 token 列中附加展示。
     */
    private Integer cacheReadInputTokens;
    /**
     * 上游返回的输出 token 数，供应商未返回时为空。
     */
    private Integer outputTokens;
    /**
     * 上游返回的总 token 数，供应商未返回时为空。
     */
    private Integer totalTokens;
    /**
     * 失败时的网关错误码。
     */
    private String errorCode;
    /**
     * 失败原因；上游内容由供应商客户端脱敏后写入。
     */
    private String errorMessage;
    /**
     * 日志创建时间，由项目上海时区自动填充。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
