package cn.ms08.apiconvert.endpoint;

public final class ProtocolFormat {

    private ProtocolFormat() {}

    public static final String OPENAI = "openai";
    public static final String OPENAI_RESPONSE = "openai-response";
    public static final String CLAUDE = "claude";
    public static final String GEMINI = "gemini";
    public static final String CODEX = "codex";
    public static final String GEMINI_CLI = "gemini-cli";
    public static final String ANTIGRAVITY = "antigravity";

    public static String fromEndpoint(EndpointType endpoint) {
        return switch (endpoint) {
            case CHAT_COMPLETIONS -> OPENAI;
            case OPENAI_RESPONSES -> OPENAI_RESPONSE;
            case ANTHROPIC_MESSAGES -> CLAUDE;
            case OPENAI_VIDEOS -> OPENAI;
            case OPENAI_IMAGES -> OPENAI;
            case OPENAI_MODELS -> OPENAI;
            case OPENAI_EMBEDDINGS -> OPENAI;
            case AUDIO_SPEECH -> OPENAI;
            case AUDIO_TRANSCRIPTIONS -> OPENAI;
            case HEALTH -> "health";
        };
    }

    public static String fromProvider(cn.ms08.apiconvert.provider.ProviderType provider) {
        return switch (provider) {
            case OPENAI, GPT_AUTH, CUSTOM, MIMO_TOKEN_PLAN -> OPENAI;
            case DEEPSEEK, VOLC_CODINGPLAN, OPENCODE -> OPENAI;
            case ANTHROPIC, CLAUDE_AUTH -> CLAUDE;
            case GEMINI -> GEMINI;
            case LOCAL -> "local";
        };
    }
}
