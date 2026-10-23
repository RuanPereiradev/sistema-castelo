import { useEffect, useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { stompClient } from '../../lib/stomp-client';

interface UseKitchenSocketOptions {
  station: 'KITCHEN' | 'PIZZA' | 'BAR';
  enabled?: boolean;
}

/**
 * Liga a fila do setor ao tópico STOMP do setor.
 *
 * Toda mensagem e toda conexão — a primeira e cada reconexão — recarregam a fila inteira
 * pelo GET. Durante a queda podem ter se perdido mensagens, e as que chegam podem vir fora
 * de ordem: a fila do servidor é a verdade, não o acúmulo de mensagens.
 */
export function useKitchenSocket({ station, enabled = true }: UseKitchenSocketOptions) {
  const queryClient = useQueryClient();
  const [isConnected, setIsConnected] = useState(stompClient.isConnected());

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const reloadQueue = () => {
      void queryClient.invalidateQueries({ queryKey: ['kitchen-tickets', station] });
    };

    // Só o tópico do setor: toda mudança de item do setor chega por ele (K8). O de itens prontos
    // é do garçom (K10); a inscrição da cozinha nele é recusada com ACCESS_DENIED, e a recusa
    // derruba a conexão inteira.
    const unsubscribeStation = stompClient.subscribe(`/topic/kitchen/${station}`, reloadQueue);
    const stopListening = stompClient.onConnectionChange((connected) => {
      setIsConnected(connected);
      if (connected) {
        reloadQueue();
      }
    });

    return () => {
      stopListening();
      unsubscribeStation();
    };
  }, [enabled, station, queryClient]);

  const reconnect = useCallback(() => {
    void stompClient.reconnect();
  }, []);

  return { isConnected, reconnect };
}
