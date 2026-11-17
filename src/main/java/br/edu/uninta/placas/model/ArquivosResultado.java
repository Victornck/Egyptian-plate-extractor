package br.edu.uninta.placas.model;

import java.nio.file.Path;
import java.util.List;

public record ArquivosResultado(Path raiz, String nome) {
    public static final List<String> PASTAS = List.of("placas", "processadas", "intermediarias", "relatorios");

    public Path placa() { return raiz.resolve("placas").resolve(nome + "_placa.png"); }

    public Path processada() { return raiz.resolve("processadas").resolve(nome + "_processada.png"); }

    public Path intermediaria(String etapa) { return raiz.resolve("intermediarias").resolve(nome + "_" + etapa + ".png"); }

    public Path relatorio() { return raiz.resolve("relatorios").resolve(nome + "_resultado.properties"); }

    public Path candidatas() { return raiz.resolve("relatorios").resolve(nome + "_candidatas.csv"); }

    public Path configuracao() { return raiz.resolve("relatorios").resolve(nome + "_configuracao.properties"); }
}
