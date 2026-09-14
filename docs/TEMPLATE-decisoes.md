# Decisões — Task <código> <nome>

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Breno confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/<código>-<slug>` |
| Rodada atual | <n> |
| Build | <passa / falha> |
| Testes | <n> |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | <o que foi decidido> | implementado |
| 2 | 1 | <...> | pendente |
| 3 | 2 | <...> | **revertida pela #7** |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Breno **entra aqui** e só sai por decisão
explícita dele.

- [ ] <item>
- [x] <item concluído>

### Movido para outra task

Só com aprovação explícita, e dizendo para qual task e por quê.

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| <item> | 0.5b | <motivo> | Breno, rodada 3 |

---

## Contrato com o front

Tudo o que o front vai consumir. Depois do merge isso vira contrato e mudar
custa caro.

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

Coisas que sabemos que não estão perfeitas e decidimos aceitar. Registrar evita
que a próxima rodada de review levante de novo como se fosse novidade.

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| | | |

---

## Pontos em aberto

Aguardando decisão do Breno. Some daqui quando ele responder.

| # | Pergunta | Desde a rodada |
|---|---|---|
| | | |
