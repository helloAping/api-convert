# 模块 10：代码目录结构

> 对应代码：`src/main/java/cn/ms08/apiconvert/`
> 参考模块：所有业务模块

---

## Java 源码结构

```
src/main/java/cn/ms08/apiconvert/
├── adapter/                            # 端点-供应商协议适配器
│   ├── endpoint/
│   │   ├── EndpointProviderAdapter.java              # 接口：按 (端点, 供应商) 组合适配
│   │   ├── EndpointProviderAdapterRegistry.java      # 注册表
│   │   ├── AnthropicMessagesToDeepSeekAnthropicAdapter.java
│   │   ├── AnthropicMessagesToGeminiAdapter.java
│   │   ├── AnthropicTools.java                       # Anthropic 工具结构转换辅助
│   │   ├── AnthropicToDeepSeekChatAdapter.java
│   │   ├── AnthropicToOpenAiCompatibleAdapter.java
│   │   ├── ChatCompletionsToAnthropicAdapter.java
│   │   ├── ChatCompletionsToDeepSeekAnthropicAdapter.java
│   │   ├── ChatCompletionsToDeepSeekChatAdapter.java
│   │   ├── ChatCompletionsToGeminiAdapter.java
│   │   ├── ChatToolSequenceNormalizer.java          # 严格 Chat 上游 tool_calls/tool 结果序列归一化
│   │   ├── ResponsesToAnthropicAdapter.java
│   │   ├── ResponsesToDeepSeekAnthropicAdapter.java
│   │   ├── ResponsesToDeepSeekChatAdapter.java
│   │   └── ResponsesToOpenAiCompatibleAdapter.java
├── config/                             # 配置
│   ├── DateTimeConfig.java
│   ├── GatewayProperties.java
│   ├── MyBatisPlusConfig.java
│   ├── MyBatisTimeFillConfig.java
│   ├── PaginationInnerInterceptor.java   # MyBatis-Plus 分页参数映射兼容处理
│   ├── SaTokenWebConfig.java
│   ├── SpaRouteConfig.java                # SPA 路由兜底
│   └── WebConfig.java
├── controller/                         # HTTP 接口
│   ├── GatewayController.java             # 统一调度入口（委托给 EndpointHandler）
│   ├── HealthController.java              # 健康检查（不纳入策略）
│   └── admin/
│       ├── AdminApiKeyController.java
│       ├── AdminAuthController.java
│       ├── AdminChannelAuthController.java
│       ├── AdminChannelController.java
│       ├── AdminDashboardController.java
│       ├── AdminGatewayInfoController.java
│       ├── AdminModelController.java
│       ├── AdminRequestLogController.java
│       └── AdminSystemConfigController.java
├── dao/                                # MyBatis-Plus Mapper
│   ├── AiChannelMapper.java
│   ├── AiChannelModelMapper.java
│   ├── GatewayApiKeyChannelMapper.java
│   ├── GatewayApiKeyMapper.java
│   ├── GatewaySystemConfigMapper.java
│   └── RequestLogMapper.java
├── dto/                                # DTO / 内部传输对象
│   ├── AnthropicMessage.java
│   ├── AnthropicMessageRequest.java
│   ├── ModelRoute.java
│   ├── OpenAiChatCompletionRequest.java
│   ├── OpenAiMessage.java
│   ├── OpenAiResponsesRequest.java
│   ├── ProviderModel.java
│   ├── ProviderModelFetchRequest.java
│   ├── ProviderQuota.java
│   ├── ProviderQuotaFetchRequest.java
│   ├── ResponseFormat.java                # response_format（text/json_object/json_schema）
│   ├── RoutingConfig.java
│   ├── RoutingMode.java
│   ├── UnifiedChatRequest.java
│   ├── UnifiedChatResponse.java
│   ├── UnifiedContentPart.java
│   ├── UnifiedMessage.java
│   ├── UnifiedUsage.java
│   └── admin/
│       ├── AdminLoginRequest.java
│       ├── ApiKeyForm.java
│       ├── ApiKeyQuotaAddRequest.java
│       ├── ApiKeyUpdateForm.java
│       ├── ChannelAuthCallbackRequest.java
│       ├── ChannelForm.java
│       ├── ChannelModelFetchRequest.java
│       ├── ChannelModelForm.java
│       ├── DashboardStatsParam.java
│       ├── ModelCapabilitiesForm.java
│       ├── ModelEnabledForm.java
│       ├── ModelQuotaForm.java
│       ├── RequestLogSearchParam.java
│       └── RoutingConfigRequest.java
├── endpoint/                           # 端点策略模式 + 协议格式标识符
│   ├── EndpointType.java                   # 端点枚举
│   ├── ProtocolFormat.java                 # 协议格式标识符常量
│   ├── EndpointHandler.java                # 策略接口
│   ├── EndpointRegistry.java               # EnumMap 注册表
│   ├── ChatCompletionsEndpointHandler.java
│   ├── AnthropicMessagesEndpointHandler.java
│   ├── OpenAiResponsesEndpointHandler.java
│   └── OpenAiModelsEndpointHandler.java
├── entity/                             # 数据库实体
│   ├── AiChannelEntity.java
│   ├── AiChannelModelEntity.java
│   ├── GatewayApiKeyChannelEntity.java
│   ├── GatewayApiKeyEntity.java
│   ├── GatewaySystemConfigEntity.java
│   └── RequestLogEntity.java
├── exception/                          # 异常处理
│   ├── ErrorCode.java
│   ├── GatewayException.java
│   ├── ProviderException.java
│   └── GlobalExceptionHandler.java
├── logging/                            # HTTP 日志脱敏
│   ├── HttpTrafficLoggingFilter.java
│   ├── LogSanitizer.java
│   └── RestClientLoggingInterceptor.java
├── provider/                           # Provider 策略层
│   ├── ProviderType.java                  # 供应商枚举
│   ├── AiProviderClient.java              # 策略接口
│   ├── ProviderClientRegistry.java        # EnumMap 注册表
│   ├── OpenAiCompatibleProviderClient.java
│   ├── AnthropicProviderClient.java
│   ├── GptAuthProviderClient.java
│   ├── ClaudeAuthProviderClient.java
│   ├── DeepSeekAnthropicProviderClient.java
│   ├── DeepSeekChatProviderClient.java
│   ├── OpenAiResponsesProviderClient.java
│   ├── GeminiProviderClient.java
│   └── auth/
│       └── AuthCredential.java
├── security/                           # 鉴权
│   ├── ApiKeyHasher.java
│   ├── GatewayPrincipal.java
│   └── GatewayApiKeyFilter.java
├── service/                            # 业务服务
│   ├── ChatGatewayService.java
│   ├── ApiKeyQuotaService.java
│   ├── DatabaseInstaller.java
│   ├── GatewayBootstrapService.java
│   ├── InstallStatusService.java
│   ├── RoutingService.java
│   ├── SystemConfigService.java
│   ├── UsageRecorder.java
│   ├── admin/
│   │   ├── AdminApiKeyService.java
│   │   ├── AdminAuthService.java
│   │   ├── AdminChannelAuthService.java
│   │   ├── AdminChannelService.java
│   │   ├── AdminDashboardService.java
│   │   ├── AdminModelService.java
│   │   └── AdminRequestLogService.java
│   └── auth/
│       ├── AuthFileService.java
│       └── AuthStorageService.java
├── stream/                             # 流式 SSE 转换器
│   ├── StreamResponseTransformer.java     # 接口
│   ├── StreamTransformerRegistry.java     # 注册表
│   └── RealTimeResponsesTransformer.java  # Responses API 实时转换器
└── vo/                                 # API 响应 VO
    ├── AnthropicMessageResponse.java
    ├── OpenAiChatCompletionResponse.java
    ├── OpenAiModelListResponse.java
    ├── OpenAiResponsesResponse.java
    ├── ApiResponse.java
    ├── PageResult.java
    └── admin/
        ├── AdminLoginVO.java
        ├── ApiKeyCreationVO.java
        ├── ApiKeyVO.java
        ├── ChannelAuthStartVO.java
        ├── ChannelAuthStatusVO.java
        ├── ChannelModelMappingVO.java
        ├── ChannelQuotaVO.java
        ├── ChannelVO.java
        ├── DashboardDimensionUsageVO.java
        ├── DashboardSeriesPointVO.java
        ├── DashboardSeriesVO.java
        ├── DashboardStatsVO.java
        ├── DashboardSummaryVO.java
        ├── DashboardTokenPointVO.java
        ├── GatewayInfoVO.java
        ├── ModelVO.java
        ├── RequestLogVO.java
        ├── RoutingConfigVO.java
        └── UpstreamModelVO.java
```
