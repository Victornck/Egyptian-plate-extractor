package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.model.RegiaoPlaca;
import br.edu.uninta.placas.utils.RecursosNativos;
import java.util.*;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public final class DeteccaoPlaca {
    public record Deteccao(Optional<RegiaoPlaca> melhor, List<RegiaoPlaca> candidatas, boolean limiteAtingido) { }
    private final AnaliseTextura textura = new AnaliseTextura();
    private final ExtracaoPlaca extracao = new ExtracaoPlaca();

    public Deteccao detectar(Mat imagem, Mat cinza, Mat bordas, Mat morfologia,
                             int larguraOriginal, int alturaOriginal, ConfiguracaoProcessamento config) {
        List<RegiaoPlaca> candidatas = new ArrayList<>();
        boolean limitado;
        try (RecursosNativos r = new RecursosNativos()) {
            Mat clara = r.nova();
            Imgproc.threshold(cinza, clara, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
            Mat kernel = r.registrar(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3)));
            Mat unidas = r.nova();
            Imgproc.morphologyEx(bordas, unidas, Imgproc.MORPH_CLOSE, kernel);
            Mat hsv = r.nova();
            Imgproc.cvtColor(imagem, hsv, Imgproc.COLOR_BGR2HSV);
            Mat colorida = r.nova();
            Mat kernelFaixa = r.registrar(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 3)));
            limitado = buscar(clara, "limiar", false, imagem, larguraOriginal, alturaOriginal, config, candidatas);
            limitado |= buscar(unidas, "bordas", false, imagem, larguraOriginal, alturaOriginal, config, candidatas);
            limitado |= buscar(morfologia, "morfologia", false, imagem, larguraOriginal, alturaOriginal, config, candidatas);
            for (int[] intervalo : new int[][]{{85, 135}, {35, 84}, {0, 34}, {136, 179}}) {
                Core.inRange(hsv, new Scalar(intervalo[0], config.valor("saturacaoMinima"), config.valor("brilhoCorMinimo")),
                        new Scalar(intervalo[1], 255, 255), colorida);
                Imgproc.morphologyEx(colorida, colorida, Imgproc.MORPH_CLOSE, kernelFaixa);
                limitado |= buscar(colorida, "faixa", true, imagem, larguraOriginal, alturaOriginal, config, candidatas);
            }
        }
        candidatas.sort(Comparator.comparingDouble(RegiaoPlaca::pontuacao).reversed());
        List<RegiaoPlaca> unicas = new ArrayList<>();
        for (RegiaoPlaca candidata : candidatas) {
            if (unicas.stream().noneMatch(existente -> intersecao(existente, candidata) > 0.65)) unicas.add(candidata);
        }
        Optional<RegiaoPlaca> melhor = unicas.stream().filter(c -> c.pontuacao() >= config.valor("pontuacaoMinima")).findFirst();
        return new Deteccao(melhor, List.copyOf(unicas), limitado);
    }

    private boolean buscar(Mat mascara, String origem, boolean faixa, Mat imagem, int originalW, int originalH,
                           ConfiguracaoProcessamento config, List<RegiaoPlaca> candidatas) {
        List<MatOfPoint> contornos = new ArrayList<>();
        try (RecursosNativos r = new RecursosNativos()) {
            Mat hierarquia = r.nova();
            Imgproc.findContours(mascara, contornos, hierarquia, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
            contornos.sort(Comparator.comparingDouble((MatOfPoint c) -> Imgproc.contourArea(c)).reversed());
            int limite = Math.min(contornos.size(), config.inteiro("limiteContornos"));
            for (int i = 0; i < limite; i++) {
                MatOfPoint contorno = contornos.get(i);
                if (contorno.total() < 4) continue;
                MatOfPoint2f pontos = new MatOfPoint2f(contorno.toArray());
                RotatedRect retangulo;
                try { retangulo = Imgproc.minAreaRect(pontos); } finally { pontos.release(); }
                double w = retangulo.size.width;
                double h = retangulo.size.height;
                double angulo = retangulo.angle;
                if (w < h) { double troca = w; w = h; h = troca; angulo += 90; }
                while (angulo >= 90) angulo -= 180;
                while (angulo < -90) angulo += 180;
                if (h < 2 || Math.abs(angulo) > config.valor("anguloMaximo")) continue;
                double preenchimento = Imgproc.contourArea(contorno) / (w * h);
                if (preenchimento < config.valor("retangularidadeMinima")) continue;
                if (faixa) {
                    if (w / h < 2.5 || w / h > 16) continue;
                    for (double proporcao : new double[]{1.8, 2.06, 2.4, 2.8, 3.4}) {
                        double altura = w / proporcao;
                        if (altura < h * 1.4 || altura > h * 6) continue;
                        double deslocamento = (altura - h) * 0.5;
                        double cx = retangulo.center.x - Math.sin(Math.toRadians(angulo)) * deslocamento;
                        double cy = retangulo.center.y + Math.cos(Math.toRadians(angulo)) * deslocamento;
                        avaliar(cx, cy, w, altura, angulo, preenchimento, origem, imagem, originalW, originalH, config, candidatas);
                    }
                } else {
                    avaliar(retangulo.center.x, retangulo.center.y, w, h, angulo, preenchimento, origem,
                            imagem, originalW, originalH, config, candidatas);
                }
            }
            return contornos.size() > limite;
        } finally { contornos.forEach(Mat::release); }
    }

    private void avaliar(double cx, double cy, double w, double h, double angulo, double preenchimento, String origem,
                         Mat imagem, int originalW, int originalH, ConfiguracaoProcessamento c, List<RegiaoPlaca> candidatas) {
        double proporcao = w / h;
        double area = w * h / imagem.total();
        if (w < c.valor("larguraMinima") || h < c.valor("alturaMinima")
                || proporcao < c.valor("proporcaoMinima") || proporcao > c.valor("proporcaoMaxima")
                || area < c.valor("areaMinima") || area > c.valor("areaMaxima")) return;
        Point[] pontos = ExtracaoPlaca.vertices(cx, cy, w, h, angulo);
        if (Arrays.stream(pontos).anyMatch(p -> p.x < 0 || p.y < 0 || p.x >= imagem.cols() || p.y >= imagem.rows())) return;
        AnaliseTextura.Medidas m;
        try (RecursosNativos r = new RecursosNativos()) {
            Mat recorte = r.registrar(extracao.retificar(imagem, pontos, Math.max(2, (int) w), Math.max(2, (int) h)));
            m = textura.medir(recorte, c);
        }
        if (m.contraste() < c.valor("contrasteMinimo") || m.densidade() < c.valor("densidadeBordasMinima")
                || m.densidade() > c.valor("densidadeBordasMaxima") || m.componentes() < c.inteiro("componentesMinimos")) return;
        double geometria = Math.exp(-Math.abs(Math.log(proporcao / c.valor("proporcaoPreferida"))));
        double caracteres = Math.min(1, m.componentes() / 7.0);
        if (m.componentes() > 20) caracteres *= 0.6;
        double densidade = Math.max(0, 1 - Math.abs(m.densidade() - 0.18) / 0.23);
        double pontuacao = 0.18 * geometria + 0.28 * caracteres + 0.16 * densidade
                + 0.12 * Math.min(1, m.contraste() / 130) + 0.07 * Math.min(1, preenchimento)
                + 0.07 * Math.min(1, m.claro() / 0.55) + c.valor("pesoFaixaColorida") * m.faixaColorida();
        pontuacao /= 0.88 + c.valor("pesoFaixaColorida");
        GeometriaRegiao forma = new GeometriaRegiao(cx, cy, w, h, angulo)
                .escalada(originalW / (double) imagem.cols(), originalH / (double) imagem.rows());
        candidatas.add(forma.regiao(c.valor("margemRecorte"), originalW, originalH, pontuacao, origem, m.componentes(), m.densidade()));
    }

    private double intersecao(RegiaoPlaca a, RegiaoPlaca b) {
        double w = Math.max(0, Math.min(a.x() + a.largura(), b.x() + b.largura()) - Math.max(a.x(), b.x()));
        double h = Math.max(0, Math.min(a.y() + a.altura(), b.y() + b.altura()) - Math.max(a.y(), b.y()));
        return w * h / ((double) a.largura() * a.altura() + (double) b.largura() * b.altura() - w * h);
    }
}
