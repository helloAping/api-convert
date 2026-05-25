package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dto.OpenAiImageRequest;
import cn.ms08.apiconvert.service.ImageGatewayService;
import cn.ms08.apiconvert.vo.OpenAiImageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OpenAI Images API 端点处理器，处理 POST /v1/images/generations 的非流式图片生成请求。
 */
@Component
public class OpenAiImagesEndpointHandler implements EndpointHandler {

    private final ImageGatewayService imageGatewayService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiImagesEndpointHandler(ImageGatewayService imageGatewayService) {
        this.imageGatewayService = imageGatewayService;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.OPENAI_IMAGES;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiImageRequest imageRequest = objectMapper.readValue(request.getInputStream(), OpenAiImageRequest.class);
        OpenAiImageResponse imageResponse = imageGatewayService.generate(imageRequest, request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), imageResponse);
    }
}
