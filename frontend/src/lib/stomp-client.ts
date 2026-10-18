import { Client, type StompSubscription } from '@stomp/stompjs';
import { currentSession } from '../features/auth/session';

/**
 * Gerenciador singleton de conexão STOMP com WebSocket.
 * Mantém uma única conexão por sessão.
 */
class StompClientManager {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private reconnectDelay = 3000; // 3 segundos

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.client?.active) {
        resolve();
        return;
      }

      const session = currentSession();
      if (!session?.accessToken) {
        reject(new Error('No access token available'));
        return;
      }

      this.client = new Client({
        brokerURL: this.getWebSocketURL(),
        // The backend authenticates the CONNECT frame by this native header only.
        connectHeaders: {
          Authorization: `Bearer ${session.accessToken}`,
        },
        debug: (str) => {
          if (import.meta.env.DEV) {
            console.log('[STOMP]', str);
          }
        },
        reconnectDelay: this.reconnectDelay,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: () => {
          resolve();
        },
        onStompError: (frame) => {
          console.error('[STOMP] Error', frame.headers, frame.body);
          reject(new Error(`STOMP error: ${frame.body}`));
        },
        onWebSocketClose: () => {
          console.log('[STOMP] WebSocket closed');
          this.client = null;
          this.subscriptions.clear();
        },
      });

      this.client.activate();
    });
  }

  subscribe(destination: string, callback: (message: any) => void): string {
    if (!this.client?.active) {
      throw new Error('STOMP client not connected');
    }

    const subscriptionId = `${destination}-${Date.now()}`;
    const subscription = this.client.subscribe(destination, (message) => {
      try {
        const body = JSON.parse(message.body);
        callback(body);
      } catch (error) {
        console.error('[STOMP] Failed to parse message', error);
        callback(message.body);
      }
    });

    this.subscriptions.set(subscriptionId, subscription);
    return subscriptionId;
  }

  unsubscribe(subscriptionId: string): void {
    const subscription = this.subscriptions.get(subscriptionId);
    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(subscriptionId);
    }
  }

  disconnect(): void {
    if (this.client?.active) {
      this.client.deactivate();
    }
    this.subscriptions.clear();
    this.client = null;
  }

  isConnected(): boolean {
    return this.client?.active ?? false;
  }

  private getWebSocketURL(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    return `${protocol}//${host}/ws/kitchen`;
  }
}

export const stompClient = new StompClientManager();
