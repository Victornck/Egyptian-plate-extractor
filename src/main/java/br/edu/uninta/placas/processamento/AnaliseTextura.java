package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.utils.RecursosNativos;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public final class AnaliseTextura {
    public record Medidas(double contraste, double densidade, int componentes, double faixaColorida, double claro) { }

    public Medidas medir(Mat recorte, ConfiguracaoProcessamento config) {
        try (RecursosNativos r = new RecursosNativos()) {
            Mat normalizada = r.nova();
            int altura = Math.max(35, (int) Math.round(240.0 * recorte.rows() / recorte.cols()));
            Imgproc.resize(recorte, normalizada, new Size(240, altura));
            Mat cinza = r.registrar(new ConversaoCinza().converter(normalizada));
            Mat interior = r.registrar(cinza.submat(new Rect(8, 4, cinza.cols() - 16, cinza.rows() - 8)));
            byte[] pixels = new byte[(int) interior.total()];
            interior.get(0, 0, pixels);
            int[] histograma = new int[256];
            for (byte pixel : pixels) histograma[Byte.toUnsignedInt(pixel)]++;
            int inferior = quantil(histograma, pixels.length, 0.10);
            int superior = quantil(histograma, pixels.length, 0.90);
            Mat bordas = r.nova();
            Imgproc.Canny(interior, bordas, config.valor("cannyInferior"), config.valor("cannySuperior"));
            double densidade = Core.countNonZero(bordas) / (double) bordas.total();
            Mat binaria = r.nova();
            Imgproc.threshold(cinza, binaria, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
            Mat rotulos = r.nova();
            Mat estatisticas = r.nova();
            Mat centroides = r.nova();
            int total = Imgproc.connectedComponentsWithStats(binaria, rotulos, estatisticas, centroides);
            int componentes = 0;
            for (int i = 1; i < total; i++) {
                int x = (int) estatisticas.get(i, Imgproc.CC_STAT_LEFT)[0];
                int y = (int) estatisticas.get(i, Imgproc.CC_STAT_TOP)[0];
                int w = (int) estatisticas.get(i, Imgproc.CC_STAT_WIDTH)[0];
                int h = (int) estatisticas.get(i, Imgproc.CC_STAT_HEIGHT)[0];
                int area = (int) estatisticas.get(i, Imgproc.CC_STAT_AREA)[0];
                double preenchimento = area / (double) (w * h);
                if (x > 2 && x + w < 238 && y > altura * 0.12 && y + h < altura - 1
                        && h > altura * 0.17 && h < altura * 0.75 && w >= 2 && w < 240 * 0.23
                        && h / (double) w > 0.65 && h / (double) w < 12 && preenchimento > 0.12 && preenchimento < 0.95) componentes++;
            }
            byte[] bgr = new byte[(int) normalizada.total() * 3];
            normalizada.get(0, 0, bgr);
            int coloridosTopo = 0;
            int coloridosBase = 0;
            int claros = 0;
            for (int y = 0; y < altura; y++) {
                for (int x = 0; x < 240; x++) {
                    int offset = (y * 240 + x) * 3;
                    int b = Byte.toUnsignedInt(bgr[offset]);
                    int g = Byte.toUnsignedInt(bgr[offset + 1]);
                    int v = Byte.toUnsignedInt(bgr[offset + 2]);
                    int max = Math.max(b, Math.max(g, v));
                    int min = Math.min(b, Math.min(g, v));
                    if (max > 50 && max - min > 45 && (max - min) / (double) max > 0.35) {
                        if (y < altura * 0.4) coloridosTopo++;
                        else coloridosBase++;
                    }
                    if (y >= altura * 0.4 && max - min < 55 && max > 100) claros++;
                }
            }
            double topo = coloridosTopo / (240.0 * altura * 0.4);
            double base = coloridosBase / (240.0 * altura * 0.6);
            return new Medidas(superior - inferior, densidade, componentes,
                    Math.max(0, Math.min(1, (topo - base) * 2)), claros / (240.0 * altura * 0.6));
        }
    }

    private int quantil(int[] histograma, int total, double fracao) {
        int soma = 0;
        for (int i = 0; i < histograma.length; i++) {
            soma += histograma[i];
            if (soma >= total * fracao) return i;
        }
        return 255;
    }
}
