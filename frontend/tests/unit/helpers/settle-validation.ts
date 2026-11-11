import { flushPromises } from '@vue/test-utils'

/** vee-validate debounces form validation by 5 ms (useForm), so flushPromises alone does not wait for it. */
const VALIDATION_DEBOUNCE_MS = 5

/** Waits for a vee-validate form validation and the promises it starts (save, publish, emits). */
export async function settleValidation(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, VALIDATION_DEBOUNCE_MS * 2))
  await flushPromises()
}
