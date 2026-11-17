package br.edu.uninta.placas.model;

import java.nio.file.Path;
import java.util.Optional;

public record ResultadoProcessamento(Path entrada, Estado estado, int largura, int altura,
                                     Optional<RegiaoPlaca> regiao, ArquivosResultado arquivos, Path placa,
                                     String mensagem, long duracaoMillis) {
    public enum Estado { ENCONTRADA, NAO_ENCONTRADA, ERRO }
}
