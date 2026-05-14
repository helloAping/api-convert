package cn.ms08.apiconvert.controller;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.vo.OpenAiModelListResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;

/**
 * OpenAI 兼容模型列表接口，返回至少有一个可用渠道承载的网关对外模型名。
 */
@RestController
public class OpenAiModelController {

    /**
     * 渠道模型映射 Mapper。
     */
    private final AiChannelModelMapper modelMapper;
    /**
     * 渠道主表 Mapper，用于过滤不可用渠道。
     */
    private final AiChannelMapper channelMapper;

    /**
     * 注入模型映射和渠道 Mapper。
     */
    public OpenAiModelController(AiChannelModelMapper modelMapper, AiChannelMapper channelMapper) {
        this.modelMapper = modelMapper;
        this.channelMapper = channelMapper;
    }

    /**
     * 返回启用且可路由的对外模型列表，同名模型只展示一次。
     */
    @GetMapping("/v1/models")
    public OpenAiModelListResponse models() {
        LinkedHashMap<String, OpenAiModelListResponse.Model> models = new LinkedHashMap<>();
        modelMapper.selectList(new LambdaQueryWrapper<AiChannelModelEntity>()
                        .eq(AiChannelModelEntity::getEnabled, true)
                        .orderByAsc(AiChannelModelEntity::getPublicName))
                .forEach(model -> {
                    AiChannelEntity channel = channelMapper.selectOne(new LambdaQueryWrapper<AiChannelEntity>()
                            .eq(AiChannelEntity::getCode, model.getChannelCode()));
                    if (isRoutable(channel)) {
                        models.putIfAbsent(model.getPublicName(),
                                new OpenAiModelListResponse.Model(model.getPublicName(), "model", model.getChannelCode()));
                    }
                });
        return new OpenAiModelListResponse("list", models.values().stream().toList());
    }

    /**
     * 模型列表只暴露可实际转发的渠道，避免外部工具选择后立即路由失败。
     */
    private boolean isRoutable(AiChannelEntity channel) {
        return channel != null
                && Boolean.TRUE.equals(channel.getEnabled())
                && "ACTIVE".equals(channel.getStatus())
                && StringUtils.hasText(channel.getApiKey());
    }
}
