package br.edu.uninta.placas.processamento;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import br.edu.uninta.placas.utils.RecursosNativos;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public final class OperacoesMorfologicas {
    public void aplicar(Mat bordas, Mat saida, ConfiguracaoProcessamento config) {
        int largura = Math.max(3, (int) Math.round(bordas.cols() * config.valor("morfologiaLargura")) | 1);
        int altura = Math.max(3, (int) Math.round(bordas.rows() * config.valor("morfologiaAltura")) | 1);
        try (RecursosNativos recursos = new RecursosNativos()) {
            Mat kernel = recursos.registrar(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(largura, altura)));
            Imgproc.morphologyEx(bordas, saida, Imgproc.MORPH_CLOSE, kernel);
        }
    }
}
