package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dao.AiChannelMapper;
import cn.ms08.apiconvert.dao.AiChannelModelMapper;
import cn.ms08.apiconvert.entity.AiChannelEntity;
import cn.ms08.apiconvert.entity.AiChannelModelEntity;
import cn.ms08.apiconvert.vo.OpenAiModelListResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * OpenAI 兼容模型列表端点处理器，处理 GET /v1/models。
 */
@Component
public class OpenAiModelsEndpointHandler implements EndpointHandler {

    private final AiChannelModelMapper modelMapper;
    private final AiChannelMapper channelMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiModelsEndpointHandler(AiChannelModelMapper modelMapper, AiChannelMapper channelMapper) {
        this.modelMapper = modelMapper;
        this.channelMapper = channelMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.OPENAI_MODELS;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
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

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new OpenAiModelListResponse("list", models.values().stream().toList()));
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
