package cn.ms08.apiconvert.vo.admin;

/**
 * 获取供应商模型列表后返回给管理前端的模型选项。
 *
 * @param id 可写入 providerModel 的上游模型 ID
 * @param ownedBy 仅用于展示的可选归属文本
 */
public record UpstreamModelVO(
    String id,
    String ownedBy
) {}
