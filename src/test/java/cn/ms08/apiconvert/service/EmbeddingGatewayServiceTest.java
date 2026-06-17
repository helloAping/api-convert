package cn.ms08.apiconvert.service;

import cn.ms08.apiconvert.dto.ModelRoute;
import cn.ms08.apiconvert.dto.OpenAiEmbeddingRequest;
import cn.ms08.apiconvert.dto.ProviderModel;
import cn.ms08.apiconvert.endpoint.EndpointType;
import cn.ms08.apiconvert.exception.ErrorCode;
import cn.ms08.apiconvert.exception.GatewayException;
import cn.ms08.apiconvert.exception.ProviderException;
import cn.ms08.apiconvert.metrics.GatewayMetrics;
import cn.ms08.apiconvert.provider.AiProviderClient;
import cn.ms08.apiconvert.provider.ProviderClientRegistry;
import cn.ms08.apiconvert.provider.ProviderType;
import cn.ms08.apiconvert.vo.OpenAiEmbeddingResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingGatewayServiceTest {

    private RoutingService routingService;
    private ProviderClientRegistry providerClientRegistry;
    private UsageRecorder usageRecorder;
    private ApiKeyQuotaService apiKeyQuotaService;
    private GatewayMetrics metrics;
    private EmbeddingGatewayService service;

    @BeforeEach
    void setUp() {
        routingService = mock(RoutingService.class);
        providerClientRegistry = mock(ProviderClientRegistry.class);
        usageRecorder = mock(UsageRecorder.class);
        apiKeyQuotaService = mock(ApiKeyQuotaService.class);
        metrics = new GatewayMetrics(new SimpleMeterRegistry());
        service = new EmbeddingGatewayService(routingService, providerClientRegistry,
                usageRecorder, apiKeyQuotaService, metrics);
    }

    @Test
    void emptyInputIsRejected() {
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest();
        request.setModel("text-embedding-3-small");
        request.setInput(List.of());

        assertThatThrownBy(() -> service.embed(request, new MockHttpServletRequest()))
                .isInstanceOf(GatewayException.class)
                .extracting(e -> ((GatewayException) e).code())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void singleStringInputIsNormalisedAndForwarded() {
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest();
        request.setModel("text-embedding-3-small");
        request.setInput("hello world");

        ModelRoute route = route("text-embedding-3-small", "/v1/embeddings");
        when(routingService.resolveModel(eq("text-embedding-3-small"), any(), any(), any(), eq(EndpointType.OPENAI_EMBEDDINGS)))
                .thenReturn(route);

        AiProviderClient client = mock(AiProviderClient.class);
        OpenAiEmbeddingResponse upstream = new OpenAiEmbeddingResponse();
        OpenAiEmbeddingResponse.Item item = new OpenAiEmbeddingResponse.Item();
        item.setIndex(0);
        item.setEmbedding(List.of(0.1f, 0.2f, 0.3f));
        upstream.setData(List.of(item));
        OpenAiEmbeddingResponse.Usage usage = new OpenAiEmbeddingResponse.Usage();
        usage.setPromptTokens(2);
        usage.setTotalTokens(2);
        upstream.setUsage(usage);
        when(providerClientRegistry.get(ProviderType.OPENAI)).thenReturn(client);
        when(client.embed(any(), any())).thenReturn(upstream);

        OpenAiEmbeddingResponse response = service.embed(request, new MockHttpServletRequest());

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getEmbedding()).containsExactly(0.1f, 0.2f, 0.3f);

        ArgumentCaptor<OpenAiEmbeddingRequest> sentCaptor = ArgumentCaptor.forClass(OpenAiEmbeddingRequest.class);
        verify(client).embed(any(), sentCaptor.capture());
        assertThat(sentCaptor.getValue().getModel()).isEqualTo("text-embedding-3-small");
        verify(apiKeyQuotaService).deductEmbeddings(any(), any(), eq(2));
    }

    @Test
    void providerUnsupportedFeatureBubblesUp() {
        OpenAiEmbeddingRequest request = new OpenAiEmbeddingRequest();
        request.setModel("text-embedding-3-small");
        request.setInput("hello");

        when(routingService.resolveModel(anyString(), any(), any(), any(), any()))
                .thenReturn(route("text-embedding-3-small", "/v1/embeddings"));

        AiProviderClient client = mock(AiProviderClient.class);
        when(providerClientRegistry.get(any())).thenReturn(client);
        doThrow(new ProviderException(ErrorCode.UNSUPPORTED_FEATURE, HttpStatus.BAD_REQUEST, "not supported"))
                .when(client).embed(any(), any());

        assertThatThrownBy(() -> service.embed(request, new MockHttpServletRequest()))
                .isInstanceOf(ProviderException.class)
                .extracting(e -> ((ProviderException) e).code())
                .isEqualTo(ErrorCode.UNSUPPORTED_FEATURE);

        verify(usageRecorder).recordFailure(anyString(), any(), eq("openai"), eq("embeddings"), any(),
                any(), eq(false), eq(HttpStatus.BAD_REQUEST.value()), anyLong(), eq("UNSUPPORTED_FEATURE"), anyString());
    }

    private ModelRoute route(String providerModel, String embeddingPath) {
        return new ModelRoute(
                providerModel, "openai-main", ProviderType.OPENAI, providerModel,
                "https://api.openai.com", "/v1/chat/completions", null, null, embeddingPath,
                "sk-test", null, null,
                new BigDecimal("1.0"), new BigDecimal("2.0"), new BigDecimal("0.5"),
                List.of(), null);
    }
}
