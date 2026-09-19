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
| Rodada atual | 1 — fatia vertical escrita |
| Build | `./mvnw clean install` **verde** — 998 testes, 0 falhas |
| Testes | 998: os 978 herdados, mais 16 do agregado e 4 da fatia vertical |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **O cardápio é público: qualquer um com o link lê, sem autenticar.** É a informação menos sensível do sistema e a que mais gente precisa ver — hóspede, cliente de fora, garçom, cozinha | pendente |
| 2 | 0 | Consequência da #1: a leitura pública mora em **`/public/menu`**, não em `/api/restaurant/menu-items`. O plano técnico (seção 8) já separa as superfícies — `/api/*` é interno, `/public/*` é público — e misturar rota autenticada com rota aberta no mesmo prefixo é como se esquece de proteger uma | pendente |
| 3 | 0 | Dois caminhos, **dois payloads**. O público devolve o que o cliente lê: nome, descrição, preço, categoria. Não devolve `prepStation`, `isActive`, `displayOrder` nem colunas de auditoria — isso é operação interna, e um DTO só serviria aos dois errado. O cadastro em `/api/restaurant/menu-items` é `ADMIN` e devolve tudo | pendente |
| 4 | 0 | O `V3__menu.sql` cria as **seis tabelas** que `docs/MIGRATIONS.md` reserva para ele, mas a 0.8 mapeia apenas `MenuCategory`, `MenuItem` e `AvailabilityWindow`. `menu_item_variant`, `modifier` e `menu_item_modifier` ficam sem agregado até a task 1.2, que é dona deles. Migration é forward-only: criar as seis de uma vez evita um V10 só para duas tabelas | pendente |
| 5 | 0 | `isAvailableAt(Instant)` converte usando o **`time_zone` da `property`**, não o fuso da JVM. A coluna já existe desde a V1 e traz `America/Fortaleza` no seed. Um servidor em UTC, que é o normal em container, faria a pizza aparecer três horas fora do lugar — e o erro seria silencioso | pendente |

| 6 | 1 | Nasce a porta **`CurrentProperty`** no `shared-kernel`, com `id()` e `timeZone()`, respondida no `app` pelo `SinglePropertyId` da 0.5b. Toda tabela transacional carrega `property_id` e só a raiz de composição sabe o valor — sem a porta, o `restaurant` teria de enxergar o `app`. **Isto revisa a decisão #27 da 0.5b**, que dizia não nascer provedor de propriedade: lá o contexto era a assinatura do `Settings`, e nenhum módulo de domínio escrevia linha ainda | pendente (aguarda Ruan) |
| 7 | 1 | `MoneyConverter` (`@Converter(autoApply = true)`) no `shared-kernel`: todo campo `Money` de todo módulo persiste igual, sem cada entidade declarar. Usa só `jakarta.persistence`, que é o que a regra A4 permite ali | pendente |
| 8 | 1 | A unicidade de nome (item e categoria) é checada **no caso de uso**, não no agregado: é regra sobre o conjunto, não sobre um elemento. O banco carrega a mesma regra em `uk_menu_item_name`, que é o que a torna verdadeira sob concorrência; a checagem em Java existe para responder um `code` legível em vez de violação de constraint | pendente |
| 9 | 1 | **Consequência achada rodando o `.http`, não pelo build:** o parent pom declarava `<configuration>` do `maven-compiler-plugin` e com isso **substituía** a do `spring-boot-starter-parent`, perdendo a flag `-parameters`. Sem ela o Spring não lê o nome de um `@PathVariable`, e toda rota com variável de caminho responde 500 — em execução, nunca no build. Nenhum controller anterior tinha variável de caminho, então ninguém tinha batido nisso. A flag volta ao parent pom, e o gabarito **nomeia a variável explicitamente**, para não depender de flag de build | pendente |
| 10 | 1 | `AvailabilityWindow` **não carrega o id do item**. O lado dono escreve `menu_item_id` pela `@JoinColumn`, e mapear a mesma coluna duas vezes é o que o Hibernate recusa — `Column 'menu_item_id' is duplicated in mapping` | pendente |
| 11 | 1 | Janela: `startTime` inclusivo, `endTime` exclusivo. 18:30–23:00 serve às 18:30 e não serve mais às 23:00. Sem isso, duas janelas coladas serviriam o mesmo minuto duas vezes | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Migration `V3__menu.sql` com as seis tabelas do documento de schema (#4)
- [x] `MenuCategory` como agregado
- [x] `MenuItem` como agregado rico: preço unitário **ou** `pricePerKilo`, `PrepStation`, `serviceChargeEligible`, `markUnavailable()`
- [x] `AvailabilityWindow` com `isAvailableAt(Instant)`, incluindo janela que cruza a meia-noite (#5)
- [x] Repositório: interface no domínio, implementação JPA na infra
- [x] Casos de uso: criar, atualizar, listar, marcar indisponível
- [x] Controller interno `ADMIN` em `/api/restaurant/menu-items`
- [x] Controller público em `/public/menu` (#1, #2, #3)
- [x] `http/30-restaurant-menu.http` com caminho feliz e cenários negativos
- [x] Testes de unidade do agregado e teste de integração do fluxo

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
| `MENU_ITEM_NOT_FOUND` | 404 | O item pedido não existe |
| `MENU_CATEGORY_NOT_FOUND` | 404 | A categoria pedida não existe |
| `MENU_ITEM_NAME_ALREADY_USED` | 409 | Outro item da propriedade já tem esse nome |
| `MENU_CATEGORY_NAME_ALREADY_USED` | 409 | Outra categoria da propriedade já tem esse nome |
| `INVALID_MENU_ITEM_PRICING` | 422 | Preço ausente, zero ou negativo |
| `INVALID_MENU_ITEM_NAME` | 422 | Nome em branco ou acima de 150 caracteres |
| `INVALID_MENU_CATEGORY_NAME` | 422 | Nome em branco ou acima de 100 caracteres |
| `INVALID_AVAILABILITY_WINDOW` | 422 | Janela que começa e termina no mesmo horário |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `price` | String decimal (`"62.00"`). Preço da unidade, ou do quilo quando `soldByWeight` |
| `startTime` / `endTime` | `HH:mm`. `endTime` menor que `startTime` cruza a meia-noite |
| `dayOfWeek` | Nome do dia em inglês (`WEDNESDAY`); ausente significa todos os dias |
| `availableNow` | Só no cardápio público: já considera a janela e a falta na cozinha |
| Cardápio público | `GET /public/menu`, sem token, sem `prepStation`, `isActive` nem auditoria |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| `menu_item_variant`, `modifier` e `menu_item_modifier` existem no banco sem agregado | A task 1.2 é dona deles; criar as seis tabelas de uma vez evita uma migration só para duas (#4) | Task 1.2 |
| O cardápio público não pagina nem tem cache | Um cardápio de hotel tem dezenas de itens, e a rota é aberta mas não é rota de campanha | Cache quando existir volume que justifique |
| Não há endpoint para remover item ou categoria | `deactivate()` existe no agregado; o que já foi vendido não pode sumir do histórico | Onda 4, junto com a tela de administração |

---

## Pontos em aberto

Nenhum.

| # | Pergunta | Desde a rodada |
|---|---|---|
