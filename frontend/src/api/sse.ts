import { fetchEventSource } from '@microsoft/fetch-event-source'

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
