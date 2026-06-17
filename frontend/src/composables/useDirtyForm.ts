import { onBeforeUnmount, ref, watch, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

/**
 * 表单 dirty 检测：源 ref 变化时设置 dirty=true；保存/重置后调用 markClean 清零。
 * <p>
 * 用法：
 * <pre>{@code
 * const form = ref<RoutingConfigForm>({ ... })
 * const { isDirty, markClean } = useDirtyForm(form)
 * watch(isDirty, (d) => { saveButton.disabled = d ? false : !hasChanges })
 * }</pre>
 * <p>
 * 离开未保存页面时通过 {@code beforeunload} 弹浏览器原生确认（仅 dirty 状态）；
 * 路由级跳转通过 {@code onBeforeRouteLeave} 在路由切换时拦截。
 */
export function useDirtyForm<T>(source: Ref<T>, options: { isEqual?: (a: T, b: T) => boolean } = {}) {
  const isDirty = ref(false)
  let initialSnapshot = cloneValue(source.value)
  const isEqual = options.isEqual ?? defaultEqual

  watch(source, (next) => {
    isDirty.value = !isEqual(next, initialSnapshot)
  }, { deep: true })

  function markClean() {
    initialSnapshot = cloneValue(source.value)
    isDirty.value = false
  }

  function markDirty() {
    isDirty.value = true
  }

  function reset(nextValue: T) {
    source.value = nextValue
    markClean()
  }

  // 浏览器原生 beforeunload：仅在 dirty 时弹出确认
  function onBeforeUnload(event: BeforeUnloadEvent) {
    if (!isDirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }
  window.addEventListener('beforeunload', onBeforeUnload)
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload)
  })

  return { isDirty, markClean, markDirty, reset }
}

function cloneValue<T>(value: T): T {
  if (value === null || typeof value !== 'object') return value
  try {
    return JSON.parse(JSON.stringify(value)) as T
  } catch {
    return value
  }
}

function defaultEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true
  if (a === null || b === null) return a === b
  if (typeof a !== 'object' || typeof b !== 'object') return false
  try {
    return JSON.stringify(a) === JSON.stringify(b)
  } catch {
    return false
  }
}

/**
 * 在 setup 顶层调用：在用户尝试离开未保存页面时通过 vue-router 拦截。
 * <p>
 * Naive UI 的 {@code useDialog} 弹确认框，避免直接 alert；用户确认后放行。
 */
export function useDirtyGuard(isDirty: Ref<boolean>, confirm: () => Promise<boolean> = windowConfirm) {
  onBeforeRouteLeave(async () => {
    if (!isDirty.value) return true
    return await confirm()
  })
}

function windowConfirm(): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    if (typeof window !== 'undefined' && typeof window.confirm === 'function') {
      resolve(window.confirm('页面有未保存的修改，确定离开？'))
    } else {
      resolve(true)
    }
  })
}
