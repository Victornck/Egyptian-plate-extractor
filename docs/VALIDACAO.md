# Validação executada (versão 1.1.0)

Execução em 17/11/2026, em Linux x64, `javac` 21 com `--release 17`, OpenCV 4.9.0 (OpenPnP). O Maven Central não estava acessível no ambiente de verificação; por isso a compilação foi feita com `javac` sobre as mesmas dependências declaradas no `pom.xml`, e os testes JUnit Jupiter 5.11.4 foram executados acionando o motor do JUnit diretamente. O JAR foi montado com o mesmo conteúdo que o Maven Shade gera (classes do projeto, OpenCV e bibliotecas nativas Windows x64 e Linux x64). **Windows 11 e IntelliJ não foram executados neste ambiente.**

## Testes de software

**93 testes JUnit, zero falhas, zero erros, zero ignorados.** Log por teste em [testes.log](testes.log).

| Classe | Casos | Conteúdo |
|---|---:|---|
| `ProcessamentoTest` | 31 | Conversão de pixels, formatos JPG/JPEG/PNG, caminhos com espaços e acentos, arquivo inexistente, formato indevido, arquivo disfarçado, corrupção, truncamento, CRC PNG, limite de pixels, imagem sem veículo, gravação das intermediárias e relatórios, lote que continua após falha, colisão de nomes no lote, remoção de recorte obsoleto, erro de gravação, configuração, códigos de saída, menu e histórico |
| `FotografiasTest` | 62 | Leitura das 50 fotografias reais; localização em `img_003` e cinco derivações controladas; rejeição das quatro fotografias sem veículo; nomes de arquivo no padrão `<imagem>_placa.png`; regressão do refinamento em `img_013` |

Não são 93 veículos distintos: 50 casos apenas verificam a integridade das fotografias.

## Protocolo de avaliação

As 50 fotografias vêm do mesmo conjunto público (ver [FONTES-IMAGENS.md](FONTES-IMAGENS.md)). A versão 1.0.0 usou 20; a versão 1.1.0 incorporou as outras 30.

| Grupo | Fotografias | Uso |
|---|---|---|
| Ajuste da 1.0.0 | `img_001` a `img_010` | Desenvolvimento |
| Reservadas da 1.0.0 | `img_011` a `img_020` | Avaliação da 1.0.0; **na 1.1.0 passaram a ser desenvolvimento**, porque as falhas de `img_013`, `img_017` e `img_018` foram depuradas diretamente |
| Desenvolvimento da 1.1.0 | `img_021` a `img_035` | Inspeção visual durante o ajuste do refinamento |
| **Reservadas novas** | `img_036` a `img_050` | Caixas de referência marcadas manualmente **antes** de qualquer alteração do algoritmo; avaliadas uma única vez ao final, nas duas versões |

O critério é IoU ≥ 0,50 entre a caixa envolvente detectada e a caixa de referência. As referências são anotações visuais manuais, não anotações oficiais do fornecedor do conjunto, e têm a precisão de alguns pixels.

## Resultado nas reservadas novas (avaliação independente)

**1.0.0: 15/15. 1.1.0: 15/15. IoU médio: 0,817 na 1.0.0 e 0,821 na 1.1.0.**

| Fotografia | IoU 1.0.0 | IoU 1.1.0 |
|---|---:|---:|
| img_036.jpg | 0.655 | 0.735 |
| img_037.jpg | 0.822 | 0.860 |
| img_038.jpg | 0.900 | 0.785 |
| img_039.jpg | 0.806 | 0.775 |
| img_040.jpg | 0.832 | 0.802 |
| img_041.jpg | 0.854 | 0.873 |
| img_042.jpg | 0.816 | 0.902 |
| img_043.jpg | 0.796 | 0.855 |
| img_044.jpg | 0.807 | 0.848 |
| img_045.jpg | 0.925 | 0.892 |
| img_046.jpg | 0.752 | 0.885 |
| img_047.jpg | 0.860 | 0.825 |
| img_048.jpg | 0.842 | 0.683 |
| img_049.jpg | 0.763 | 0.767 |
| img_050.jpg | 0.823 | 0.823 |

A diferença média de IoU é pequena demais para afirmar ganho de localização. A diferença visível está na **completude do recorte**: na comparação lado a lado ([reservadas-novas-comparacao.jpg](reservadas-novas-comparacao.jpg)), a 1.0.0 cortou parte da faixa superior ou da borda em `img_036`, `img_039`, `img_046` e `img_048`; na 1.1.0 esses recortes contêm a placa inteira. Em compensação, `img_048` passou a incluir fundo escuro acima e abaixo da placa, e `img_038` incluiu mais moldura, o que reduziu o IoU desses dois casos. Essa leitura visual é uma avaliação do autor, sem segundo anotador.

[CSV 1.1.0](avaliacao-reservadas-novas.csv) · [CSV 1.0.0](avaliacao-reservadas-novas-v1.0.0.csv) · [Referências](referencias-reservadas-novas.csv)

## Resultado nas antigas reservadas (agora desenvolvimento)

**1.0.0: 8/10, IoU médio 0,689. 1.1.0: 9/10, IoU médio 0,738.** Esses números não são uma avaliação independente da 1.1.0, porque essas fotografias orientaram o ajuste.

| Fotografia | IoU 1.0.0 | IoU 1.1.0 | Observação |
|---|---:|---:|---|
| img_011.jpg | 0.734 | 0.793 | |
| img_012.jpg | 0.839 | 0.882 | |
| img_013.jpg | 0.420 | 0.842 | Reflexo azul unido à faixa removido pelo refinamento |
| img_014.jpg | 0.000 | 0.000 | Continua sem detecção |
| img_015.jpg | 0.868 | 0.764 | Recorte completo com mais moldura |
| img_016.jpg | 0.758 | 0.764 | |
| img_017.jpg | 0.808 | 0.750 | Base da placa deixou de ser cortada |
| img_018.jpg | 0.855 | 0.844 | Faixa superior deixou de ser cortada |
| img_019.jpg | 0.820 | 0.834 | |
| img_020.jpg | 0.784 | 0.908 | |

[CSV 1.1.0](avaliacao-reservadas.csv) · [CSV 1.0.0](avaliacao-reservadas-v1.0.0.csv) · [Recortes 1.1.0](reservadas-resultados.jpg) · [Evidências por imagem](evidencias/)

## Lote completo

`java -jar target/egyptian-plate-extractor.jar --diretorio imagens --recursivo`: 54 imagens, **49 placas encontradas, 5 sem placa (`img_014` e as 4 fotografias sem veículo), 0 erros**, código de saída 1. [Log completo](execucao-lote.log) · [Recortes de 001 a 035](desenvolvimento-recortes.jpg)

## O que mudou no algoritmo e por quê

1. **Refinamento de bordas** (`RefinamentoPlaca`). As hipóteses geradas a partir da faixa colorida usavam a largura da própria faixa e alturas fixas. Isso produzia dois defeitos medidos: recortes que perdiam a base ou a faixa (`img_017`, `img_018`) e caixas infladas quando um reflexo da mesma cor se unia à faixa (`img_013`). O refinamento retifica uma janela ampliada em torno da região escolhida e classifica cada pixel, em Java, como corpo claro neutro ou cor da faixa (matiz dominante estimado na própria região). Uma coluna só conta como placa se tiver cor da faixa em cima **e** corpo claro embaixo; o topo é procurado no perfil da faixa e a base no perfil do corpo. Se não houver fronteira clara, se a nova geometria sair dos limites de proporção ou se o contraste cair abaixo do mínimo, a região original é mantida.
2. **Proporção preferida 2,06** em vez de 2,2, igual à razão 35 × 17 cm da placa egípcia padrão. Isoladamente, essa mudança não alterou os resultados de forma relevante; foi mantida por ter base física, não por ganho medido.

Tentativas descartadas durante o desenvolvimento, registradas para transparência: estimar o matiz pela média circular (contaminada pela lataria em táxis amarelos), aceitar como placa qualquer pixel "faixa ou claro" (expandia sobre reflexos), validar o recorte refinado pela contagem de componentes (rejeitava justamente placas completas, porque a faixa escura se conecta aos caracteres no limiar de Otsu) e exigir IoU ≥ 0,5 com a região original (impedia corrigir caixas infladas).

## Falhas conhecidas

- **img_014:** placa desgastada, com faixa desbotada. Nenhuma proposta passa pelos filtros. Falso negativo conhecido.
- **img_001:** a placa tem cisalhamento de perspectiva; a borda esquerda da faixa e a do corpo estão em colunas diferentes. Um retângulo rotacionado não enquadra isso e o primeiro caractere fica parcialmente cortado. Corrigir exige estimar um quadrilátero, não implementado.
- **img_048** (reservada nova): o refinamento manteve fundo escuro acima e abaixo da placa.
- As variações controladas de `img_003` (escura × 0,32, metade e dobro da resolução, rotação de 12°, sem cor) passam, mas não substituem fotografias noturnas ou inclinadas reais. [Recortes](variacoes-recortes.jpg)
- 0/4 falsos positivos nas fotografias sem veículo não garante ausência de falsos positivos em fachadas, letreiros ou cenas urbanas.

Todas as 50 fotografias positivas vêm de uma única coleção, com câmera, distância e iluminação parecidas. Os números acima não permitem afirmar uma taxa de acerto geral para placas egípcias.

## Reproduzir

Na raiz do projeto:

```text
mvn clean verify
java -cp target/egyptian-plate-extractor.jar br.edu.uninta.placas.avaliacao.Avaliador imagens/carros docs/referencias-reservadas-novas.csv resultados/avaliacao-novas
java -cp target/egyptian-plate-extractor.jar br.edu.uninta.placas.avaliacao.Avaliador imagens/carros docs/referencias-reservadas.csv resultados/avaliacao
```

O primeiro avaliador termina com código 0 (15/15); o segundo com código 1, porque `img_014` não atende ao critério. Para comparar com o comportamento sem refinamento, use uma configuração com `refinarBordas=0`.
