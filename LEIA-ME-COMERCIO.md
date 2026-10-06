# Intoxicantes 1.2.6 — Comércio do Esquinão

Minecraft 26.3 · Fabric Loader 0.19.5 · Java 25 · Fabric API.

## Como testar

1. Feche e abra novamente o Minecraft pela instância `mod cet` depois da instalação.
2. Clique com o botão direito no Gago. A tela tem **Comprar** e **Vender ingredientes**.
3. Leve os ingredientes das bebidas no inventário. O tamanho do lote aparece na tela: 8 unidades, ou 4 frascos de mel. O pagamento entra no saldo virtual, consultável com `/saldo`.
4. Use esse saldo na aba Comprar. O Gago também vende sementes e lâmpadas UV para reinvestir na fazenda.
5. Dinheiro em item pode ser depositado com `/depositar`. Para negociar com o Traficante, retire dinheiro com `/sacar <quantia>`.
6. Cada Gago compra até 12 lotes de cada ingrediente por dia. As quantidades restantes aparecem na tela.
7. Às **07:00 do jogo**, os estoques são repostos. Feche o atendimento e abra novamente se a virada de horário ocorrer com a tela aberta.

Não é necessário gerar outro mundo: Gagos e Traficantes existentes recebem o novo comércio. O estoque de cada NPC é compartilhado entre seus clientes e salvo com a entidade. Reabrir o mundo ou voltar no relógio não renova a cota do mesmo dia.

Para acelerar o teste de reposição com comandos habilitados, use `/time add 24000`, feche a tela e abra outra vez. `/time set day` pode voltar no relógio e não é uma forma confiável de avançar para a próxima reposição.

## Ingredientes comprados pelo Gago

| Lote entregue | Pagamento | Cota diária por Gago |
|---|---:|---:|
| 8 lúpulos frescos | R$ 6 | 12 lotes |
| 8 uvas | R$ 6 | 12 lotes |
| 8 trigos | R$ 4 | 12 lotes |
| 8 canas-de-açúcar do mod | R$ 4 | 12 lotes |
| 4 frascos de mel | R$ 8 | 12 lotes |
| 8 garrafas vazias | R$ 4 | 12 lotes |

As vendas de ingredientes não contam como compras no cartão fidelidade. Os descontos e os exclusivos da tela criada no outro app foram preservados; os exclusivos também têm estoque diário.

O Gago compra os ingredientes usados nas receitas de cerveja, vinho, hidromel, cachaça e rum. Café, folhas e ópio não fazem parte dessa lista. Para preparar a cana do mod, coloque cana comum na bancada: cada unidade gera duas. Ao vender mel, você entrega o frasco junto.

Passe o cursor sobre uma oferta para ver sua finalidade, o pagamento por lote e o motivo de um botão estar indisponível. Use a roda ou arraste a barra para ver todos os itens. A tecla configurada para abrir o inventário e Esc fecham o menu.

## Escolha entre vendedores

O Gago mantém um catálogo previsível. O Traficante sorteia três produtos diferentes por dia, com apenas quatro unidades de cada. Os preços da rua variam diariamente e ficam abaixo do preço cheio do Gago nos produtos em comum. Os descontos de fidelidade podem mudar qual vendedor compensa mais.

| Produto | Gago (sem desconto) | Referência do Traficante (varia por dia) |
|---|---:|---:|
| Baseado | R$ 50 | R$ 34 |
| Pó Branco | R$ 60 | R$ 42 |
| LSD | R$ 64 | R$ 48 |
| Extrato de cafeína | R$ 30 | R$ 20 |

O Traficante também pode oferecer Heroína, folhas e Ópio, com referências de R$ 44, R$ 12 e R$ 14. Na configuração padrão, a cotação varia de -30% a +40%, limitada abaixo do preço cheio do Gago quando ambos vendem o produto. Nem todos os itens estarão disponíveis no mesmo dia.

## Correção necessária para a economia

As tabelas antigas das cinco plantações usavam campos que o Minecraft 26.3 ignora. Foram atualizadas para o formato do jogo: plantas novas devolvem apenas a semente; plantas crescidas dão produto; a maturação UV rende o bônus. A semente plantada sempre retorna para permitir replantio. A planta crescida continua recebendo atualizações até terminar a maturação UV.

## Desenvolvimento e verificações

- Compilar e executar testes de servidor: `gradlew.bat build` com `JAVA_HOME` apontando para JDK 25.
- Testar tela, pacotes, compra e venda no cliente real: `gradlew.bat runClientGameTest`.
- Testes ficam em `src/gametest` e não são incluídos no JAR do jogador.
- Log consolidado da entrega: `build/ingredients-test.log`.
- Referência consultada: https://docs.fabricmc.net/develop/automatic-testing ; assinaturas e formatos conferidos contra o Minecraft 26.3 instalado.

O trabalho está limitado ao comércio e às correções necessárias para esse fluxo. As outras melhorias sugeridas ficam para as próximas etapas.
