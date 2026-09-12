# Migrations

## Regra forward-only

Migration aplicada nunca é editada. Uma correção vira uma nova migration com
número seguinte. O histórico do Flyway é a fonte da verdade do schema em
produção — reescrever uma versão já aplicada quebra a validação em qualquer
ambiente que já rodou aquela versão.

## Convenção de nome

```
V{n}__{descricao}.sql
```

`{n}` é a versão (inteiro, sem zero à esquerda), `{descricao}` em
`snake_case`, em inglês. Duas letras `_` entre o número e a descrição.

## Numeração reservada

Cada task já tem seu número atribuído em `docs/schema-banco-de-dados.md`
(seção 3), para evitar colisão entre agentes trabalhando em paralelo.

| Versão | Arquivo | Task | Tabelas |
|---|---|---|---|
| V1 | `V1__baseline.sql` | 0.3 | `property`, `setting`, `app_user`, `user_role` |
| V2 | `V2__menu.sql` | 0.8 | `menu_category`, `menu_item`, `menu_item_variant`, `modifier`, `menu_item_modifier`, `availability_window` |
| V3 | `V3__billing.sql` | 1.3 | `folio`, `charge`, `payment`, `payment_intent` |
| V4 | `V4__hotel_inventory.sql` | 1.1 | `room_type`, `room`, `rate_plan` |
| V5 | `V5__dining_table.sql` | 1.5 | `dining_table` |
| V6 | `V6__reservation.sql` | 2.1 | `guest`, `daily_inventory`, `reservation`, `reservation_child`, `room_night` |
| V7 | `V7__tab.sql` | 2.2 | `tab`, `tab_item`, `tab_item_modifier` |
| V8 | `V8__cash.sql` | 2.4 | `cash_drawer_session`, `cash_movement` + FK em `payment` |
