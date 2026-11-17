package br.edu.uninta.placas.processamento;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public final class FiltroGaussiano {
    public void aplicar(Mat entrada, Mat saida, int kernel) {
        Imgproc.GaussianBlur(entrada, saida, new Size(kernel, kernel), 0);
    }
}
