package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dto.OpenAiEmbeddingRequest;
import cn.ms08.apiconvert.service.EmbeddingGatewayService;
import cn.ms08.apiconvert.vo.OpenAiEmbeddingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OpenAI Embeddings API 端点处理器，处理 POST /v1/embeddings。
 * 仅支持非流式调用，模型路由和额度扣减由 {@link EmbeddingGatewayService} 承担。
 */
@Component
public class OpenAiEmbeddingsEndpointHandler implements EndpointHandler {

    private final EmbeddingGatewayService embeddingGatewayService;
    private final ObjectMapper objectMapper;

    public OpenAiEmbeddingsEndpointHandler(EmbeddingGatewayService embeddingGatewayService,
                                           ObjectMapper objectMapper) {
        this.embeddingGatewayService = embeddingGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.OPENAI_EMBEDDINGS;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiEmbeddingRequest embeddingRequest = objectMapper.readValue(
                request.getInputStream(), OpenAiEmbeddingRequest.class);
        OpenAiEmbeddingResponse embeddingResponse = embeddingGatewayService.embed(embeddingRequest, request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), embeddingResponse);
    }
}
