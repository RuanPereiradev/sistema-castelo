# Brief para o agente de design — telas da recepção

> Cole este arquivo inteiro como prompt. Ele **substitui** a instrução do
> `docs/brief-front-end.md` que dizia para não desenhar a recepção.

---

## Leia isto primeiro, porque muda como você trabalha

**O backend do hotel não existe.** Não há `Room`, `RoomType`, `RatePlan`, reserva, check-in nem
check-out em código. Você **não** está desenhando contra uma API que roda.

O que existe é o **contrato já desenhado**: as tabelas estão modeladas em
`docs/schema-banco-de-dados.md` (seções 8 e 10) e as regras de negócio estão fechadas em
`docs/plano-tecnico.md` (seção 2.1). Então o desenho aqui vem **antes** da API — e vai
informá-la. Se a tela precisar de um dado que o schema não tem, isso não é erro seu: é achado,
e você deve dizer qual é em vez de inventar o campo.

**Uma tentativa anterior de desenho (salão e comanda) foi reprovada pelo Ruan**, e ele não
disse o que não gostou. Então: **entregue o plano e pare.** Paleta, tipografia, conceito de
layout em wireframe. Não construa nada antes de ele aprovar a direção. Se puder, ofereça duas
direções visuais diferentes em vez de uma.

Carregue a skill `frontend-design` antes de decidir qualquer coisa de cor, tipo ou layout.

---

## Quem usa, e como

Recepcionista de um hotel pequeno. **Desktop, sentada, com mouse e teclado** — nada de
mobile-first aqui, ao contrário do garçom.

Mas o ritmo tem dois modos, e o desenho precisa dos dois:

| Modo | Quando | O que importa |
|---|---|---|
| **Sem pressa** | manhã, planejando o dia; cadastro; conferência | densidade, informação completa, poder comparar |
| **Com pressa** | **hóspede em pé na frente dela** no check-in e no check-out | achar a reserva em segundos, poucos campos, nada de rolagem |

O check-in é o momento crítico: alguém esperando, mala no chão, e ela precisa achar a reserva,
escolher o quarto e entregar a chave.

Interface **em português**, com os termos do glossário do `CLAUDE.md`: "reserva", "hóspede",
"quarto", "tarifa", "diária", "localizador", "sinal", "folio". Nunca "booking", nunca "guest".

---

## Tela 1 — Painel do dia

A primeira coisa que ela abre, às 7h. O schema já tem três índices parciais feitos
exatamente para esta tela, o que confirma que era prevista.

| Bloco | O que mostra |
|---|---|
| **Chegadas de hoje** | reservas `CONFIRMED` com entrada hoje |
| **Saídas de hoje** | reservas `CHECKED_IN` com saída hoje |
| **Na casa** | todas as `CHECKED_IN` |
| **Pré-reservas expirando** | `PENDING` com prazo perto de vencer |
| **Quartos livres** | quartos `AVAILABLE` |

Por reserva: localizador, nome do hóspede principal, tipo de quarto, adultos e crianças,
número de noites, e **se o sinal foi pago**. A reserva que chegou sem sinal é a que ela precisa
resolver antes de entregar a chave — isso é hierarquia, não um ícone no canto.

## Tela 2 — Mapa de quartos

Status é só três: `AVAILABLE` · `OCCUPIED` · `MAINTENANCE`. **Não há etapa de limpeza no
sistema** — decisão explícita do plano: housekeeping é no boca a boca e a recepção troca o
status na mão.

Por quarto: número, andar, tipo, status, e se ocupado, quem está dentro e até quando. A única
ação que ela faz direto aqui é **marcar e desmarcar manutenção**.

No check-out o quarto volta a `AVAILABLE` na hora, sem passo intermediário.

## Tela 3 — Disponibilidade e reserva

**A busca é por tipo de quarto, nunca por quarto específico.** O quarto físico só é atribuído
no check-in. Isso muda o desenho: não há planta nem seleção de unidade — a tela mostra
*quantos restam de cada tipo* no período.

A busca manda: entrada, saída, adultos, e **as idades das crianças** — não a quantidade.
Criança não paga até 5 anos e paga meia de 6 a 11, por configuração, então idade é dado de
preço.

A resposta traz, por tipo: quantos disponíveis em **todas** as noites do período, e o **valor
total da estadia já calculado dia a dia** — a diária varia por período (alta e baixa temporada)
e por ocupação (individual, casal, pessoa adicional).

**Uma armadilha para desenhar, não para ignorar:** o hóspede é único por documento (CPF ou
passaporte) na propriedade. Se ela digitar o CPF de quem já se hospedou, o sistema **não pode**
criar hóspede duplicado nem cuspir erro de unicidade. Precisa de busca por documento antes, e
a tela precisa dizer "este hóspede já esteve aqui" e oferecer reaproveitar o cadastro.

Campos do hóspede que o schema prevê: nome e documento **obrigatórios**; e-mail, telefone,
nascimento, nacionalidade e endereço completo opcionais. Não peça tudo como se fosse
obrigatório — ela está com fila.

## Tela 4 — Check-in

Onde a reserva sai de "tipo de quarto" para **quarto físico**.

Precisa: achar a reserva por **localizador**, por nome, ou pela lista de chegadas; escolher o
quarto entre os disponíveis **daquele tipo**; completar o que faltou da ficha; e ver quanto já
foi pago de sinal e quanto falta.

O banco **garante** que não existe hóspede em check-in sem quarto atribuído, então a tela não
pode deixar avançar sem escolher o quarto.

Um detalhe do schema que muda a tela: **o folio da reserva nasce com a reserva**, não no
check-in — foi feito para o sinal ter onde cair antes de o hóspede chegar. Então a conta já
existe quando ela faz o check-in, e não é "aberta" aqui.

## Tela 5 — A conta durante a estadia

**Esta parte já existe no backend** (task 1.3) e o perfil `FRONT_DESK` já alcança as rotas. Veja
`http/40-billing-folios.http` para o contrato real.

Mostra lançamentos (diária, consumo do restaurante, ajuste), pagamentos e **saldo**. A recepção
recebe pagamento; o `ADMIN` estorna e dá desconto.

O que a tela tem de deixar claro: **estorno aparece como linha nova**, nunca como remoção. E
não há limite de crédito — o hóspede diz o número do quarto no restaurante e o consumo cai aqui.

## Tela 6 — Check-out

Junta diárias e consumo, fecha o folio e libera o quarto. **O folio recusa fechar com saldo
diferente de zero** — essa regra já existe e está testada. Então, na prática, esta é uma tela
de pagamento: mostra o total, recebe, e só então fecha.

Dinheiro exige turno de caixa aberto, e **o caixa já existe** (task 2.4, `FRONT_DESK`). Veja
`http/41-billing-cash-sessions.http`.

Late check-out cobra meia diária até as 18h, por configuração.

## Tela 7 — Cancelamento e no-show

Cancelamento grátis até 48h antes; depois retém o sinal. No-show é quem não apareceu até o
horário limite. Os dois são mudança de status com motivo e instante registrados.

---

## O que a recepção já pode operar hoje

Se você quiser desenhar algo que dá para construir **esta semana**, é isto — e só isto:

| Já pronto no backend | Task | Contrato em |
|---|---|---|
| Turno de caixa: abrir, sangria, suprimento, fechamento cego | 2.4 | `http/41` |
| Folio: ver conta, receber pagamento, estornar | 1.3 | `http/40` |
| Mover comanda de mesa, transferir e juntar | 3.6 | `http/36` |

---

## Decisões de negócio que ninguém tomou — **não invente**

Se o desenho encostar em alguma destas, **pare e pergunte ao Ruan**:

1. **Hóspede sem reserva (walk-in).** O plano não menciona, e a recepção faz isso todo dia:
   criar reserva e fazer check-in num passo. Existe no v1?
2. **A busca segura inventário enquanto ela digita?** O prazo de 30 minutos da pré-reserva foi
   desenhado para o portal público pagando sinal. No balcão, consultar e não reservar seguraria
   quarto à toa.
3. **Como ela pede overbooking.** O plano diz que o `ADMIN` força e o sistema registra quem
   autorizou. Mas qual é o fluxo: ela tenta, recebe a recusa e chama o gerente? Ele libera de onde?
4. **A diária é lançada quando?** Noite a noite ou uma vez no check-in pelo total?
5. **Late check-out é automático ou ela decide?**

---

## O que entregar

**Só o plano, nesta rodada.** Paleta em 4–6 hex nomeados, tipografia e papéis, conceito de
layout em wireframe ASCII, princípios. Mais a revisão da passagem dois, dizendo o que você
trocou e por quê. **Não escreva código até o Ruan aprovar a direção.**

Comece pelo **painel do dia** e pelo **check-in** — são a tela que ela mais abre e o momento em
que o desenho mais custa se estiver errado.

Diga também: quais dados a tela precisaria e o schema não tem, e qual das cinco decisões acima
atrapalhou o desenho.
