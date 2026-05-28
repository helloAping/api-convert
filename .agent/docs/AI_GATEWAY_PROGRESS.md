# AI Gateway 椤圭洰鎬昏

**api-convert** 鏄竴涓?AI API 缃戝叧锛岃仛鍚堜笉鍚?AI 鍘傚晢 API 绔偣锛岄€傞厤 OpenAI / Claude 绛夊鎴风鍗忚锛屽苟璺敱鍒版寚瀹氬巶鍟嗙殑鎸囧畾妯″瀷銆?
鎶€鏈爤锛歋pring Boot 4.0.6 + Java 25 + Maven + MyBatis-Plus 3.5.16銆?鏁版嵁搴?schema 鐗堟湰锛?*V15**銆傜鐞嗗墠绔細Vue 3.5 + Naive UI + Vite銆?
---

## 妯″潡鏂囨。绱㈠紩

鎸変笟鍔℃ā鍧楁媶鍒嗭紝姣忎釜妯″潡鐙珛缁存姢锛屼簰涓嶅奖鍝嶃€侫I 鍦ㄤ慨鏀规煇涓€妯″潡鏃跺彧闇€璇诲彇瀵瑰簲鏂囨。锛屽悓鏃堕€氳繃鏈储寮曚簡瑙ｅ叾浠栨ā鍧楃殑瀛樺湪鍜屽姛鑳借竟鐣屻€?
| 缂栧彿 | 妯″潡 | 鏂囦欢 | 璇存槑 |
|---|---|---|---|
| 01 | **鍩虹璁炬柦涓庢暟鎹眰** | `modules/01-infrastructure.md` | Spring Boot銆丮yBatis-Plus銆佹暟鎹簱瀹夎/鍗囩骇銆佹牳蹇冩暟鎹〃銆佸惎鍔ㄥ紩瀵?|
| 02 | **瀹夊叏閴存潈涓庨檺娴?* | `modules/02-security.md` | API Key 閴存潈锛圫HA-256锛夈€侀搴﹁璐广€佹粦鍔ㄧ獥鍙ｉ檺娴?|
| 03 | **璺敱涓庤皟搴?* | `modules/03-routing.md` | 妯″瀷璺敱瑙ｆ瀽锛圧ANDOM/ROUND_ROBIN/WEIGHTED/SESSION_STICKY锛夈€佸伐鍏蜂紭鍏堛€侀敊璇伩璁┿€佽姹傛棩蹇?|
| 04 | **绔偣涓庡崗璁€傞厤** | `modules/04-endpoints.md` | 6 涓叕寮€绔偣锛圕HAT_COMPLETIONS/ANTHROPIC_MESSAGES/OPENAI_RESPONSES/OPENAI_VIDEOS/OPENAI_IMAGES/OPENAI_MODELS锛夈€?2 涓法鍗忚閫傞厤鍣ㄣ€佷袱灞傜瓥鐣ユā寮?|
| 05 | **Provider 鍘傚晢瀹炵幇** | `modules/05-providers.md` | 8 涓?Provider 绫诲瀷锛圤PENAI_COMPATIBLE/ANTHROPIC/OPENAI_RESPONSES/GPT_AUTH/CLAUDE_AUTH/DEEPSEEK_CHAT/DEEPSEEK_ANTHROPIC/GEMINI锛墊
| 06 | **娴佸紡浼犺緭涓?SSE 杞崲** | `modules/06-streaming.md` | SSE 瀛楄妭绾ч€忎紶銆乣RealTimeResponsesTransformer` Codex 鍏煎杞崲 |
| 07 | **绠＄悊绔笌鍓嶇** | `modules/07-admin.md` | 9 涓鐞嗙鎺у埗鍣ㄣ€丼a-Token 閴存潈銆丏ashboard 缁熻銆乂ue 3.5 鍓嶇 |
| 08 | **娴嬭瘯浣撶郴** | `modules/08-testing.md` | 12 涓祴璇曠被銆?2 涓敤渚嬨€佽繍琛屽懡浠?|
| 09 | **閮ㄧ讲涓庤繍缁?* | `modules/09-deployment.md` | Docker銆丯ginx銆佺幆澧冨彉閲忋€丄PI 娴嬭瘯鍛戒护銆佹湰鍦拌繍琛?|
| 10 | **浠ｇ爜鐩綍缁撴瀯** | `modules/10-code-structure.md` | 瀹屾暣鐨?Java 婧愮爜鐩綍鏍?|

---

## 蹇€熷姛鑳芥瑙?
### 鍏紑 API 绔偣

| 绔偣 | 鏂规硶 | 閴存潈 | 璇存槑 |
|---|---|---|---|
| `/health` | GET | 鉂?鍏紑 | 鍋ュ悍妫€鏌ワ紙鍚暟鎹簱鐘舵€侊級 |
| `/v1/models` | GET | 鉁?Bearer | OpenAI 鍏煎妯″瀷鍒楄〃 |
| `/v1/chat/completions` | POST | 鉁?Bearer | OpenAI Chat Completions锛堟祦寮?闈炴祦寮忥級 |
| `/v1/messages` | POST | 鉁?Bearer | Anthropic Messages锛堟祦寮?闈炴祦寮忥級 |
| `/v1/responses` | POST | 鉁?Bearer | OpenAI Responses API锛圫SE 娴佸紡锛?|
| `/v1/videos` | POST | 鉁?Bearer | OpenAI Videos API锛堥潪娴佸紡瑙嗛鐢熸垚锛?|
| `/v1/images/generations` | POST | 鉁?Bearer | OpenAI Images API锛堥潪娴佸紡鍥剧墖鐢熸垚锛?|

### 绠＄悊绔鐐?
| 绔偣 | 璇存槑 |
|---|---|
| `POST /api/admin/login` | Sa-Token 鐧诲綍 |
| `/api/admin/api-keys` | API Key CRUD銆侀搴﹁拷鍔犮€佹笭閬?妯″瀷鎺堟潈銆佹粦鍔ㄧ獥鍙ｉ檺鍒?|
| `/api/admin/channels` | 娓犻亾 CRUD銆佹ā鍨嬫姄鍙栥€丱Auth 鎺堟潈 |
| `/api/admin/channels/{id}/auth/*` | AUTH 娓犻亾鎺堟潈鏂囦欢涓婁紶銆佹巿鏉冮摼鎺ョ敓鎴愩€佸洖璋?URL 瀵煎叆銆佺姸鎬佹煡璇?|
| `/api/admin/models` | 妯″瀷鏄犲皠 CRUD |
| `/api/admin/request-logs` | 璇锋眰鏃ュ織鍒嗛〉 |
| `/api/admin/dashboard` | 缁熻浠〃鐩?|
| `/api/admin/gateway-info` | 绔偣鍏冧俊鎭?|
| `/api/admin/system-config` | 璺敱妯″紡閰嶇疆 |

### Provider 绫诲瀷

| 绫诲瀷 | 閴存潈 | 鍗忚 | 娴佸紡 | 璇存槑 |
|---|---|---|---|---|
| `OPENAI_COMPATIBLE` | Bearer | Chat Completions + Videos + Images | 鉁?| 閫氱敤鍏煎 |
| `ANTHROPIC` | Bearer + version | Messages | 鉁?| Claude 瀹樻柟 |
| `OPENAI_RESPONSES` | Bearer | Responses API | 鉁?| 鍘熺敓 Responses |
| `GPT_AUTH` | Bearer (auth.json) | Chat Completions + Videos + Images | 鉁?| OAuth 鎺堟潈锛圴12锛墊
| `CLAUDE_AUTH` | Bearer (auth.json) | Messages | 鉁?| OAuth 鎺堟潈锛圴12锛墊
| `DEEPSEEK_CHAT` | Bearer | Chat + reasoning | 鉁?| DeepSeek Chat 椋庢牸 |
| `DEEPSEEK_ANTHROPIC` | Bearer + version | Messages + thinking | 鉁?| DeepSeek Claude 椋庢牸 |
| `GEMINI` | `x-goog-api-key` | `generateContent` | 鉂?| Google Gemini |

---

## 寰呭疄鐜板姛鑳斤紙鎸変紭鍏堢骇鎺掑簭锛?
| 浼樺厛绾?| 鍔熻兘 | 璇存槑 | 鐩稿叧妯″潡 |
|---|---|---|---|
| P2 | 鍑瘉鍔犲瘑 | `ai_channel.api_key` 鍔犲瘑瀛樺偍鎴栧閮ㄥ瘑閽ョ鐞?| 01-鍩虹璁炬柦銆?2-瀹夊叏 |
| P2 | 闆嗘垚娴嬭瘯 | SQLite 瀹夎銆佸仴搴锋鏌ャ€侀壌鏉冨け璐ャ€佹祦寮忚浆鍙戠瓑鍦烘櫙 | 08-娴嬭瘯 |
| P3 | 鍏朵粬 Provider | 鏈湴妯″瀷 client 瀹炵幇 | 05-Provider |

## 宸蹭慨澶?Bug

| 鏃ユ湡 | 闂 | 淇 | 鐩稿叧鏂囦欢 |
|---|---|---|---|
| 2026-05-27 | OpenAiChatCompletionRequest 灏?null 鐨?frequency_penalty/presence_penalty 搴忓垪鍖栧彂閫佽嚦涓婃父锛屽鑷村浘鐗囪瘑鍒笂娓歌繑鍥?400 閿欒 | 娣诲姞 @JsonInclude(JsonInclude.Include.NON_NULL) 娉ㄨВ锛岄伩鍏嶅簭鍒楀寲 null 瀛楁 | dto/OpenAiChatCompletionRequest.java |

---

## 杩戞湡鏇存柊

### 鏂囨。

- ?? `.agent/docs/API_REFERENCE.md` API ????????? 7 ??? API ?? + 9 ???? API + ?????? + ???Provider ????????????????????????????????????
- 新增 src/main/resources/static/docs/api-reference.html 静态 HTML 文档页面
- README 琛ュ厖绠€鍖栫増鍚姩璇存槑锛氬垱寤哄伐浣滅洰褰曞苟鍏嬮殕浠撳簱銆佹寜绯荤粺涓嬭浇骞惰В鍘?JDK 25銆侀€氳繃 `scripts/start.*` 鎸囧畾 JDK 璺緞鍜岀鐞嗗憳璐﹀彿瀵嗙爜鍚姩锛涘悓鏃朵繚鐣欐竻鍗?TUNA 鍥藉唴闀滃儚鐩綍璇存槑锛屽紑鍙戞枃妗ｈ烦杞埌 README銆?
### 鍓嶇

- 控制台侧边栏移除独立“API 文档”菜单；API 文档入口收敛到控制台“接口调用信息”卡片右上角按钮和端点表“文档”操作列；Vite 本地开发代理 `/docs` 到后端静态文档，前后端分离调试不会再落到 5173 404。
- Channel management exposes `GPT_AUTH`/`CLAUDE_AUTH`, hides API Key inputs for AUTH channels, provides `auth.json` upload + OAuth link generation.
- Dashboard pie charts with hoverable SVG segments (name, tokens, count, share).
- Vite manual chunks (Vue/Naive UI/Axios), explicit component registration.

### 鍚庣

- OpenAI 鍏煎瑙嗛鐢熸垚绔偣锛氭柊澧?`POST /v1/videos`锛岄€氳繃 `OpenAiVideosEndpointHandler` 鍜?`VideoGatewayService` 澶嶇敤 API Key 閴存潈銆佹ā鍨?娓犻亾鎺堟潈銆佽姹傛暟闄愬埗銆佽矾鐢遍伩璁╀笌璇锋眰鏃ュ織锛沗AiProviderClient.generateVideo()` 榛樿涓嶆敮鎸侊紝`OPENAI_COMPATIBLE` 涓?`GPT_AUTH` 閫忎紶鍒颁笂娓?`/v1/videos`銆?- **V15 澶氭ā鎬佺鐐硅矾寰?*锛氭笭閬撹〃鏂板 `video_path`銆乣image_path`锛屽墠绔笭閬撶鐞嗘敮鎸佷繚瀛樿棰戠敓鎴愬拰鍥剧墖鐢熸垚 API 璺緞锛涙柊澧?`POST /v1/images/generations` 鍥剧墖鐢熸垚绔偣锛宍AiProviderClient.generateImage()` 榛樿涓嶆敮鎸侊紝`OPENAI_COMPATIBLE` 涓?`GPT_AUTH` 鎸夋笭閬撲繚瀛樿矾寰勯€忎紶鍥剧墖鐢熸垚璇锋眰銆?- JSON 瑙ｆ瀽鍏煎锛氬叏灞€ `ObjectMapper` 鐨?Jackson 鍗曚釜瀛楃涓叉渶澶ч暱搴﹂粯璁ゆ彁鍗囧埌 `100000000`锛屽苟閫氳繃 `API_CONVERT_JACKSON_MAX_STRING_LENGTH` 鍙厤缃紱鍏紑绔偣鍜?`RestClient` JSON 杞崲鍣ㄧ粺涓€浣跨敤璇?mapper锛屾敮鎸?base64 鍥剧墖/瑙嗛璇锋眰鍜屽搷搴旈€忎紶锛屽苟鍏煎涓婃父 OpenAI 鍏煎鍝嶅簲涓殑渚涘簲鍟嗘墿灞曞瓧娈典笌 MiMo `audio_tokens`/`video_tokens` 鐢ㄩ噺鏄庣粏銆?- **V12 auth-file provider**: `GPT_AUTH`/`CLAUDE_AUTH` with `auth.json` upload, `auth-dir` storage, desensitized API responses.
- HTTP 浣撴棩蹇楀唴瀛樹繚鎶わ細鍑虹珯 RestClient 鏃ュ織涓嶅啀璇诲彇骞剁紦瀛樺畬鏁翠笂娓稿搷搴斾綋锛涜秴澶ц姹?鍝嶅簲姝ｆ枃鍦ㄨ劚鏁忛樁娈电洿鎺ヨ緭鍑烘憳瑕侊紝娴佸紡涓婃父璇锋眰鏃ュ織閬囧埌澶ф枃鏈?base64 鍐呭鏃朵笉鍐嶉澶栧簭鍒楀寲瀹屾暣 JSON銆?- Auto-fill official upstream addresses for AUTH channels on save.
- OAuth start/callback endpoints with built-in Codex/OpenAI and Claude metadata.
- `ChatGatewayService` maps `GPT_AUTH` 鈫?OpenAI adapters, `CLAUDE_AUTH` 鈫?Anthropic adapters.
- DeepSeek split into `DEEPSEEK_CHAT` and `DEEPSEEK_ANTHROPIC` independent providers.
- `/v1/responses` 鈫?`DEEPSEEK_CHAT` adapter restores `reasoning` items to `reasoning_content`.
- `DEEPSEEK_CHAT` ensures historical assistant messages include `reasoning_content` fallback.
- `ChatToolSequenceNormalizer` repairs strict Chat tool-call sequences for DeepSeek Chat by moving matching tool results next to assistant tool calls and trimming unanswered calls.
- Fixed MyBatis 3.5.19 `BoundSql` pagination by copying immutable parameter mappings.
- **V13 gateway key limits**: API Key limits moved to extensible rows, supporting simultaneous quota limits by hour/day and request-count limits by minute/hour/day; request-count limits are recorded after routing so failed upstream requests are counted, and each limit type allows only one row per window unit.
- API Key model allowlist added alongside channel allowlist; routing applies both scopes, including direct `channel/model` requests.
- Channel model selection now deduplicates custom typed and fetched upstream model IDs; backend rejects repeated provider models in the same channel before insert.
- Deleting a channel now removes matching `gateway_api_key_channel` allowlist rows and disables keys that lose their last explicit channel scope, preventing an empty allowlist from expanding back to all channels.
- **V14 API key failover switch**: gateway keys can enable multi-channel failover; when a route attempt fails before any response bytes are written, the gateway retries the unchanged request against remaining authorized routes for the same model and only returns failure after all candidates fail. It covers upstream provider errors such as no balance, rate limiting, auth failures, bad upstream parameters/responses, unsupported route capabilities, and unexpected route-attempt exceptions; gateway-local auth/quota/model failures are returned directly. For streaming requests, failover only happens before the SSE response has written to the client.
- 澶辫触閲嶈瘯鍒囨崲娓犻亾鏃讹紝鍚屾鍜屾祦寮忚矾寰勫潎鍐欏叆澶辫触璇锋眰鏃ュ織锛汥ashboard 鏌ヨ澧炲姞 `success=true` 杩囨护锛屽け璐ヤ笉璁″叆璇锋眰鏁般€?
