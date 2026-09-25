# Task 1.5 — Mesas

**Objetivo:** cadastrar as mesas do restaurante, onde a comanda da 2.2 vai abrir.

Decisões: `docs/decisions/task-1.5.md` (#1 a #9). Os cartões do self-service
**não** estão aqui: são número livre na `Tab`, task 2.2 (#1, #2). Gabarito de
estilo: o módulo `restaurant` (`MenuCategory` é o agregado mais parecido;
`MenuItemService` e `MenuAdministrationController` para casos de uso e rotas).

Risco de negócio baixo — cadastro sem dinheiro nem concorrência. Teste enxuto.

---

## 1. Modelo de dados

`V5__dining_table.sql`, a partir do desenho de `docs/schema-banco-de-dados.md`
(seção 9), com o `UNIQUE` trocado por índice sobre `lower(label)` (#5):

```sql
CREATE TABLE dining_table (
    id              UUID PRIMARY KEY,
    property_id     UUID        NOT NULL REFERENCES property(id),
    label           VARCHAR(20) NOT NULL,
    seats           SMALLINT    CHECK (seats BETWEEN 1 AND 999),
    area            VARCHAR(50),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMPTZ,
    updated_by      UUID
);

CREATE UNIQUE INDEX uk_dining_table_label ON dining_table (property_id, lower(label));
```

---

## 2. Invariantes do agregado `DiningTable`

1. Pertence a uma propriedade
2. `label` obrigatório, aparado, no máximo 20 caracteres depois de aparar
3. `seats` opcional; quando informado, de 1 a 999
4. `area` opcional, aparada, no máximo 50 caracteres; em branco vira `null` (#4)
5. Nasce ativa. Desativar e ativar são idempotentes (mudança repetida aceita em silêncio, como na 1.2)
6. Nunca é apagada (#6)
7. `label` único na propriedade ignorando maiúsculas: checado no caso de uso (mesmo padrão do `ModifierService` da 1.2, excluindo a própria mesa ao editar), garantido pelo índice

---

## 3. Assinaturas públicas

`DiningTableId` em `restaurant.domain`, no formato de `MenuItemId` (#9).

```java
static DiningTable create(UUID propertyId, String label, Integer seats, String area);
void redescribe(String label, Integer seats, String area);   // substitui os três (#7)
void deactivate();
void activate();

DiningTableId id(); UUID propertyId(); String label();
Optional<Integer> seats(); Optional<String> area(); boolean isActive();
```

`DiningTableRepository` no domínio, implementação JPA na infra.

---

## 4. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `DINING_TABLE_NOT_FOUND` | 404 | A mesa não existe |
| `DINING_TABLE_LABEL_ALREADY_USED` | 409 | Outra mesa da propriedade já usa a identificação, ignorando maiúsculas |
| `INVALID_DINING_TABLE_LABEL` | 422 | Identificação em branco ou acima de 20 caracteres |
| `INVALID_DINING_TABLE_SEATS` | 422 | Lugares informados fora de 1 a 999 |
| `INVALID_DINING_TABLE_AREA` | 422 | Área acima de 50 caracteres |

---

## 5. API

```
POST /api/restaurant/dining-tables                      ADMIN           {label, seats?, area?}  201
GET  /api/restaurant/dining-tables                      ADMIN, WAITER   só ativas
GET  /api/restaurant/dining-tables?includeInactive=true ADMIN           todas
GET  /api/restaurant/dining-tables/{diningTableId}      ADMIN, WAITER
PUT  /api/restaurant/dining-tables/{diningTableId}      ADMIN           {label, seats?, area?}
POST /api/restaurant/dining-tables/{diningTableId}/deactivate   ADMIN
POST /api/restaurant/dining-tables/{diningTableId}/activate     ADMIN
```

- Resposta: `{id, label, seats, area, isActive}`. `seats` e `area` saem `null` quando vazios
- `includeInactive=true` pedido por `WAITER` responde 403
- A lista é ordenada por área (sem área por último) e, dentro dela, pelo `label` em
  **ordem natural**: "Mesa 2" antes de "Mesa 10" (#8)
- Corpo sem bean validation para o que o domínio já recusa com código (mesmo
  critério da #21 da 1.2); o 422 sai do agregado

---

## 6. Testes

- **Unidade (TEST):** invariantes 2 a 5, um caso por regra e as bordas (20/21
  caracteres, 0/1/999/1000 lugares, área em branco). Mais a ordem natural da
  lista, se ela morar em código testável sem Spring
- **Integração (DEV):** um teste da fatia no `app`: cria, lista na ordem da #8,
  edita limpando `seats`, desativa, e confere que o `WAITER` lê a lista e recebe
  403 ao criar

---

## 7. `.http`

`http/32-restaurant-dining-tables.http`, no padrão do `31`. Caminho feliz
(cria "Mesa 2", "Mesa 10", "V1" na Varanda e uma sem área; lista; edita;
desativa; lista com e sem inativas) e um cenário negativo por código de erro,
mais 401 sem token e 403 do `WAITER` ao criar e ao pedir as inativas.
