# Decisões — Task 0.8 módulo de referência (cardápio)

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Esta é a fatia vertical que vira **gabarito**: o que passar aqui é replicado por
vários agentes nas ondas seguintes. A revisão do Ruan é a mais importante da
Onda 0.

Vale o teto de três rodadas (decisão #74 da 0.4) e o **orçamento de teste** do
`CLAUDE.md`: teste denso nas invariantes do agregado (preço por peso, janela de
disponibilidade), enxuto no encanamento que a fundação já cobre.

---

## Estado

| | |
|---|---|
| Branch | `task/0.8-menu-reference-module` |
| Rodada atual | 0 — decisões antes da primeira linha de código |
| Build | não iniciado |
| Testes | 978 herdados (shared-kernel 200, identity 552, app 226) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **O cardápio é público: qualquer um com o link lê, sem autenticar.** É a informação menos sensível do sistema e a que mais gente precisa ver — hóspede, cliente de fora, garçom, cozinha | pendente |
| 2 | 0 | Consequência da #1: a leitura pública mora em **`/public/menu`**, não em `/api/restaurant/menu-items`. O plano técnico (seção 8) já separa as superfícies — `/api/*` é interno, `/public/*` é público — e misturar rota autenticada com rota aberta no mesmo prefixo é como se esquece de proteger uma | pendente |
| 3 | 0 | Dois caminhos, **dois payloads**. O público devolve o que o cliente lê: nome, descrição, preço, categoria. Não devolve `prepStation`, `isActive`, `displayOrder` nem colunas de auditoria — isso é operação interna, e um DTO só serviria aos dois errado. O cadastro em `/api/restaurant/menu-items` é `ADMIN` e devolve tudo | pendente |
| 4 | 0 | O `V3__menu.sql` cria as **seis tabelas** que `docs/MIGRATIONS.md` reserva para ele, mas a 0.8 mapeia apenas `MenuCategory`, `MenuItem` e `AvailabilityWindow`. `menu_item_variant`, `modifier` e `menu_item_modifier` ficam sem agregado até a task 1.2, que é dona deles. Migration é forward-only: criar as seis de uma vez evita um V10 só para duas tabelas | pendente |
| 5 | 0 | `isAvailableAt(Instant)` converte usando o **`time_zone` da `property`**, não o fuso da JVM. A coluna já existe desde a V1 e traz `America/Fortaleza` no seed. Um servidor em UTC, que é o normal em container, faria a pizza aparecer três horas fora do lugar — e o erro seria silencioso | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] Migration `V3__menu.sql` com as seis tabelas do documento de schema (#4)
- [ ] `MenuCategory` como agregado
- [ ] `MenuItem` como agregado rico: preço unitário **ou** `pricePerKilo`, `PrepStation`, `serviceChargeEligible`, `markUnavailable()`
- [ ] `AvailabilityWindow` com `isAvailableAt(Instant)`, incluindo janela que cruza a meia-noite (#5)
- [ ] Repositório: interface no domínio, implementação JPA na infra
- [ ] Casos de uso: criar, atualizar, listar, marcar indisponível
- [ ] Controller interno `ADMIN` em `/api/restaurant/menu-items`
- [ ] Controller público em `/public/menu` (#1, #2, #3)
- [ ] `http/30-restaurant-menu.http` com caminho feliz e cenários negativos
- [ ] Testes de unidade do agregado e teste de integração do fluxo

### Fora do escopo

- `MenuItemVariant` e `Modifier`: tabelas criadas, agregados são da task 1.2 (#4)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| | | |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| | |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| | | |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

| # | Pergunta | Desde a rodada |
|---|---|---|
