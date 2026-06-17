package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiAudioBinaryResponse;
import cn.ms08.apiconvert.dto.OpenAiAudioSpeechRequest;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.metrics.GatewayMetrics;
import cn.ms08.apiconvert.provider.AiProviderClient;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.provider.ProviderType;
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

class AudioSpeechGatewayServiceTest {

    private RoutingService routingService;
    private ProviderClientRegistry providerClientRegistry;
    private UsageRecorder usageRecorder;
    private ApiKeyQuotaService apiKeyQuotaService;
    private GatewayMetrics metrics;
    private AudioSpeechGatewayService service;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        providerClientRegistry = mock(ProviderClientRegistry.class);
        usageRecorder = mock(UsageRecorder.class);
        apiKeyQuotaService = mock(ApiKeyQuotaService.class);
        metrics = new GatewayMetrics(new SimpleMeterRegistry());
        service = new AudioSpeechGatewayService(routingService, providerClientRegistry,
                usageRecorder, apiKeyQuotaService, metrics);
    }

    @Test
    void missingInputIsRejected() {
        OpenAiAudioSpeechRequest request = new OpenAiAudioSpeechRequest();
        request.setModel("tts-1");
        request.setVoice("alloy");

        assertThatThrownBy(() -> service.speech(request, new MockHttpServletRequest()))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void inputExceeding4096CharsIsRejected() {
        OpenAiAudioSpeechRequest request = new OpenAiAudioSpeechRequest();
        request.setModel("tts-1");
        request.setVoice("alloy");
        request.setInput("a".repeat(4097));

        assertThatThrownBy(() -> service.speech(request, new MockHttpServletRequest()))
                .isInstanceOf(GatewayException.class)
                .satisfies(e -> assertThat(((GatewayException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void successfulSpeechIsForwarded() {
        OpenAiAudioSpeechRequest request = new OpenAiAudioSpeechRequest();
        request.setModel("tts-1");
        request.setInput("hello world");
        request.setVoice("alloy");
        request.setResponseFormat("mp3");

        ModelRoute route = audioRoute();
        when(routingService.resolveModel(eq("tts-1"), any(), any(), any(), eq(EndpointType.AUDIO_SPEECH)))
                .thenReturn(route);

        AiProviderClient client = mock(AiProviderClient.class);
        byte[] payload = new byte[]{0x49, 0x44, 0x33, 0x04}; // fake ID3 frame bytes
        OpenAiAudioBinaryResponse upstream = new OpenAiAudioBinaryResponse(
                payload, OpenAiAudioBinaryResponse.mediaTypeFor("mp3"), "speech.mp3");
        when(providerClientRegistry.get(ProviderType.OPENAI)).thenReturn(client);
        when(client.speech(any(), any())).thenReturn(upstream);

        OpenAiAudioBinaryResponse response = service.speech(request, new MockHttpServletRequest());

        assertThat(response.getBody()).isEqualTo(payload);
        assertThat(response.getMediaType().toString()).isEqualTo("audio/mpeg");
        verify(usageRecorder).recordSuccess(any(), any(), eq("openai"), eq("audio_speech"),
                any(), any(), any(), eq(false), eq(HttpStatus.OK.value()), anyLong(), any());
    }

    @Test
    void providerErrorIsPropagated() {
        OpenAiAudioSpeechRequest request = new OpenAiAudioSpeechRequest();
        request.setModel("tts-1");
        request.setInput("hi");
        request.setVoice("alloy");

        when(routingService.resolveModel(any(), any(), any(), any(), any())).thenReturn(audioRoute());
        AiProviderClient client = mock(AiProviderClient.class);
        when(providerClientRegistry.get(any())).thenReturn(client);
        doThrow(new ProviderException(ErrorCode.PROVIDER_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS, "rate"))
                .when(client).speech(any(), any());

        assertThatThrownBy(() -> service.speech(request, new MockHttpServletRequest()))
                .isInstanceOf(ProviderException.class);
    }

    private ModelRoute audioRoute() {
        return new ModelRoute(
                "tts-1", "openai-main", ProviderType.OPENAI, "tts-1",
                "https://api.openai.com", "/v1/chat/completions", null, null, null,
                "/v1/audio/speech", "/v1/audio/transcriptions",
                "sk-test", null, null,
                new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("0.5"),
                List.of(), null);
    }
}
