package br.edu.uninta.placas.utils;

import br.edu.uninta.placas.config.ConfiguracaoProcessamento;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.CRC32;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;

public final class GerenciadorImagem {
    public boolean formatoSuportado(Path arquivo) {
        String nome = arquivo.getFileName().toString().toLowerCase(Locale.ROOT);
        return nome.endsWith(".jpg") || nome.endsWith(".jpeg") || nome.endsWith(".png");
    }

    public Mat carregar(Path arquivo, ConfiguracaoProcessamento config) throws IOException {
        if (!Files.isRegularFile(arquivo)) throw new IOException("Arquivo inexistente ou não regular: " + arquivo);
        if (!formatoSuportado(arquivo)) throw new IOException("Formato não suportado. Utilize JPG, JPEG ou PNG: " + arquivo);
        if (Files.size(arquivo) > config.valor("limiteArquivoMB") * 1024 * 1024) throw new IOException("Arquivo excede limiteArquivoMB.");
        validarCabecalho(arquivo, config);
        byte[] conteudo = Files.readAllBytes(arquivo);
        validarEstrutura(conteudo);
        MatOfByte bytes = new MatOfByte(conteudo);
        try {
            Mat imagem = Imgcodecs.imdecode(bytes, Imgcodecs.IMREAD_COLOR | Imgcodecs.IMREAD_IGNORE_ORIENTATION);
            if (imagem.empty()) { imagem.release(); throw new IOException("Imagem corrompida ou não decodificável: " + arquivo); }
            return imagem;
        } finally { bytes.release(); }
    }

    private void validarEstrutura(byte[] dados) throws IOException {
        if (dados.length < 12) throw new IOException("Imagem truncada.");
        if ((dados[0] & 255) == 255 && (dados[1] & 255) == 216) {
            validarJpeg(dados);
            return;
        }
        int posicao = 8;
        boolean fim = false;
        while (posicao <= dados.length - 12) {
            long tamanho = Integer.toUnsignedLong(ByteBuffer.wrap(dados, posicao, 4).getInt());
            if (tamanho > dados.length - posicao - 12) throw new IOException("PNG com bloco truncado.");
            int comprimento = (int) tamanho;
            CRC32 crc = new CRC32();
            crc.update(dados, posicao + 4, comprimento + 4);
            long esperado = Integer.toUnsignedLong(ByteBuffer.wrap(dados, posicao + 8 + comprimento, 4).getInt());
            if (crc.getValue() != esperado) throw new IOException("PNG corrompido: checksum inválido.");
            String tipo = new String(dados, posicao + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            posicao += comprimento + 12;
            if (tipo.equals("IEND")) {
                if (comprimento != 0) throw new IOException("PNG com bloco final inválido.");
                fim = true;
                break;
            }
        }
        if (!fim || posicao != dados.length) throw new IOException("PNG incompleto ou com conteúdo extra.");
    }

    private void validarJpeg(byte[] dados) throws IOException {
        int posicao = 2;
        while (posicao < dados.length) {
            if ((dados[posicao++] & 255) != 255) throw new IOException("JPEG com marcador inválido.");
            while (posicao < dados.length && (dados[posicao] & 255) == 255) posicao++;
            if (posicao >= dados.length) break;
            int marcador = dados[posicao++] & 255;
            if (marcador == 217) return;
            if (marcador == 0 || marcador == 216) throw new IOException("Estrutura JPEG inválida.");
            if (marcador == 1 || (marcador >= 208 && marcador <= 215)) continue;
            if (posicao > dados.length - 2) break;
            int tamanho = ((dados[posicao] & 255) << 8) | (dados[posicao + 1] & 255);
            if (tamanho < 2 || tamanho > dados.length - posicao) throw new IOException("JPEG com segmento truncado.");
            posicao += tamanho;
            if (marcador == 218) {
                while (posicao < dados.length) {
                    if ((dados[posicao] & 255) != 255) { posicao++; continue; }
                    int inicio = posicao++;
                    while (posicao < dados.length && (dados[posicao] & 255) == 255) posicao++;
                    if (posicao >= dados.length) break;
                    int proximo = dados[posicao] & 255;
                    if (proximo == 0 || (proximo >= 208 && proximo <= 215)) { posicao++; continue; }
                    posicao = inicio;
                    break;
                }
            }
        }
        throw new IOException("JPEG sem marcador final; arquivo truncado.");
    }

    private void validarCabecalho(Path arquivo, ConfiguracaoProcessamento config) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(arquivo.toFile())) {
            if (stream == null) throw new IOException("Não foi possível ler a imagem: " + arquivo);
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(stream);
            if (!leitores.hasNext()) throw new IOException("Imagem corrompida ou conteúdo não suportado: " + arquivo);
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(stream);
                String formato = leitor.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("jpeg") && !formato.equals("png")) throw new IOException("Conteúdo deve ser JPEG ou PNG.");
                long pixels = (long) leitor.getWidth(0) * leitor.getHeight(0);
                if (pixels <= 0 || pixels > config.valor("limiteMegapixels") * 1_000_000) throw new IOException("Dimensões inválidas ou acima de limiteMegapixels.");
            } finally { leitor.dispose(); }
        }
    }

    public void salvar(Path arquivo, Mat imagem) throws IOException {
        if (imagem.empty()) throw new IOException("Não é possível salvar uma imagem vazia.");
        Files.createDirectories(arquivo.toAbsolutePath().getParent());
        MatOfByte bytes = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".png", imagem, bytes)) throw new IOException("OpenCV não conseguiu codificar a imagem.");
            Files.write(arquivo, bytes.toArray());
            if (Files.size(arquivo) == 0) throw new IOException("Arquivo de saída vazio: " + arquivo);
        } finally { bytes.release(); }
    }
}
