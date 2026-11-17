package br.edu.uninta.placas.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class ConfiguracaoProcessamento {
    private record Parametro(double padrao, double minimo, double maximo, boolean inteiro) { }
    private static final Map<String, Parametro> PARAMETROS = new LinkedHashMap<>();
    static {
        definir("dimensaoMaxima", 1400, 320, 4000, true);
        definir("limiteMegapixels", 40, 1, 150, true);
        definir("limiteArquivoMB", 50, 1, 250, true);
        definir("gaussianoKernel", 3, 1, 15, true);
        definir("cannyInferior", 50, 1, 254, false);
        definir("cannySuperior", 150, 2, 255, false);
        definir("claheContraste", 2.5, 0.1, 10, false);
        definir("morfologiaLargura", 0.012, 0.001, 0.1, false);
        definir("morfologiaAltura", 0.004, 0.001, 0.05, false);
        definir("proporcaoMinima", 1.35, 1, 8, false);
        definir("proporcaoMaxima", 5.8, 1.1, 12, false);
        definir("proporcaoPreferida", 2.06, 1, 8, false);
        definir("areaMinima", 0.0005, 0.00001, 0.1, false);
        definir("areaMaxima", 0.25, 0.001, 0.9, false);
        definir("larguraMinima", 35, 12, 500, true);
        definir("alturaMinima", 12, 6, 200, true);
        definir("anguloMaximo", 35, 0, 80, false);
        definir("retangularidadeMinima", 0.35, 0.05, 1, false);
        definir("densidadeBordasMinima", 0.035, 0, 0.4, false);
        definir("densidadeBordasMaxima", 0.45, 0.1, 0.95, false);
        definir("contrasteMinimo", 18, 1, 100, false);
        definir("componentesMinimos", 3, 1, 15, true);
        definir("pontuacaoMinima", 0.57, 0, 1, false);
        definir("margemRecorte", 0.04, 0, 0.2, false);
        definir("refinarBordas", 1, 0, 1, true);
        definir("expansaoRefinamentoLargura", 0.2, 0, 0.5, false);
        definir("expansaoRefinamentoAltura", 0.35, 0, 0.8, false);
        definir("saturacaoMinima", 95, 0, 255, true);
        definir("brilhoCorMinimo", 65, 0, 255, true);
        definir("pesoFaixaColorida", 0.12, 0, 0.3, false);
        definir("limiteContornos", 12000, 100, 100000, true);
    }
    private final Map<String, Double> valores = new LinkedHashMap<>();

    public ConfiguracaoProcessamento() {
        PARAMETROS.forEach((chave, parametro) -> valores.put(chave, parametro.padrao()));
    }

    private static void definir(String nome, double padrao, double min, double max, boolean inteiro) {
        PARAMETROS.put(nome, new Parametro(padrao, min, max, inteiro));
    }

    public double valor(String nome) {
        if (!valores.containsKey(nome)) throw new IllegalArgumentException("Parâmetro desconhecido: " + nome);
        return valores.get(nome);
    }

    public int inteiro(String nome) { return (int) valor(nome); }

    public Map<String, Double> valores() { return Collections.unmodifiableMap(valores); }

    public ConfiguracaoProcessamento alterar(String nome, String texto) {
        if (!PARAMETROS.containsKey(nome)) throw new IllegalArgumentException("Parâmetro desconhecido: " + nome);
        ConfiguracaoProcessamento nova = new ConfiguracaoProcessamento();
        nova.valores.putAll(valores);
        try { nova.valores.put(nome, Double.parseDouble(texto)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Valor numérico inválido: " + texto, e); }
        nova.validar();
        return nova;
    }

    public static ConfiguracaoProcessamento carregar(Path arquivo) throws IOException {
        Properties propriedades = new Properties();
        try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) { propriedades.load(leitor); }
        ConfiguracaoProcessamento config = new ConfiguracaoProcessamento();
        for (String chave : propriedades.stringPropertyNames()) {
            if (!PARAMETROS.containsKey(chave)) throw new IllegalArgumentException("Parâmetro desconhecido: " + chave);
            try { config.valores.put(chave, Double.parseDouble(propriedades.getProperty(chave))); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Valor inválido para " + chave, e); }
        }
        config.validar();
        return config;
    }

    public void salvar(Path arquivo) throws IOException {
        Path destino = arquivo.toAbsolutePath();
        Files.createDirectories(destino.getParent());
        try (Writer escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            for (var item : valores.entrySet()) escritor.write(item.getKey() + "=" + item.getValue() + System.lineSeparator());
        }
    }

    private void validar() {
        valores.forEach((nome, valor) -> {
            Parametro p = PARAMETROS.get(nome);
            if (!Double.isFinite(valor) || valor < p.minimo() || valor > p.maximo() || (p.inteiro() && valor != Math.rint(valor)))
                throw new IllegalArgumentException(nome + " deve estar entre " + p.minimo() + " e " + p.maximo() + (p.inteiro() ? " (inteiro)." : "."));
        });
        if (inteiro("gaussianoKernel") % 2 == 0) throw new IllegalArgumentException("gaussianoKernel deve ser ímpar.");
        ordenar("cannyInferior", "cannySuperior");
        ordenar("proporcaoMinima", "proporcaoMaxima");
        ordenar("areaMinima", "areaMaxima");
        ordenar("densidadeBordasMinima", "densidadeBordasMaxima");
        if (valor("proporcaoPreferida") < valor("proporcaoMinima") || valor("proporcaoPreferida") > valor("proporcaoMaxima"))
            throw new IllegalArgumentException("proporcaoPreferida deve estar no intervalo de proporções.");
    }

    private void ordenar(String menor, String maior) {
        if (valor(menor) >= valor(maior)) throw new IllegalArgumentException(menor + " deve ser menor que " + maior);
    }
}
