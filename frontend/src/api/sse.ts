import { fetchEventSource } from '@microsoft/fetch-event-source'

/** 打开服务端事件流；调用方持有 AbortSignal，以便页面切换或用户取消时关闭连接。 */
export async function openEventStream(
  path: string,
  options: {
    signal: AbortSignal
    onMessage: (data: string) => void
  },
) {
  const url = path.startsWith('/api/') ? path : `/api${path.startsWith('/') ? path : `/${path}`}`

  await fetchEventSource(url, {
    signal: options.signal,
    onmessage(event) {
      options.onMessage(event.data)
    },
  })
}
