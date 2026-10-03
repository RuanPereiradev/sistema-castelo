# Front-end — convenções

React 18 · TypeScript · Vite · React Query · Zustand · React Router · PWA.

Este arquivo carrega quando se trabalha em `frontend/`. O **`CLAUDE.md` da raiz continua
valendo** e é a fonte da verdade de glossário, dinheiro, contrato de erro e perfis — aqui
não se duplica nenhuma dessas regras, só se diz o que o front faz com elas.

---

## A regra de idioma, que aqui tem uma consequência especial

**Código em inglês. Interface em português.** Vale igual ao backend, mas o front é o
**único lugar do sistema onde string em português existe**: o backend não tem nenhuma.

Daí duas obrigações que são só do front:

1. **Arquivo de tradução.** Nenhuma frase em português fica solta em componente. Texto de
   tela mora no arquivo de tradução, e o componente referencia a chave.
2. **Mapa de código de erro → frase.** A API responde RFC 7807 com um campo `code` estável
   (`TAB_NOT_OPEN`, `FOLIO_BALANCE_NOT_ZERO`). O front traduz:

```ts
"TAB_ALREADY_CLOSED": "Esta comanda já foi fechada"
```

Há mais de cem códigos no sistema. **Código sem tradução não pode virar tela em branco nem
o código cru:** mostre uma frase genérica honesta e registre a falta de forma visível, para
a lacuna aparecer em desenvolvimento em vez de na mão do garçom.

## O glossário da raiz vale na tela

O `CLAUDE.md` da raiz tem a linguagem ubíqua. No código é o termo em inglês (`Tab`,
`TabItem`, `DiningTable`); na tela é o termo em português do glossário — "comanda", "item da
comanda", "mesa". **Não invente sinônimo**: se a tela chamar de "pedido" o que o sistema
chama de comanda, o operador e o suporte param de falar a mesma língua. Conceito que não
está no glossário: **pergunte antes de nomear**.

## Dinheiro

Valor monetário **chega e trafega como string decimal** (`"180.00"`). Nunca `Number`:
`0.1 + 0.2` não é `0.3`, e isso é a conta do cliente.

**O front não calcula total, subtotal, taxa nem rateio.** O backend manda tudo calculado,
inclusive a divisão por grupo e a divisão igual. Se uma tela precisa de um número que a API
não devolve, isso é lacuna de API — peça o campo, não recalcule no cliente.

Para formatar, converta na borda da exibição apenas. Para comparar ou somar (raro), use
decimal de precisão arbitrária, nunca ponto flutuante.

## Estado

- **React Query** para tudo que vem do servidor. O backend é a verdade; não espelhe resposta
  em store local.
- **Zustand** só para estado que não existe no servidor: filtro de tela, rascunho de
  formulário, qual aba está aberta.
- Depois de uma ação que muda dinheiro ou status, **releia do servidor** em vez de aplicar o
  resultado otimista por conta própria. Duas pessoas operam a mesma comanda ao mesmo tempo.

## Conflito de operação simultânea é cena comum, não exceção

Dois garçons na mesma mesa, a cozinha avançando um item que o garçom está transferindo, o
caixa fechando o que o outro está lançando. O backend resolve com trava e responde o conflito
com código próprio. **A tela precisa de desenho para isso**, não de `try/catch`: "esta
comanda acabou de ser fechada" é informação para o operador, não erro de sistema.

## Autenticação

O token de acesso expira em 15 minutos e há refresh. **O renova é silencioso: o garçom não
pode ser deslogado no meio de um pedido.** Falha de refresh leva ao login preservando o que
dava para preservar.

Os quatro perfis (`ADMIN`, `FRONT_DESK`, `WAITER`, `KITCHEN`) decidem a navegação: a tela
mostra só o que o perfil alcança. Não esconda por CSS o que o backend recusa — e não confie
no front para autorizar: ele esconde, o backend é quem barra.

## Tempo real (KDS)

STOMP sobre WebSocket em `/ws/kitchen`, tópicos `/topic/kitchen/{SETOR}` e
`/topic/restaurant/ready-items`. Regras que não são escolha de implementação:

- **Sem polling** nessas telas.
- Mensagens do mesmo item podem chegar fora de ordem: vale a de `updatedAt` maior.
- **Ao reconectar, recarregue a fila inteira** pelo `GET`: pode ter perdido mensagem.
- Tempo de espera se compara com o `serverTime` que a API devolve, **nunca com o relógio do
  navegador** — a tela da cozinha fica ligada por dias e o relógio do aparelho derrapa.

## Idempotência de pagamento

`POST` de pagamento exige o cabeçalho `Idempotency-Key`. A chave é gerada **uma vez por
tentativa do operador** e **reusada no retry**. Gerar chave nova no retry cobra o cliente
duas vezes.

## Toque

O garçom opera de pé, com uma mão, tela possivelmente suja. Alvo de toque grande, e
**confirmação explícita em toda ação irreversível** — cancelar item, juntar comandas (não há
desfazer), fechar comanda.

A tela da cozinha é lida a dois metros, em monitor, e muitas vezes ninguém toca nela.

---

## A decidir na task 4.1, antes da primeira linha de componente

Isto **não está decidido** e não deve ser escolhido no meio de uma task:

- Estrutura de pastas dentro de `frontend/`
- Estilo: CSS Modules, Tailwind, ou outro
- Biblioteca de tradução, ou mapa próprio
- Ferramenta de teste (unidade e ponta a ponta) e o que vale testar no front
- Scripts do `package.json` e os comandos que entram no `CLAUDE.md` da raiz
- **Como o front é servido em produção** — o `docs/plano-tecnico.md` §10 diz Nginx com proxy
  reverso da API; a alternativa é o Spring servir os estáticos. Isso decide se os builds se
  acoplam, e ainda está em aberto.

## Build independente do backend

O Maven **não** constrói o front, e o npm não constrói o backend. Plugar o front no ciclo do
Maven faria todo build de backend rodar `npm install`, e o CI já leva quatro minutos.

Por isso o CI precisa de **filtro de caminho**: o job do backend roda quando muda
`*/src/**` ou `pom.xml`; o job do front, quando muda `frontend/**`. Mudança só de front passa
a levar segundos.

## Entregáveis de uma task de front

Vale a regra da raiz: a task mantém `docs/decisions/task-<código>.md`, criado **antes da
primeira linha de código** e atualizado na mesma resposta em que uma decisão é confirmada.

E o que é próprio do front: **estado vazio, estado de erro e estado carregando** não são
enfeite — toda tela que lê do servidor precisa dos três, e a revisão vai pedir.

## Se a especificação estiver ambígua, pare e pergunte

Igual ao backend. Regra de negócio não se inventa na tela: as decisões estão em
`docs/decisions/`, e o contrato executável da API está em `http/`.
