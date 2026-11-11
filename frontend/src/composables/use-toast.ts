import { reactive } from 'vue'

export type ToastKind = 'error' | 'info'
export interface ToastItem {
  id: number
  message: string
  kind: ToastKind
}

const DISPLAY_MS = 4000
const toasts = reactive<ToastItem[]>([])
let seq = 0

function show(message: string, kind: ToastKind = 'error'): void {
  const id = ++seq
  toasts.push({ id, message, kind })
  setTimeout(() => dismiss(id), DISPLAY_MS)
}

function dismiss(id: number): void {
  const i = toasts.findIndex((t) => t.id === id)
  if (i >= 0) toasts.splice(i, 1)
}

export function useToast() {
  return { show }
}

export function useToastItems() {
  return { toasts, dismiss }
}
