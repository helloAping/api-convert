package cn.ms08.apiconvert.dto;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiAudioBinaryResponseTest {

    @Test
    void nullFormatDefaultsToMp3() {
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor(null).toString()).isEqualTo("audio/mpeg");
        assertThat(OpenAiAudioBinaryResponse.extensionFor(null)).isEqualTo("mp3");
    }

    @Test
    void mapsAllOpenAiFormats() {
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("mp3").toString()).isEqualTo("audio/mpeg");
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("OPUS").toString()).isEqualTo("audio/ogg");
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("aac").toString()).isEqualTo("audio/aac");
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("flac").toString()).isEqualTo("audio/flac");
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("wav").toString()).isEqualTo("audio/wav");
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("pcm").toString()).isEqualTo("audio/pcm");
    }

    @Test
    void unknownFormatFallsBackToOctetStream() {
        assertThat(OpenAiAudioBinaryResponse.mediaTypeFor("midi").toString())
                .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        assertThat(OpenAiAudioBinaryResponse.extensionFor("midi")).isEqualTo("bin");
    }
}
