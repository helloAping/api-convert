package cn.ms08.apiconvert.dto.admin;

/**
 * 管理端更新模型能力配置的请求体。
 *
 * @param vision 是否支持图片/视觉输入
 * @param toolsSupport 是否支持工具/函数调用
 * @param jsonModeSupport 是否支持 JSON 输出模式
 * @param contextLength 最大上下文窗口（token 数）
 */
public record ModelCapabilitiesForm(
        Boolean vision,
        Boolean toolsSupport,
        Boolean jsonModeSupport,
        Long contextLength
) {}
