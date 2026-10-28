# 👔 Requisitos Funcionais - Visão do Garçom

**Data:** 2026-10-09  
**Versão:** 1.0  
**Ator:** Garçom (WAITER)  
**Sistema:** Hospedaria - Frontend Salão + Backend

---

## 📋 Visão Geral

O garçom precisa gerenciar comandas (abas) do início ao fim, desde a abertura
até o fechamento. Cada comanda representa as consumições de uma mesa durante
um atendimento.

---

## 🎯 Requisitos Funcionais

### RF1 - Visualizar Mapa de Mesas
**Prioridade:** CRÍTICA  
**Status:** ✅ IMPLEMENTADO

**Descrição:**
O garçom acessa a tela de salão e vê todas as mesas em um grid visual.

**Critérios de Aceitação:**
- [x] Listar todas as mesas com nome/número
- [x] Mostrar quantidade de lugares em cada mesa
- [x] Exibir área (Salão / Varanda)
- [x] Filtrar por área (Salão / Varanda)
- [x] Mostrar status visual:
  - 🟢 **Livre** - mesa desocupada (borda tracejada)
  - 🔴 **Ocupada** - comanda aberta (borda sólida salmão)
  - 🟠 **Prato Pronto** - item pronto na cozinha (fundo salmão)
  - 📋 **Conta Pedida** - pré-conta gerada (fundo pergaminho)
  - 🔵 **Reservada** - mesa reservada (borda dupla)
- [x] Scroll horizontal/vertical em celular
- [x] Design Portal (medieval, vinho/salmão/pergaminho)

**Endpoints:**
- `GET /api/restaurant/dining-tables`

**Design:**
- Grid responsivo (3 colunas em desktop, 2 em tablet, 1 em celular)
- Animação ao mudar status
- Feedback visual de seleção

---

### RF2 - Abrir Comanda em uma Mesa
**Prioridade:** CRÍTICA  
**Status:** ✅ IMPLEMENTADO

**Descrição:**
Garçom clica em uma mesa livre e abre uma nova comanda com o número de pessoas.

**Critérios de Aceitação:**
- [x] Ao clicar em mesa livre → Modal com opções
- [x] Input de número de pessoas (1-20)
- [x] Botão "Abrir mesa"
- [x] Feedback visual (loading)
- [x] Mensagem de sucesso
- [x] Mesa muda para 🔴 Ocupada
- [x] Se mesa já ocupada → erro "mesa já está ocupada"
- [x] Comanda recebe ID único (público_token)

**Endpoints:**
- `POST /api/restaurant/tabs`

**Dados Enviados:**
```json
{
  "diningTableId": "uuid",
  "origin": "TABLE_SERVICE",
  "guestCount": 4
}
```

---

### RF3 - Lançar Item (Adicionar Produto)
**Prioridade:** CRÍTICA  
**Status:** 🟡 PARCIAL (Modal criado, integração pendente)

**Descrição:**
Garçom clica em mesa ocupada e lança itens do cardápio (bebidas, comidas, etc).

**Critérios de Aceitação:**
- [ ] Ao clicar em mesa ocupada → Modal "Lançar item"
- [ ] Mostrar cardápio em modal scrollável
- [ ] Itens do cardápio com preço unitário
- [ ] Organizar por categoria (opcional na V1)
- [ ] Selecionar quantidade (1-20)
- [ ] Campo de observações (sem cebola, mal passado, etc)
- [ ] Botão "Adicionar"
- [ ] Adicionar múltiplos itens sem fechar modal
- [ ] Atualizar total em tempo real
- [ ] Avisar cozinha (emit evento para KDS)

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/items`

**Dados Enviados:**
```json
{
  "menuItemId": "uuid",
  "quantity": 2,
  "specialInstructions": "Sem cebola"
}
```

**Response:**
```json
{
  "id": "uuid",
  "itemName": "Cerveja Skol",
  "quantity": 2,
  "unitPrice": "8.00",
  "status": "PENDING",
  "specialInstructions": "Sem cebola"
}
```

---

### RF4 - Ver Lista de Itens da Comanda
**Prioridade:** ALTA  
**Status:** ✅ IMPLEMENTADO (parcial)

**Descrição:**
Garçom abre a comanda e vê todos os itens com status.

**Critérios de Aceitação:**
- [x] Mostrar número da mesa
- [x] Mostrar número de pessoas
- [x] Listar itens com:
  - Quantidade (2×)
  - Nome do produto
  - Preço unitário
  - **Status** (PENDING/IN_PREPARATION/READY/DELIVERED/CANCELLED)
  - Tempo decorrido (opcional)
- [x] Subtotal (soma dos itens)
- [x] Taxa de serviço (10% por padrão)
- [x] **Total** (subtotal + taxa)
- [ ] Avisar se prato está pronto na cozinha
- [ ] Atualizar em tempo real (polling 5s)

**Endpoints:**
- `GET /api/restaurant/tabs/{tabId}`

---

### RF5 - Cancelar Item Individual
**Prioridade:** ALTA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom cancela um item específico da comanda (produto indisponível, cliente desistiu).

**Critérios de Aceitação:**
- [ ] Ao clicar em item → opção "Cancelar"
- [ ] Modal pedindo motivo (Indisponível, Cliente desistiu, Erro)
- [ ] Item muda para status CANCELLED
- [ ] Total recalculado automaticamente
- [ ] Cozinha notificada para não preparar

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/items/{itemId}/cancel`

**Dados Enviados:**
```json
{
  "reason": "UNAVAILABLE"
}
```

---

### RF6 - Marcar Item como Entregue
**Prioridade:** MÉDIA  
**Status:** ❌ NÃO IMPLEMENTADO (KDS faz isso)

**Descrição:**
Garçom marca item como entregue ao cliente (quando leva físicamente).

**Critérios de Aceitação:**
- [ ] Ao receber prato pronto da cozinha → marcar como entregue
- [ ] Item muda de 🔴 READY para 🟢 DELIVERED
- [ ] Visual diferente (itens entregues em cinza/opaco)
- [ ] Não impacta total (continua contando)

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/items/{itemId}/deliver`

---

### RF7 - Pedir Pré-Conta (Bill)
**Prioridade:** ALTA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom gera pré-conta para mostrar ao cliente ou preparar fechamento.

**Critérios de Aceitação:**
- [ ] Botão "Pedir conta" no modal
- [ ] Gera documento com:
  - Lista de itens entregues
  - Subtotal
  - Taxa de serviço
  - **Total a pagar**
  - Data/hora
  - Número da mesa
- [ ] Modal para print/compartilhamento
- [ ] Comanda muda para status "CONTA_PEDIDA" (🟡 cor diferente)
- [ ] Permite múltiplas pré-contas (não fecha ainda)

**Endpoints:**
- `GET /api/restaurant/tabs/{tabId}/bill`

---

### RF8 - Fechar Comanda (Encerramento)
**Prioridade:** CRÍTICA  
**Status:** 🟡 PARCIAL

**Descrição:**
Garçom fecha a comanda após pagamento ou transferência de saldo.

**Critérios de Aceitação:**
- [ ] Botão "Fechar comanda" após pedir conta
- [ ] Modal confirmando:
  - Total final
  - Forma de pagamento (Dinheiro / Pix / Cartão / Conta do Hotel)
- [ ] Se "Conta do Hotel":
  - Transferir para folio do hóspede
  - Pedir número do quarto
- [ ] Se "Pagamento direto":
  - Abrir modal de pagamento (recepção valida depois)
- [ ] Comanda muda para status "CLOSED"
- [ ] Mesa volta para 🟢 Livre
- [ ] Registro permanente (não deleta, arquivo apenas)

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/closing` (inicia processo)
- `POST /api/restaurant/tabs/{tabId}/close` (confirma fechamento)

---

### RF9 - Cancelar Comanda Inteira
**Prioridade:** ALTA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom cancela toda a comanda (mesa nunca consumiu nada, erro ao abrir, etc).

**Critérios de Aceitação:**
- [ ] Botão "Cancelar comanda" no modal (comanda ainda aberta)
- [ ] Modal pedindo motivo (Cliente desistiu, Erro, Mesa duplicada)
- [ ] Confirmação dupla (⚠️ não pode desfazer)
- [ ] Todos os itens marcados como CANCELLED
- [ ] Mesa volta para 🟢 Livre
- [ ] Comanda não gera cobrança

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/cancel`

---

### RF10 - Transferir Itens Entre Mesas
**Prioridade:** MÉDIA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom move itens de uma comanda para outra (cliente muda de mesa, split de conta).

**Critérios de Aceitação:**
- [ ] Botão "Transferir itens" no modal da comanda
- [ ] Modal mostra todos os itens com checkbox
- [ ] Selecionar itens a transferir
- [ ] Escolher mesa de destino
- [ ] Se mesa está vazia → pergunta se abre comanda lá
- [ ] Se mesa tem comanda → mescla itens
- [ ] Registra rastreamento (audit log)
- [ ] Atualiza totais em ambas as mesas

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/transfer`

**Dados Enviados:**
```json
{
  "targetTableId": "uuid",
  "itemIds": ["uuid1", "uuid2"]
}
```

---

### RF11 - Juntar Comandas (Merge)
**Prioridade:** MÉDIA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom junta dois grupos em uma só comanda (mesas próximas pediram juntas).

**Critérios de Aceitação:**
- [ ] Ao abrir comanda de mesa A → opção "Juntar com..."
- [ ] Listar outras mesas com comando abertas
- [ ] Clicar em mesa B → mescla toda a comanda A em B
- [ ] Mesa A volta para 🟢 Livre
- [ ] Comanda A é marcada como "mergedIntoTabId" (audit)
- [ ] Total de B inclui itens de A
- [ ] Histórico mostra que foram juntadas

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/merge`

**Dados Enviados:**
```json
{
  "mergeIntoTabId": "uuid"
}
```

---

### RF12 - Trocar de Mesa
**Prioridade:** BAIXA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom muda comanda aberta de uma mesa para outra (cliente pediu pra mudar).

**Critérios de Aceitação:**
- [ ] Botão "Trocar de mesa" no modal
- [ ] Selecionar nova mesa (deve estar livre)
- [ ] Comanda se move completa com histórico
- [ ] Antigo número de mesa muda na comanda
- [ ] Mesa antiga volta para 🟢 Livre
- [ ] Cozinha notificada do novo número

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/move`

**Dados Enviados:**
```json
{
  "targetTableId": "uuid"
}
```

---

### RF13 - Reabrir Comanda Fechada
**Prioridade:** BAIXA  
**Status:** ❌ NÃO IMPLEMENTADO

**Descrição:**
Garçom reabre uma comanda já fechada (cliente pediu mais algo).

**Critérios de Aceitação:**
- [ ] Histórico mostra comanda como "REABERTURA"
- [ ] Novos itens podem ser lançados
- [ ] Novo total com items adicionados
- [ ] Auditoria registra quem reabre e quando

**Endpoints:**
- `POST /api/restaurant/tabs/{tabId}/reopen`

---

### RF14 - Notificações da Cozinha (KDS)
**Prioridade:** ALTA  
**Status:** 🟡 PARCIAL (Backend pronto, frontend polling)

**Descrição:**
Garçom vê quando prato está pronto na cozinha.

**Critérios de Aceitação:**
- [ ] Item muda para status "READY" em tempo real
- [ ] Ícone/badge visual (🔴 → 🟠 Pronto)
- [ ] Som/notificação (opcional)
- [ ] Lista de "Itens Prontos" destacada
- [ ] Polling 5s ou WebSocket (atual: polling)

**Endpoints:**
- Monitorado via `GET /api/restaurant/tabs/{tabId}` (polling 5s)
- OU `GET /api/kitchen/queue` para ver fila geral

---

### RF15 - Filtrar Mesas por Status
**Prioridade:** MÉDIA  
**Status:** ✅ IMPLEMENTADO

**Descrição:**
Garçom filtra mapa para ver apenas mesas em certo estado.

**Critérios de Aceitação:**
- [x] Chips de filtro (Todas / Livres / Ocupadas / Prontas / Conta / Reservadas)
- [x] Mostrar contagem em cada chip
- [x] Atualizar grid automaticamente
- [x] Manter filtro de área (não resetar)
- [x] Filtro "Só as minhas" (opcional, baixa prioridade)

---

### RF16 - Logout
**Prioridade:** ALTA  
**Status:** ✅ IMPLEMENTADO

**Descrição:**
Garçom faz logout (sair da sessão).

**Critérios de Aceitação:**
- [x] Botão "Sair" no header
- [x] Confirma logout
- [x] Invalida token JWT no backend
- [x] Redireciona para login
- [x] Limpa sessionStorage

**Endpoints:**
- `POST /api/auth/logout`

---

## 📊 Matriz de Prioridade vs Status

| RF | Título | Prioridade | Status | V1 | V2 |
|----|--------|-----------|--------|----|----|
| RF1 | Visualizar Mapa | CRÍTICA | ✅ | ✅ | ✅ |
| RF2 | Abrir Comanda | CRÍTICA | ✅ | ✅ | ✅ |
| RF3 | Lançar Item | CRÍTICA | 🟡 | ✅ | ✅ |
| RF4 | Ver Itens da Comanda | ALTA | ✅ | ✅ | ✅ |
| RF5 | Cancelar Item | ALTA | ❌ | ✅ | ✅ |
| RF6 | Marcar Entregue | MÉDIA | ❌ | ✅ | ✅ |
| RF7 | Pedir Pré-Conta | ALTA | ❌ | ✅ | ✅ |
| RF8 | Fechar Comanda | CRÍTICA | 🟡 | ✅ | ✅ |
| RF9 | Cancelar Comanda | ALTA | ❌ | ✅ | ✅ |
| RF10 | Transferir Itens | MÉDIA | ❌ | | ✅ |
| RF11 | Juntar Comandas | MÉDIA | ❌ | | ✅ |
| RF12 | Trocar de Mesa | BAIXA | ❌ | | ✅ |
| RF13 | Reabrir Comanda | BAIXA | ❌ | | ✅ |
| RF14 | KDS Notificações | ALTA | 🟡 | ✅ | ✅ |
| RF15 | Filtrar Mesas | MÉDIA | ✅ | ✅ | ✅ |
| RF16 | Logout | ALTA | ✅ | ✅ | ✅ |

---

## 🎯 Escopo V1 (MVP)

**Requisitos críticos para o garçom começar a usar:**

1. ✅ RF1 - Ver mapa de mesas
2. ✅ RF2 - Abrir comanda
3. ✅ RF3 - Lançar itens (em progresso)
4. ✅ RF4 - Ver itens da comanda
5. ✅ RF8 - Fechar comanda (em progresso)
6. ✅ RF14 - Notificações KDS
7. ✅ RF15 - Filtrar mesas
8. ✅ RF16 - Logout

---

## 🎨 Design (Portal Frame)

**Paleta:**
- Vinho Lacre: #3D0F1A (fundo)
- Vinho: #4A1522 (superfície)
- Salmão: #E8957F (destaques)
- Pergaminho: #F4ECE3 (texto)

**Tipografia:**
- Cormorant Garamond (títulos)
- Lora (corpo)

**Responsividade:**
- Desktop: Grid 3 colunas
- Tablet: Grid 2 colunas  
- Celular: 1 coluna + fullscreen modals

---

## 🔄 Fluxo Ideal (Happy Path)

```
1. Login (garcom/admin123)
   ↓
2. Ver mapa (GET /dining-tables)
   ↓
3. Clicar mesa → Modal
   ↓
4. Abrir comanda (POST /tabs)
   ↓
5. Lançar itens (POST /tabs/{id}/items)
   ↓
6. [Cozinha prepara]
   ↓
7. Ver item pronto (GET /tabs/{id} atualiza)
   ↓
8. Marcar entregue (POST /items/{id}/deliver)
   ↓
9. Pedir conta (GET /tabs/{id}/bill)
   ↓
10. Fechar (POST /tabs/{id}/close)
   ↓
11. Mesa volta para Livre
   ↓
12. Logout
```

---

## ⚠️ Casos de Erro

| Caso | Tratamento |
|------|-----------|
| Mesa já ocupada | Toast: "Mesa já está ocupada" |
| Item indisponível | Toast: "Item indisponível" |
| Token expirado | Redireciona para login |
| Sem permissão | Toast: "Sem permissão" |
| Servidor offline | Toast: "Erro ao conectar" |
| Item desaparece | Recarregar comanda |

---

## 📱 Responsividade

**Celular (< 640px):**
- Mapa em 1 coluna
- Mapa em fullscreen height
- Modal ocupa 90% da altura
- Botões full-width

**Tablet (640px - 1024px):**
- Mapa em 2 colunas
- Modal com max-width 500px

**Desktop (> 1024px):**
- Mapa em 3 colunas
- Modal com max-width 600px

---

## 🔐 Permissões

**Garçom pode:**
- Ver todas as mesas do imóvel
- Abrir/fechar comandas nas mesas
- Lançar/cancelar itens
- Gerar pré-contas
- Transferir/juntar/trocar mesas

**Garçom NÃO pode:**
- Ver folios/faturas (recepção)
- Registrar pagamentos
- Acessar cozinha
- Relatórios

---

**Versão:** 1.0  
**Última atualização:** 2026-10-09  
**Autor:** Claude Code + Ruan Pereira
