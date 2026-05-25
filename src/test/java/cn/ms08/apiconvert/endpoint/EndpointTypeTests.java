package cn.ms08.apiconvert.endpoint;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointTypeTests {

    /**
     * 视频端点需要出现在管理端网关信息中，便于前端自动展示公开 API 清单。
     */
    @Test
    void openAiVideosEndpointIsPublished() {
        assertThat(EndpointType.OPENAI_VIDEOS.method()).isEqualTo("POST");
        assertThat(EndpointType.OPENAI_VIDEOS.path()).isEqualTo("/v1/videos");
        assertThat(EndpointType.OPENAI_VIDEOS.toEndpointVO().path()).isEqualTo("/v1/videos");
        assertThat(ProtocolFormat.fromEndpoint(EndpointType.OPENAI_VIDEOS)).isEqualTo(ProtocolFormat.OPENAI);
        assertThat(EndpointType.OPENAI_IMAGES.path()).isEqualTo("/v1/images/generations");
        assertThat(ProtocolFormat.fromEndpoint(EndpointType.OPENAI_IMAGES)).isEqualTo(ProtocolFormat.OPENAI);
    }
}
