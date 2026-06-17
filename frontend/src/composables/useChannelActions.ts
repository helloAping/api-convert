import { ref, type Ref } from 'vue'
import { useMessage } from 'naive-ui'
import { createChannel, deleteChannel, fetchChannelModels, fetchChannelQuota, getChannels, updateChannel } from '@/api/channels'
import type { ChannelForm, ChannelQuotaVO, ChannelVO, UpstreamModelVO } from '@/types'
import { useAsyncAction } from './useAsyncAction'

/**
 * 渠道 CRUD + 额度刷新 + 上游模型拉取的状态机集合。
 * <p>
 * 视图层只关心：
 * <pre>{@code
 * const { channels, loading, load, remove } = useChannelActions()
 * await load()
 * }</pre>
 * 所有异步操作自动处理 loading / error / success toast。
 */
export function useChannelActions() {
  const message = useMessage()
  const run = useAsyncAction()

  const channels: Ref<ChannelVO[]> = ref([])
  const loading = ref(false)
  const quotaMap = ref<Record<number, ChannelQuotaVO>>({})
  const quotaLoading = ref<Record<number, boolean>>({})

  async function load() {
    loading.value = true
    try {
      const res = await run(() => getChannels(), { errorFallback: '加载渠道失败' })
      if (res) {
        channels.value = res.data.data
      }
    } finally {
      loading.value = false
    }
  }

  async function refreshQuota(row: ChannelVO) {
    quotaLoading.value = { ...quotaLoading.value, [row.id]: true }
    try {
      const res = await run(() => fetchChannelQuota(row.id))
      if (!res) return
      quotaMap.value = { ...quotaMap.value, [row.id]: res.data.data }
      if (res.data.data.supported) {
        message.success('额度已刷新')
      } else {
        message.warning(res.data.data.summary || '当前供应商不支持额度获取')
      }
    } finally {
      quotaLoading.value = { ...quotaLoading.value, [row.id]: false }
    }
  }

  function save(editingId: number | null, form: ChannelForm): Promise<boolean> {
    return run(
      () => editingId ? updateChannel(editingId, form) : createChannel(form),
      { success: '保存成功', errorFallback: '保存失败' }
    ).then(result => result !== undefined)
  }

  function remove(id: number): Promise<boolean> {
    return run(() => deleteChannel(id), { success: '删除成功', errorFallback: '删除失败' })
      .then(result => result !== undefined)
  }

  function fetchUpstreamModels(params: {
    type: string
    channelId: number | null
    baseUrl: string
    modelsPath: string
    apiKey: string
  }): Promise<UpstreamModelVO[] | undefined> {
    return run(() => fetchChannelModels(params), {
      success: '模型列表已更新',
      errorFallback: '获取上游模型失败',
    }).then(res => res?.data.data)
  }

  return {
    channels, loading, quotaMap, quotaLoading,
    load, refreshQuota, save, remove, fetchUpstreamModels,
  }
}
