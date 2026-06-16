package cn.ms08.apiconvert.provider;

public enum ProviderType {
    /**
     * 通用 OpenAI 兼容协议：用户自填 baseUrl + apiKey。
     * 同时声明 CHAT_COMPLETIONS / OPENAI_RESPONSES / OPENAI_VIDEOS / OPENAI_IMAGES 能力。
     */
    OPENAI,
    /**
     * 官方 Anthropic Messages 协议供应商，默认 baseUrl https://api.anthropic.com，
     * 使用 x-api-key 鉴权；仅声明 ANTHROPIC_MESSAGES 能力。
     */
    ANTHROPIC,
    /**
     * 自定义供应商：用户自填 baseUrl，同时声明 CHAT_COMPLETIONS + ANTHROPIC_MESSAGES 两个能力，
     * 适用于自行实现标准 OpenAI / Anthropic 兼容协议的网关。
     */
    CUSTOM,
    /**
     * Xiaomi MiMo Token Plan：固定订阅费，按套餐限量调用。
     * OpenAI 兼容协议 baseUrl https://token-plan-cn.xiaomimimo.com/v1，
     * Anthropic 兼容协议路径 /anthropic/v1/messages；同时声明 CHAT_COMPLETIONS + ANTHROPIC_MESSAGES。
     * 参考 https://mimo.mi.com/docs/zh-CN/quick-start/summary/first-api-call
     */
    MIMO_TOKEN_PLAN,
    DEEPSEEK,
    VOLC_CODINGPLAN,
    OPENCODE,
    /**
     * GPT OAuth 授权供应商：使用 auth.json 中的 access_token 调官方 OpenAI API。
     */
    GPT_AUTH,
    /**
     * Claude OAuth 授权供应商：使用 auth.json 中的 access_token 调官方 Anthropic API。
     */
    CLAUDE_AUTH,
    GEMINI,
    LOCAL
}
