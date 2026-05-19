package cn.ms08.apiconvert.service.admin;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.dto.ProviderModelFetchRequest;
import cn.ms08.apiconvert.dto.ProviderQuotaFetchRequest;
import cn.ms08.apiconvert.dto.admin.ChannelForm;
import cn.ms08.apiconvert.dto.admin.ChannelModelFetchRequest;
import cn.ms08.apiconvert.dto.admin.ChannelModelForm;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.admin.ChannelModelMappingVO;
import cn.ms08.apiconvert.vo.admin.ChannelQuotaVO;
import cn.ms08.apiconvert.vo.admin.ChannelVO;
import cn.ms08.apiconvert.vo.admin.UpstreamModelVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端渠道服务，直接读写渠道主表和渠道模型表。
 */
@Service
public class AdminChannelService {

    /**
     * 默认供应商策略。
     */
    private static final String DEFAULT_TYPE = "OPENAI_COMPATIBLE";
    /**
     * OpenAI 兼容渠道的默认对话补全路径。
     */
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    /**
     * Anthropic 风格渠道的默认消息路径。
     */
    private static final String DEFAULT_ANTHROPIC_PATH = "/v1/messages";
    /**
     * 默认模型发现路径。
     */
    private static final String DEFAULT_MODELS_PATH = "/v1/models";
    /**
     * 默认凭证状态。
     */
    private static final String DEFAULT_STATUS = "ACTIVE";
    /**
     * 默认路由权重，加权模式下数值越高分配流量越多。
     */
    private static final int DEFAULT_PRIORITY = 100;

    /**
     * 读写整合后的渠道主表。
     */
    private final AiChannelMapper channelMapper;
    /**
     * 读写渠道模型映射表。
     */
    private final AiChannelModelMapper channelModelMapper;
    /**
     * 将模型发现分派给供应商特定客户端实现。
     */
    private final ProviderClientRegistry providerClientRegistry;

    /**
     * 注入渠道聚合操作所需的 Mapper 和供应商注册表。
     */
    public AdminChannelService(
            AiChannelMapper channelMapper,
            AiChannelModelMapper channelModelMapper,
            ProviderClientRegistry providerClientRegistry
    ) {
        this.channelMapper = channelMapper;
        this.channelModelMapper = channelModelMapper;
        this.providerClientRegistry = providerClientRegistry;
    }

    /**
     * 查询所有渠道，API Key 返回前必须脱敏。
     */
    public List<ChannelVO> list() {
        return channelMapper.selectList(new LambdaQueryWrapper<AiChannelEntity>().orderByAsc(AiChannelEntity::getId))
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 按渠道主键查询单个渠道。
     */
    public ChannelVO getById(Long id) {
        var channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "渠道不存在");
        }
        return toVO(channel);
    }

    /**
     * 通过选定协议的供应商客户端获取上游模型选项。
     */
    public List<UpstreamModelVO> fetchModels(ChannelModelFetchRequest request) {
        requireText(request.baseUrl(), "Base URL 不能为空");
        String modelsPath = StringUtils.hasText(request.modelsPath()) ? request.modelsPath() : DEFAULT_MODELS_PATH;
        ProviderType providerType = StringUtils.hasText(request.type()) ? ProviderType.valueOf(request.type()) : ProviderType.OPENAI_COMPATIBLE;
        String apiKey = resolveModelFetchApiKey(request);
        return providerClientRegistry.get(providerType)
                .models(new ProviderModelFetchRequest(request.baseUrl(), modelsPath, apiKey))
                .stream()
                .map(model -> new UpstreamModelVO(model.id(), model.ownedBy()))
                .toList();
    }

    /**
     * 编辑渠道发现模型时，前端不会回传已保存密钥；仅在本次输入为空时读取数据库密钥。
     */
    private String resolveModelFetchApiKey(ChannelModelFetchRequest request) {
        if (StringUtils.hasText(request.apiKey())) {
            return request.apiKey();
        }
        if (request.channelId() == null) {
            requireText(request.apiKey(), "渠道 API Key 不能为空");
        }
        var channel = channelMapper.selectById(request.channelId());
        if (channel == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "渠道不存在");
        }
        requireText(channel.getApiKey(), "渠道 API Key 不能为空");
        return channel.getApiKey();
    }

    /**
     * 使用当前已保存渠道配置实时查询上游额度，结果不写入数据库。
     */
    public ChannelQuotaVO fetchQuota(Long id) {
        var channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "渠道不存在");
        }
        requireText(channel.getBaseUrl(), "Base URL 不能为空");
        requireText(channel.getApiKey(), "渠道 API Key 不能为空");
        ProviderType providerType = ProviderType.valueOf(channel.getType());
        var quota = providerClientRegistry.get(providerType)
                .quota(new ProviderQuotaFetchRequest(channel.getBaseUrl(), channel.getApiKey()));
        return new ChannelQuotaVO(
                channel.getId(),
                channel.getCode(),
                quota.supported(),
                quota.summary(),
                quota.balance(),
                quota.used(),
                quota.available(),
                quota.currency(),
                quota.rawSummary()
        );
    }

    /**
     * 在同一个事务中创建渠道主记录和多个模型映射。
     */
    @Transactional
    public ChannelVO create(ChannelForm form) {
        requireText(form.code(), "渠道编码不能为空");
        requireText(form.name(), "渠道名称不能为空");
        requireText(form.baseUrl(), "Base URL 不能为空");

        var existing = channelMapper.selectOne(new LambdaQueryWrapper<AiChannelEntity>()
                .eq(AiChannelEntity::getCode, form.code()));
        if (existing != null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "渠道编码已存在");
        }

        var channel = new AiChannelEntity();
        applyChannelForm(channel, form, true);
        channelMapper.insert(channel);

        replaceModels(channel.getCode(), form.modelPrefix(), modelForms(form));
        return toVO(channel);
    }

    /**
     * 更新渠道主记录；渠道编码创建后不可修改，API Key 为空时保留现有密钥。
     */
    @Transactional
    public ChannelVO update(Long id, ChannelForm form) {
        var channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.NOT_FOUND, "渠道不存在");
        }
        if (StringUtils.hasText(form.code()) && !channel.getCode().equals(form.code())) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "渠道编码创建后不能修改");
        }

        applyChannelForm(channel, form, false);
        channelMapper.updateById(channel);
        replaceModels(channel.getCode(), form.modelPrefix(), modelForms(form));
        return toVO(channel);
    }

    /**
     * 删除渠道主记录和该渠道下的全部模型映射。
     */
    @Transactional
    public void delete(Long id) {
        var channel = channelMapper.selectById(id);
        if (channel == null) {
            return;
        }
        channelModelMapper.delete(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getChannelCode, channel.getCode()));
        channelMapper.deleteById(id);
    }

    /**
     * 将表单字段写入渠道主表实体，更新时空 API Key 不覆盖现有密钥。
     */
    private void applyChannelForm(AiChannelEntity channel, ChannelForm form, boolean creating) {
        if (creating) {
            channel.setCode(form.code());
        }
        if (StringUtils.hasText(form.name())) channel.setName(form.name());
        if (StringUtils.hasText(form.type())) channel.setType(form.type()); else if (creating) channel.setType(DEFAULT_TYPE);
        if (StringUtils.hasText(form.baseUrl())) channel.setBaseUrl(form.baseUrl());
        if (StringUtils.hasText(form.chatPath())) channel.setChatPath(form.chatPath()); else if (creating) channel.setChatPath(defaultPath(channel.getType(), null));
        if (StringUtils.hasText(form.modelsPath())) channel.setModelsPath(form.modelsPath()); else if (creating) channel.setModelsPath(DEFAULT_MODELS_PATH);
        if (StringUtils.hasText(form.apiKey())) channel.setApiKey(form.apiKey());
        if (form.priority() != null) channel.setPriority(form.priority()); else if (creating) channel.setPriority(DEFAULT_PRIORITY);
        if (StringUtils.hasText(form.status())) channel.setStatus(form.status()); else if (creating) channel.setStatus(DEFAULT_STATUS);
        if (form.enabled() != null) channel.setEnabled(form.enabled()); else if (creating) channel.setEnabled(true);
    }

    /**
     * 从渠道主表和模型表组装管理端响应，API Key 必须脱敏。
     */
    private ChannelVO toVO(AiChannelEntity channel) {
        var models = findModels(channel.getCode());
        return new ChannelVO(
                channel.getId(),
                channel.getCode(),
                channel.getName(),
                channel.getType(),
                channel.getEnabled(),
                channel.getBaseUrl(),
                channel.getChatPath(),
                channel.getModelsPath(),
                channel.getId(),
                channel.getName() + " 密钥",
                ChannelVO.maskApiKey(channel.getApiKey()),
                channel.getPriority(),
                channel.getStatus(),
                (long) models.size(),
                models.stream()
                        .map(model -> new ChannelModelMappingVO(model.getId(), model.getPublicName(),
                                model.getProviderModel(), model.getModelAlias(),
                                model.getVision(), model.getToolsSupport(), model.getJsonModeSupport(), model.getContextLength(),
                                model.getEnabled(),
                                model.getInputQuotaPerMillion(), model.getOutputQuotaPerMillion(), model.getCacheReadQuotaPerMillion()))
                        .toList()
        );
    }

    /**
     * 从新批量字段或旧单模型字段中归一化渠道模型映射。
     */
    private List<ChannelModelForm> modelForms(ChannelForm form) {
        if (form.models() != null && !form.models().isEmpty()) {
            return form.models();
        }
        if (StringUtils.hasText(form.publicModel()) || StringUtils.hasText(form.providerModel())) {
            return List.of(new ChannelModelForm(form.publicModel(), form.providerModel()));
        }
        return List.of();
    }

    /**
     * 用本次表单选择的模型完整覆盖当前渠道的模型映射。
     */
    private void replaceModels(String channelCode, String modelPrefix, List<ChannelModelForm> models) {
        channelModelMapper.delete(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getChannelCode, channelCode));
        for (ChannelModelForm modelForm : normalizeModels(modelPrefix, models)) {
            assertAliasUnused(modelForm.modelAlias());
            var model = new AiChannelModelEntity();
            model.setPublicName(modelForm.publicName());
            model.setModelAlias(modelForm.modelAlias());
            model.setChannelCode(channelCode);
            model.setProviderModel(modelForm.providerModel());
            model.setInputQuotaPerMillion(modelForm.inputQuotaPerMillion());
            model.setOutputQuotaPerMillion(modelForm.outputQuotaPerMillion());
            model.setCacheReadQuotaPerMillion(modelForm.cacheReadQuotaPerMillion());
            model.setVision(modelForm.vision());
            model.setToolsSupport(modelForm.toolsSupport());
            model.setJsonModeSupport(modelForm.jsonModeSupport());
            model.setContextLength(modelForm.contextLength());
            model.setEnabled(true);
            channelModelMapper.insert(model);
        }
    }

    /**
     * 检查手动别名全局唯一；普通 publicName 允许重复以支持随机渠道路由。
     */
    private void assertAliasUnused(String alias) {
        if (!StringUtils.hasText(alias)) {
            return;
        }
        Long aliasCount = channelModelMapper.selectCount(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getModelAlias, alias));
        if (aliasCount > 0) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "模型别名已被其他渠道使用: " + alias);
        }
    }

    /**
     * 校验并按对外模型名去重，避免同一渠道重复提交同一个模型。
     */
    private List<ChannelModelForm> normalizeModels(String modelPrefix, List<ChannelModelForm> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        Map<String, ChannelModelForm> normalized = new LinkedHashMap<>();
        for (ChannelModelForm model : models) {
            if (model == null) {
                continue;
            }
            requireText(model.providerModel(), "上游模型名不能为空");
            String alias = resolveAlias(model);
            String publicName = StringUtils.hasText(alias)
                    ? alias
                    : applyModelPrefix(modelPrefix, model.providerModel());
            if (normalized.containsKey(publicName)) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, "公开模型名重复: " + publicName);
            }
            normalized.put(publicName, new ChannelModelForm(publicName, model.providerModel(), alias,
                    model.inputQuotaPerMillion(), model.outputQuotaPerMillion(), model.cacheReadQuotaPerMillion(),
                    model.vision(), model.toolsSupport(), model.jsonModeSupport(), model.contextLength()));
        }
        return new ArrayList<>(normalized.values());
    }

    /**
     * 解析模型别名；兼容旧前端把别名提交在 publicName 字段中的请求。
     */
    private String resolveAlias(ChannelModelForm model) {
        String alias = StringUtils.hasText(model.modelAlias()) ? model.modelAlias() : model.publicName();
        return StringUtils.hasText(alias) ? alias.trim() : null;
    }

    /**
     * 未设置别名时，将可选模型前缀拼到上游模型名前方，形成默认对外模型名。
     */
    private String applyModelPrefix(String modelPrefix, String publicName) {
        if (!StringUtils.hasText(modelPrefix)) {
            return publicName;
        }
        String normalizedPrefix = modelPrefix.trim().replaceAll("^/+", "").replaceAll("/+$", "");
        if (!StringUtils.hasText(normalizedPrefix) || publicName.startsWith(normalizedPrefix + "/")) {
            return publicName;
        }
        return normalizedPrefix + "/" + publicName;
    }

    /**
     * 查询当前渠道保存的全部模型映射。
     */
    private List<AiChannelModelEntity> findModels(String channelCode) {
        return channelModelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                .eq(AiChannelModelEntity::getChannelCode, channelCode)
                .orderByAsc(AiChannelModelEntity::getPublicName));
    }

    /**
     * 当管理员未填写路径时，选择供应商特定的默认请求路径。
     * GEMINI 的对话路径会由客户端按 /v1beta/models/{model}:generateContent 动态构造，这里仅设基础路径。
     */
    private String defaultPath(String type, String path) {
        if (StringUtils.hasText(path)) return path;
        return switch (type) {
            case "ANTHROPIC", "DEEPSEEK_ANTHROPIC" -> DEFAULT_ANTHROPIC_PATH;
            case "OPENAI_RESPONSES" -> "/v1/responses";
            case "DEEPSEEK_CHAT" -> DEFAULT_CHAT_PATH;
            case "GEMINI" -> "/v1beta/models";
            default -> DEFAULT_CHAT_PATH;
        };
    }

    /**
     * 必填文本缺失时抛出面向客户端的校验错误。
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, message);
        }
    }
}
