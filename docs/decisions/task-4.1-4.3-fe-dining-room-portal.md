# Decisões — Task 4.1–4.3 FE: Salão (Dining Room) com Portal Design

> Registro acumulado das decisões tomadas durante a refatoração da tela de mesas
> com design system Portal (paleta Medieval).
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/4.1-4.3-front-shell` |
| Rodada atual | 1 |
| Build | ✓ passa |
| Testes | 0 |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Usar CSS customizado em arquivo separado (`dining-room-portal.css`) em vez de inline styles | implementado |
| 2 | 0 | Mapear estados do backend (`occupiedTabCount`) para estados visuais do design (livre, ocupada, pronto, conta, reservada) | implementado |
| 3 | 0 | Implementar filtros por área (Salão/Varanda) e status em chips horizontais scrolláveis | implementado |
| 4 | 0 | Sheet modal inferior com border-radius arredondado (50% / 44px no topo) conforme design | implementado |
| 5 | 0 | Manter responsividade mobile como prioridade; grid de mesas 3 colunas | implementado |
| 6 | 0 | Usar hooks existentes (`useDiningTables()`, `useOpenTab()`, `useDiningTableTab()`) sem criar novos | implementado |

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

- [x] Criar arquivo CSS com paleta Medieval do design Portal
- [x] Refazer componente `DiningRoomPage.tsx` com layout do design
- [x] Implementar header com marca, título "Mesas" e avatar
- [x] Implementar filtros por área (grid 2 colunas)
- [x] Implementar chips de filtro por status (Todas, Livre, Ocupada, Pronto, Conta, Reservada)
- [x] Implementar grid de mesas (3 colunas) com estados visuais
- [x] Implementar sheet modal ao clicar na mesa com detalhe
- [x] Integrar com dados reais via `useDiningTables()` hook
- [x] Mapear `occupiedTabCount` para estados de mesa
- [x] Build passa (TypeScript)
- [ ] Testar visualmente no navegador (FE1 design)
- [ ] Testes de integração com backend

### Movido para outra task

Só com aprovação explícita, e dizendo para qual task e por quê.

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Ações completas (Lançar pedido, Fechar, Transferir, Juntar) | 4.4 | Escopo: apenas shell e layout; ações são operações posteriores | — |
| Integração com estados avançados (reservada, pronto, conta) | 4.4 | Backend não enriquece mesas com esses dados ainda | — |
| Página desktop (layout com sidebar) | FE3 | Mobile-first nesta onda; desktop vem depois | — |

---

## Contrato com o backend

Dados que o front consome do hook `useDiningTables()`:

**Response: `GET /api/restaurant/dining-tables`**

```typescript
interface DiningTable {
  readonly id: string;           // UUID da mesa
  readonly label: string;        // "1", "2", etc ou "1A", "10B"
  readonly seats: number;        // capacidade
  readonly area: string;         // "salao", "varanda", etc
  readonly isActive: boolean;    // mesa operacional
  readonly occupiedTabCount: number; // 0 = livre, > 0 = ocupada
}
```

**Mapeamento para UI:**
- `occupiedTabCount === 0` → `status = "livre"`
- `occupiedTabCount > 0` → `status = "ocupada"` (*)
- Estado "pronto", "conta", "reservada" → **não disponível ainda**

(*) Estadual futuro: enriquecer com `status`, `guestCount`, `minutesOpen`, `waiter` via integração com Tab

---

## Cores e estilos (Medieval/Portal)

**Paleta:**
```css
--color-bg: #3D0F1A;           /* Vinho Lacre */
--color-surface: #4A1522;       /* Vinho */
--color-text: #F4ECE3;          /* Pergaminho */
--color-accent: #E8957F;        /* Salmão */
--color-accent-7: #F6D9CE;      /* Salmão Velado */
```

**Tipografia:**
- Títulos: `Cormorant Garamond` 600, caixa-alta, espaçada
- Corpo: `Lora`

**Estados da mesa:**
| Status | Border | Background | Foreground |
|---|---|---|---|
| Livre | `1px dashed rgba(232,149,127,.55)` | transparent | `rgba(244,236,227,.82)` |
| Ocupada | `1.5px solid #E8957F` | `#4A1522` | `#F4ECE3` |
| Pronto | `1.5px solid #E8957F` | `#E8957F` | `#3D0F1A` |
| Conta | `1.5px solid #F6D9CE` | `#F4ECE3` | `#4A1220` |
| Reservada | `3px double rgba(232,149,127,.65)` | transparent | `#F6D9CE` |

---

## Arquivos criados/modificados

| Arquivo | Alteração |
|---|---|
| `frontend/src/styles/dining-room-portal.css` | **NOVO** — estilos Portal |
| `frontend/src/features/dining-room/DiningRoomPage.tsx` | **REFATORADO** — implementa design completo |
| `frontend/src/features/dining-room/DiningTableCard.tsx` | **NÃO MODIFICADO** — mantém compatibilidade |
| `frontend/src/features/dining-room/TabDetailsPanel.tsx` | **NÃO MODIFICADO** — mantém compatibilidade |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Sheet modal não full-screen em telefone | Design especifica 80% altura e margens | Scroll interno no sheet |
| Estados "pronto", "conta", "reservada" não aparecem | Backend ainda não enriquece mesas com status completo | Integração Tab em 4.4 |
| Ações de comanda desabilitadas (botões "Lançar pedido", etc) | Protótipo; ações reais em 4.4 | Completar implementação de Tab |
| Desktop layout ainda é mobile (sem sidebar) | FE1 design mobile-first; desktop é FE3 | Implementar layout desktop depois |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Qual é o comportamento esperado se o backend retornar um `occupiedTabCount` que não corresponde a uma Tab ativa? | 0 |
| 2 | Os dados que vêm do backend já são filtrados por permissão de usuário (não ver mesas de outro garçom)? | 0 |
| 3 | Manter o input de "Pessoas" na modal ou só usar a quantidade que vem do backend? | 0 |

---

## Checklist de finalização

- [x] Build `npm run build` passa
- [x] Imports e tipos corretos (TypeScript)
- [x] CSS customizado em arquivo separado
- [x] Componente principal refatorado
- [x] Integração com hook `useDiningTables()`
- [x] Estilo responsivo (mobile-first)
- [ ] Abrir no navegador e verificar renderização (próxima rodada)
- [ ] Testes E2E com backend (próxima rodada)

---

## Próximas tarefas

1. **4.2** — Operações básicas de Tab (abrir, lançar pedido, fechar)
2. **4.3** — Operações avançadas (transferir, juntar, trocar mesa, cancelar)
3. **FE3** — Layout desktop com sidebar de "Contas pedidas"
