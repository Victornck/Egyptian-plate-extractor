package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.utils.RecursosNativos;
import org.opencv.core.Mat;

public final class RefinamentoPlaca {
    public record Resultado(RegiaoPlaca regiao, boolean alterada) { }

    private static final int LARGURA_JANELA = 320;
    private static final double LIMIAR_COLUNA = 0.2;
    private static final double LIMIAR_LINHA = 0.25;
    private static final double CROMA_COR = 0.35;
    private static final double CROMA_NEUTRO = 0.30;
    private static final double TOLERANCIA_MATIZ = 22;

    private final ExtracaoPlaca extracao = new ExtracaoPlaca();
    private final AnaliseTextura textura = new AnaliseTextura();

    public Resultado refinar(Mat original, RegiaoPlaca regiao, ConfiguracaoProcessamento config) {
        double margem = config.valor("margemRecorte");
        double expansaoX = 1 + 2 * config.valor("expansaoRefinamentoLargura");
        double expansaoY = 1 + 2 * config.valor("expansaoRefinamentoAltura");
        GeometriaRegiao inicial = GeometriaRegiao.de(regiao, margem);
        if (inicial.largura() < 8 || inicial.altura() < 4) return new Resultado(regiao, false);
        int largura = LARGURA_JANELA;
        int altura = Math.max(40, (int) Math.round(largura * inicial.altura() * expansaoY / (inicial.largura() * expansaoX)));
        Janela janela;
        try (RecursosNativos r = new RecursosNativos()) {
            Mat retificada = r.registrar(extracao.retificar(original, inicial.vertices(expansaoX, expansaoY), largura, altura));
            janela = new Janela(retificada);
        }
        double pxPorUnidadeX = largura / (inicial.largura() * expansaoX);
        double pxPorUnidadeY = altura / (inicial.altura() * expansaoY);
        int esquerdaInicial = (int) Math.round((largura - inicial.largura() * pxPorUnidadeX) / 2);
        int direitaInicial = largura - 1 - esquerdaInicial;
        int topoInicial = (int) Math.round((altura - inicial.altura() * pxPorUnidadeY) / 2);
        int baseInicial = altura - 1 - topoInicial;
        if (!janela.classificar(esquerdaInicial, direitaInicial, topoInicial, baseInicial)) return new Resultado(regiao, false);
        int divisao = topoInicial + (int) Math.round((baseInicial - topoInicial) * 0.42);
        double[] colunas = suavizar(janela.pontuacaoColunas(topoInicial, divisao, baseInicial));
        int lacunaX = Math.max(3, (int) Math.round(0.04 * (direitaInicial - esquerdaInicial)));
        int esquerda = fronteira(colunas, largura / 2, -1, lacunaX, LIMIAR_COLUNA, esquerdaInicial);
        int direita = fronteira(colunas, largura / 2, 1, lacunaX, LIMIAR_COLUNA, direitaInicial);
        if (direita - esquerda < (direitaInicial - esquerdaInicial) / 2) return new Resultado(regiao, false);
        double[] faixa = janela.linhas(esquerda, direita, true);
        double[] corpo = janela.linhas(esquerda, direita, false);
        int lacunaY = Math.max(2, (int) Math.round(0.02 * (baseInicial - topoInicial)));
        int topo;
        int base;
        if (janela.temFaixa()) {
            topo = fronteira(faixa, maximo(faixa, topoInicial, divisao), -1, lacunaY, LIMIAR_LINHA, topoInicial);
            base = fronteira(corpo, maximo(corpo, divisao, baseInicial), 1, lacunaY, LIMIAR_LINHA, baseInicial);
        } else {
            topo = fronteira(corpo, altura / 2, -1, lacunaY, LIMIAR_LINHA, topoInicial);
            base = fronteira(corpo, altura / 2, 1, lacunaY, LIMIAR_LINHA, baseInicial);
        }
        if (esquerda == esquerdaInicial && direita == direitaInicial && topo == topoInicial && base == baseInicial)
            return new Resultado(regiao, false);

        double novaLargura = (direita - esquerda + 1) / pxPorUnidadeX;
        double novaAltura = (base - topo + 1) / pxPorUnidadeY;
        double proporcao = novaLargura / novaAltura;
        if (novaLargura < inicial.largura() * 0.55 || novaLargura > inicial.largura() * 1.4
                || novaAltura < inicial.altura() * 0.6 || novaAltura > inicial.altura() * 1.6
                || proporcao < config.valor("proporcaoMinima") || proporcao > config.valor("proporcaoMaxima"))
            return new Resultado(regiao, false);

        double centroX = largura / 2.0;
        double centroY = altura / 2.0;
        GeometriaRegiao refinada = inicial.deslocada((esquerda - centroX) / pxPorUnidadeX, (direita + 1 - centroX) / pxPorUnidadeX,
                (topo - centroY) / pxPorUnidadeY, (base + 1 - centroY) / pxPorUnidadeY);
        AnaliseTextura.Medidas medidas;
        try (RecursosNativos r = new RecursosNativos()) {
            int larguraTextura = Math.max(2, (int) Math.round(refinada.largura()));
            int alturaTextura = Math.max(2, (int) Math.round(refinada.altura()));
            Mat recorte = r.registrar(extracao.retificar(original, refinada.vertices(1, 1), larguraTextura, alturaTextura));
            medidas = textura.medir(recorte, config);
        }
        if (medidas.contraste() < config.valor("contrasteMinimo")
                || fracaoContida(esquerda, direita, topo, base, esquerdaInicial, direitaInicial, topoInicial, baseInicial) < 0.6)
            return new Resultado(regiao, false);
        return new Resultado(refinada.regiao(margem, original.cols(), original.rows(), regiao.pontuacao(),
                regiao.origem() + "+refino", medidas.componentes(), medidas.densidade()), true);
    }

    private int fronteira(double[] fracao, int centro, int sentido, int lacuna, double limiar, int padrao) {
        int consecutivas = 0;
        for (int i = centro; i >= 0 && i < fracao.length; i += sentido) {
            if (fracao[i] < limiar) {
                consecutivas++;
                if (consecutivas >= lacuna) return i - sentido * lacuna;
            } else {
                consecutivas = 0;
            }
        }
        return padrao;
    }

    private double fracaoContida(int e1, int d1, int t1, int b1, int e2, int d2, int t2, int b2) {
        double largura = Math.max(0, Math.min(d1, d2) - Math.max(e1, e2) + 1);
        double altura = Math.max(0, Math.min(b1, b2) - Math.max(t1, t2) + 1);
        return largura * altura / ((d1 - e1 + 1.0) * (b1 - t1 + 1));
    }

    private int maximo(double[] valores, int inicio, int fim) {
        int melhor = inicio;
        for (int i = inicio; i <= fim; i++) if (valores[i] > valores[melhor]) melhor = i;
        return melhor;
    }

    private double[] suavizar(double[] valores) {
        double[] saida = new double[valores.length];
        for (int i = 0; i < valores.length; i++) {
            double soma = 0;
            int n = 0;
            for (int j = Math.max(0, i - 1); j <= Math.min(valores.length - 1, i + 1); j++) { soma += valores[j]; n++; }
            saida[i] = soma / n;
        }
        return saida;
    }

    private static final class Janela {
        private final int largura;
        private final int altura;
        private final int[] cinza;
        private final double[] croma;
        private final double[] matiz;
        private final boolean[] neutro;
        private final boolean[] faixa;
        private boolean temFaixa;

        Janela(Mat bgr) {
            largura = bgr.cols();
            altura = bgr.rows();
            byte[] dados = new byte[largura * altura * 3];
            bgr.get(0, 0, dados);
            cinza = new int[largura * altura];
            croma = new double[largura * altura];
            matiz = new double[largura * altura];
            neutro = new boolean[largura * altura];
            faixa = new boolean[largura * altura];
            for (int i = 0; i < cinza.length; i++) {
                int b = Byte.toUnsignedInt(dados[3 * i]);
                int g = Byte.toUnsignedInt(dados[3 * i + 1]);
                int r = Byte.toUnsignedInt(dados[3 * i + 2]);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                cinza[i] = (int) Math.round(0.114 * b + 0.587 * g + 0.299 * r);
                croma[i] = max == 0 ? 0 : (max - min) / (double) max;
                matiz[i] = calcularMatiz(r, g, b, max, min);
            }
        }

        boolean classificar(int esquerda, int direita, int topo, int base) {
            int limiar = otsu(esquerda, direita, topo, base);
            int alturaFaixa = topo + (int) Math.round((base - topo) * 0.4);
            int[] histograma = new int[36];
            int coloridos = 0;
            int total = 0;
            for (int y = topo; y <= alturaFaixa; y++) {
                for (int x = esquerda; x <= direita; x++) {
                    int i = y * largura + x;
                    total++;
                    if (croma[i] >= CROMA_COR && cinza[i] > 35) {
                        coloridos++;
                        histograma[(int) (matiz[i] / 10) % 36]++;
                    }
                }
            }
            int moda = 0;
            for (int i = 1; i < 36; i++) if (vizinhanca(histograma, i) > vizinhanca(histograma, moda)) moda = i;
            temFaixa = total > 0 && vizinhanca(histograma, moda) >= total * 0.12;
            double matizFaixa = moda * 10 + 5;
            int claros = 0;
            for (int i = 0; i < cinza.length; i++) {
                neutro[i] = cinza[i] >= limiar && croma[i] < CROMA_NEUTRO;
                faixa[i] = temFaixa && croma[i] >= CROMA_NEUTRO && distanciaMatiz(matiz[i], matizFaixa) <= TOLERANCIA_MATIZ;
                if (neutro[i]) claros++;
            }
            return claros > 0;
        }

        double[] pontuacaoColunas(int topo, int divisao, int base) {
            double[] pontuacao = new double[largura];
            for (int x = 0; x < largura; x++) {
                int faixas = 0;
                int claros = 0;
                for (int y = topo; y < divisao; y++) if (faixa[y * largura + x]) faixas++;
                for (int y = divisao; y <= base; y++) if (neutro[y * largura + x]) claros++;
                double corpo = claros / (double) (base - divisao + 1);
                pontuacao[x] = temFaixa ? Math.min(faixas / (double) (divisao - topo), corpo) : corpo;
            }
            return pontuacao;
        }

        boolean temFaixa() { return temFaixa; }

        double[] linhas(int esquerda, int direita, boolean daFaixa) {
            boolean[] mascara = daFaixa ? faixa : neutro;
            double[] pontuacao = new double[altura];
            for (int y = 0; y < altura; y++) {
                int n = 0;
                for (int x = esquerda; x <= direita; x++) if (mascara[y * largura + x]) n++;
                pontuacao[y] = n / (double) (direita - esquerda + 1);
            }
            return pontuacao;
        }

        private int otsu(int esquerda, int direita, int topo, int base) {
            int[] histograma = new int[256];
            int total = 0;
            for (int y = topo; y <= base; y++) {
                for (int x = esquerda; x <= direita; x++) { histograma[cinza[y * largura + x]]++; total++; }
            }
            double somaTotal = 0;
            for (int i = 0; i < 256; i++) somaTotal += i * (double) histograma[i];
            double somaFundo = 0;
            int pesoFundo = 0;
            double melhorVariancia = -1;
            int limiar = 128;
            for (int t = 0; t < 256; t++) {
                pesoFundo += histograma[t];
                if (pesoFundo == 0) continue;
                int pesoFrente = total - pesoFundo;
                if (pesoFrente == 0) break;
                somaFundo += t * (double) histograma[t];
                double mediaFundo = somaFundo / pesoFundo;
                double mediaFrente = (somaTotal - somaFundo) / pesoFrente;
                double variancia = (double) pesoFundo * pesoFrente * (mediaFundo - mediaFrente) * (mediaFundo - mediaFrente);
                if (variancia > melhorVariancia) { melhorVariancia = variancia; limiar = t + 1; }
            }
            return limiar;
        }

        private static int vizinhanca(int[] histograma, int indice) {
            return histograma[(indice + 35) % 36] + histograma[indice] + histograma[(indice + 1) % 36];
        }

        private static double calcularMatiz(int r, int g, int b, int max, int min) {
            if (max == min) return 0;
            double delta = max - min;
            double h;
            if (max == r) h = ((g - b) / delta) % 6;
            else if (max == g) h = (b - r) / delta + 2;
            else h = (r - g) / delta + 4;
            h *= 60;
            return h < 0 ? h + 360 : h;
        }

        private static double distanciaMatiz(double a, double b) {
            double d = Math.abs(a - b) % 360;
            return d > 180 ? 360 - d : d;
        }
    }
}
