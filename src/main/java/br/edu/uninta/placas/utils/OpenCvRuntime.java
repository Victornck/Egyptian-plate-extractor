package br.edu.uninta.placas.utils;

import nu.pattern.OpenCV;

public final class OpenCvRuntime {
    private static boolean carregado;

    private OpenCvRuntime() { }

    public static synchronized void carregar() {
        if (!carregado) {
            OpenCV.loadLocally();
            carregado = true;
        }
    }
}
