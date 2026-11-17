package br.edu.uninta.placas.model;

import java.util.List;
import org.opencv.core.Point;
import org.opencv.core.Rect;

public record RegiaoPlaca(int x, int y, int largura, int altura, double pontuacao,
                         double angulo, List<Vertice> vertices, String origem,
                         int componentes, double densidadeBordas) {
    public record Vertice(double x, double y) { }
    public RegiaoPlaca { vertices = List.copyOf(vertices); }
    public Rect retangulo() { return new Rect(x, y, largura, altura); }
    public Point[] pontos() { return vertices.stream().map(v -> new Point(v.x(), v.y())).toArray(Point[]::new); }
}
