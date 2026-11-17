package br.edu.uninta.placas.cli;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.service.ProcessamentoService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class Menu {
    private final BufferedReader leitor;
    private final PrintStream saida;
    private final ProcessamentoService service;
    private ConfiguracaoProcessamento config;
    private final Path resultados;

    public Menu(InputStream entrada, PrintStream saida, Path resultados, ConfiguracaoProcessamento config) {
        this.leitor = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
        this.saida = saida;
        this.service = new ProcessamentoService(saida::println);
        this.resultados = resultados;
        this.config = config;
    }

    public void executar() throws IOException {
        while (true) {
            saida.println("\n" + ProcessamentoService.SEPARADOR + "\nEGYPTIAN LICENSE PLATE EXTRACTOR\n");
            saida.println("[1] Processar uma imagem\n[2] Processar várias imagens\n[3] Consultar resultados anteriores\n[4] Configurações de processamento\n[0] Sair");
            String opcao = ler("Escolha uma opção: ");
            if (opcao == null || opcao.equals("0")) return;
            try {
                switch (opcao) {
                    case "1" -> service.processar(caminho("Informe o caminho da imagem: "), resultados, config);
                    case "2" -> {
                        Path pasta = caminho("Informe o diretório das imagens: ");
                        boolean recursivo = "s".equalsIgnoreCase(ler("Incluir subpastas? [s/N]: "));
                        service.processarLote(service.listarImagens(pasta, resultados, recursivo), resultados, config);
                    }
                    case "3" -> historico();
                    case "4" -> configuracoes();
                    default -> saida.println("[ERRO] Opção inválida.");
                }
            } catch (IOException | IllegalArgumentException e) { saida.println("[ERRO] " + e.getMessage()); }
        }
    }

    private void historico() throws IOException {
        if (!Files.isDirectory(resultados)) { saida.println("[INFO] Nenhum resultado anterior."); return; }
        List<Path> arquivos;
        try (Stream<Path> itens = Files.walk(resultados, 2)) {
            arquivos = itens.filter(p -> p.getFileName().toString().endsWith("_resultado.properties"))
                    .sorted(Comparator.comparing(Menu::modificacao).reversed()).toList();
        }
        if (arquivos.isEmpty()) saida.println("[INFO] Nenhum resultado anterior.");
        for (Path arquivo : arquivos) {
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) { p.load(r); }
            saida.println("[" + p.getProperty("estado") + "] " + p.getProperty("arquivo"));
            if (!p.getProperty("placa", "").isEmpty()) saida.println("  Placa: " + ProcessamentoService.exibir(resultados.resolve(p.getProperty("placa"))));
            saida.println("  Relatório: " + ProcessamentoService.exibir(arquivo));
        }
    }

    private static java.nio.file.attribute.FileTime modificacao(Path arquivo) {
        try { return Files.getLastModifiedTime(arquivo); }
        catch (IOException e) { return java.nio.file.attribute.FileTime.fromMillis(0); }
    }

    private void configuracoes() throws IOException {
        config.valores().forEach((nome, valor) -> saida.println(nome + " = " + valor));
        saida.println("[1] Alterar parâmetro [2] Carregar arquivo [3] Salvar arquivo [0] Voltar");
        String escolha = ler("Escolha: ");
        if (escolha == null) return;
        switch (escolha) {
            case "1" -> {
                String nome = ler("Nome do parâmetro: ");
                String valor = ler("Novo valor (decimal com ponto): ");
                if (nome != null && valor != null) {
                    config = config.alterar(nome, valor);
                    saida.println("[OK] Parâmetro atualizado nesta sessão. Use salvar para persistir.");
                }
            }
            case "2" -> { config = ConfiguracaoProcessamento.carregar(caminho("Arquivo .properties: ")); saida.println("[OK] Configuração carregada."); }
            case "3" -> { config.salvar(caminho("Salvar configuração em: ")); saida.println("[OK] Configuração salva."); }
            case "0" -> { }
            default -> saida.println("[ERRO] Opção inválida.");
        }
    }

    private String ler(String mensagem) throws IOException {
        saida.print(mensagem);
        saida.flush();
        String valor = leitor.readLine();
        return valor == null ? null : valor.trim();
    }

    private Path caminho(String mensagem) throws IOException {
        String texto = ler(mensagem);
        if (texto == null || texto.isBlank()) throw new IOException("Caminho não informado.");
        if (texto.startsWith("\"") && texto.endsWith("\"")) texto = texto.substring(1, texto.length() - 1);
        return Path.of(texto);
    }
}
