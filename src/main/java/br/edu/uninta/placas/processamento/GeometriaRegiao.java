package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.model.RegiaoPlaca.Vertice;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.Point;

public record GeometriaRegiao(double cx, double cy, double largura, double altura, double angulo) {

    public static GeometriaRegiao de(RegiaoPlaca regiao, double margem) {
        Point[] p = regiao.pontos();
        double fator = 1 + margem * 2;
        double cx = (p[0].x + p[1].x + p[2].x + p[3].x) / 4;
        double cy = (p[0].y + p[1].y + p[2].y + p[3].y) / 4;
        double largura = Math.hypot(p[1].x - p[0].x, p[1].y - p[0].y) / fator;
        double altura = Math.hypot(p[3].x - p[0].x, p[3].y - p[0].y) / fator;
        double angulo = Math.toDegrees(Math.atan2(p[1].y - p[0].y, p[1].x - p[0].x));
        return new GeometriaRegiao(cx, cy, largura, altura, angulo);
    }

    public Point[] vertices(double fatorLargura, double fatorAltura) {
        return ExtracaoPlaca.vertices(cx, cy, largura * fatorLargura, altura * fatorAltura, angulo);
    }

    public GeometriaRegiao deslocada(double esquerda, double direita, double topo, double base) {
        double dx = (esquerda + direita) / 2;
        double dy = (topo + base) / 2;
        double c = Math.cos(Math.toRadians(angulo));
        double s = Math.sin(Math.toRadians(angulo));
        return new GeometriaRegiao(cx + dx * c - dy * s, cy + dx * s + dy * c, direita - esquerda, base - topo, angulo);
    }

    public GeometriaRegiao escalada(double sx, double sy) {
        return new GeometriaRegiao(cx * sx, cy * sy, largura * sx, altura * sy, angulo);
    }

    public RegiaoPlaca regiao(double margem, int limiteLargura, int limiteAltura, double pontuacao,
                              String origem, int componentes, double densidade) {
        double fator = 1 + margem * 2;
        List<Vertice> vertices = Arrays.stream(vertices(fator, fator)).map(p -> new Vertice(
                Math.max(0, Math.min(limiteLargura - 1, p.x)), Math.max(0, Math.min(limiteAltura - 1, p.y)))).toList();
        int x = (int) Math.floor(vertices.stream().mapToDouble(Vertice::x).min().orElseThrow());
        int y = (int) Math.floor(vertices.stream().mapToDouble(Vertice::y).min().orElseThrow());
        int direita = Math.min(limiteLargura, (int) Math.ceil(vertices.stream().mapToDouble(Vertice::x).max().orElseThrow()) + 1);
        int base = Math.min(limiteAltura, (int) Math.ceil(vertices.stream().mapToDouble(Vertice::y).max().orElseThrow()) + 1);
        return new RegiaoPlaca(x, y, direita - x, base - y, pontuacao, angulo, vertices, origem, componentes, densidade);
    }
}
