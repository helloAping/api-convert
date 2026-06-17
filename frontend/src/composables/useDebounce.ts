import { onUnmounted, ref, watch, type Ref } from 'vue'

/**
 * 通用防抖 ref：源 ref 变化后 {@code delayMs} 内若再次变化则重置定时器，
 * 稳定后才把去抖后的值写入结果 ref。适用于搜索输入、尺寸调整等高频触发场景。
 * <p>
 * 用法：
 * <pre>{@code
 * const keyword = ref('')
 * const debouncedKeyword = useDebouncedRef(keyword, 300)
 * watch(debouncedKeyword, () => reload())
 * }</pre>
 */
export function useDebouncedRef<T>(source: Ref<T>, delayMs = 300): Ref<T> {
  const result = ref(source.value) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | null = null
  const stop = watch(source, (next) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      result.value = next as T
      timer = null
    }, delayMs)
  })
  onUnmounted(() => {
    if (timer) clearTimeout(timer)
    stop()
  })
  return result
}

/**
 * 通用防抖函数：返回一个新函数，连续调用时只在最后一次调用后 {@code delayMs} 才执行。
 */
export function useDebouncedFn<T extends (...args: never[]) => void>(fn: T, delayMs = 300): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  onUnmounted(() => {
    if (timer) clearTimeout(timer)
  })
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn(...args)
      timer = null
    }, delayMs)
  }
}
