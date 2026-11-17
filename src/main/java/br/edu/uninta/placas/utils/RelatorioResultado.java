package br.edu.uninta.placas.utils;

import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.model.ResultadoProcessamento;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class RelatorioResultado {
    public void salvar(ResultadoProcessamento resultado, List<RegiaoPlaca> candidatas) throws IOException {
        Properties p = new Properties();
        p.setProperty("arquivo", resultado.entrada().toString());
        p.setProperty("estado", resultado.estado().name());
        p.setProperty("larguraImagem", Integer.toString(resultado.largura()));
        p.setProperty("alturaImagem", Integer.toString(resultado.altura()));
        p.setProperty("duracaoMillis", Long.toString(resultado.duracaoMillis()));
        p.setProperty("mensagem", resultado.mensagem());
        p.setProperty("placa", resultado.placa() == null ? "" : resultado.arquivos().raiz().relativize(resultado.placa()).toString());
        resultado.regiao().ifPresent(r -> {
            p.setProperty("x", Integer.toString(r.x()));
            p.setProperty("y", Integer.toString(r.y()));
            p.setProperty("largura", Integer.toString(r.largura()));
            p.setProperty("altura", Integer.toString(r.altura()));
            p.setProperty("pontuacaoHeuristica", Double.toString(r.pontuacao()));
            p.setProperty("angulo", Double.toString(r.angulo()));
            p.setProperty("origem", r.origem());
            for (int i = 0; i < r.vertices().size(); i++) {
                p.setProperty("vertice" + i, r.vertices().get(i).x() + "," + r.vertices().get(i).y());
            }
        });
        try (Writer w = Files.newBufferedWriter(resultado.arquivos().relatorio(), StandardCharsets.UTF_8)) { p.store(w, null); }
        try (Writer w = Files.newBufferedWriter(resultado.arquivos().candidatas(), StandardCharsets.UTF_8)) {
            w.write("x,y,largura,altura,pontuacao,angulo,origem,componentes,densidadeBordas\n");
            for (RegiaoPlaca r : candidatas) w.write(String.format(Locale.ROOT, "%d,%d,%d,%d,%.6f,%.3f,%s,%d,%.6f%n",
                    r.x(), r.y(), r.largura(), r.altura(), r.pontuacao(), r.angulo(), r.origem(), r.componentes(), r.densidadeBordas()));
        }
    }

    public void salvarLote(Path arquivo, List<ResultadoProcessamento> resultados) throws IOException {
        Files.createDirectories(arquivo.getParent());
        try (Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            w.write("arquivo,estado,mensagem,relatorio\n");
            for (var r : resultados) w.write(campo(r.entrada().toString()) + "," + r.estado() + "," + campo(r.mensagem()) + "," + campo(r.arquivos() == null ? "" : r.arquivos().relatorio().toString()) + "\n");
        }
    }

    private String campo(String valor) { return "\"" + valor.replace("\"", "\"\"") + "\""; }
}
