package br.edu.uninta.placas.avaliacao;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.model.ResultadoProcessamento.Estado;
import br.edu.uninta.placas.service.ProcessamentoService;
import br.edu.uninta.placas.utils.OpenCvRuntime;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Avaliador {
    private Avaliador() { }

    public static void main(String[] args) {
        try { System.exit(avaliar(args)); }
        catch (Exception | LinkageError e) {
            System.err.println("[ERRO] " + e.getMessage());
            System.exit(2);
        }
    }

    private static int avaliar(String[] args) throws IOException {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Uso: Avaliador <pasta-imagens> <referencias.csv> <pasta-saida> [config.properties]");
            return 2;
        }
        ConfiguracaoProcessamento config = args.length == 4 ? ConfiguracaoProcessamento.carregar(Path.of(args[3])) : new ConfiguracaoProcessamento();
        Path pasta = Path.of(args[0]);
        Path referencias = Path.of(args[1]);
        Path saida = Path.of(args[2]);
        List<String> linhas = Files.readAllLines(referencias, StandardCharsets.UTF_8);
        if (linhas.size() < 2 || !linhas.get(0).equals("arquivo,x,y,largura,altura")) throw new IOException("CSV deve ter cabeçalho arquivo,x,y,largura,altura e ao menos uma referência.");
        OpenCvRuntime.carregar();
        ProcessamentoService service = new ProcessamentoService(System.out::println);
        Files.createDirectories(saida);
        Path arquivo = saida.resolve("avaliacao_" + UUID.randomUUID().toString().substring(0, 8) + ".csv");
        int total = 0;
        int corretas = 0;
        int erros = 0;
        try (Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            w.write("arquivo,estado,iou,localizada_iou_050,ref_x,ref_y,ref_largura,ref_altura,det_x,det_y,det_largura,det_altura\n");
            for (String linha : linhas.subList(1, linhas.size())) {
                if (linha.isBlank()) continue;
                String[] campos = linha.split(",", -1);
                if (campos.length != 5) throw new IOException("Linha inválida no CSV: " + linha);
                int x = Integer.parseInt(campos[1]);
                int y = Integer.parseInt(campos[2]);
                int largura = Integer.parseInt(campos[3]);
                int altura = Integer.parseInt(campos[4]);
                if (x < 0 || y < 0 || largura <= 0 || altura <= 0) throw new IOException("Caixa de referência inválida: " + linha);
                var resultado = service.processar(pasta.resolve(campos[0]), saida, config);
                double iou = resultado.regiao().map(r -> iou(r, x, y, largura, altura)).orElse(0.0);
                boolean correta = resultado.estado() == Estado.ENCONTRADA && iou >= 0.5;
                if (correta) corretas++;
                if (resultado.estado() == Estado.ERRO) erros++;
                total++;
                String coordenadas = resultado.regiao().map(r -> r.x() + "," + r.y() + "," + r.largura() + "," + r.altura()).orElse(",,,");
                w.write(String.format(Locale.ROOT, "%s,%s,%.6f,%s,%d,%d,%d,%d,%s%n", campos[0], resultado.estado(), iou, correta, x, y, largura, altura, coordenadas));
                System.out.printf(Locale.ROOT, "[AVALIAÇÃO] %s | IoU: %.3f | Critério IoU >= 0.50: %s%n", campos[0], iou, correta ? "ATENDIDO" : "NÃO ATENDIDO");
            }
        }
        System.out.printf("[AVALIAÇÃO] %d/%d localizadas pelo critério IoU >= 0.50; %d erros de execução.%n", corretas, total, erros);
        System.out.println("[ARQUIVO] " + arquivo.toAbsolutePath());
        return erros > 0 ? 2 : corretas == total ? 0 : 1;
    }

    private static double iou(RegiaoPlaca r, int x, int y, int largura, int altura) {
        double w = Math.max(0, Math.min(r.x() + r.largura(), x + largura) - Math.max(r.x(), x));
        double h = Math.max(0, Math.min(r.y() + r.altura(), y + altura) - Math.max(r.y(), y));
        return w * h / ((double)r.largura() * r.altura() + (double)largura * altura - w * h);
    }
}
