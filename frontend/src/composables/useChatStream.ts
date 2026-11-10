import { onScopeDispose, ref } from 'vue'
import { openEventStream } from '@/api/sse'
import { useStreamStore } from '@/stores/stream'

/** 管理一次对话流的消息累积、错误状态与作用域销毁时的主动取消。 */
export function useChatStream(streamId: string) {
  const stream = useStreamStore()
  const messages = ref<string[]>([])
  let controller: AbortController | null = null

  async function open(path: string) {
    controller?.abort()
    controller = new AbortController()
    stream.start(streamId)
    messages.value = []

    try {
      await openEventStream(path, {
        signal: controller.signal,
        onMessage(data) {
          messages.value = [...messages.value, data]
        },
      })
    } finally {
      if (stream.activeStreamId === streamId) {
        stream.stop()
      }
    }
  }

  function cancel() {
    controller?.abort()
    stream.stop()
  }

  onScopeDispose(cancel)

  return { messages, open, cancel }
}
