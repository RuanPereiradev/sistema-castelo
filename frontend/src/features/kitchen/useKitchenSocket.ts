import { useEffect, useRef, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { stompClient } from '../../lib/stomp-client';

export type KitchenMessage =
  | { type: 'ORDERED'; ticketId: string; itemId: string; itemName: string; quantity: number; timestamp: string }
  | { type: 'STATUS_CHANGED'; ticketId: string; itemId: string; status: string; timestamp: string }
  | { type: 'CANCELLED'; ticketId: string; itemId: string; timestamp: string }
  | { type: 'TRANSFERRED'; ticketId: string; itemId: string; fromTabId: string; toTabId: string; timestamp: string }
  | { type: 'READY_ITEM'; tabId: string; itemId: string; timestamp: string };

interface UseKitchenSocketOptions {
  station: 'KITCHEN' | 'PIZZA' | 'BAR';
  enabled?: boolean;
}

/**
 * Hook para conectar à fila da cozinha via WebSocket STOMP.
 * Escuta mensagens em tempo real e atualiza React Query.
 * Reconecta automaticamente se desconectar.
 */
export function useKitchenSocket({ station, enabled = true }: UseKitchenSocketOptions) {
  const queryClient = useQueryClient();
  const connectionRef = useRef(false);
  const subscriptionsRef = useRef<string[]>([]);

  const handleMessage = useCallback(
    (message: KitchenMessage) => {
      // Invalidar a query para recarregar a fila
      // As mensagens podem chegar fora de ordem; a verdade é o servidor
      queryClient.invalidateQueries({ queryKey: ['kitchen-tickets'] });

      // Também pode atualizar cache otimista aqui se necessário
      // mas o padrão do projeto é confiar no servidor
    },
    [queryClient],
  );

  const connect = useCallback(async () => {
    if (!enabled || connectionRef.current) {
      return;
    }

    try {
      // Conectar ao servidor STOMP
      await stompClient.connect();
      connectionRef.current = true;

      // Inscrever em tópicos específicos do setor
      const stationSubscription = stompClient.subscribe(
        `/topic/kitchen/${station}`,
        handleMessage,
      );
      subscriptionsRef.current.push(stationSubscription);

      // Inscrever em tópicos globais (avisos de itens prontos)
      const readySubscription = stompClient.subscribe(
        `/topic/restaurant/ready-items`,
        handleMessage,
      );
      subscriptionsRef.current.push(readySubscription);

      console.log(`[Kitchen] Connected to ${station} station`);
    } catch (error) {
      console.error('[Kitchen] Connection failed:', error);
      connectionRef.current = false;

      // Retry em 5 segundos
      setTimeout(connect, 5000);
    }
  }, [enabled, station, handleMessage]);

  const disconnect = useCallback(() => {
    subscriptionsRef.current.forEach((subId) => {
      stompClient.unsubscribe(subId);
    });
    subscriptionsRef.current = [];
    connectionRef.current = false;
    stompClient.disconnect();
  }, []);

  // Conectar ao montar; desconectar ao desmontar
  useEffect(() => {
    if (enabled) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [enabled, connect, disconnect]);

  return {
    isConnected: connectionRef.current,
    disconnect,
    reconnect: connect,
  };
}
