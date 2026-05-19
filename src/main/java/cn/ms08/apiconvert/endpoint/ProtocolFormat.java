package cn.ms08.apiconvert.endpoint;

/**
 * 协议格式标识符常量，类似 CLIProxyAPI 中 sdk/translator/formats.go 的 Format* 常量。
 * <p>
 * 用于端点-供应商适配器注册表键匹配和请求日志标记，支持字符串级别的格式标识，
 * 与 {@link EndpointType}、{@link cn.ms08.apiconvert.provider.ProviderType} 枚举互补。
 * </p>
 */
public final class ProtocolFormat {

    private ProtocolFormat() {
    }

    /** OpenAI Chat Completions 格式。 */
    public static final String OPENAI = "openai";

    /** OpenAI Responses API 格式。 */
    public static final String OPENAI_RESPONSE = "openai-response";

    /** Anthropic Messages 格式。 */
    public static final String CLAUDE = "claude";

    /** Google Gemini 格式。 */
    public static final String GEMINI = "gemini";

    /** OpenAI Codex 格式（预留）。 */
    public static final String CODEX = "codex";

    /** Gemini CLI 格式（预留）。 */
    public static final String GEMINI_CLI = "gemini-cli";

    /** Antigravity 格式（预留）。 */
    public static final String ANTIGRAVITY = "antigravity";

    /**
     * 从端点类型映射到协议格式标识符。
     */
    public static String fromEndpoint(EndpointType endpoint) {
        return switch (endpoint) {
            case CHAT_COMPLETIONS -> OPENAI;
            case OPENAI_RESPONSES -> OPENAI_RESPONSE;
            case ANTHROPIC_MESSAGES -> CLAUDE;
            case OPENAI_MODELS -> OPENAI;
            case HEALTH -> "health";
        };
    }

    /**
     * 从供应商类型映射到协议格式标识符。
     */
    public static String fromProvider(cn.ms08.apiconvert.provider.ProviderType provider) {
        return switch (provider) {
            case OPENAI_COMPATIBLE, DEEPSEEK_CHAT -> OPENAI;
            case OPENAI_RESPONSES -> OPENAI_RESPONSE;
            case ANTHROPIC, DEEPSEEK_ANTHROPIC -> CLAUDE;
            case GEMINI -> GEMINI;
            case LOCAL -> "local";
        };
    }
}
