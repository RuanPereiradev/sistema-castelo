# Task 0.2 — `shared-kernel`

Dois documentos em um arquivo. A **Parte A** vai para o `@agent-java-dev`.
A **Parte B** vai para o `@agent-unit-tester`, em **sessão separada**, sem a
implementação.

---

# PARTE A — spec do `java-dev`

## Objetivo

Implementar os value objects e tipos base que sustentam todos os módulos de
domínio do sistema.

## Escopo

Apenas o módulo `shared-kernel`. Nenhuma entidade JPA, nenhum endpoint, nenhuma
migration.

> **Nota:** esta spec foi revisada após o relatório de ambiguidades do
> `unit-tester`. Treze pontos foram decididos e quatro conflitos entre spec e
> testes resolvidos. A mudança de maior impacto: `Money` **não tem mais campo
> `Currency`**, e `CurrencyMismatchException` deixou de existir.
>
> **Segunda revisão**, após a implementação: sétima exceção
> (`InvalidEntityIdException`), limites de `NUMERIC(12,2)` e `NUMERIC(5,4)`,
> formato de texto aceito em `Money` e `Percentage`, UUID v7 para id e v4 para
> token público, e `priceAt` rejeitando preço por quilo zero ou negativo.

**Restrição de arquitetura:** `shared-kernel` **não depende de Spring**. Nenhum
`import org.springframework`, nenhuma anotação de framework, nenhum JPA. Java
puro. Se algo parecer exigir Spring aqui, é sinal de que pertence a outro módulo.

Dependências permitidas: apenas JUnit e AssertJ, ambas em escopo `test`.

## Classes a implementar

### `Money`

O tipo mais importante do sistema. Todo valor monetário passa por ele.

- Interno: `BigDecimal` com **scale 2** e `RoundingMode.HALF_UP`
- **Sem campo `Currency`.** O sistema é um hotel único no Brasil, cobrando em
  reais. Um campo com um único valor possível obriga a criar factory, exceção e
  testes que existem só para se justificar. Se um dia houver multimoeda, é uma
  mudança contida
- Imutável e `final`. Sem setter, sem construtor público
- Toda operação retorna nova instância **já arredondada para 2 casas**

Criação:
```java
Money.of(BigDecimal)      Money.of(String)      Money.ofCents(long)
Money.ZERO
```

Operações: `plus`, `minus`, `multiply(int)`, `multiply(BigDecimal)`,
`percentage(Percentage)`, `negate`, `abs`

Consultas: `isZero()`, `isPositive()`, `isNegative()`,
`isGreaterThan(Money)`, `isLessThan(Money)`, `amount()`

Serialização: `asString()` devolve o decimal simples (`"180.00"`), que é o
formato que trafega na API. Formatação com símbolo e vírgula é responsabilidade
do front, nunca do backend.

**Construção é exata; operação arredonda.** Esta é a regra central:

- `Money.of("0.001")` **rejeita** com `MONEY_SCALE_EXCEEDED`. Quem escreve três
  casas cometeu um erro, e arredondar em silêncio esconde o bug
- `Money.of("10")` é igual a `Money.of("10.00")` — normaliza para scale 2
- `multiply` e `percentage` **arredondam**, porque 10% de 12,35 legitimamente
  produz 1,235

**Regras duras**
- Nenhum método aceita ou devolve `double` ou `float`
- `equals` e `hashCode` comparam o valor normalizado
- Arredondamento de negativo vai para longe do zero: `−0.005` vira `−0.01`
  (vale para estorno)
- `null` ou texto inválido lançam `InvalidMoneyException`, nunca
  `NullPointerException` ou `IllegalArgumentException`

**Formato do texto em `of(String)`**
- Espaço em volta é removido com `String.strip()` antes de validar:
  `" 10.00 "` vira `10.00`. Texto vazio ou só com espaços lança `INVALID_MONEY`
- Sinais combinados ou isolados (`"+-10"`, `"++10"`, `"+"`, `"-"`) lançam
  `INVALID_MONEY`
- Sinal positivo explícito é aceito: `"+10.00"` vira `10.00`
- Só decimal simples: `".5"` e `"5."` lançam `INVALID_MONEY`
- Notação científica é rejeitada: `"1E+3"` lança `INVALID_MONEY`

**Limite de `NUMERIC(12,2)`**
- Valor acima de `9999999999.99` ou abaixo de `-9999999999.99` lança
  `InvalidMoneyException` com `MONEY_OUT_OF_RANGE`
- O limite vale para **toda instância**, inclusive o resultado de `plus`,
  `minus`, `multiply`, `percentage`, `negate`, `abs`, `ofCents` e
  `Weight.priceAt`
- `multiply(BigDecimal)` rejeita fator de precisão absurda com `INVALID_MONEY`
  **antes** de calcular, em vez de travar no arredondamento: fator com
  `|scale| > 16` ou `precision > 32` (ex.: `1E-999999999`, `1E+999999999`).
  Os fatores legítimos têm escala 4 (`Percentage`) ou 3 (`Weight`)

`InvalidMoneyException` tem três códigos: `INVALID_MONEY`,
`MONEY_SCALE_EXCEEDED` e `MONEY_OUT_OF_RANGE`.

### `Percentage`

Representa taxa, como os 10% de serviço. Armazenado como fração
(`0.1000` = 10%), coerente com `NUMERIC(5,4)` no banco.

```java
Percentage.ofFraction(BigDecimal)   // 0.10
Percentage.ofPercent(int)           // 10
Percentage.ofPercent(BigDecimal)    // 10
Percentage.ofPercent(String)        // "12.5"
```
- `applyTo(Money)` devolve `Money` arredondado `HALF_UP`
- Percentual fracionário aceito (`12.5%`)
- Percentual **acima de 100% é aceito** — nenhuma regra de negócio proíbe, e
  rejeitar seria arbitrário
- Negativo rejeitado com `InvalidPercentageException`
- **Limite de `NUMERIC(5,4)`:** fração acima de `9.9999` (999,99%) é rejeitada
  com `InvalidPercentageException`, em todas as factories
- Em `ofPercent(String)`, espaço em volta é removido antes de validar
  (`" 12.5 "` é aceito), sinal positivo explícito é aceito (`"+12.5"`) e
  notação científica é rejeitada (`"1E+2"`). Texto vazio ou só com espaços
  lança `INVALID_PERCENTAGE`
- Imutável

### `Quantity`

Inteiro positivo. `of(int)`, `value()`, `plus(Quantity)`.

- Rejeita zero e negativo
- **Limite superior de 999.** Quantidade 10000 num item de comanda é digitação
  errada, não pedido. Barrar no value object é mais barato que barrar na tela

### `Weight`

Peso do self-service.

```java
Weight.ofGrams(int)                 Weight.ofKilos(BigDecimal)
```
- Interno em gramas (inteiro), coerente com `weight_grams INTEGER` no banco
- `priceAt(Money pricePerKilo)` devolve `Money` arredondado `HALF_UP`
- `priceAt` rejeita `pricePerKilo` zero ou negativo com `InvalidMoneyException`
  (`INVALID_MONEY`)
- Rejeita zero e negativo
- **Fração de grama é rejeitada.** `ofKilos("0.4375")` = 437,5g lança
  `InvalidWeightException`. A coluna é `weight_grams INTEGER`; aceitar meio
  grama criaria um valor que não volta do banco igual ao que entrou
- `grams()`, `kilos()`

### `DateRange`

Período de hospedagem.

- `LocalDate start` e `LocalDate end`
- `of(LocalDate, LocalDate)` rejeita `end` menor ou igual a `start`
- `nights()` devolve o número de noites
- `contains(LocalDate)`
- `overlaps(DateRange)`
- `dates()` devolve `Stream<LocalDate>`

**Semântica crítica:** o intervalo é `[start, end)` — fim **exclusivo**. Uma
estadia de 01/10 a 04/10 tem 3 noites (01, 02 e 03). O dia do check-out não é
uma diária. Errar isso significa cobrar uma noite a mais de todo hóspede.

### `Cpf`

- `of(String)` normaliza removendo apenas `.`, `-` e espaço em branco.
  Qualquer outro caractere é rejeitado
- Aceita pontuação parcial: `123.456.789 09` e `12345678909` produzem
  instâncias iguais
- Valida os dois dígitos verificadores
- Rejeita sequências de dígito repetido (`11111111111`, `00000000000`)
- `value()` devolve normalizado, `formatted()` devolve `123.456.789-09`
- `null` e string vazia lançam `InvalidCpfException`
- Imutável, `equals` por valor

### `EntityId`

IDs tipados, para que `TabId` não seja atribuível a `FolioId`. Use `record`.

```java
public record TabId(UUID value) implements EntityId { ... }
```

Fornecer a interface base e um mecanismo de criação (`newId()`,
`of(UUID)`, `of(String)`). Os IDs concretos de cada módulo são criados nas
tasks dos respectivos módulos, **não aqui** — aqui vai apenas a base.

Como um record sem corpo não herda método estático, a criação recebe o
construtor do record: `EntityId.newId(TabId::new)`.

**Validação.** `EntityId.requireValid(UUID)` é público e estático. Todo ID
concreto o chama no construtor compacto:

```java
public record TabId(UUID value) implements EntityId {
    public TabId { EntityId.requireValid(value); }
}
```

UUID nulo, texto malformado ou fora da forma canônica lançam
`InvalidEntityIdException` (`INVALID_ENTITY_ID`). Nenhuma
`IllegalArgumentException` no módulo.

**Geração.**
- `newId()` gera **UUID v7** (RFC 9562): 48 bits de timestamp em milissegundos,
  4 bits de versão (`0111`), 2 bits de variante, resto aleatório com
  `SecureRandom`. Mantém a localidade do índice B-tree no insert. A ordem é
  garantida entre milissegundos distintos; dentro do mesmo milissegundo, não.
- `newPublicToken()` gera **UUID v4** com `UUID.randomUUID()`. O `public_token`
  é exposto no QR code; v7 revelaria o horário de criação e seria ordenável,
  facilitando adivinhar tokens vizinhos. Para chave pública, aleatoriedade vale
  mais que índice.

O teste de tipagem declara dois records descartáveis **no próprio arquivo de
teste** (`record FooId(UUID value) implements EntityId {}`), só para exercitar a
base. Nenhum `TabId` ou `FolioId` em `src/main` deste módulo.

### `DomainEvent`

Interface com `Instant occurredAt()`.

### `DomainException`

Classe base abstrata de todas as exceções de domínio.

- `String code()` — código estável em `UPPER_SNAKE_CASE`
- Estende `RuntimeException`
- Subclasses concretas aqui, sete ao todo: `InvalidMoneyException`,
  `InvalidCpfException`, `InvalidDateRangeException`, `InvalidQuantityException`,
  `InvalidWeightException`, `InvalidPercentageException`,
  `InvalidEntityIdException`
- O `code()` é uma **constante explícita** em cada classe, nunca derivada do
  nome. Código derivado muda sozinho num rename e quebra o front sem ninguém
  notar. Convenção: nome da classe sem o sufixo, em `UPPER_SNAKE_CASE`
  (`InvalidCpfException` → `INVALID_CPF`)

O código é o que vira resposta HTTP, `assert` do arquivo `.http` e mensagem
traduzida no front. Uma exceção sem código não serve para nenhuma das três.

## Fora do escopo

Entidades, repositórios, Spring, JPA, controllers, migrations, IDs concretos de
módulos específicos.

## Critérios de aceite

- `./mvnw clean install -pl shared-kernel` passa
- Nenhum `import org.springframework` no módulo
- Nenhum `double` ou `float` em assinatura pública
- Nenhuma classe com setter público
- Todas as classes `final` e imutáveis
- Testes escritos pelo `@agent-unit-tester` passam

## Ao terminar

Reporte os arquivos criados, as decisões tomadas fora da spec, e as ambiguidades
encontradas. **Não escreva testes** — outro agente faz isso a partir da spec.

---

# PARTE B — spec do `unit-tester`

> Sessão separada. Não leia a implementação. Escreva os testes a partir das
> regras abaixo e das assinaturas públicas.

## Invariantes a cobrir

### `Money`

**Arredondamento** (a área mais crítica do sistema)

Estes três casos **diferenciam `HALF_UP` de `HALF_EVEN`** e por isso são os que
importam. O exemplo de `12.35` dá `1.24` nos dois modos e não prova nada:

| Operação | Resultado exato | `HALF_UP` (correto) | `HALF_EVEN` (errado) |
|---|---|---|---|
| 10% de `12.25` | `1.225` | `1.23` | `1.22` |
| `Money.of("0.01").percentage(50%)` | `0.005` | `0.01` | `0.00` |
| `10.05` × `0.5` | `5.025` | `5.03` | `5.02` |

Mais:
- `−0.005` arredonda para `−0.01` (para longe do zero)
- `multiply` por decimal arredonda a 2 casas
- Uma cadeia de operações não acumula erro de precisão

**Imutabilidade**
- `plus` devolve nova instância e a original permanece intacta
- Vale para `minus`, `multiply`, `percentage`, `negate`, `abs`

**Construção**
- `of("10")` é igual a `of("10.00")`
- `of("0.001")` lança `InvalidMoneyException` com code `MONEY_SCALE_EXCEEDED`
- `of(null)` e `of("abc")` lançam `InvalidMoneyException`, não `NullPointerException`
- `of("0.010")` é aceito e é igual a `of("0.01")`
- `of(" 10.00 ")` e `of("+10.00")` são iguais a `of("10.00")`
- `of(".5")`, `of("5.")` e `of("1E+3")` lançam `INVALID_MONEY`
- `of("9999999999.99")` é aceito; `of("10000000000.00")` lança `MONEY_OUT_OF_RANGE`
- `multiply` com fator de precisão absurda (`1E-999999999`) lança `INVALID_MONEY`

**Borda**
- `Money.ZERO.isZero()` é verdadeiro
- Valor negativo é permitido (necessário para desconto e estorno)

### `Percentage`
- `ofPercent(10)` e `ofFraction(0.10)` são iguais
- `ofPercent("12.5")` é aceito
- `ofPercent(150)` é aceito
- `applyTo(Money)` arredonda `HALF_UP`
- Percentual negativo lança `InvalidPercentageException`
- `ofPercent(0)` aplicado a qualquer valor devolve zero
- `ofPercent("999.99")` é aceito; `ofPercent("1000")` lança `INVALID_PERCENTAGE`
- `ofPercent("1E+2")` lança `INVALID_PERCENTAGE`
- `ofPercent(" 12.5 ")` é igual a `ofPercent("12.5")`

### `Quantity`
- Zero é rejeitado
- Negativo é rejeitado
- `of(1)` e `of(999)` são válidos
- `of(1000)` é rejeitado

### `Weight`
- `437g` a `R$ 89,90/kg` resulta em `R$ 39,29`
- `1000g` a `R$ 50,00/kg` resulta em `R$ 50,00`
- `1g` a `R$ 89,90/kg` arredonda corretamente
- Zero e negativo são rejeitados
- `ofKilos("0.437")` e `ofGrams(437)` são iguais
- `ofKilos("0.4375")` lança `InvalidWeightException` (meio grama)
- `priceAt` com preço por quilo zero ou negativo lança `INVALID_MONEY`

### `DateRange`
- **01/10 a 04/10 tem exatamente 3 noites**
- `end` igual a `start` é rejeitado
- `end` anterior a `start` é rejeitado
- `dates()` devolve 01, 02 e 03 — **não** inclui 04
- `contains(01/10)` verdadeiro; `contains(04/10)` **falso**
- `overlaps` verdadeiro quando há sobreposição parcial
- `overlaps` **falso** quando um termina exatamente onde o outro começa
- `overlaps` verdadeiro quando um contém o outro inteiro
- Período de uma única noite funciona
- Período cruzando virada de ano funciona

### `Cpf`
- `12345678909` é aceito (CPF válido de referência)
- Último dígito verificador errado é rejeitado
- Primeiro dígito verificador errado é rejeitado
- `11111111111` é rejeitado
- `00000000000` é rejeitado
- `123.456.789-09`, `123.456.789 09` e `12345678909` produzem instâncias iguais
- Menos de 11 dígitos é rejeitado
- Mais de 11 dígitos é rejeitado
- Letras e outros caracteres são rejeitados
- `null` e string vazia são rejeitados
- `formatted()` devolve `123.456.789-09`

### IDs tipados
- Use um record descartável declarado no próprio arquivo de teste, com
  construtor compacto chamando `EntityId.requireValid`
- `of(String)` rejeita UUID malformado com `INVALID_ENTITY_ID`
- `requireValid(null)` e `new FooId(null)` lançam `INVALID_ENTITY_ID`
- `newId()` gera valores distintos, com versão 7 e variante RFC 9562
- `newId()` gera valores crescentes entre milissegundos distintos (comparação
  unsigned, não `UUID.compareTo`)
- `newPublicToken()` gera UUID versão 4
- Não teste igualdade entre IDs de tipos diferentes: isso exercita o `equals`
  gerado pelo compilador, não código nosso
- A incompatibilidade de atribuição entre tipos é garantida pelo compilador e
  não é testável em runtime — não tente

### `DomainException`
- Cada uma das sete subclasses expõe um `code()` não nulo e estável
- O código está em `UPPER_SNAKE_CASE`

## Regras

- JUnit 5 e AssertJ
- Nome descreve a regra: `shouldRejectDateRangeWhenEndEqualsStart()`
- Um comportamento por teste
- Sem mock — aqui é tudo objeto puro
- Sem teste que apenas exercita getter

## Ao terminar

Reporte os testes por invariante e, principalmente, a seção **AMBIGUIDADES**:
casos em que esta spec não diz qual é o comportamento esperado. Liste em vez de
assumir. Essa seção costuma ser a parte mais valiosa do trabalho.
