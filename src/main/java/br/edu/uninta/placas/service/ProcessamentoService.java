package br.edu.uninta.placas.service;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.*;
import br.edu.uninta.placas.model.ResultadoProcessamento.Estado;
import br.edu.uninta.placas.processamento.*;
import br.edu.uninta.placas.utils.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

public final class ProcessamentoService {
    public static final String SEPARADOR = "===============================================";
    private final GerenciadorImagem imagens = new GerenciadorImagem();
    private final RelatorioResultado relatorios = new RelatorioResultado();
    private final Consumer<String> log;

    public ProcessamentoService(Consumer<String> log) { this.log = Objects.requireNonNull(log); }

    public ResultadoProcessamento processar(Path entrada, Path saida, ConfiguracaoProcessamento config) {
        return processar(entrada, saida, config, new HashSet<>());
    }

    private ResultadoProcessamento processar(Path entrada, Path saida, ConfiguracaoProcessamento config, Set<String> reservados) {
        long inicio = System.nanoTime();
        ArquivosResultado arquivos = null;
        int largura = 0;
        int altura = 0;
        log.accept("[INFO] Arquivo selecionado:\n" + exibir(entrada) + "\n");
        try (RecursosNativos r = new RecursosNativos()) {
            log.accept("[INFO] Carregando imagem...");
            Mat original = r.registrar(imagens.carregar(entrada, config));
            largura = original.cols();
            altura = original.rows();
            log.accept("[OK] Imagem carregada com sucesso.\n");
            log.accept("[INFO] Dimensões: " + largura + " x " + altura + " pixels.\n");
            arquivos = reservar(saida, entrada, reservados);
            config.salvar(arquivos.configuracao());
            log.accept("[INFO] Iniciando processamento...\n");
            Mat trabalho = r.nova();
            double escala = Math.min(1, config.valor("dimensaoMaxima") / Math.max(largura, altura));
            Imgproc.resize(original, trabalho, new Size(Math.max(1, Math.round(largura * escala)), Math.max(1, Math.round(altura * escala))), 0, 0, Imgproc.INTER_AREA);
            log.accept("[1/6] Convertendo imagem para escala de cinza...");
            Mat cinza = r.registrar(new ConversaoCinza().converter(trabalho));
            imagens.salvar(arquivos.intermediaria("cinza"), cinza);
            log.accept("[OK] Conversão concluída.\n");
            log.accept("[2/6] Aplicando filtro Gaussiano e ajuste local de contraste...");
            Mat filtrada = r.nova();
            new FiltroGaussiano().aplicar(cinza, filtrada, config.inteiro("gaussianoKernel"));
            imagens.salvar(arquivos.intermediaria("gaussiano"), filtrada);
            Mat contraste = r.nova();
            CLAHE clahe = Imgproc.createCLAHE(config.valor("claheContraste"), new Size(8, 8));
            try { clahe.apply(filtrada, contraste); }
            finally { clahe.collectGarbage(); clahe.clear(); }
            imagens.salvar(arquivos.intermediaria("contraste"), contraste);
            log.accept("[OK] Redução de ruídos e ajuste de contraste concluídos.\n");
            log.accept("[3/6] Detectando bordas...");
            Mat bordas = r.nova();
            new DeteccaoBordas().aplicar(contraste, bordas, config.valor("cannyInferior"), config.valor("cannySuperior"));
            imagens.salvar(arquivos.intermediaria("bordas"), bordas);
            log.accept("[OK] Bordas identificadas.\n");
            log.accept("[4/6] Aplicando operações morfológicas...");
            Mat morfologia = r.nova();
            new OperacoesMorfologicas().aplicar(bordas, morfologia, config);
            imagens.salvar(arquivos.intermediaria("morfologia"), morfologia);
            log.accept("[OK] Processamento morfológico concluído.\n");
            log.accept("[5/6] Analisando regiões candidatas...");
            var deteccao = new DeteccaoPlaca().detectar(trabalho, contraste, bordas, morfologia, largura, altura, config);
            if (deteccao.limiteAtingido()) log.accept("[AVISO] limiteContornos atingido; foram priorizados os maiores contornos.");
            log.accept(deteccao.melhor().isPresent()
                    ? "[OK] Região candidata identificada (" + deteccao.candidatas().size() + " candidatas após filtros de geometria e textura)."
                    : "[OK] Análise concluída: nenhuma candidata atingiu os critérios (" + deteccao.candidatas().size() + " avaliadas).");
            Optional<RegiaoPlaca> selecionada = deteccao.melhor().map(regiao -> refinar(original, regiao, config));
            log.accept("");
            Mat anotada = r.registrar(original.clone());
            Path placa = null;
            Estado estado = Estado.NAO_ENCONTRADA;
            String mensagem = "Nenhuma placa foi identificada nesta imagem.";
            if (selecionada.isPresent()) {
                log.accept("[6/6] Extraindo placa...");
                RegiaoPlaca regiao = selecionada.orElseThrow();
                Mat extraida = r.registrar(new ExtracaoPlaca().extrair(original, regiao));
                placa = arquivos.placa();
                imagens.salvar(placa, extraida);
                MatOfPoint poligono = r.registrar(new MatOfPoint(regiao.pontos()));
                Imgproc.polylines(anotada, List.of(poligono), true, new Scalar(0, 255, 0), Math.max(2, largura / 500));
                estado = Estado.ENCONTRADA;
                mensagem = "Região mais provável extraída; a pontuação é heurística, não uma probabilidade.";
                log.accept("[OK] Extração concluída.\n");
            } else {
                Files.deleteIfExists(arquivos.placa());
                log.accept("[6/6] Extração não realizada: nenhuma candidata atingiu os critérios.\n");
            }
            imagens.salvar(arquivos.processada(), anotada);
            ResultadoProcessamento resultado = new ResultadoProcessamento(entrada.toAbsolutePath(), estado, largura, altura,
                    selecionada, arquivos, placa, mensagem, (System.nanoTime() - inicio) / 1_000_000);
            relatorios.salvar(resultado, deteccao.candidatas());
            resumir(resultado);
            return resultado;
        } catch (IOException | RuntimeException e) {
            String mensagem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.accept("[ERRO] " + entrada + ": " + mensagem);
            ResultadoProcessamento erro = new ResultadoProcessamento(entrada.toAbsolutePath(), Estado.ERRO, largura, altura,
                    Optional.empty(), arquivos, null, mensagem, (System.nanoTime() - inicio) / 1_000_000);
            if (arquivos != null) {
                try { relatorios.salvar(erro, List.of()); }
                catch (IOException falha) { log.accept("[ERRO] Não foi possível registrar a falha: " + falha.getMessage()); }
            }
            return erro;
        }
    }

    public List<ResultadoProcessamento> processarLote(List<Path> entradas, Path saida, ConfiguracaoProcessamento config) throws IOException {
        if (entradas.isEmpty()) throw new IllegalArgumentException("Nenhuma imagem JPG, JPEG ou PNG foi selecionada.");
        Set<String> reservados = new HashSet<>();
        List<ResultadoProcessamento> resultados = new ArrayList<>();
        for (int i = 0; i < entradas.size(); i++) {
            log.accept(SEPARADOR + "\n[LOTE] Imagem " + (i + 1) + " de " + entradas.size() + "\n");
            resultados.add(processar(entradas.get(i), saida, config, reservados));
        }
        Path resumo = saida.resolve("lotes/lote_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")) + ".csv");
        relatorios.salvarLote(resumo, resultados);
        long encontradas = resultados.stream().filter(r -> r.estado() == Estado.ENCONTRADA).count();
        long erros = resultados.stream().filter(r -> r.estado() == Estado.ERRO).count();
        log.accept(SEPARADOR + "\nRESUMO DO LOTE\n");
        log.accept("Total de imagens: " + resultados.size());
        log.accept("Placas encontradas: " + encontradas);
        log.accept("Sem placa identificada: " + (resultados.size() - encontradas - erros));
        log.accept("Erros: " + erros + "\n");
        log.accept("Resumo do lote:\n" + exibir(resumo) + "\n\n" + SEPARADOR);
        return List.copyOf(resultados);
    }

    public List<Path> listarImagens(Path pasta, Path saida, boolean recursivo) throws IOException {
        if (!Files.isDirectory(pasta)) throw new IOException("Diretório inexistente: " + pasta);
        Path raiz = pasta.toRealPath();
        Path destino = Files.exists(saida) ? saida.toRealPath() : saida.toAbsolutePath().normalize();
        if (raiz.startsWith(destino)) throw new IOException("A pasta de entrada não pode ser a pasta de resultados nem estar dentro dela.");
        try (Stream<Path> arquivos = Files.walk(pasta, recursivo ? Integer.MAX_VALUE : 1)) {
            return arquivos.filter(Files::isRegularFile).filter(imagens::formatoSuportado)
                    .filter(p -> !p.toAbsolutePath().normalize().startsWith(destino)).sorted().toList();
        }
    }

    public static String exibir(Path caminho) {
        Path absoluto = caminho.toAbsolutePath().normalize();
        Path atual = Path.of("").toAbsolutePath().normalize();
        return (absoluto.startsWith(atual) && !absoluto.equals(atual) ? atual.relativize(absoluto) : absoluto).toString();
    }

    private void resumir(ResultadoProcessamento resultado) {
        StringBuilder texto = new StringBuilder(SEPARADOR).append("\nRESULTADO\n\n");
        texto.append("Arquivo processado: ").append(resultado.entrada().getFileName()).append("\n\n");
        if (resultado.estado() == Estado.ENCONTRADA) {
            RegiaoPlaca regiao = resultado.regiao().orElseThrow();
            texto.append("Placa encontrada: SIM\n\n");
            texto.append("Região identificada:\n");
            texto.append("X: ").append(regiao.x()).append('\n');
            texto.append("Y: ").append(regiao.y()).append('\n');
            texto.append("Largura: ").append(regiao.largura()).append('\n');
            texto.append("Altura: ").append(regiao.altura()).append('\n');
            texto.append(String.format(Locale.ROOT, "Pontuação heurística: %.3f%n%n", regiao.pontuacao()));
            texto.append("Arquivo gerado:\n").append(exibir(resultado.placa())).append("\n\n");
        } else {
            texto.append("Placa encontrada: NÃO\n\n");
            texto.append("[RESULTADO] ").append(resultado.mensagem()).append("\n\n");
        }
        ArquivosResultado arquivos = resultado.arquivos();
        texto.append("Imagem anotada:\n").append(exibir(arquivos.processada())).append('\n');
        texto.append("Etapas intermediárias:\n").append(exibir(arquivos.raiz().resolve("intermediarias")))
                .append(FileSystems.getDefault().getSeparator()).append(arquivos.nome()).append("_*.png\n");
        texto.append("Relatório:\n").append(exibir(arquivos.relatorio())).append("\n\n").append(SEPARADOR);
        log.accept(texto.toString());
    }

    private RegiaoPlaca refinar(Mat original, RegiaoPlaca regiao, ConfiguracaoProcessamento config) {
        if (config.inteiro("refinarBordas") == 0) return regiao;
        RefinamentoPlaca.Resultado refinamento = new RefinamentoPlaca().refinar(original, regiao, config);
        if (refinamento.alterada()) {
            RegiaoPlaca nova = refinamento.regiao();
            log.accept(String.format(Locale.ROOT, "[OK] Bordas refinadas: %d x %d -> %d x %d pixels.",
                    regiao.largura(), regiao.altura(), nova.largura(), nova.altura()));
        } else {
            log.accept("[INFO] Bordas mantidas: o refinamento não encontrou limites mais confiáveis.");
        }
        return refinamento.regiao();
    }

    private ArquivosResultado reservar(Path saida, Path entrada, Set<String> reservados) throws IOException {
        String base = entrada.getFileName().toString().replaceFirst("\\.[^.]+$", "").replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (base.length() > 60) base = base.substring(0, 60);
        if (base.isEmpty()) base = "imagem";
        String nome = base;
        for (int n = 2; !reservados.add(nome.toLowerCase(Locale.ROOT)); n++) nome = base + "_" + n;
        ArquivosResultado arquivos = new ArquivosResultado(saida.toAbsolutePath().normalize(), nome);
        for (String pasta : ArquivosResultado.PASTAS) Files.createDirectories(arquivos.raiz().resolve(pasta));
        return arquivos;
    }
}
