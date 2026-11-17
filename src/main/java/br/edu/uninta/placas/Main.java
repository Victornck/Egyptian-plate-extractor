package br.edu.uninta.placas;

import br.edu.uninta.placas.cli.Menu;
import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.ResultadoProcessamento;
import br.edu.uninta.placas.service.ProcessamentoService;
import br.edu.uninta.placas.utils.OpenCvRuntime;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

public final class Main {
    private Main() { }

    public static void main(String[] args) { System.exit(executar(args, System.out, System.err)); }

    public static int executar(String[] args, PrintStream saida, PrintStream erros) {
        try {
            Map<String, String> opcoes = new LinkedHashMap<>();
            Set<String> flags = new HashSet<>();
            Set<String> comValor = Set.of("--imagem", "--diretorio", "--saida", "--config", "--salvar-config");
            for (int i = 0; i < args.length; i++) {
                String chave = args[i];
                if (Set.of("--ajuda", "-h", "--recursivo").contains(chave)) {
                    if (!flags.add(chave)) throw new IllegalArgumentException("Opção repetida: " + chave);
                } else if (comValor.contains(chave)) {
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) throw new IllegalArgumentException("Falta valor para " + chave);
                    if (opcoes.putIfAbsent(chave, args[++i]) != null) throw new IllegalArgumentException("Opção repetida: " + chave);
                } else throw new IllegalArgumentException("Opção desconhecida: " + chave);
            }
            if (flags.contains("--ajuda") || flags.contains("-h")) { ajuda(saida); return 0; }
            if (opcoes.containsKey("--imagem") && opcoes.containsKey("--diretorio")) throw new IllegalArgumentException("Escolha --imagem ou --diretorio.");
            if (flags.contains("--recursivo") && !opcoes.containsKey("--diretorio")) throw new IllegalArgumentException("--recursivo exige --diretorio.");
            if (opcoes.containsKey("--salvar-config") && (opcoes.containsKey("--imagem") || opcoes.containsKey("--diretorio")))
                throw new IllegalArgumentException("Use --salvar-config separadamente do processamento.");
            ConfiguracaoProcessamento config = opcoes.containsKey("--config") ? ConfiguracaoProcessamento.carregar(Path.of(opcoes.get("--config"))) : new ConfiguracaoProcessamento();
            if (opcoes.containsKey("--salvar-config")) {
                config.salvar(Path.of(opcoes.get("--salvar-config")));
                saida.println("[OK] Configuração salva.");
                return 0;
            }
            OpenCvRuntime.carregar();
            Path destino = Path.of(opcoes.getOrDefault("--saida", "resultados"));
            ProcessamentoService service = new ProcessamentoService(saida::println);
            if (opcoes.containsKey("--imagem") || opcoes.containsKey("--diretorio"))
                saida.println(ProcessamentoService.SEPARADOR + "\nEGYPTIAN LICENSE PLATE EXTRACTOR\n");
            if (opcoes.containsKey("--imagem")) return codigo(List.of(service.processar(Path.of(opcoes.get("--imagem")), destino, config)));
            if (opcoes.containsKey("--diretorio")) {
                var entradas = service.listarImagens(Path.of(opcoes.get("--diretorio")), destino, flags.contains("--recursivo"));
                return codigo(service.processarLote(entradas, destino, config));
            }
            new Menu(System.in, saida, destino, config).executar();
            return 0;
        } catch (Exception | LinkageError e) {
            erros.println("[ERRO] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return 2;
        }
    }

    private static int codigo(List<ResultadoProcessamento> resultados) {
        if (resultados.stream().anyMatch(r -> r.estado() == ResultadoProcessamento.Estado.ERRO)) return 2;
        return resultados.stream().anyMatch(r -> r.estado() == ResultadoProcessamento.Estado.NAO_ENCONTRADA) ? 1 : 0;
    }

    private static void ajuda(PrintStream saida) {
        saida.println("EGYPTIAN LICENSE PLATE EXTRACTOR\nJava 17+ | OpenCV | Sem OCR ou redes neurais");
        saida.println("Sem argumentos: menu interativo.");
        saida.println("--imagem <arquivo> | --diretorio <pasta> [--recursivo]");
        saida.println("[--saida <pasta>] [--config <arquivo.properties>]");
        saida.println("--salvar-config <arquivo.properties> | --ajuda");
        saida.println("Códigos: 0 = detecções ou operação concluída; 1 = alguma placa não encontrada; 2 = erro.");
    }
}
