import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { WsMessage } from '../types'

export const useStomp = (auctionId: number, onMessage: (msg: WsMessage) => void) => {
  const clientRef = useRef<Client | null>(null)

  useEffect(() => {
    const stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        stompClient.subscribe(`/topic/auction/${auctionId}`, (frame) => {
          try {
            onMessage(JSON.parse(frame.body) as WsMessage)
          } catch {
            // ignore malformed messages
          }
        })
      },
    })

    stompClient.activate()
    clientRef.current = stompClient

    return () => {
      stompClient.deactivate()
    }
  }, [auctionId]) // eslint-disable-line react-hooks/exhaustive-deps

  return clientRef
}