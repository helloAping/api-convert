package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiAudioTranscriptionRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.metrics.GatewayMetrics;
import cn.ms08.apiconvert.provider.AiProviderClient;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiAudioTranscriptionResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AudioTranscriptionGatewayServiceTest {

    private RoutingService routingService;
    private ProviderClientRegistry providerClientRegistry;
    private UsageRecorder usageRecorder;
    private ApiKeyQuotaService apiKeyQuotaService;
    private GatewayMetrics metrics;
    private AudioTranscriptionGatewayService service;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        providerClientRegistry = mock(ProviderClientRegistry.class);
        usageRecorder = mock(UsageRecorder.class);
        apiKeyQuotaService = mock(ApiKeyQuotaService.class);
        metrics = new GatewayMetrics(new SimpleMeterRegistry());
        service = new AudioTranscriptionGatewayService(routingService, providerClientRegistry,
                usageRecorder, apiKeyQuotaService, metrics);
    }

    @Test
    void missingFileIsRejected() {
        OpenAiAudioTranscriptionRequest request = new OpenAiAudioTranscriptionRequest();
        request.setModel("whisper-1");

        assertThatThrownBy(() -> service.transcribe(request, new MockHttpServletRequest()))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void successfulTranscribeIsForwarded() {
        OpenAiAudioTranscriptionRequest request = new OpenAiAudioTranscriptionRequest();
        request.setModel("whisper-1");
        request.setFileBytes(new byte[]{1, 2, 3, 4});
        request.setFilename("clip.mp3");
        request.setContentType("audio/mpeg");
        request.setResponseFormat("verbose_json");

        ModelRoute route = audioRoute();
        when(routingService.resolveModel(eq("whisper-1"), any(), any(), any(), eq(EndpointType.AUDIO_TRANSCRIPTIONS)))
                .thenReturn(route);

        AiProviderClient client = mock(AiProviderClient.class);
        OpenAiAudioTranscriptionResponse upstream = new OpenAiAudioTranscriptionResponse();
        upstream.setText("hello world");
        upstream.setLanguage("en");
        upstream.setDuration(1.2);
        when(providerClientRegistry.get(ProviderType.OPENAI)).thenReturn(client);
        when(client.transcribe(any(), any())).thenReturn(upstream);

        OpenAiAudioTranscriptionResponse response = service.transcribe(request, new MockHttpServletRequest());

        assertThat(response.getText()).isEqualTo("hello world");
        assertThat(response.getLanguage()).isEqualTo("en");
        verify(usageRecorder).recordSuccess(any(), any(), eq("openai"), eq("audio_transcriptions"),
                any(), any(), any(), eq(false), eq(HttpStatus.OK.value()), anyLong(), any());
    }

    @Test
    void providerErrorIsPropagated() {
        OpenAiAudioTranscriptionRequest request = new OpenAiAudioTranscriptionRequest();
        request.setModel("whisper-1");
        request.setFileBytes(new byte[]{0x01});

        when(routingService.resolveModel(any(), any(), any(), any(), any())).thenReturn(audioRoute());
        AiProviderClient client = mock(AiProviderClient.class);
        when(providerClientRegistry.get(any())).thenReturn(client);
        doThrow(new ProviderException(ErrorCode.PROVIDER_BAD_RESPONSE, HttpStatus.BAD_GATEWAY, "bad"))
                .when(client).transcribe(any(), any());

        assertThatThrownBy(() -> service.transcribe(request, new MockHttpServletRequest()))
                .isInstanceOf(ProviderException.class);
    }

    private ModelRoute audioRoute() {
        return new ModelRoute(
                "whisper-1", "openai-main", ProviderType.OPENAI, "whisper-1",
                "https://api.openai.com", "/v1/chat/completions", null, null, null,
                "/v1/audio/speech", "/v1/audio/transcriptions",
                "sk-test", null, null,
                new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("0.5"),
                List.of(), null);
    }
}
