package cn.ms08.apiconvert.dto;

/**
 * 供应商特定模型列表接口返回后的标准化模型项。
 *
 * @param id 路由到上游服务时使用的供应商模型标识
 * @param ownedBy 可选的供应商归属字段，上游返回时会保留用于展示
 */
public record ProviderModel(
        String id,
        String ownedBy
) {
}
