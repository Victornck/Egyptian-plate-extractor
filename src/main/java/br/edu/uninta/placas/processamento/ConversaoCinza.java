package br.edu.uninta.placas.processamento;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

public final class ConversaoCinza {
    public Mat converter(Mat imagem) {
        if (imagem.empty() || imagem.type() != CvType.CV_8UC3) throw new IllegalArgumentException("Esperada imagem BGR de 8 bits com três canais.");
        Mat cinza = new Mat(imagem.rows(), imagem.cols(), CvType.CV_8UC1);
        byte[] entrada = new byte[imagem.cols() * 3];
        byte[] saida = new byte[imagem.cols()];
        for (int y = 0; y < imagem.rows(); y++) {
            imagem.get(y, 0, entrada);
            for (int x = 0; x < imagem.cols(); x++) {
                int azul = Byte.toUnsignedInt(entrada[3 * x]);
                int verde = Byte.toUnsignedInt(entrada[3 * x + 1]);
                int vermelho = Byte.toUnsignedInt(entrada[3 * x + 2]);
                saida[x] = (byte) Math.round(0.114 * azul + 0.587 * verde + 0.299 * vermelho);
            }
            cinza.put(y, 0, saida);
        }
        return cinza;
    }
}
