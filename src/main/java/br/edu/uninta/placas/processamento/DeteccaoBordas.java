package br.edu.uninta.placas.processamento;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public final class DeteccaoBordas {
    public void aplicar(Mat entrada, Mat saida, double inferior, double superior) {
        Imgproc.Canny(entrada, saida, inferior, superior);
    }
}
