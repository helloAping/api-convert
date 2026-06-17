import { ref, type Ref } from 'vue'
import { useMessage } from 'naive-ui'

/**
 * 从 axios / 任意异常对象中抽取后端返回的中文消息，统一 fallback。
 */
export function errorMessage(error: unknown, fallback: string): string {
  const response = (error as { response?: { data?: { message?: string } } })?.response
  return response?.data?.message || fallback
}

/**
 * 异步动作选项：可选择是否自动 success / error toast，error 永远自动捕获并展示 fallback。
 */
export interface AsyncActionOptions<T> {
  success?: string
  errorFallback?: string
  onSuccess?: (result: T) => void
  onError?: (error: unknown) => void
}

/**
 * 通用异步动作封装：把 try / catch / loading / message 收敛到一个工具方法，
 * 让视图层只关注业务调用，避免 41 处重复的 try { ... } catch (e) { message.error(...) }。
 * <p>
 * 用法：
 * <pre>{@code
 * const run = useAsyncAction()
 * const saving = run(async () => await api.save(form), { success: '保存成功', errorFallback: '保存失败' })
 * }</pre>
 */
export function useAsyncAction() {
  const message = useMessage()
  return async <T>(fn: () => Promise<T>, options: AsyncActionOptions<T> = {}): Promise<T | undefined> => {
    try {
      const result = await fn()
      if (options.success) {
        message.success(options.success)
      }
      options.onSuccess?.(result)
      return result
    } catch (error) {
      if (options.errorFallback) {
        message.error(errorMessage(error, options.errorFallback))
      }
      options.onError?.(error)
      return undefined
    }
  }
}

/**
 * 显式管理 loading 状态的 ref 包装，适合多次串行调用复用同一个 loading（如表格 load + 单行 refresh）。
 */
export function useLoading(initial = false): { loading: Ref<boolean>; withLoading: <T>(fn: () => Promise<T>) => Promise<T | undefined> } {
  const loading = ref(initial)
  const run = useAsyncAction()
  const withLoading = async <T>(fn: () => Promise<T>): Promise<T | undefined> => {
    loading.value = true
    try {
      return await run(fn)
    } finally {
      loading.value = false
    }
  }
  return { loading, withLoading }
}
