package br.edu.uninta.placas;

import br.edu.uninta.placas.cli.Menu;
import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.ResultadoProcessamento.Estado;
import br.edu.uninta.placas.processamento.ConversaoCinza;
import br.edu.uninta.placas.service.ProcessamentoService;
import br.edu.uninta.placas.utils.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import static org.junit.jupiter.api.Assertions.*;

class ProcessamentoTest {
    @TempDir Path temp;
    private final ConfiguracaoProcessamento config = new ConfiguracaoProcessamento();
    private final GerenciadorImagem imagens = new GerenciadorImagem();
    private final ProcessamentoService service = new ProcessamentoService(s -> { });

    @BeforeAll static void carregar() { OpenCvRuntime.carregar(); }

    @Test void convertePixelsSemOpenCvCvtColor() {
        try (RecursosNativos r = new RecursosNativos()) {
            Mat original = r.registrar(new Mat(1, 4, CvType.CV_8UC3));
            original.put(0, 0, new byte[]{0, 0, (byte)255, 0, (byte)255, 0, (byte)255, 0, 0, (byte)255, (byte)255, (byte)255});
            Mat cinza = r.registrar(new ConversaoCinza().converter(original));
            byte[] valores = new byte[4];
            cinza.get(0, 0, valores);
            assertArrayEquals(new byte[]{76, (byte)150, 29, (byte)255}, valores);
        }
    }

    @Test void converteSubmatrizNaoContinua() {
        try (RecursosNativos r = new RecursosNativos()) {
            Mat original = r.registrar(new Mat(4, 5, CvType.CV_8UC3, new Scalar(0, 0, 255)));
            Mat roi = r.registrar(original.submat(new Rect(1, 1, 2, 2)));
            Mat cinza = r.registrar(new ConversaoCinza().converter(roi));
            assertEquals(76, cinza.get(1, 1)[0]);
        }
    }

    @ParameterizedTest @ValueSource(strings={"jpg", "jpeg", "png", "JPG", "PNG"})
    void carregaFormatosValidos(String extensao) throws IOException {
        Path arquivo = temp.resolve("veículo com espaço." + extensao);
        BufferedImage imagem = new BufferedImage(180, 90, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(imagem, extensao.equalsIgnoreCase("png") ? "png" : "jpg", arquivo.toFile()));
        try (RecursosNativos r = new RecursosNativos()) {
            Mat carregada = r.registrar(imagens.carregar(arquivo, config));
            assertEquals(180, carregada.cols());
            assertEquals(90, carregada.rows());
        }
    }

    @Test void rejeitaArquivoInexistente() { assertThrows(IOException.class, () -> imagens.carregar(temp.resolve("ausente.jpg"), config)); }
    @Test void rejeitaFormatoNaoSuportado() throws IOException {
        Path p = temp.resolve("teste.gif"); Files.writeString(p, "GIF89a");
        assertThrows(IOException.class, () -> imagens.carregar(p, config));
    }
    @Test void rejeitaImagemCorrompida() throws IOException {
        Path p = temp.resolve("quebrada.jpg"); Files.write(p, new byte[]{(byte)255, (byte)216, 1, 2, 3});
        assertThrows(IOException.class, () -> imagens.carregar(p, config));
    }
    @Test void rejeitaPngComChecksumInvalido() throws IOException {
        Path p = imagemLisa("checksum.png");
        byte[] dados = Files.readAllBytes(p);
        dados[dados.length - 1] ^= 1;
        Files.write(p, dados);
        assertThrows(IOException.class, () -> imagens.carregar(p, config));
    }
    @Test void rejeitaJpegTruncado() throws IOException {
        Path p = temp.resolve("truncado.jpg");
        ImageIO.write(new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB), "jpg", p.toFile());
        byte[] dados = Files.readAllBytes(p);
        Files.write(p, Arrays.copyOf(dados, dados.length - 4));
        assertThrows(IOException.class, () -> imagens.carregar(p, config));
    }
    @Test void aceitaJpegComDadosAdicionaisAposFimDaImagem() throws IOException {
        Path p = temp.resolve("adicionais.jpg");
        ImageIO.write(new BufferedImage(60, 60, BufferedImage.TYPE_INT_RGB), "jpg", p.toFile());
        Files.write(p, new byte[]{1, 2, 3, 4}, StandardOpenOption.APPEND);
        try (RecursosNativos r = new RecursosNativos()) {
            assertEquals(60, r.registrar(imagens.carregar(p, config)).cols());
        }
    }
    @Test void rejeitaConteudoDisfarcado() throws IOException {
        Path p = temp.resolve("gif.png");
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "gif", p.toFile());
        assertThrows(IOException.class, () -> imagens.carregar(p, config));
    }
    @Test void respeitaLimiteDePixels() throws IOException {
        Path p = temp.resolve("grande.png");
        ImageIO.write(new BufferedImage(1100, 1000, BufferedImage.TYPE_INT_RGB), "png", p.toFile());
        assertThrows(IOException.class, () -> imagens.carregar(p, config.alterar("limiteMegapixels", "1")));
    }
    @Test void informaAusenciaDePlacaEGravaIntermediarias() throws IOException {
        Path p = imagemLisa("sem_veiculo.png");
        var resultado = service.processar(p, temp.resolve("saida"), config);
        assertEquals(Estado.NAO_ENCONTRADA, resultado.estado());
        assertTrue(resultado.regiao().isEmpty());
        assertNull(resultado.placa());
        for (String etapa : List.of("cinza", "gaussiano", "contraste", "bordas", "morfologia"))
            assertTrue(Files.isRegularFile(temp.resolve("saida/intermediarias/sem_veiculo_" + etapa + ".png")), etapa);
        assertTrue(Files.isRegularFile(temp.resolve("saida/relatorios/sem_veiculo_resultado.properties")));
        assertTrue(Files.isRegularFile(temp.resolve("saida/relatorios/sem_veiculo_candidatas.csv")));
        assertTrue(Files.isRegularFile(temp.resolve("saida/relatorios/sem_veiculo_configuracao.properties")));
        try (RecursosNativos r = new RecursosNativos()) {
            Mat salvo = r.registrar(imagens.carregar(temp.resolve("saida/processadas/sem_veiculo_processada.png"), config));
            assertEquals(640, salvo.cols());
        }
    }
    @Test void loteContinuaAposFalhaENaoSobrescreve() throws IOException {
        Path p = imagemLisa("carro.png");
        var resultados = service.processarLote(List.of(temp.resolve("inexistente.png"), p, p), temp.resolve("saida"), config);
        assertEquals(List.of(Estado.ERRO, Estado.NAO_ENCONTRADA, Estado.NAO_ENCONTRADA), resultados.stream().map(x -> x.estado()).toList());
        assertEquals("carro", resultados.get(1).arquivos().nome());
        assertEquals("carro_2", resultados.get(2).arquivos().nome());
        assertNotEquals(resultados.get(1).arquivos().relatorio(), resultados.get(2).arquivos().relatorio());
        try (var arquivos = Files.list(temp.resolve("saida/lotes"))) { assertEquals(1, arquivos.count()); }
    }
    @Test void reprocessamentoSemPlacaRemoveRecorteAnterior() throws IOException {
        Path p = imagemLisa("antiga.png");
        Path recorteAntigo = temp.resolve("saida/placas/antiga_placa.png");
        Files.createDirectories(recorteAntigo.getParent());
        Files.writeString(recorteAntigo, "recorte de execução anterior");
        assertEquals(Estado.NAO_ENCONTRADA, service.processar(p, temp.resolve("saida"), config).estado());
        assertFalse(Files.exists(recorteAntigo));
    }
    @Test void erroDeGravacaoNaoRetornaSucesso() throws IOException {
        Path p = imagemLisa("carro.png");
        Path saida = temp.resolve("arquivo"); Files.writeString(saida, "obstrução");
        assertEquals(Estado.ERRO, service.processar(p, saida, config).estado());
    }
    @Test void listaLoteComRecursaoEExcluiSaida() throws IOException {
        imagemLisa("um.png");
        Files.createDirectories(temp.resolve("sub"));
        Files.copy(temp.resolve("um.png"), temp.resolve("sub/dois.png"));
        Files.createDirectories(temp.resolve("saida"));
        Files.copy(temp.resolve("um.png"), temp.resolve("saida/ignorada.png"));
        Files.writeString(temp.resolve("texto.txt"), "texto");
        assertEquals(1, service.listarImagens(temp, temp.resolve("saida"), false).size());
        assertEquals(2, service.listarImagens(temp, temp.resolve("saida"), true).size());
        assertThrows(IOException.class, () -> service.listarImagens(temp, temp, true));
    }
    @ParameterizedTest @ValueSource(strings={"NaN", "Infinity", "-1", "1.5", "4"})
    void rejeitaKernelInvalido(String valor) { assertThrows(IllegalArgumentException.class, () -> config.alterar("gaussianoKernel", valor)); }
    @Test void validaRelacoesEParametrosDesconhecidos() {
        assertThrows(IllegalArgumentException.class, () -> config.alterar("cannyInferior", "200"));
        assertThrows(IllegalArgumentException.class, () -> config.alterar("inexistente", "2"));
        assertEquals(3, config.inteiro("gaussianoKernel"));
    }
    @Test void persisteConfiguracao() throws IOException {
        Path p = temp.resolve("config.properties");
        config.alterar("pontuacaoMinima", "0.7").salvar(p);
        assertEquals(0.7, ConfiguracaoProcessamento.carregar(p).valor("pontuacaoMinima"));
    }
    @Test void rejeitaConfiguracaoDesconhecidaEmArquivo() throws IOException {
        Path p = temp.resolve("config.properties"); Files.writeString(p, "desconhecido=1");
        assertThrows(IllegalArgumentException.class, () -> ConfiguracaoProcessamento.carregar(p));
    }
    @Test void cliRetornaCodigosCorretos() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        assertEquals(0, Main.executar(new String[]{"--ajuda"}, out, out));
        assertEquals(2, Main.executar(new String[]{"--imagem"}, out, out));
        assertEquals(2, Main.executar(new String[]{"--recursivo"}, out, out));
        assertEquals(2, Main.executar(new String[]{"--imagem", "x", "--diretorio", "y"}, out, out));
        Path p = imagemLisa("lisa.png");
        assertEquals(1, Main.executar(new String[]{"--imagem", p.toString(), "--saida", temp.resolve("saida").toString()}, out, out));
    }
    @Test void menuHistoricoConfiguracaoESaida() throws IOException {
        String comandos = "3\n4\n1\npontuacaoMinima\n0.70\n4\n3\n" + temp.resolve("config.properties") + "\n0\n";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new Menu(new ByteArrayInputStream(comandos.getBytes(StandardCharsets.UTF_8)), new PrintStream(buffer, true, StandardCharsets.UTF_8), temp.resolve("saida"), config).executar();
        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("Nenhum resultado"));
        assertEquals(0.7, ConfiguracaoProcessamento.carregar(temp.resolve("config.properties")).valor("pontuacaoMinima"));
    }
    @Test void menuEncerraNoFimDaEntrada() throws IOException {
        new Menu(new ByteArrayInputStream(new byte[0]), new PrintStream(OutputStream.nullOutputStream()), temp, config).executar();
    }
    private Path imagemLisa(String nome) throws IOException {
        Path arquivo = temp.resolve(nome);
        try (RecursosNativos r = new RecursosNativos()) {
            Mat m = r.registrar(new Mat(480, 640, CvType.CV_8UC3, new Scalar(80, 80, 80)));
            imagens.salvar(arquivo, m);
        }
        return arquivo;
    }
}
