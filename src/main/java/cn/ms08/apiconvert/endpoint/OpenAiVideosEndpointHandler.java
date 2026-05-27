package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dto.OpenAiVideoRequest;
import cn.ms08.apiconvert.service.VideoGatewayService;
import cn.ms08.apiconvert.vo.OpenAiVideoResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OpenAI Videos API 端点处理器，处理 POST /v1/videos 的非流式视频生成请求。
 */
@Component
public class OpenAiVideosEndpointHandler implements EndpointHandler {

    private final VideoGatewayService videoGatewayService;
    private final ObjectMapper objectMapper;

    public OpenAiVideosEndpointHandler(VideoGatewayService videoGatewayService,
                                       ObjectMapper objectMapper) {
        this.videoGatewayService = videoGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.OPENAI_VIDEOS;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiVideoRequest videoRequest = objectMapper.readValue(request.getInputStream(), OpenAiVideoRequest.class);
        OpenAiVideoResponse videoResponse = videoGatewayService.generate(videoRequest, request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), videoResponse);
    }
}
