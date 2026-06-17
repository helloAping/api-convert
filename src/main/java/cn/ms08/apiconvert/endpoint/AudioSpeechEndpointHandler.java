package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dto.OpenAiAudioBinaryResponse;
import cn.ms08.apiconvert.dto.OpenAiAudioSpeechRequest;
import cn.ms08.apiconvert.service.AudioSpeechGatewayService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OpenAI Audio Speech 端点处理器，处理 POST /v1/audio/speech；
 * 客户端请求体按 OpenAI 标准 JSON 解析，上游响应为二进制音频字节，按 response_format 解析 Content-Type。
 */
@Component
public class AudioSpeechEndpointHandler implements EndpointHandler {

    private final AudioSpeechGatewayService audioSpeechGatewayService;
    private final ObjectMapper objectMapper;

    public AudioSpeechEndpointHandler(AudioSpeechGatewayService audioSpeechGatewayService, ObjectMapper objectMapper) {
        this.audioSpeechGatewayService = audioSpeechGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.AUDIO_SPEECH;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OpenAiAudioSpeechRequest speechRequest = objectMapper.readValue(
                request.getInputStream(), OpenAiAudioSpeechRequest.class);
        OpenAiAudioBinaryResponse speechResponse = audioSpeechGatewayService.speech(speechRequest, request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(speechResponse.getMediaType().toString());
        response.setContentLength(speechResponse.getBody().length);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + speechResponse.getFilename() + "\"");
        response.getOutputStream().write(speechResponse.getBody());
    }
}
