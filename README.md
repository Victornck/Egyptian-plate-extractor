# Egyptian License Plate Extractor

Projeto acadêmico em Java 17, Maven e OpenCV 4.9.0 para localizar e extrair a região mais provável de uma placa egípcia em fotografias de veículos. Funciona integralmente por terminal. Não usa redes neurais, modelos treinados ou OCR.

**Versão 1.1.0, funcional e com limitações medidas.** Em 15 fotografias reservadas e nunca usadas no ajuste, a placa foi localizada em 15/15 pelo critério IoU ≥ 0,50 (IoU médio 0,821). No lote completo de 50 fotografias reais, 49 tiveram placa extraída; `img_014` não foi detectada. Nenhuma das 4 fotografias sem veículo gerou detecção. A amostra é pequena e de uma única coleção; esses números não representam precisão geral. Detalhes em [docs/VALIDACAO.md](docs/VALIDACAO.md).

## Início rápido no Windows 11

1. Baixe o projeto (**Code → Download ZIP**) e extraia mantendo todas as subpastas.
2. Instale um **JDK 17 ou superior, de 64 bits**. Defina `JAVA_HOME` para a pasta do JDK e acrescente `%JAVA_HOME%\bin` ao `PATH`.
3. Abra PowerShell ou Terminal na pasta que contém o `pom.xml` e confira `java -version`.
4. O JAR entregue já inclui OpenCV e as bibliotecas nativas para Windows x64 e Linux x64. Para executá-lo, basta Java; Maven só é necessário para recompilar e testar.

```powershell
java "-Dfile.encoding=UTF-8" -jar target/egyptian-plate-extractor.jar
```

No PowerShell, as aspas em `"-Dfile.encoding=UTF-8"` são obrigatórias: sem elas, o PowerShell separa o argumento no ponto e o Java tenta abrir `.encoding=UTF-8` como classe principal. Também é possível rodar `.\executar.bat` ou dar dois cliques nele; ele usa o `cmd`, ajusta o terminal para UTF-8 e abre o menu.

O menu oferece processamento individual, processamento de diretório, consulta de resultados e configurações. Aceita caminhos absolutos, relativos, com espaços, com acentos e colados entre aspas. Caminhos relativos partem do diretório atual.

## Uso sem menu

```powershell
java -jar target/egyptian-plate-extractor.jar --imagem imagens/carros/img_003.jpg
java -jar target/egyptian-plate-extractor.jar --diretorio imagens/carros --saida resultados
java -jar target/egyptian-plate-extractor.jar --diretorio "C:\Fotos de carros" --recursivo --saida resultados
java -jar target/egyptian-plate-extractor.jar --imagem imagens/carros/img_003.jpg --config config/processamento.properties
java -jar target/egyptian-plate-extractor.jar --salvar-config minha-config.properties
java -jar target/egyptian-plate-extractor.jar --ajuda
```

No modo diretório, só arquivos JPG/JPEG/PNG são selecionados. Um arquivo inválido gera erro próprio e o lote continua. A pasta de saída é excluída da seleção e não pode conter a pasta de entrada. Sem `--recursivo`, somente o primeiro nível é processado.

Códigos de saída: **0** = todas as imagens tiveram uma região selecionada, ou comando de manutenção/menu concluído; **1** = pelo menos uma imagem válida sem detecção; **2** = erro de entrada, configuração, leitura ou gravação. Em lote, erro tem prioridade sobre ausência de placa. O estado `ENCONTRADA` é uma decisão heurística, não uma confirmação humana.

## Exemplo de execução

```text
===============================================
EGYPTIAN LICENSE PLATE EXTRACTOR

[INFO] Arquivo selecionado:
imagens/carros/img_003.jpg

[INFO] Carregando imagem...
[OK] Imagem carregada com sucesso.

[INFO] Dimensões: 1200 x 1600 pixels.

[INFO] Iniciando processamento...

[1/6] Convertendo imagem para escala de cinza...
[OK] Conversão concluída.

[2/6] Aplicando filtro Gaussiano e ajuste local de contraste...
[OK] Redução de ruídos e ajuste de contraste concluídos.

[3/6] Detectando bordas...
[OK] Bordas identificadas.

[4/6] Aplicando operações morfológicas...
[OK] Processamento morfológico concluído.

[5/6] Analisando regiões candidatas...
[OK] Região candidata identificada (2 candidatas após filtros de geometria e textura).
[OK] Bordas refinadas: 176 x 95 -> 176 x 86 pixels.

[6/6] Extraindo placa...
[OK] Extração concluída.

===============================================
RESULTADO

Arquivo processado: img_003.jpg

Placa encontrada: SIM

Região identificada:
X: 493
Y: 806
Largura: 176
Altura: 86
Pontuação heurística: 0.926

Arquivo gerado:
resultados/placas/img_003_placa.png

Imagem anotada:
resultados/processadas/img_003_processada.png
Etapas intermediárias:
resultados/intermediarias/img_003_*.png
Relatório:
resultados/relatorios/img_003_resultado.properties

===============================================
```

Cada linha `[OK]` é emitida somente depois que a etapa terminou e o arquivo correspondente foi gravado. Quando nenhuma região atinge os critérios, o bloco final traz `Placa encontrada: NÃO` e `[RESULTADO] Nenhuma placa foi identificada nesta imagem.`

## Resultados gravados

```text
resultados/
  placas/img_003_placa.png
  processadas/img_003_processada.png
  intermediarias/img_003_cinza.png
  intermediarias/img_003_gaussiano.png
  intermediarias/img_003_contraste.png
  intermediarias/img_003_bordas.png
  intermediarias/img_003_morfologia.png
  relatorios/img_003_resultado.properties
  relatorios/img_003_candidatas.csv
  relatorios/img_003_configuracao.properties
  lotes/lote_DATA_HORA.csv
```

Reprocessar a mesma imagem sobrescreve os arquivos dela. Se a nova execução não encontrar placa, o recorte antigo em `placas/` é apagado, para não ficar um resultado desatualizado. Em um mesmo lote, imagens com o mesmo nome em subpastas diferentes recebem sufixo (`img_003_2_placa.png`). Intermediárias estão na resolução de trabalho; a imagem anotada usa a resolução original. `candidatas.csv` registra as candidatas que passaram pelos filtros, inclusive as que ficaram abaixo do limiar final. Erros de escrita são reportados como `ERRO`, nunca como sucesso.

A orientação EXIF é ignorada intencionalmente: dimensões e coordenadas referem-se aos pixels codificados. JPEGs com dados após o término da imagem são aceitos; PNG com CRC inválido ou conteúdo após IEND é rejeitado.

## Compilar e testar com Maven

Instale Maven 3.9 ou superior e verifique se `mvn -version` usa o mesmo JDK de `java -version`.

```powershell
mvn clean verify
java -jar target/egyptian-plate-extractor.jar --imagem imagens/carros/img_003.jpg
```

Na primeira compilação é necessário acesso ao Maven Central para baixar os plugins (clean, resources, compiler, surefire, jar, shade). Depois disso, a execução do JAR é local. O Maven Shade gera `target/egyptian-plate-extractor.jar` com as dependências e as bibliotecas nativas para Windows x64 e Linux x64; macOS, ARM e x86 de 32 bits ficam de fora para reduzir o tamanho. Não execute `original-egyptian-plate-extractor.jar`, caso exista, pois ele não inclui as dependências.

Os testes usam as fotografias de `imagens/`; mantenha-as ao executar `mvn test`. Relatórios JUnit ficam em `target/surefire-reports/`.

## IntelliJ IDEA Community

1. Use **File → Open** e selecione o `pom.xml`; abra como projeto Maven. O ideal é abrir a pasta que contém o `pom.xml` diretamente, e não uma pasta acima dela.
2. Em **Project Structure → Project SDK**, selecione JDK 17 ou superior.
3. Em **Settings → Build Tools → Maven → Runner**, escolha o mesmo JDK.
4. Recarregue o projeto no painel Maven para baixar as dependências.
5. Execute `br.edu.uninta.placas.Main` com a pasta do `pom.xml` como **Working directory**.
6. Sem argumentos, o menu abre no console Run. Para execução direta, use `--imagem imagens/carros/img_003.jpg` em **Program arguments**.
7. Execute `verify` ou `package` no painel Maven para testar e gerar o JAR.

## Etapas reais do algoritmo

1. O arquivo é validado por extensão, formato decodificado, dimensões e limite de bytes. JPEG tem segmentos e término verificados; PNG tem blocos e CRC verificados. O OpenCV decodifica os pixels em BGR.
2. A cópia de trabalho é reduzida, preservando proporção, para até `dimensaoMaxima`. A imagem original continua disponível para refinamento e extração.
3. `ConversaoCinza` percorre as linhas e calcula em Java `Y = round(0,114 B + 0,587 G + 0,299 R)`, sem `cvtColor`.
4. OpenCV aplica Gaussiano e CLAHE. Canny gera as bordas; fechamento morfológico conecta regiões próximas.
5. Há propostas independentes por limiar de Otsu, bordas fechadas, morfologia e faixas coloridas. Faixas azuis, verdes e de outras tonalidades são procuradas separadamente. Uma faixa gera várias hipóteses de altura.
6. Contornos são avaliados por área relativa, proporção, tamanho mínimo, retangularidade e inclinação. Retângulos rotacionados permitem tratar inclinações moderadas.
7. Cada candidato é retificado. A análise calcula contraste por quantis, densidade de bordas e componentes escuros compatíveis com traços de caracteres. São apenas formas; **nenhuma letra ou número é interpretado**. Geometria, textura e faixa colorida compõem uma pontuação.
8. Candidatos muito sobrepostos são suprimidos. A melhor região só é aceita se passar pelos filtros e por `pontuacaoMinima`. Sem isso, o resultado é `NAO_ENCONTRADA`.
9. **Refinamento de bordas** (`RefinamentoPlaca`, implementação própria). Uma janela ampliada em torno da região escolhida é retificada a partir da imagem original. Cada pixel é classificado em Java como corpo claro neutro ou cor da faixa, com o matiz dominante estimado na própria região. Uma coluna só pertence à placa se tiver cor da faixa em cima e corpo claro embaixo; o topo da placa é procurado no perfil da faixa e a base no perfil do corpo branco. Se não houver fronteira clara, se a nova geometria sair dos limites ou se o contraste cair, a região original é mantida e o terminal informa isso.
10. Os vértices recebem margem configurável e são recortados da imagem original por transformação geométrica. As coordenadas X/Y/largura/altura descrevem a caixa envolvente do quadrilátero na imagem original.

A retificação usa um retângulo orientado. Corrige rotação no plano, mas **não corrige perspectiva trapezoidal** (ver `img_001`). Não há detector de veículo: placas de sinalização, letreiros e texturas podem gerar falsos positivos.

A pontuação varia de 0 a 1 e serve para ordenação e limiar. Não é probabilidade nem taxa de confiança.

## Configurações e calibração

Consulte [o catálogo dos parâmetros](docs/CONFIGURACAO.md). Todos os valores são validados; propriedades desconhecidas geram erro, para evitar erros de digitação. No menu, alterações valem para a sessão; use **Salvar arquivo** para persistir. `config/processamento.properties` corresponde aos valores internos padrão. `refinarBordas=0` desativa o refinamento e reproduz o comportamento da versão 1.0.0.

Para uma nova coleção, use parte das imagens para ajuste e reserve outra parte para avaliação. Veja primeiro as intermediárias: se a placa tem poucos pixels, aumente `dimensaoMaxima` ou diminua os tamanhos mínimos; se as bordas somem, revise Canny e contraste; se regiões se fundem, reduza os kernels de morfologia. Reduzir `pontuacaoMinima` pode aumentar falsos positivos.

## Reproduzir a avaliação

```powershell
java -cp target/egyptian-plate-extractor.jar br.edu.uninta.placas.avaliacao.Avaliador imagens/carros docs/referencias-reservadas-novas.csv resultados/avaliacao-novas
java -cp target/egyptian-plate-extractor.jar br.edu.uninta.placas.avaliacao.Avaliador imagens/carros docs/referencias-reservadas.csv resultados/avaliacao
```

Um quarto argumento opcional indica o arquivo de configuração. O avaliador usa as mesmas etapas da aplicação e gera um CSV com caixa de referência, caixa detectada, IoU e resultado do critério. Com o conjunto entregue, o primeiro comando termina com código 0 (15/15) e o segundo com código 1, porque `img_014` não é detectada; isso não indica falha de execução.

O CSV de referência tem cabeçalho `arquivo,x,y,largura,altura`. IoU é a área de interseção dividida pela área da união das caixas envolventes; a localização é considerada atendida com IoU ≥ 0,50. O critério não mede legibilidade. As caixas foram marcadas manualmente; não são anotações oficiais do fornecedor.

## Arquitetura

| Pacote | Responsabilidade |
|---|---|
| `cli` | Menu interativo, histórico, alteração de configuração |
| `config` | Valores padrão, leitura/gravação e validação |
| `model` | Regiões, resultados e caminhos dos arquivos gerados, sem memória nativa |
| `processamento` | Cinza, filtros, bordas, morfologia, propostas, textura, geometria, refinamento e extração |
| `service` | Sequência das etapas, mensagens do terminal e lote |
| `utils` | Carga nativa, validação de imagens, gravação e relatórios |
| `avaliacao` | Avaliação reproduzível com referência manual |

Matrizes nativas pertencem a um escopo `RecursosNativos`, encerrado por `try-with-resources`; buffers temporários são liberados mesmo em exceções. O serviço processa uma imagem de cada vez.

## Limites conhecidos

- As 50 fotografias positivas são de uma única coleção, com condições parecidas; não cobrem a diversidade de placas egípcias.
- `img_014`: placa desgastada com faixa desbotada; nenhuma proposta passa pelos filtros.
- `img_001`: perspectiva trapezoidal; o primeiro caractere fica parcialmente cortado.
- O refinamento pode manter fundo escuro acima e abaixo da placa, como em `img_048`.
- Somente a melhor placa é extraída por fotografia, mesmo com vários veículos.
- As variações escurecida e rotacionada são transformações controladas; não substituem fotografias noturnas ou inclinadas reais.
- A execução em Windows 11 e no IntelliJ foi documentada, mas a verificação automatizada desta versão foi feita em Linux x64.

## Fontes e dependências

As 50 fotografias de veículos vêm do [conjunto público de Ahm3d0x](https://github.com/Ahm3d0x/Automatic-License-Plate-Recognition-Classical-Image-Processing-), commit `612bcd8b09aace4181b8737dc0e739823902db03`. Apenas as imagens foram usadas; nenhum código de OCR desse projeto é executado. A licença MIT do repositório está em `docs/LICENCA-DATASET.txt`.

As quatro imagens sem veículos são amostras do [scikit-image 0.19.3](https://github.com/scikit-image/scikit-image/tree/v0.19.3/skimage/data): camera (Lav Varshney, CC0), coffee (Rachel Michetti/Pikolo Espresso Bar, CC0), astronaut (NASA, domínio público) e grass (linolafett, CC0). Consulte `docs/FONTES-IMAGENS.md` e os hashes em `docs/imagens-sha256.txt`.

A dimensão de referência da placa (35 × 17 cm) é a do padrão egípcio em vigor desde 2008, conforme o [verbete sobre placas do Egito](https://en.wikipedia.org/wiki/Vehicle_registration_plates_of_Egypt).

OpenCV é distribuído pelo [pacote OpenPnP](https://github.com/openpnp/opencv), coordenada Maven `org.openpnp:opencv:4.9.0-0`. JUnit Jupiter é usado somente nos testes. Para problemas de carga nativa, confira arquitetura x64, pasta temporária gravável e restrições do antivírus.
