package cn.ms08.apiconvert.endpoint;

import cn.ms08.apiconvert.dto.OpenAiAudioTranscriptionRequest;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.service.AudioTranscriptionGatewayService;
import cn.ms08.apiconvert.vo.OpenAiAudioTranscriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import java.io.IOException;
import java.util.Arrays;

/**
 * OpenAI Audio Transcriptions 端点处理器，处理 POST /v1/audio/transcriptions；
 * 客户端以 multipart/form-data 上传音频文件，按 OpenAI 协议解析 file / model / language / prompt / response_format /
 * temperature / timestamp_granularities 字段，透传到上游 verbose_json 响应。
 */
@Component
public class AudioTranscriptionEndpointHandler implements EndpointHandler {

    private final AudioTranscriptionGatewayService audioTranscriptionGatewayService;
    private final ObjectMapper objectMapper;

    public AudioTranscriptionEndpointHandler(AudioTranscriptionGatewayService audioTranscriptionGatewayService,
                                             ObjectMapper objectMapper) {
        this.audioTranscriptionGatewayService = audioTranscriptionGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EndpointType endpointType() {
        return EndpointType.AUDIO_TRANSCRIPTIONS;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        MultipartHttpServletRequest multipartRequest = resolveMultipart(request);
        MultipartFile file = multipartRequest.getFile("file");
        String model = multipartRequest.getParameter("model");
        OpenAiAudioTranscriptionRequest transcriptionRequest = OpenAiAudioTranscriptionRequest.fromMultipart(file, model);
        transcriptionRequest.setLanguage(multipartRequest.getParameter("language"));
        transcriptionRequest.setPrompt(multipartRequest.getParameter("prompt"));
        transcriptionRequest.setResponseFormat(multipartRequest.getParameter("response_format"));
        String temperature = multipartRequest.getParameter("temperature");
        if (StringUtils.hasText(temperature)) {
            try {
                transcriptionRequest.setTemperature(Double.parseDouble(temperature));
            } catch (NumberFormatException ignored) {
                throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                        "temperature must be a number");
            }
        }
        String[] granularities = multipartRequest.getParameterValues("timestamp_granularities[]");
        if (granularities == null) {
            granularities = multipartRequest.getParameterValues("timestamp_granularities");
        }
        if (granularities != null && granularities.length > 0) {
            transcriptionRequest.setTimestampGranularities(Arrays.asList(granularities));
        }
        OpenAiAudioTranscriptionResponse transcriptionResponse = audioTranscriptionGatewayService.transcribe(
                transcriptionRequest, request);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), transcriptionResponse);
    }

    /**
     * 兼容未自动包装为 MultipartHttpServletRequest 的情况（如 filter 在前吃掉解析），
     * 主动从 raw parts 构造请求。
     */
    private MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) throws IOException {
        if (request instanceof MultipartHttpServletRequest multipart) {
            return multipart;
        }
        if (request.getContentType() != null
                && request.getContentType().toLowerCase().startsWith("multipart/")) {
            return new StandardMultipartHttpServletRequest(request);
        }
        throw new GatewayException(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST,
                "Content-Type must be multipart/form-data");
    }
}
