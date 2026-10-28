import { Client, type StompSubscription } from '@stomp/stompjs';
import { currentSession } from '../features/auth/session';

type MessageHandler = (message: unknown) => void;
type ConnectionListener = (connected: boolean) => void;

/**
 * Conexão STOMP única da sessão, com reconexão automática.
 *
 * Quem usa declara a inscrição uma vez; ela sobrevive à queda da conexão e é refeita a cada
 * reconexão, porque o broker esquece as inscrições quando o WebSocket cai. Os ouvintes de
 * conexão são avisados a cada conexão — inclusive a reconexão, que é quando a tela precisa
 * recarregar o que pode ter perdido.
 */
class StompClientManager {
  private client: Client | null = null;
  private readonly handlers = new Map<string, { destination: string; handler: MessageHandler }>();
  private readonly active = new Map<string, StompSubscription>();
  private readonly listeners = new Set<ConnectionListener>();
  private nextId = 0;

  /** Inscreve no destino e devolve a função que desfaz a inscrição. */
  subscribe(destination: string, handler: MessageHandler): () => void {
    const id = String(this.nextId++);
    this.handlers.set(id, { destination, handler });
    this.ensureClient();
    if (this.client?.connected) {
      this.attach(id);
    }
    return () => this.release(id);
  }

  /** Avisa a cada mudança de conexão e devolve a função que para de avisar. */
  onConnectionChange(listener: ConnectionListener): () => void {
    this.listeners.add(listener);
    listener(this.isConnected());
    return () => {
      this.listeners.delete(listener);
    };
  }

  /** Força uma tentativa imediata, sem esperar o intervalo de reconexão. */
  async reconnect(): Promise<void> {
    if (!this.client) {
      return;
    }
    await this.client.deactivate();
    this.client.activate();
  }

  isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  private ensureClient(): void {
    if (this.client) {
      return;
    }
    const client = new Client({
      brokerURL: this.getWebSocketURL(),
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      // Lê o token a cada tentativa: numa reconexão horas depois, o da primeira já expirou.
      // O backend autentica o CONNECT só por este cabeçalho.
      beforeConnect: () => {
        const session = currentSession();
        client.connectHeaders = session?.accessToken
          ? { Authorization: `Bearer ${session.accessToken}` }
          : {};
      },
      debug: (line) => {
        if (import.meta.env.DEV) {
          console.log('[STOMP]', line);
        }
      },
      onConnect: () => {
        this.active.clear();
        this.handlers.forEach((_, id) => this.attach(id));
        this.notify(true);
      },
      onStompError: (frame) => {
        console.error('[STOMP] Error', frame.headers, frame.body);
      },
      onWebSocketClose: () => {
        this.active.clear();
        this.notify(false);
      },
    });
    this.client = client;
    client.activate();
  }

  private attach(id: string): void {
    const entry = this.handlers.get(id);
    if (!entry || !this.client) {
      return;
    }
    const subscription = this.client.subscribe(entry.destination, (message) => {
      try {
        entry.handler(JSON.parse(message.body));
      } catch (error) {
        console.error('[STOMP] Failed to parse message', error);
      }
    });
    this.active.set(id, subscription);
  }

  private release(id: string): void {
    this.handlers.delete(id);
    const subscription = this.active.get(id);
    this.active.delete(id);
    if (subscription && this.client?.connected) {
      subscription.unsubscribe();
    }
    if (this.handlers.size === 0 && this.client) {
      const client = this.client;
      this.client = null;
      void client.deactivate();
      this.notify(false);
    }
  }

  private notify(connected: boolean): void {
    this.listeners.forEach((listener) => listener(connected));
  }

  private getWebSocketURL(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/kitchen`;
  }
}

export const stompClient = new StompClientManager();
