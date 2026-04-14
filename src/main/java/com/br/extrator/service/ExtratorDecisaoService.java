package com.br.extrator.service;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.coyote.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class ExtratorDecisaoService {

    private static final Pattern CONTROLE_OCR = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFE\\uFFFF]");
    private static final Pattern ESPACOS_REPETIDOS = Pattern.compile("[ \\t]{2,}");
    private static final Pattern LINHAS_VAZIAS_EXCESSO = Pattern.compile("\\n{3,}");
    private static final Pattern HIFEN_QUEBRA_LINHA = Pattern.compile("(\\p{L}|\\d)-\\n(\\p{L}|\\d)");
    private static final Pattern N_ANTES_ROTULO = Pattern.compile("(?iu)([\\p{L}\\d])n(?=(PROCESSO\\s+N[\\u00BA\\u00B0O]|CCICMS\\b|GEJUP\\b))");
    private static final Pattern ROTULO_COLADO_APOS_PONTUACAO = Pattern.compile("(?iu)([,:;])(?=(PROCESSO\\s+N[\\u00BA\\u00B0O]|CCICMS\\b|GEJUP\\b))");

    private static final List<String> MARCADORES_DECISAO = Arrays.asList(
            "PROCESSO",
            "AUTUADO",
            "CCICMS",
            "ENDERECO",
            "PREPARADORA",
            "AUTUANTE",
            "JULGADOR FISCAL",
            "AUTO DE INFRACAO",
            "EX POSITIS",
            "GEJUP"
    );

    @Value("${ocr.tesseract.datapath}")
    private String tessdataPath;

    @Value("${ocr.tesseract.language:por+eng}")
    private String language;

    @Value("${ocr.tesseract.page-seg-mode:1}")
    private int pageSegMode;

    @Value("${ocr.pdf.render-dpi:350}")
    private int pdfRenderDpi;

    @Value("${ocr.pdf.usar-texto-nativo:true}")
    private boolean usarTextoNativoPdf;

    @Value("${ocr.pdf.texto-nativo.min-chars:120}")
    private int textoNativoMinChars;

    @Value("${ocr.pdf.texto-nativo.min-letters:60}")
    private int textoNativoMinLetters;

    @Value("${ocr.image.scale-factor:2.0}")
    private double scaleFactor;

    /** Objetivo: Orquestrar o fluxo completo de extracao para PDF/imagem e retornar texto final. */
    public String extrair(MultipartFile file) throws IOException, TesseractException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Arquivo nao informado");
        }

        ITesseract tesseract = criarTesseract();
        String textoBruto = ehPdf(file)
                ? extrairTextoDePdf(file, tesseract)
                : extrairTextoDeImagem(file, tesseract);

        return posProcessarTexto(textoBruto);
    }

    /** Objetivo: Extrair texto de imagem enviada aplicando pre-processamento antes do OCR. */
    private String extrairTextoDeImagem(MultipartFile file, ITesseract tesseract)
            throws IOException, TesseractException {

        Mat imagemOriginal = carregarImagemDoUpload(file);
        BufferedImage imagemPreProcessada = preProcessarImagemParaOcr(matToBufferedImage(imagemOriginal));
        return executarOcr(imagemPreProcessada, tesseract);
    }

    /** Objetivo: Extrair texto de PDF usando texto nativo quando viavel e OCR como fallback. */
    private String extrairTextoDePdf(MultipartFile file, ITesseract tesseract)
            throws IOException, TesseractException {

        try (PDDocument documento = Loader.loadPDF(file.getBytes())) {
            if (usarTextoNativoPdf) {
                String textoNativo = extrairTextoNativoDoPdf(documento);
                if (textoNativoTemQualidadeMinima(textoNativo)) {
                    return textoNativo;
                }
            }

            return extrairTextoPdfViaOcr(documento, tesseract);
        }
    }

    /** Objetivo: Ler texto vetorial do PDF sem OCR para preservar acentuacao e qualidade. */
    private String extrairTextoNativoDoPdf(PDDocument documento) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(documento);
    }

    /** Objetivo: Validar se o texto nativo possui conteudo suficiente para ser confiavel. */
    private boolean textoNativoTemQualidadeMinima(String texto) {
        if (texto == null) {
            return false;
        }

        String textoLimpo = texto.replaceAll("\\s+", " ").trim();
        if (textoLimpo.length() < textoNativoMinChars) {
            return false;
        }

        long letras = textoLimpo.chars().filter(Character::isLetter).count();
        return letras >= textoNativoMinLetters;
    }

    /** Objetivo: Renderizar PDF em imagens e executar OCR pagina a pagina. */
    private String extrairTextoPdfViaOcr(PDDocument documento, ITesseract tesseract)
            throws IOException, TesseractException {

        StringBuilder texto = new StringBuilder();
        PDFRenderer renderer = new PDFRenderer(documento);

        for (int pagina = 0; pagina < documento.getNumberOfPages(); pagina++) {
            BufferedImage paginaRenderizada = renderer.renderImageWithDPI(pagina, pdfRenderDpi, ImageType.GRAY);
            BufferedImage paginaPreProcessada = preProcessarImagemParaOcr(paginaRenderizada);
            String textoPagina = executarOcr(paginaPreProcessada, tesseract);

            if (!textoPagina.isBlank()) {
                if (texto.length() > 0) {
                    texto.append("\n\n");
                }
                texto.append(textoPagina);
            }
        }

        return texto.toString();
    }

    /** Objetivo: Decodificar o upload como imagem OpenCV para processamento. */
    private Mat carregarImagemDoUpload(MultipartFile file) throws IOException, BadRequestException {
        Mat image = imdecode(new Mat(file.getBytes()), opencv_imgcodecs.IMREAD_COLOR);
        if (image.empty()) {
            throw new BadRequestException("Formato de arquivo nao suportado");
        }
        return image;
    }

    /** Objetivo: Detectar se o arquivo enviado deve seguir fluxo de PDF. */
    private boolean ehPdf(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf"))
                || (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
    }

    /** Objetivo: Criar instancia do Tesseract com parametros de qualidade para documentos. */
    private ITesseract criarTesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage(language);
        tesseract.setPageSegMode(pageSegMode);
        tesseract.setTessVariable("user_defined_dpi", String.valueOf(pdfRenderDpi));
        tesseract.setTessVariable("preserve_interword_spaces", "1");
        return tesseract;
    }

    /** Objetivo: Aplicar pipeline de pre-processamento para melhorar legibilidade no OCR. */
    private BufferedImage preProcessarImagemParaOcr(BufferedImage imagemOriginal) {
        Mat imagemMat = bufferedImageToMat(imagemOriginal);
        Mat imagemCinza = garantirEscalaCinza(imagemMat);
        Mat imagemSemRuido = reduzirRuido(imagemCinza);
        Mat imagemBinarizada = binarizarComOtsu(imagemSemRuido);
        Mat imagemReforcada = reforcarCaracteres(imagemBinarizada);
        Mat imagemEscalada = escalarParaOcr(imagemReforcada);
        return matToBufferedImage(imagemEscalada);
    }

    /** Objetivo: Garantir imagem monocromatica para operacoes de limiarizacao. */
    private Mat garantirEscalaCinza(Mat imagemOriginal) {
        if (imagemOriginal.channels() == 1) {
            return imagemOriginal.clone();
        }

        Mat imagemCinza = new Mat();
        opencv_imgproc.cvtColor(imagemOriginal, imagemCinza, opencv_imgproc.COLOR_BGR2GRAY);
        return imagemCinza;
    }

    /** Objetivo: Remover ruido fino preservando contornos de letras. */
    private Mat reduzirRuido(Mat imagemCinza) {
        Mat imagemFiltrada = new Mat();
        opencv_imgproc.medianBlur(imagemCinza, imagemFiltrada, 3);
        return imagemFiltrada;
    }

    /** Objetivo: Separar texto/fundo com threshold automatico Otsu. */
    private Mat binarizarComOtsu(Mat imagemCinza) {
        Mat imagemBinarizada = new Mat();
        opencv_imgproc.threshold(
                imagemCinza,
                imagemBinarizada,
                0,
                255,
                opencv_imgproc.THRESH_BINARY | opencv_imgproc.THRESH_OTSU
        );
        return imagemBinarizada;
    }

    /** Objetivo: Fechar falhas pequenas em caracteres para reduzir letras quebradas. */
    private Mat reforcarCaracteres(Mat imagemBinarizada) {
        Mat imagemReforcada = new Mat();
        Mat kernel = opencv_imgproc.getStructuringElement(
                opencv_imgproc.MORPH_RECT,
                new Size(2, 2),
                new Point(-1, -1)
        );
        opencv_imgproc.morphologyEx(
                imagemBinarizada,
                imagemReforcada,
                opencv_imgproc.MORPH_CLOSE,
                kernel
        );
        return imagemReforcada;
    }

    /** Objetivo: Escalar a imagem para aumentar detalhes de acentos e caracteres pequenos. */
    private Mat escalarParaOcr(Mat imagemBinarizada) {
        Mat imagemEscalada = new Mat();
        double fator = Math.max(1.0, scaleFactor);
        opencv_imgproc.resize(
                imagemBinarizada,
                imagemEscalada,
                new Size((int) (imagemBinarizada.cols() * fator), (int) (imagemBinarizada.rows() * fator)),
                0,
                0,
                opencv_imgproc.INTER_CUBIC
        );
        return imagemEscalada;
    }

    /** Objetivo: Executar OCR na imagem pre-processada e retornar texto bruto. */
    private String executarOcr(BufferedImage imagemPreProcessada, ITesseract tesseract)
            throws TesseractException {
        return tesseract.doOCR(imagemPreProcessada);
    }

    /** Objetivo: Limpar ruido de OCR e recompor estrutura textual do documento de decisao. */
    private String posProcessarTexto(String textoBruto) {
        if (textoBruto == null || textoBruto.isBlank()) {
            return "";
        }

        String texto = textoBruto.replace("\r\n", "\n").replace("\r", "\n");
        texto = CONTROLE_OCR.matcher(texto).replaceAll("");
        texto = texto.replace("￾", "");
        texto = HIFEN_QUEBRA_LINHA.matcher(texto).replaceAll("$1-$2");
        texto = N_ANTES_ROTULO.matcher(texto).replaceAll("$1\n");
        texto = ROTULO_COLADO_APOS_PONTUACAO.matcher(texto).replaceAll("$1\n");
        texto = corrigirPalavrasColadasComuns(texto);
        texto = juntarQuebrasInternasDeFrase(texto);
        texto = reintroduzirQuebrasPorMarcadores(texto);
        texto = ESPACOS_REPETIDOS.matcher(texto).replaceAll(" ");
        texto = LINHAS_VAZIAS_EXCESSO.matcher(texto).replaceAll("\n\n");
        return texto.trim();
    }

    /** Objetivo: Corrigir colagens recorrentes observadas em decisoes OCRizadas. */
    private String corrigirPalavrasColadasComuns(String texto) {
        String ajustado = texto;
        ajustado = ajustado.replaceAll("(?iu)Estadomembro", "Estado-membro");
        ajustado = ajustado.replaceAll("(?iu)atividadefim", "atividade fim");
        ajustado = ajustado.replaceAll("(?iu)cabendome", "cabendo-me");
        return ajustado;
    }

    /** Objetivo: Unir linhas quebradas no meio da frase e preservar blocos semanticos. */
    private String juntarQuebrasInternasDeFrase(String texto) {
        String[] linhas = texto.split("\\n", -1);
        StringBuilder sb = new StringBuilder();

        for (String linha : linhas) {
            String atual = linha.trim();
            if (atual.isEmpty()) {
                if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) {
                    sb.append("\n\n");
                }
                continue;
            }

            if (sb.length() == 0) {
                sb.append(atual);
                continue;
            }

            char ultimo = sb.charAt(sb.length() - 1);
            boolean terminaFrase = ultimo == ':' || ultimo == ';' || ultimo == '.' || ultimo == '!' || ultimo == '?';
            boolean iniciaMarcador = comecaComMarcador(atual);
            if (terminaFrase || iniciaMarcador) {
                sb.append('\n').append(atual);
            } else {
                sb.append(' ').append(atual);
            }
        }

        return sb.toString();
    }

    /** Objetivo: Inserir quebra antes de rotulos relevantes para melhorar leitura final. */
    private String reintroduzirQuebrasPorMarcadores(String texto) {
        String resultado = texto;
        for (String marcador : MARCADORES_DECISAO) {
            resultado = resultado.replaceAll("\\s+(" + Pattern.quote(marcador) + ")\\s+", "\\n$1 ");
        }
        return resultado;
    }

    /** Objetivo: Detectar se uma linha inicia com marcador conhecido de documento de decisao. */
    private boolean comecaComMarcador(String linha) {
        String normalizada = linha.toUpperCase(Locale.ROOT);
        for (String marcador : MARCADORES_DECISAO) {
            if (normalizada.startsWith(marcador)) {
                return true;
            }
        }
        return false;
    }

    /** Objetivo: Converter Mat OpenCV para BufferedImage exigido pelo Tesseract. */
    private static BufferedImage matToBufferedImage(Mat imagemMat) {
        try (OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
             Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter()) {
            return converterToBufferedImage.convert(converterToMat.convert(imagemMat));
        }
    }

    /** Objetivo: Converter BufferedImage para Mat OpenCV para aplicar filtros da pipeline. */
    private static Mat bufferedImageToMat(BufferedImage bufferedImage) {
        try (Java2DFrameConverter converterToFrame = new Java2DFrameConverter();
             OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat()) {
            Mat converted = converterToMat.convert(converterToFrame.convert(bufferedImage));
            return converted.clone();
        }
    }
}
