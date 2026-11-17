package br.edu.uninta.placas;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.ResultadoProcessamento.Estado;
import br.edu.uninta.placas.processamento.ConversaoCinza;
import br.edu.uninta.placas.service.ProcessamentoService;
import br.edu.uninta.placas.utils.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.junit.jupiter.api.Assertions.*;

class FotografiasTest {
    @TempDir Path temp;
    private final GerenciadorImagem imagens = new GerenciadorImagem();
    private final ConfiguracaoProcessamento config = new ConfiguracaoProcessamento();
    private final ProcessamentoService service = new ProcessamentoService(s -> { });

    @BeforeAll static void iniciar() { OpenCvRuntime.carregar(); }

    @ParameterizedTest
    @ValueSource(strings={"original", "escura", "pequena", "grande", "inclinada", "sem_cor"})
    void extraiPlacaDeFotografiaRealEDerivacoes(String variante) throws IOException {
        try (RecursosNativos r = new RecursosNativos()) {
            Mat original = r.registrar(imagens.carregar(Path.of("imagens/carros/img_003.jpg"), config));
            Mat alterada = r.nova();
            Rect esperado = new Rect(499, 810, 164, 83);
            switch (variante) {
                case "escura" -> original.convertTo(alterada, -1, 0.32);
                case "pequena", "grande" -> {
                    double escala = variante.equals("pequena") ? 0.5 : 2;
                    Imgproc.resize(original, alterada, new Size(), escala, escala, Imgproc.INTER_AREA);
                    esperado = new Rect((int)(esperado.x * escala), (int)(esperado.y * escala), (int)(esperado.width * escala), (int)(esperado.height * escala));
                }
                case "inclinada" -> {
                    Mat rotacao = r.registrar(Imgproc.getRotationMatrix2D(new Point(original.cols() / 2.0, original.rows() / 2.0), 12, 1));
                    Imgproc.warpAffine(original, alterada, rotacao, original.size(), Imgproc.INTER_CUBIC);
                    esperado = transformar(esperado, rotacao);
                }
                case "sem_cor" -> {
                    Mat cinza = r.registrar(new ConversaoCinza().converter(original));
                    Imgproc.cvtColor(cinza, alterada, Imgproc.COLOR_GRAY2BGR);
                }
                default -> original.copyTo(alterada);
            }
            Path entrada = temp.resolve(variante + ".png");
            imagens.salvar(entrada, alterada);
            var resultado = service.processar(entrada, temp.resolve("resultados"), config);
            assertEquals(Estado.ENCONTRADA, resultado.estado(), resultado.mensagem());
            Rect detectado = resultado.regiao().orElseThrow().retangulo();
            assertTrue(iou(esperado, detectado) >= 0.5, "Recorte não coincide com a placa de referência: " + variante);
            Mat placa = r.registrar(imagens.carregar(resultado.placa(), config));
            assertTrue(placa.cols() > placa.rows());
            assertTrue(Files.size(resultado.placa()) > 100);
            if (variante.equals("inclinada")) assertTrue(Math.abs(resultado.regiao().orElseThrow().angulo()) > 5);
        }
    }

    @org.junit.jupiter.api.Test
    void gravaArquivosComNomeDaImagem() {
        var resultado = service.processar(Path.of("imagens/carros/img_003.jpg"), temp.resolve("resultados"), config);
        assertEquals(Estado.ENCONTRADA, resultado.estado(), resultado.mensagem());
        assertEquals(temp.resolve("resultados/placas/img_003_placa.png").toAbsolutePath(), resultado.placa());
        assertTrue(Files.isRegularFile(temp.resolve("resultados/processadas/img_003_processada.png")));
        assertTrue(Files.isRegularFile(temp.resolve("resultados/intermediarias/img_003_morfologia.png")));
    }

    @org.junit.jupiter.api.Test
    void refinamentoRemoveReflexoUnidoAFaixa() throws IOException {
        Rect referencia = new Rect(1316, 2846, 633, 330);
        var refinado = service.processar(Path.of("imagens/carros/img_013.jpg"), temp.resolve("com"), config);
        var semRefino = service.processar(Path.of("imagens/carros/img_013.jpg"), temp.resolve("sem"), config.alterar("refinarBordas", "0"));
        double iouRefinado = iou(referencia, refinado.regiao().orElseThrow().retangulo());
        double iouSemRefino = iou(referencia, semRefino.regiao().orElseThrow().retangulo());
        assertTrue(iouSemRefino < 0.5, "Sem refinamento o caso deveria reproduzir a falha documentada: " + iouSemRefino);
        assertTrue(iouRefinado >= 0.7, "Refinamento não corrigiu o excesso de fundo: " + iouRefinado);
    }

    @ParameterizedTest
    @ValueSource(strings={"astronaut.png", "camera.png", "coffee.png", "grass.png"})
    void rejeitaFotografiasSemVeiculo(String nome) {
        var resultado = service.processar(Path.of("imagens/sem_veiculo", nome), temp.resolve("resultados"), config);
        assertEquals(Estado.NAO_ENCONTRADA, resultado.estado(), resultado.mensagem());
        assertTrue(resultado.regiao().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50})
    void validaIntegridadeDasFotografiasDoConjunto(int indice) throws IOException {
        try (RecursosNativos r = new RecursosNativos()) {
            Mat imagem = r.registrar(imagens.carregar(Path.of(String.format(java.util.Locale.ROOT, "imagens/carros/img_%03d.jpg", indice)), config));
            assertFalse(imagem.empty());
        }
    }

    private Rect transformar(Rect retangulo, Mat matriz) {
        double[] xs = {retangulo.x, retangulo.x + retangulo.width};
        double[] ys = {retangulo.y, retangulo.y + retangulo.height};
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (double x : xs) for (double y : ys) {
            double tx = matriz.get(0, 0)[0] * x + matriz.get(0, 1)[0] * y + matriz.get(0, 2)[0];
            double ty = matriz.get(1, 0)[0] * x + matriz.get(1, 1)[0] * y + matriz.get(1, 2)[0];
            minX = Math.min(minX, tx); minY = Math.min(minY, ty);
            maxX = Math.max(maxX, tx); maxY = Math.max(maxY, ty);
        }
        return new Rect((int)minX, (int)minY, (int)Math.ceil(maxX - minX), (int)Math.ceil(maxY - minY));
    }

    private double iou(Rect a, Rect b) {
        double largura = Math.max(0, Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x));
        double altura = Math.max(0, Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y));
        double intersecao = largura * altura;
        return intersecao / (a.area() + b.area() - intersecao);
    }
}
