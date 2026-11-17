package br.edu.uninta.placas.utils;

import java.util.ArrayList;
import java.util.List;
import org.opencv.core.Mat;

public final class RecursosNativos implements AutoCloseable {
    private final List<Mat> matrizes = new ArrayList<>();

    public <T extends Mat> T registrar(T matriz) {
        matrizes.add(matriz);
        return matriz;
    }

    public Mat nova() {
        return registrar(new Mat());
    }

    @Override
    public void close() {
        for (int i = matrizes.size() - 1; i >= 0; i--) {
            matrizes.get(i).release();
        }
        matrizes.clear();
    }
}
