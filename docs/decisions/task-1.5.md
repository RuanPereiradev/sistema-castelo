# Decisões — Task 1.5 mesas e cartões do self-service

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Segunda task da trilha do restaurante. Cadastra as mesas onde a comanda da
2.2 vai abrir. Migration reservada: `V5__dining_table.sql` (renumeração da
decisão #5 da 1.2).

Vale o teto de três rodadas (decisão #74 da 0.4) e o orçamento de teste do
`CLAUDE.md`. Aqui o risco de negócio é baixo — cadastro sem dinheiro nem
concorrência —, então o teste é enxuto.

---

## Estado

| | |
|---|---|
| Branch | `task/1.5-dining-table` |
| Rodada atual | 1 — review mecânico aplicado; aguarda revisão do Ruan e execução do `.http` |
| Build | `./mvnw clean install` **verde** — 1118 testes, 0 falhas |
| Testes | 24 de unidade + 1 de integração da fatia. Orçamento: 435 linhas de teste para 753 de produção (0,58:1), teste enxuto por ser cadastro de risco baixo |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | O cartão do self-service é **número livre**: o operador digita qualquer número, sem cadastro de cartões. Não existe tabela nem agregado de cartão — o `cardNumber` é só um campo da `Tab`, como o schema já desenha | implementado |
| 2 | 0 | Consequência da #1: a regra "um cartão só tem uma comanda aberta por vez" e a validação do número do cartão pertencem à `Tab`, **task 2.2**. A 1.5 fica só com `DiningTable` | implementado |
| 3 | 0 | Cadastrar, editar, ativar e desativar mesa é `ADMIN`. **Ler** a lista e uma mesa é `ADMIN` e `WAITER` — o garçom precisa ver as mesas para abrir a comanda | implementado |
| 4 | 0 | A área da mesa ("Salão", "Varanda") é **texto livre** e opcional, como o schema prevê. Sem cadastro de áreas | implementado |
| 5 | 0 | A identificação da mesa (`label`, "Mesa 5", "V3") é única na propriedade **ignorando maiúsculas**, guardada como digitada — mesma regra da #27 da 1.2, já na V5 com índice sobre `lower(label)` | pendente (DEV, aguarda Ruan) |
| 6 | 0 | Mesa não é apagada, só desativada: a `tab` da 2.2 referencia a mesa e o histórico não pode perder o lugar. A lista padrão mostra só as ativas; o `ADMIN` pede as inativas com `?includeInactive=true` | pendente (DEV, aguarda Ruan) |
| 7 | 0 | Edição é `PUT` com o cadastro inteiro (`label`, `seats`, `area`), não `PATCH`: lugares e área são opcionais, e só a substituição completa permite limpá-los | pendente (DEV, aguarda Ruan) |
| 8 | 0 | A lista sai ordenada por área (sem área por último) e, dentro da área, pela identificação em **ordem natural**: "Mesa 2" antes de "Mesa 10" | pendente (DEV, aguarda Ruan) |
| 9 | 0 | `DiningTableId` mora em `restaurant.domain`: só a `Tab`, do mesmo módulo, o usa, e o `api/` foi congelado na 0.6 (mesma linha da #13 da 1.2) | pendente (DEV, aguarda Ruan) |
| 10 | 1 | Ambiguidades levantadas pelo TEST, resolvidas pelo padrão da 1.2: `label` nulo é recusado como em branco (`INVALID_DINING_TABLE_LABEL`) · `propertyId` nulo é `NullPointerException` · o limite de 50 da área vale **depois de aparar**, como o do `label` · mesa inativa **pode ser editada** (o admin corrige antes de reativar) · com vários campos inválidos, a ordem é `label` → `seats` → `area`. Corrigida a tabela de erros deste log, que dizia "menores que 1" para `seats`; vale a spec, de 1 a 999 | pendente (DEV, aguarda Ruan) |
| 11 | 1 | ~~Ordem natural (#8) em `DiningTable.listingOrder()`, um `Comparator` estático testável sem Spring: área sem distinguir maiúsculas (sem área por último), depois `label`, com sequência de dígitos comparada pelo valor numérico sem converter (sem overflow). Empate cai no `String.compareTo`, para a ordem ser total ~~ | **revertida pela #17** |
| 12 | 1 | Filtro de inativas na consulta (`:includeInactive = true or t.active = true`), sem `if` no service. `WAITER` pedindo `includeInactive=true` recebe 403 por `@PreAuthorize` com o parâmetro nomeado via `@P`. `FRONT_DESK` e `KITCHEN` não têm acesso nenhum | implementado (DEV, aguarda Ruan) |
| 13 | 1 | A unicidade do `label` é checada **antes** do agregado, como no `ModifierService`: a consulta JPQL faria auto-flush do rótulo novo e bateria no índice (500) antes do 409. Efeito: `label` duplicado com `seats` inválido responde 409, não 422 | implementado (DEV, aguarda Ruan) |
| 14 | 1 | `seats` guardado como `Short` (a coluna é `SMALLINT` e o `ddl-auto` é `validate`); a assinatura pública segue `Integer`/`Optional<Integer>`. O banco passa a ter `CHECK (seats BETWEEN 1 AND 999)`, o mesmo limite do agregado — a V5 ainda não foi aplicada fora de branch | implementado |
| 15 | 1 | O controller chama-se `DiningTableController`, não `...AdministrationController`, porque o garçom também lê por ele. Resposta `{id, label, seats, area, isActive}`, com `null` para o vazio | implementado (DEV, aguarda Ruan) |
| 16 | 1 | O `http/README.md` ganha as linhas do `30`, `31` e `32` na tabela de arquivos: os dois primeiros ficaram de fora nas tasks 0.8 e 1.2 | implementado |
| 17 | 1 | **Revê a #11** (review mecânico): o texto da ordem natural é comparado como um leitor de português ordena — `Collator` pt-BR, ignorando maiúsculas **e acentos**. "Área externa" vem antes de "Varanda" (antes caía depois, pela ordem Unicode), e "varanda" e "Varanda" formam **um grupo só** (antes eram dois blocos vizinhos). Sequência de dígitos segue pelo valor numérico; o desempate exato fica só no fim, para a ordem ser total | implementado |
| 18 | 1 | Testes acrescentados pelo review: `label` nulo, ordem `label` → `seats` → `area` com tudo inválido, ordem natural com caixa e zero à esquerda, e área com caixa e acento diferentes | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `V5__dining_table.sql` (com índice único sobre `lower(label)`, #5)
- [x] Agregado `DiningTable`: identificação, lugares, área, ativar/desativar
- [x] Casos de uso e rotas de cadastro e leitura de mesas
- [x] `http/32-restaurant-dining-tables.http` com caminho feliz e cenários negativos
- [x] Testes do agregado e um teste de integração da fatia

### Fora do escopo

- Mesa ocupada ou livre: é derivado de haver comanda aberta nela, e a comanda nasce na 2.2

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Cartões do self-service (validação do número, uma comanda aberta por cartão) | 2.2 | Cartão é número livre, campo da `Tab`, sem cadastro próprio (#1, #2) | Ruan, rodada 0 |

---

## Contrato com o front

Especificado em `docs/task-1.5-dining-table.md`, seções 4 e 5.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `DINING_TABLE_NOT_FOUND` | 404 | A mesa não existe |
| `DINING_TABLE_LABEL_ALREADY_USED` | 409 | Outra mesa da propriedade já usa essa identificação (ignorando maiúsculas) |
| `INVALID_DINING_TABLE_LABEL` | 422 | Identificação em branco ou acima de 20 caracteres |
| `INVALID_DINING_TABLE_SEATS` | 422 | Lugares informados fora de 1 a 999 |
| `INVALID_DINING_TABLE_AREA` | 422 | Área acima de 50 caracteres |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `seats` | Inteiro, opcional (`null` quando não informado) |
| `area` | Texto livre, opcional; em branco vira `null` |
| Lista | Por área (sem área por último), depois identificação em ordem natural (#8) |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| A busca da mesa por id não filtra por propriedade | Instalação de propriedade única; mesmo padrão do módulo desde a 0.8 (#28 da 1.2) | Quando existir multipropriedade |
| `trim()` não remove espaço Unicode (ex.: espaço ideográfico), então "Mesa 5　" não colide com "Mesa 5" | `MenuCategory`, `MenuItem` e `Modifier` fazem igual; trocar por `strip()` só faz sentido no módulo inteiro de uma vez | Troca conjunta por `strip()` numa task de restaurante |
| Espaço duplo interno ("Mesa  2") não é normalizado e sai fora da sequência na lista | Texto livre digitado pelo admin; raro | Normalizar espaços internos se aparecer na operação |
| Desativar uma mesa não confere se há comanda aberta nela | A comanda só nasce na 2.2 | A 2.2 decide se recusa a desativação ou só impede nova comanda |
| Nome duplicado numa corrida responde 500 em vez de 409 | Mesmo caso da 1.2, já movido para a 5.3 (#28 da 1.2); o índice mantém o dado certo | Task 5.3 |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
