# Hospedaria — telas aprovadas (direção "Portal")

Sistema de restaurante com estética medieval: vinho predominante, salmão como iluminura.
Base visual: design system Classical (`_ds/`), cores sobrescritas por variáveis CSS inline.

## Arquivos
- `ABRIR-AQUI_Mapa-de-Mesas-Apresentacao.dc.html` — apresentação: celular (garçom) e desktop (caixa).
- `MapaMesasPortal.dc.html` — tela do mapa de mesas. Prop `device`: `phone` | `desktop`.
- `LoginBrasao.dc.html` — tela de login, direção Portal. Usuários: admin, garcom, recepcao, cozinha, gerente — senha 1234 (mock).
- `support.js` + `_ds/` — runtime e estilos necessários; manter na mesma pasta.

## Paleta
- Vinho Lacre #3D0F1A (fundo) · Vinho #4A1522 (superfície) · Vinho Tinto #6B1E2E
- Salmão #E8957F (filetes, destaques) · Salmão Velado #F6D9CE · Pergaminho #F4ECE3 (texto)
- Tipos: Cormorant Garamond (títulos/rótulos caixa-alta espaçada) + Lora (texto)

## Estados da mesa
- Livre: contorno tracejado salmão
- Reservada: filete duplo salmão
- Ocupada: contorno salmão, fundo #4A1522
- Conta pedida: fundo pergaminho
- Prato pronto: fundo salmão cheio

## Regras de design
- Ornamentos: filete duplo, arco românico, escudo com inicial, hedera ❧.
- Animações mínimas: mensagens/sheet aparecem em ~260–360 ms; hover tinge botões.
- Alvos de toque ≥ 44 px. Dados de mesas, pratos e preços são fictícios.
