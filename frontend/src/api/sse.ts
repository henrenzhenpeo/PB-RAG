import { fetchEventSource } from '@microsoft/fetch-event-source'

/** 打开服务端事件流；调用方持有 AbortSignal，以便页面切换或用户取消时关闭连接。 */
export async function openEventStream(
  path: string,
  options: {
    signal: AbortSignal
    method?: 'GET' | 'POST'
    body?: string
    headers?: Record<string, string>
    onMessage: (data: string, event: string) => void
  },
) {
  const url = path.startsWith('/api/') ? path : `/api${path.startsWith('/') ? path : `/${path}`}`

  await fetchEventSource(url, {
    signal: options.signal,
    method: options.method,
    body: options.body,
    headers: options.headers,
    credentials: 'same-origin',
    async onopen(response) {
      if (!response.ok) throw new Error(`流式请求失败（HTTP ${response.status}）`)
    },
    onmessage(event) {
      options.onMessage(event.data, event.event)
    },
    // 单次问答是有限事件流；传输异常应交给页面展示和显式重试，不能自动重复 POST。
    onerror(error) {
      throw error
    },
  })
}
