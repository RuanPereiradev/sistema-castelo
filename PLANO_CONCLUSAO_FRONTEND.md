# 🚀 Plano de Conclusão do Frontend - Hospedaria

**Data:** 2026-10-09  
**Objetivo:** Completar o frontend conforme Brief-Front-End oficial  
**Prazo:** 2 semanas (com dedicação)

---

## 📋 Escopo Oficial - 5 Telas

Conforme `docs/brief-front-end.md`:

### Tela 1️⃣ — SALÃO E COMANDA (Mobile-First)
**Perfil:** WAITER + ADMIN  
**Contexto:** De pé, andando no salão, celular, uma mão, tela suja  
**Status:** 🟡 PARCIAL

#### O que já existe:
- ✅ Mapa de mesas (grid responsivo)
- ✅ Design Portal (vinho, salmão, pergaminho)
- ✅ Filtros por área e status
- ✅ Modal de mesa (TableSheet.tsx)

#### O que falta:
- ❌ Integração dos botões ao backend (RF2-RF13)
- ❌ Lançar item completo (variações, adicionais, peso)
- ❌ Cancelar item com motivo
- ❌ Marcar entregue
- ❌ Pedir pré-conta (bill)
- ❌ Fechar comanda (3 passos)
- ❌ Divisão de conta (igual e por item)
- ❌ Transferir/Juntar/Trocar mesa
- ❌ Reabrir comanda

**Prioridade:** 🔴 CRÍTICA (core do sistema)  
**Horas:** 8-10h

---

### Tela 2️⃣ — KDS (Kitchen Display System)
**Perfil:** KITCHEN + ADMIN  
**Contexto:** Monitor na parede, lido a 2m de distância, ninguém toca  
**Status:** 🟡 PARCIAL

#### O que já existe:
- ✅ Componente KitchenPage.tsx
- ✅ Polling 3s funcionando
- ✅ Grid de tickets por status
- ✅ Cores e timeline

#### O que falta:
- ❌ WebSocket STOMP em `/ws/kitchen`
- ❌ Mensagens em tempo real (ORDERED, STATUS_CHANGED, CANCELLED, TRANSFERRED)
- ❌ Comparação com serverTime (não relógio local)
- ❌ Reconexão automática
- ❌ Três setores independentes (KITCHEN, PIZZA, BAR)
- ❌ Botões grandes: start, ready, undo
- ❌ Avisos visuais (warning/late alerts)

**Prioridade:** 🟠 ALTA (operação crítica)  
**Horas:** 6-8h

---

### Tela 3️⃣ — SELF-SERVICE COM BALANÇA
**Perfil:** WAITER  
**Contexto:** Balcão do buffet, fila esperando, entrada rápida  
**Status:** ❌ NÃO EXISTE

#### O que precisa:
- Digitação rápida (gramas, número de cartão)
- Confirmação do valor antes de lançar
- Acumulado do cartão visível
- Sem taxa de serviço
- Produto nasce DELIVERED (cliente se serviu)
- Sem variação/adicional, só peso

**Prioridade:** 🟡 MÉDIA (operação secundária)  
**Horas:** 3-4h

---

### Tela 4️⃣ — CAIXA (Turno + Folio)
**Perfil:** ADMIN + FRONT_DESK  
**Contexto:** Desktop, balcão, sem pressa  
**Status:** 🟡 PARCIAL

#### O que já existe:
- ✅ Componente CashierPage.tsx
- ✅ Layout básico

#### O que falta:

**Turno de Caixa:**
- ❌ Abrir com fundo de troco (openingFloat)
- ❌ Registrar sangria (CASH_DROP)
- ❌ Registrar suprimento (CASH_SUPPLY)
- ❌ Fechamento cego (não ver esperado até contar)
- ❌ Diferença de caixa
- ❌ Auditoria (quem abriu, fechou, horário)

**Folio (Conta do Hóspede):**
- ❌ Listar folios abertos
- ❌ Ver detalhes (lançamentos, pagamentos)
- ❌ Estornar lançamento (reversal)
- ❌ Estornar pagamento (refund)
- ❌ Lançar ajuste/desconto
- ❌ Saldo em tempo real

**Prioridade:** 🟡 MÉDIA (suporte operacional)  
**Horas:** 8-10h

---

### Tela 5️⃣ — ADMINISTRAÇÃO DO CARDÁPIO
**Perfil:** ADMIN  
**Contexto:** Desktop, sem pressa  
**Status:** ❌ NÃO EXISTE

#### O que precisa:

**Cardápio:**
- [ ] CRUD de categorias
- [ ] CRUD de itens
- [ ] CRUD de variações (P/M/G com preço)
- [ ] CRUD de adicionais (com max/item)
- [ ] Janelas de horário (item só servido entre X:XX e Y:YY)
- [ ] Ativo/Inativo vs Disponível/Esgotado

**Mesas:**
- [ ] CRUD de mesas
- [ ] Validação (não desativar mesa com comanda aberta)

**Prioridade:** 🟢 BAIXA (admin, não afeta operação)  
**Horas:** 12-15h

---

## 🎯 O Que Preciso De Você

### 1. **Definição de Prioridades**
Qual ordem de desenvolvimento?

```
Opção A (Recomendado - Garçom primeiro):
1. Tela 1 (Salão e Comanda) - CRÍTICA
2. Tela 2 (KDS) - ALTA
3. Tela 4 (Caixa) - MÉDIA
4. Tela 3 (Self-service) - MÉDIA
5. Tela 5 (Admin) - BAIXA

Opção B (Recepção primeiro):
1. Tela 1 (Salão) - CRÍTICA
2. Tela 4 (Caixa) - ALTA
3. Tela 2 (KDS) - MÉDIA
4. Tela 3/5 - BAIXA
```

### 2. **Design & Paleta (Se Houver Mudanças)**

Hoje definido como Portal Frame (medieval):
- 🍷 Vinho Lacre: #3D0F1A
- 🍷 Vinho Superfície: #4A1522
- 🍊 Salmão: #E8957F
- 📜 Pergaminho: #F4ECE3
- 🔤 Cormorant Garamond + Lora

**Você quer:**
- [ ] Manter Portal (medieval)?
- [ ] Mudar para Iluminura?
- [ ] Outra paleta?

### 3. **Responsividade por Tela**

| Tela | Dispositivo | Orientação |
|------|-------------|-----------|
| Salão | 📱 Celular | Portrait |
| KDS | 🖥️ Monitor | Landscape (2m de distância) |
| Self | 📱 Tablet | Portrait |
| Caixa | 🖥️ Desktop | Landscape |
| Admin | 🖥️ Desktop | Landscape |

Precisa de algo diferente?

### 4. **Ícones e Elementos Visuais**

Qual estilo preferido?
- [ ] Ícones simples (stroke)
- [ ] Ícones filled
- [ ] Emojis (rápido)
- [ ] SVG customizados

### 5. **Animações**

Nivel de animação desejado:
- [ ] Mínimo (transições CSS)
- [ ] Moderado (fade/slide)
- [ ] Ricas (spring, parallax)

---

## 📊 Matriz de Trabalho

```
SEMANA 1:
- [ ] Completar Tela 1 (Salão) - Terça/Quarta
- [ ] Tela 2 (KDS) com WebSocket - Quinta/Sexta

SEMANA 2:
- [ ] Tela 4 (Caixa) - Segunda/Terça
- [ ] Tela 3 (Self-service) - Quarta
- [ ] Tela 5 (Admin) - Quinta/Sexta

REVIEW:
- [ ] Testes E2E
- [ ] Performance
- [ ] Bug fixes
```

---

## 🔧 Tecnologia Já Definida

**Stack Confirmado:**
- React 18 + TypeScript
- Vite (dev server)
- React Router v6
- TanStack Query (React Query)
- Zustand (state)
- CSS puro (sem Tailwind/CSS-in-JS)

**APIs Confirmadas:**
- HTTP com auto-refresh de token
- RFC 7807 para erros
- STOMP WebSocket para KDS
- Polling fallback 5s (salão), 3s (KDS), 10s (caixa)

**Não usa:**
- PWA (adiado para V2)
- Testes (adiado para V2)
- E-mail transacional
- Payment gateway

---

## 💬 Próximas Respostas Que Preciso

**Marque com X os pontos que precisam ser decididos:**

- [ ] **1. Ordem de prioridades** (Opção A ou B acima?)
- [ ] **2. Paleta de cores** (Portal, Iluminura, outra?)
- [ ] **3. Estilo de ícones** (qual preferência?)
- [ ] **4. Nível de animação** (mínimo, moderado, rico?)
- [ ] **5. Há algum design em Figma/protótipo já feito?**
- [ ] **6. Responsividade customizada?** (ou as recomendações acima servem?)
- [ ] **7. Prazo:** Precisa estar pronto até quando?

---

## 📝 Entregáveis de Cada Tela

Para cada tela, vou entregar:

1. ✅ Componentes React completos
2. ✅ Hooks de integração com backend
3. ✅ CSS responsivo
4. ✅ Tratamento de erros (RFC 7807)
5. ✅ Arquivo `.http` com exemplos
6. ✅ Commit com PR para main
7. ✅ Testável no navegador

---

## 🎬 Como Começamos?

**Opção 1 (Recomendado):**
- [ ] Você responde os 7 pontos acima
- [ ] Eu começo Tela 1 (Salão) hoje
- [ ] Você testa e faz feedback
- [ ] Continuamos iterativamente

**Opção 2:**
- [ ] Você me passa especificação completa de design
- [ ] Eu implemento com base nisso
- [ ] Review depois

---

**Qual caminho prefere?**

