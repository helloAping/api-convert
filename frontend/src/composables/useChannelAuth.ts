import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { startChannelAuth, submitChannelAuthCallbackUrl, uploadChannelAuth } from '@/api/channels'
import { errorMessage, useAsyncAction } from './useAsyncAction'

/**
 * 渠道授权流程：auth.json 文件上传 + OAuth 授权链接生成 / 回调提交。
 * <p>
 * 状态机：{@code uploadState === 'idle' / 'uploading' / 'done'} 用于 UI 反馈；
 * {@code oauthState === 'idle' / 'pending' / 'submitted'} 跟踪 OAuth 流程进度。
 * <p>
 * 每次操作都需要先保存渠道（后端要求 channelId 存在），因此函数内部对 {@code channelId}
 * 为空时给出 warning 而不发起请求。
 */
export function useChannelAuth(getChannelId: () => number | null) {
  const message = useMessage()
  const run = useAsyncAction()

  const authUploading = ref(false)
  const oauthAuthorizationUrl = ref('')
  const oauthCallbackUrl = ref('')

  async function triggerAuthUpload(inputRef: HTMLInputElement | null) {
    const channelId = getChannelId()
    if (!channelId) {
      message.warning('请先保存渠道后再上传 auth.json')
      return
    }
    inputRef?.click()
  }

  async function handleAuthFileChange(event: Event, onReload: () => Promise<void> | void) {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    const channelId = getChannelId()
    if (!channelId) return
    authUploading.value = true
    try {
      await run(() => uploadChannelAuth(channelId, file), {
        success: '授权文件已上传',
        errorFallback: '上传授权文件失败',
      })
      await onReload()
    } finally {
      authUploading.value = false
    }
  }

  async function startOauthLogin() {
    const channelId = getChannelId()
    if (!channelId) {
      message.warning('请先保存渠道后再触发 OAuth 登录')
      return
    }
    const result = await run(() => startChannelAuth(channelId), {
      success: '授权链接已生成',
      errorFallback: '触发 OAuth 登录失败',
    })
    if (result) {
      oauthAuthorizationUrl.value = result.data.data.authorizationUrl
      oauthCallbackUrl.value = ''
    }
  }

  function openOauthLink() {
    if (!oauthAuthorizationUrl.value) return
    window.open(oauthAuthorizationUrl.value, '_blank', 'noopener,noreferrer')
  }

  async function copyOauthLink() {
    if (!oauthAuthorizationUrl.value) return
    try {
      await navigator.clipboard.writeText(oauthAuthorizationUrl.value)
      message.success('授权链接已复制')
    } catch {
      message.warning('复制失败，请手动复制授权链接')
    }
  }

  async function submitOauthCallbackUrl(onReload: () => Promise<void> | void) {
    const channelId = getChannelId()
    if (!channelId) {
      message.warning('请先保存渠道后再提交回调 URL')
      return
    }
    const callback = oauthCallbackUrl.value.trim()
    if (!callback) {
      message.warning('请粘贴浏览器跳转后的完整回调 URL')
      return
    }
    const ok = await run(() => submitChannelAuthCallbackUrl(channelId, callback), {
      success: 'OAuth 授权已保存',
      errorFallback: '提交 OAuth 回调失败',
    })
    if (ok) {
      oauthAuthorizationUrl.value = ''
      oauthCallbackUrl.value = ''
      await onReload()
    }
  }

  function reset() {
    oauthAuthorizationUrl.value = ''
    oauthCallbackUrl.value = ''
  }

  return {
    authUploading,
    oauthAuthorizationUrl,
    oauthCallbackUrl,
    triggerAuthUpload,
    handleAuthFileChange,
    startOauthLogin,
    openOauthLink,
    copyOauthLink,
    submitOauthCallbackUrl,
    reset,
  }
}

// 重新导出 errorMessage 给视图层使用，避免视图层直接依赖 useAsyncAction
export { errorMessage }
