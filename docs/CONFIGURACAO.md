# Configuração de processamento

Decimais usam ponto. As unidades em pixels referem-se à imagem de trabalho, exceto os limites de entrada.

| Parâmetro | Padrão | Intervalo | Finalidade |
|---|---:|---|---|
| `dimensaoMaxima` | 1400 | 320 a 4000 (inteiro) | Maior lado da imagem de trabalho, em pixels; a extração usa a original. |
| `limiteMegapixels` | 40 | 1 a 150 (inteiro) | Limite de pixels da imagem decodificada, em milhões. |
| `limiteArquivoMB` | 50 | 1 a 250 (inteiro) | Limite de tamanho do arquivo, em MiB (1024² bytes). |
| `gaussianoKernel` | 3 | 1 a 15 (inteiro) | Lado do kernel Gaussiano; inteiro ímpar. |
| `cannyInferior` | 50 | 1 a 254 | Limiar inferior de Canny. |
| `cannySuperior` | 150 | 2 a 255 | Limiar superior de Canny; maior que o inferior. |
| `claheContraste` | 2.5 | 0.1 a 10 | Limite de contraste do CLAHE, com grade interna 8 × 8. |
| `morfologiaLargura` | 0.012 | 0.001 a 0.1 | Largura do fechamento como fração da largura de trabalho; arredondada para ímpar, mínimo 3. |
| `morfologiaAltura` | 0.004 | 0.001 a 0.05 | Altura do fechamento como fração da altura de trabalho; arredondada para ímpar, mínimo 3. |
| `proporcaoMinima` | 1.35 | 1 a 8 | Menor razão largura/altura aceita no retângulo orientado. |
| `proporcaoMaxima` | 5.8 | 1.1 a 12 | Maior razão largura/altura aceita. |
| `proporcaoPreferida` | 2.06 | 1 a 8 | Centro suave da preferência geométrica. Corresponde à placa padrão de 35 × 17 cm; não é exigência de formato exato. |
| `areaMinima` | 0.0005 | 0.00001 a 0.1 | Menor área do retângulo dividida pela área da imagem de trabalho. |
| `areaMaxima` | 0.25 | 0.001 a 0.9 | Maior fração de área aceita. |
| `larguraMinima` | 35 | 12 a 500 (inteiro) | Largura mínima do retângulo orientado, em pixels de trabalho. |
| `alturaMinima` | 12 | 6 a 200 (inteiro) | Altura mínima do retângulo orientado, em pixels de trabalho. |
| `anguloMaximo` | 35 | 0 a 80 | Inclinação absoluta máxima em graus em relação à horizontal. |
| `retangularidadeMinima` | 0.35 | 0.05 a 1 | Área do contorno dividida pela área do retângulo orientado. |
| `densidadeBordasMinima` | 0.035 | 0 a 0.4 | Fração mínima de pixels de borda no interior normalizado. |
| `densidadeBordasMaxima` | 0.45 | 0.1 a 0.95 | Fração máxima de pixels de borda. |
| `contrasteMinimo` | 18 | 1 a 100 | Diferença mínima entre percentis 90 e 10 da intensidade, escala 0 a 255. Também vale para a região refinada. |
| `componentesMinimos` | 3 | 1 a 15 (inteiro) | Mínimo de componentes escuros com geometria compatível com traços. |
| `pontuacaoMinima` | 0.57 | 0 a 1 | Limiar de aceitação da pontuação heurística; reduzir pode criar falsos positivos. |
| `margemRecorte` | 0.04 | 0 a 0.2 | Margem por lado como fração da dimensão do retângulo. |
| `refinarBordas` | 1 | 0 a 1 (inteiro) | 1 ativa o refinamento de bordas da região escolhida; 0 reproduz o comportamento da versão 1.0.0. |
| `expansaoRefinamentoLargura` | 0.2 | 0 a 0.5 | Quanto a janela de refinamento se estende para cada lado, como fração da largura da região. Limita quanto a borda pode crescer. |
| `expansaoRefinamentoAltura` | 0.35 | 0 a 0.8 | Mesma ideia para cima e para baixo, como fração da altura. |
| `saturacaoMinima` | 95 | 0 a 255 (inteiro) | Saturação HSV mínima (0 a 255) para propostas por cor. |
| `brilhoCorMinimo` | 65 | 0 a 255 (inteiro) | Canal V mínimo (0 a 255) para propostas por cor. |
| `pesoFaixaColorida` | 0.12 | 0 a 0.3 | Peso do bônus de faixa colorida; zero desativa o bônus, não a busca por cor. |
| `limiteContornos` | 12000 | 100 a 100000 (inteiro) | Número máximo de contornos analisados por máscara; prioriza áreas maiores. |

Mínimos devem ser menores que máximos. A proporção preferida deve estar dentro do intervalo. Valores NaN/Infinity, chaves desconhecidas e kernels pares são rejeitados.

Os pesos básicos da pontuação são: geometria 0,18; componentes 0,28; densidade 0,16; contraste 0,12; retangularidade 0,07; região clara 0,07; mais o bônus de cor configurado. A soma é normalizada por `0.88 + pesoFaixaColorida`. As medidas são limitadas antes da soma. Consulte `AnaliseTextura.java` para os limites internos de normalização e de formas, e `DeteccaoPlaca.java` para a fórmula completa. Esses coeficientes são regras clássicas de projeto, sem treinamento ou interpretação de caracteres.

## Constantes internas do refinamento

`RefinamentoPlaca.java` usa limiares fixos de projeto, escolhidos nas fotografias de desenvolvimento: croma mínimo 0,35 para estimar o matiz da faixa, croma abaixo de 0,30 para pixel neutro claro, tolerância de matiz de 22°, fração mínima de 0,20 por coluna e 0,25 por linha. A região refinada é descartada se a largura ficar fora de 0,55 a 1,4 vezes a original, a altura fora de 0,6 a 1,6 vezes, a proporção fora de `proporcaoMinima` a `proporcaoMaxima`, ou se menos de 60% dela estiver dentro da região original.
