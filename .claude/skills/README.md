# Skills do projeto

## Origem

As doze skills de design e animação vêm de
[`emilkowalski/skills`](https://github.com/emilkowalski/skills) (Emil Kowalski — Vercel,
Linear, autor do Sonner), no commit `e8a175d` de 2026-10-02. Licença em
`LICENSE-emilkowalski-skills`.

Copiadas para cá, e não instaladas pelo `npx skills add`, para ficarem versionadas junto com o
projeto: assim todo mundo e todo agente usa a mesma versão, e uma atualização upstream é um
diff revisável em vez de uma mudança silenciosa de comportamento.

**Não instaladas**, por não serem do nosso stack: `animate-expo` (React Native) e
`write-swift`.

A `frontend-design` não vem desse repositório — é a direção de design que o Ruan trouxe, com
uma seção final sobre este projeto.

## O que cada uma serve

| Skill | Para |
|---|---|
| `frontend-design` | **Obrigatória antes de desenhar tela.** Processo de duas passagens e os vícios de tela gerada por IA |
| `emil-design-eng` | Polimento de componente: sombra, borda, os detalhes invisíveis |
| `apple-design` | Gesto, arraste, movimento físico |
| `mobile-native` | Fazer a PWA parecer app no celular |
| `animate` | Construir animação na ordem das decisões que importam |
| `animation-vocabulary` | Nomear um efeito a partir da descrição vaga |
| `prototype` | Versões genuinamente diferentes da mesma tela, para comparar |
| `pick-ui-library` | Antes de escrever componente que já existe bem resolvido |
| `break-ui` | Alimentar a tela com o pior dado possível |
| `review-animations` · `improve-animations` · `find-animation-opportunities` | Revisar e auditar movimento |
| `ask-sonner` | Toast |

## Quem usa

Os agentes `web-designer` e `front-end`, em `.claude/agents/`. As tabelas de cada agente dizem
qual skill carregar em qual situação.

## Atualizar

```bash
git clone --depth 1 https://github.com/emilkowalski/skills.git /tmp/skills
# compare e copie o que mudou; o diff entra num PR, como qualquer mudança de convenção
```
