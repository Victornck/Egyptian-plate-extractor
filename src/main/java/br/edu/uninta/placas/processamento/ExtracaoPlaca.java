package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.utils.RecursosNativos;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public final class ExtracaoPlaca {
    public Mat extrair(Mat original, RegiaoPlaca regiao) {
        Point[] p = regiao.pontos();
        int largura = Math.max(1, (int) Math.round(Math.hypot(p[1].x - p[0].x, p[1].y - p[0].y)));
        int altura = Math.max(1, (int) Math.round(Math.hypot(p[3].x - p[0].x, p[3].y - p[0].y)));
        return retificar(original, p, largura, altura);
    }

    public Mat retificar(Mat imagem, Point[] pontos, int largura, int altura) {
        try (RecursosNativos recursos = new RecursosNativos()) {
            MatOfPoint2f origem = recursos.registrar(new MatOfPoint2f(pontos));
            MatOfPoint2f destino = recursos.registrar(new MatOfPoint2f(new Point(0, 0), new Point(largura - 1, 0),
                    new Point(largura - 1, altura - 1), new Point(0, altura - 1)));
            Mat transformacao = recursos.registrar(Imgproc.getPerspectiveTransform(origem, destino));
            Mat saida = new Mat();
            try {
                Imgproc.warpPerspective(imagem, saida, transformacao, new Size(largura, altura), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE);
                return saida;
            } catch (RuntimeException e) { saida.release(); throw e; }
        }
    }

    public static Point[] vertices(double cx, double cy, double largura, double altura, double angulo) {
        double c = Math.cos(Math.toRadians(angulo));
        double s = Math.sin(Math.toRadians(angulo));
        double[][] locais = {{-0.5, -0.5}, {0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}};
        Point[] pontos = new Point[4];
        for (int i = 0; i < 4; i++) {
            double x = locais[i][0] * largura;
            double y = locais[i][1] * altura;
            pontos[i] = new Point(cx + x * c - y * s, cy + x * s + y * c);
        }
        return pontos;
    }
}
