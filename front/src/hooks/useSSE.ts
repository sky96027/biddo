import { useEffect } from 'react'

const parseData = (raw: string) => {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

export const useSSE = (url: string, onMessage: (data: unknown) => void, enabled = true) => {
  useEffect(() => {
    if (!enabled || !url) return

    const es = new EventSource(url)
    const handler = (e: MessageEvent) => onMessage(parseData(e.data as string))

    es.onmessage = handler
    es.addEventListener('notification', handler)

    return () => {
      es.removeEventListener('notification', handler)
      es.close()
    }
  }, [url, enabled]) // eslint-disable-line react-hooks/exhaustive-deps
}