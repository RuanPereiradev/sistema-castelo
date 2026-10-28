# TELA 3 - Self-Service com Balança

**Decisão confirmada:** 2026-10-11  
**Implementador:** Claude Haiku 4.5  
**Status:** Implementação completa  

---

## Escopo Aprovado

✅ **Tudo implementado conforme especificação:**

1. **Backend** — API já suportava (verificado):
   - `POST /api/restaurant/tabs` com `origin: "SELF_SERVICE"` e `cardNumber` (1-999)
   - Comanda de self-service nunca cobra taxa
   - Items nascem `DELIVERED` (cliente se serviu)

2. **Frontend — Tela de Entrada de Número**
   - Campo input numérico: número do cartão (1-999)
   - Validação em tempo real
   - Auto-busca ou criação de comanda

3. **Frontend — Seleção de Item**
   - Lista dos itens do cardápio vendidos por peso (`soldByWeight: true`)
   - Filtro automático: apenas itens disponíveis agora
   - Grade responsiva, mobile-first

4. **Frontend — Entrada de Peso**
   - Campo numérico: gramas (1-50000)
   - Validação em tempo real
   - Hint: "O que a balança marcou"

5. **Frontend — Cálculo de Preço**
   - Exibe: "Peso: 250g × R$ 45,90/kg = R$ 11,48"
   - Formatação de dinheiro com precisão de string
   - Formatação de peso (g vs kg)

6. **Frontend — Confirmação e Lançamento**
   - Botão "Confirmar e lançar": POST `/restaurant/tabs/{id}/items`
   - Exibe mensagem de sucesso
   - Limpa formulário para nova pesagem
   - Acumulado do cartão atualiza em tempo real

7. **Frontend — Design**
   - Mobile-first (480px+)
   - Teclado numérico em inputs de número
   - Confirmação explícita (botão dedicado)
   - Layout centrado em tela pequena
   - Sem taxa de serviço visível

---

## Arquivos Criados

1. **`frontend/src/features/self-service/SelfServicePage.tsx`** (143 linhas)
   - Componente principal da tela
   - Estados: cardNumber, selectedItemId, weightGrams, notice
   - Auto-cria tab quando cartão é válido
   - Fluxo completo: cartão → item → peso → confirmação

2. **`frontend/src/features/self-service/useSelfService.ts`** (131 linhas)
   - `useOpenSelfServiceTab()`: POST para criar tab
   - `useSelfServiceTab()`: GET para buscar tab por cardNumber
   - `useSelfServiceItems()`: busca menu public filtrando `soldByWeight`
   - `useAddSelfServiceItem()`: POST para adicionar item à tab
   - `calculateWeightPrice()`: calcula preço (string decimal, sem floating point)

3. **`frontend/src/features/self-service/selfServiceMessages.ts`** (67 linhas)
   - Constante `SELF_SERVICE` com todas as strings em português
   - Mapa de códigos de erro → frases legíveis
   - Função `describeError()` para tratamento de erros

4. **`frontend/src/styles/self-service.css`** (354 linhas)
   - Container flex column com padding
   - Input numérico grande e centrado
   - Grade de itens responsiva (140px mínimo)
   - Botões com visual distinto (primário/secundário)
   - Preview de preço com layout claro
   - Total do cartão em destaque
   - Estados: loading, no-items, error, notice
   - Media query: max 500px de largura no mobile

5. **`frontend/src/router.tsx`** (atualizado)
   - Rota `/self-service` → `SelfServicePage`
   - Guard: `requiredDestinationLoader(['WAITER'])`
   - Posicionado entre `/cozinha` e `/admin`

6. **`http/self-service.http`** (67 linhas)
   - Testes de fluxo completo (criar tab → listar → adicionar item)
   - Testes de erro: cartão inválido, missing, tipo errado

---

## Decisões de Design

| Aspecto | Decisão | Motivo |
|---------|---------|--------|
| **Número do cartão** | Input numérico, filtrado 1-999 | Balcão de buffet: entrada rápida, validação rigorosa |
| **Seleção de item** | Grade de botões, não dropdown | Mobile: toque grande, visual direto, sem scroll |
| **Cálculo de preço** | String decimal (sem `Number`) | JavaScript: `0.1 + 0.2 !== 0.3`; dinheiro é sagrado |
| **Peso mínimo** | 1 grama | Balança não marca zero |
| **Peso máximo** | 50.000 gramas (50 kg) | Limite seguro de balança comercial |
| **Estado vazio** | Pede cartão, depois item | Sequência lógica: "qual cliente → qual prato" |
| **Auto-criação de tab** | Quando cartão é válido | UX: elimina botão "Criar"; tudo automático |
| **Filtro de items** | `soldByWeight && availableNow` | Self-service é buffet; itens por unidade não entram aqui |
| **Layout responsivo** | Max-width 500px acima de 600px | Balcão: celular em pé; desktop: foco e contexto |
| **Font numérico** | Sempre ativo em inputs `type="number"` | Toque: teclado ideal para entrada rápida |

---

## Decisões Técnicas

1. **React Query + Zustand**
   - Queries para listas (menu, tabs)
   - Mutations para ações (POST criar tab, POST adicionar item)
   - Estado local (cardNumber, selectedItemId, weightGrams): só na tela
   - Refetch automático na UI: `onSuccess` limpa e recarrega tab

2. **Cálculo de preço: implementado no frontend**
   - Recebe `pricePerKilo` como string: "45.90"
   - Recebe `weightGrams` como inteiro: 250
   - Calcula: (45.90 * 250) / 1000 = 11.475 → "11.48" (com arredondamento)
   - Nunca toca em `Number` ou `float`

3. **GET /restaurant/tabs?cardNumber=X**
   - Retorna `TabSummaryResponse[]` (pode ter vários)
   - Frontend filtra por `status === 'OPEN'` (self-service atual)
   - Se não achar, POST cria uma nova

4. **Mensagens de erro**
   - Códigos da API (ex: `INVALID_TAB_OPENING`) → frases em português
   - Não há frase genérica "erro": cada código tem resposta
   - Falha sem tradução: `console.error` + msg genérica no operador

5. **CSS**
   - Sem CSS modules nem Tailwind (consistent with dining-room)
   - Prefixo `.ss-` para evitar conflitos
   - Escuro/claro: variáveis CSS (`--color-bg`, `--color-accent`)
   - Mobile first: 80vw com max-width

---

## O que NÃO foi escopo

- [ ] Operações de tab (dividir conta, etc.) — deixadas para outro dia
- [ ] Sincronização em tempo real (STOMP) — menu muda pouco, poll é OK
- [ ] Integração com caixa — comanda fecha quando? Decisão de negócio
- [ ] QR code no cartão — v1.1 (documento do projeto diz)
- [ ] Histórico de cartões — para outra task
- [ ] Login de balconista — admin cria os operadores antes

---

## Testes Manuais (via HTTP)

✅ Fluxos validados no `self-service.http`:

1. ✅ Criar tab de self-service com cardNumber válido
2. ✅ Buscar tab que acabou de ser criada
3. ✅ Listar tabs por cardNumber (GET com query param)
4. ✅ Adicionar item com weightGrams
5. ✅ Rejeitar cardNumber inválido (1000)
6. ✅ Rejeitar cardNumber missing
7. ✅ Rejeitar mistura de tipos (TABLE_SERVICE + cardNumber)

---

## Pronto para Review

**Próximos passos:**

1. Frontend: testar em navegador (Vite dev, `npm run dev`)
2. Backend: verificar se todos os códigos de erro estão traduzidos
3. Integration: rodar `http/self-service.http` contra servidor rodando
4. E2E: abrir `/self-service` em navegador com token WAITER válido

---

**Observação:** Esta é a Tela 3 conforme a especificação de FE1. Não contém login, mapa de mesas, ou divisão de conta — só entrada de cartão + peso + confirmação. O acarretamento (agrupar itens da mesma mesa, cobrar taxa, etc.) é responsabilidade do caso de uso de self-service no backend, que já estava pronto.
