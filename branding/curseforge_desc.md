# SNC Adventures Mod

**O mod da esquina.** Um mercado que nasce sozinho no mundo, um velhinho chamado Gago que vende de tudo, economia em real, plantação pra cuidar, bebida pra tomar — e duas armas com mecânica de verdade pra resolver treta.

---

## 🏪 O Mercado do Esquinão

- **Estrutura gerada naturalmente no mundo**: prédio, pátio e estacionamento pavimentado com vagas demarcadas, faixa de pedestre e hidrante — que dispara um jato d'água cômico se você usar a mão nele
- **Postes de luz de sódio** que acendem sozinhos às 19h e apagam às 5h
- **Letreiro de LED de 9 blocos** com fonte 5×7 codada do zero (nada de fonte do Minecraft): mostra **ABERTO/FECHADO** conforme o horário (07h–00h), zumbido de LED quando você chega perto e blip por linha ao terminar de ler
- **3 peles regionais**, no padrão das vilas do vanilla: **Clássico** (planície/floresta), **Sertão** (deserto/badlands) e **Serra** (taiga/neve/montanha) — o mercado nasce com a cara do bioma que ocupa

## 👴 O Gago

O dono da esquina. Atende na fila, fala com **voz de blip própria**, te zoa se chegar bêbado no balcão, reclama (com a voz dele) se alguém fuma perto da loja — e **cochila no balcão da meia-noite às 7h**, com Zzz e frase embolada de quem foi acordado. Freguês de fidelidade máxima (**Dono da Esquina**) tem saudação VIP exclusiva.

## 💰 Economia em real

- Dinheiro itemizado (R$) + comandos `/darreal` e `/pagar`, com teto de saldo à prova de overflow
- Caixa registradora sonora, nota de R$ voando na venda (partícula própria)
- **Programa de fidelidade** com tiers e desconto no balcão
- Bêbado paga **markup** — o Gago não é bobo
- Cotação da rua pro mercado negro; tudo calibrável em `config/intoxicantes.json`

## 🍺 16 bebidas & embriaguez completa

- Bebidas com texturas procedurais e som de gole (com refluxo grave)
- Medidor de embriaguez por dose: **fala "fonada" embaralhada no chat** (todo mundo lê), *hic!* audível e dentro da fala, cambaleio mecânico
- **Ressaca no dia seguinte** — e o remédio é o de sempre: cabelo do cachorro

## 🌱 Plantações & indoor

- **5 culturas** com semente, crescimento por estágio e colheita: maconha, lúpulo, uva, café e papoula
- **Lâmpada UV** pra plantação indoor (investimento com estoque limitado na loja) e estado **ligada/desligada por redstone** — alguém corta a energia, a fazenda para

## 🔫 Armas de mecânica de verdade

**Escopeta 12** — tubo interno + câmara como estado do item, recarga shell-by-shell segurando o botão direito (interrompível), pump-action automático, **recoil com kick de câmera**, ADS no SHIFT (zoom, dispersão pela metade, alcance maior) e HUD do mecanismo sob a mira.

**Revólver .38** — tambor de 6 de verdade (o tiro consome a câmara alinhada e o tambor **gira**), **giro no seco** estilo filme quando a câmara tá vazia, recarga buraco a buraco, kick e HUD próprio do tambor.

- Cartuchos itemizados com receita; esqueleto dropa .38; reparo na bigorna
- Todo o balanceamento no config

## 🔊 Produção própria

Sons sintetizados pro mod (estouro da 12, voz do Gago, caixa registradora, hic, glup, zumbido da lâmpada), partículas custom e **38 game tests** garantindo que nada quebre o save.

## 🚀 Como começar

1. Explore até achar o letreiro do Esquinão brilhando na esquina
2. Sem dinheiro? A rua compra o que você plantou — a cotação muda
3. Compre no balcão, cultive no quintal (ou indoor, com UV), beba com moderação (ou não)
4. Suba o tier de fidelidade e vire **Dono da Esquina**

## 📋 Requisitos & FAQ

- **Minecraft 26.3** • **Fabric Loader** • **Fabric API**
- Funciona em **client e servidor** (os dois lados precisam do mod)
- Config em `config/intoxicantes.json` (armas, UV, embriaguez, cotação)
- Mundos antigos: o Zelador do mod conserta estruturas de versões anteriores sozinho (~30s após o chunk carregar)

---

*SNC Adventures Mod — porque toda cidade tem uma esquina.*
