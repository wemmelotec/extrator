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

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Service
public class ExtratorDecisaoService {

    public enum OrigemExtracao {
        PDFBOX,
        TESSERACT
    }

    public static final class ResultadoExtracao {
        private final String texto;
        private final OrigemExtracao origem;

        public ResultadoExtracao(String texto, OrigemExtracao origem) {
            this.texto = texto;
            this.origem = origem;
        }

        public String getTexto() {
            return texto;
        }

        public OrigemExtracao getOrigem() {
            return origem;
        }
    }

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

    /**
     * Extrai texto de um arquivo local usando PDFBox ou Tesseract, conforme o
     * tipo de arquivo e a qualidade do texto nativo.
     *
     * @param caminhoArquivo caminho absoluto do arquivo no filesystem
     * @return resultado com texto pos-processado e origem da extração
     * @throws IOException quando o arquivo nao pode ser lido
     * @throws TesseractException quando o OCR falha na leitura da imagem
     */
    public ResultadoExtracao extrairDeArquivoLocal(String caminhoArquivo) throws IOException, TesseractException {
        java.nio.file.Path path = java.nio.file.Paths.get(caminhoArquivo);
        byte[] conteudoArquivo = java.nio.file.Files.readAllBytes(path);

        ITesseract tesseract = criarTesseract();
        String textoBruto;
        OrigemExtracao origem;

        if (caminhoArquivo.toLowerCase().endsWith(".pdf")) {
            try (PDDocument documento = Loader.loadPDF(conteudoArquivo)) {
                if (usarTextoNativoPdf) {
                    String textoNativo = extrairTextoNativoDoPdf(documento);
                    if (textoNativoTemQualidadeMinima(textoNativo)) {
                        textoBruto = textoNativo;
                        origem = OrigemExtracao.PDFBOX;
                    } else {
                        textoBruto = extrairTextoPdfViaOcr(documento, tesseract);
                        origem = OrigemExtracao.TESSERACT;
                    }
                } else {
                    textoBruto = extrairTextoPdfViaOcr(documento, tesseract);
                    origem = OrigemExtracao.TESSERACT;
                }
            }
        } else {
            Mat imagemOriginal = imdecode(new Mat(conteudoArquivo), opencv_imgcodecs.IMREAD_COLOR);
            if (imagemOriginal.empty()) {
                throw new BadRequestException("Formato de arquivo nao suportado");
            }
            BufferedImage imagemPreProcessada = preProcessarImagemParaOcr(matToBufferedImage(imagemOriginal));
            textoBruto = executarOcr(imagemPreProcessada, tesseract);
            origem = OrigemExtracao.TESSERACT;
        }

        return new ResultadoExtracao(posProcessarTexto(textoBruto), origem);
    }

    /**
     * Le o texto vetorial do PDF sem OCR para preservar acentuacao e estrutura.
     *
     * @param documento PDF carregado em memoria
     * @return texto nativo extraido do PDF
     * @throws IOException quando o PDF nao pode ser processado
     */
    private String extrairTextoNativoDoPdf(PDDocument documento) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(documento);
    }

    /**
     * Valida se o texto nativo possui volume minimo para ser confiavel.
     *
     * @param texto texto nativo lido do PDF
     * @return true quando o texto atinge o minimo esperado de qualidade
     */
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

    /**
     * Renderiza cada pagina do PDF em imagem e executa OCR pagina a pagina.
     *
     * @param documento PDF aberto para processamento
     * @param tesseract instancia configurada do OCR
     * @return texto concatenado das paginas reconhecidas
     * @throws IOException quando a renderizacao falha
     * @throws TesseractException quando o OCR falha
     */
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

    /**
     * Cria uma instancia do Tesseract configurada para documentos em portugues.
     *
     * @return instancia pronta para OCR
     */
    private ITesseract criarTesseract() {
        Tesseract tesseract = new Tesseract();
        
        tesseract.setDatapath(tessdataPath);
        
        tesseract.setLanguage(language);
        
        tesseract.setPageSegMode(pageSegMode);
        
        tesseract.setTessVariable("user_defined_dpi", String.valueOf(pdfRenderDpi));
        
        tesseract.setTessVariable("preserve_interword_spaces", "1");
        
        return tesseract;
    }

    /**
     * Aplica a pipeline de pre-processamento para melhorar a leitura do OCR.
     *
     * @param imagemOriginal imagem original carregada do PDF ou do arquivo
     * @return imagem preparada para OCR
     */
    private BufferedImage preProcessarImagemParaOcr(BufferedImage imagemOriginal) {
        Mat imagemMat = bufferedImageToMat(imagemOriginal);
        Mat imagemCinza = garantirEscalaCinza(imagemMat);
        Mat imagemSemRuido = reduzirRuido(imagemCinza);
        Mat imagemBinarizada = binarizarComOtsu(imagemSemRuido);
        Mat imagemReforcada = reforcarCaracteres(imagemBinarizada);
        Mat imagemEscalada = escalarParaOcr(imagemReforcada);
        return matToBufferedImage(imagemEscalada);
    }

    /**
     * Garante uma imagem em escala de cinza para as etapas de limiarizacao.
     *
     * @param imagemOriginal imagem de entrada no formato Mat
     * @return matriz em tons de cinza
     */
    private Mat garantirEscalaCinza(Mat imagemOriginal) {
        if (imagemOriginal.channels() == 1) {
            return imagemOriginal.clone();
        }

        Mat imagemCinza = new Mat();
        opencv_imgproc.cvtColor(imagemOriginal, imagemCinza, opencv_imgproc.COLOR_BGR2GRAY);
        return imagemCinza;
    }

    /**
     * Remove ruido fino preservando o contorno de letras e simbolos.
     *
     * @param imagemCinza imagem ja convertida para escala de cinza
     * @return imagem com menos ruido visual
     */
    private Mat reduzirRuido(Mat imagemCinza) {
        Mat imagemFiltrada = new Mat();
        opencv_imgproc.medianBlur(imagemCinza, imagemFiltrada, 3);
        return imagemFiltrada;
    }

    /**
     * Separa texto e fundo usando threshold automatico de Otsu.
     *
     * @param imagemCinza imagem em escala de cinza
     * @return imagem binarizada
     */
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

    /**
     * Fecha pequenas falhas nos caracteres para reduzir letras quebradas.
     *
     * @param imagemBinarizada imagem binarizada apos Otsu
     * @return imagem com caracteres reforcados
     */
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

    /**
     * Amplia a imagem para aumentar detalhes de acentos e caracteres pequenos.
     *
     * @param imagemBinarizada imagem apos a etapa de reforco
     * @return imagem redimensionada para OCR
     */
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

    /**
     * Executa OCR na imagem pre-processada e retorna o texto bruto.
     *
     * @param imagemPreProcessada imagem preparada para OCR
     * @param tesseract instancia configurada do motor OCR
     * @return texto reconhecido pela engine
     * @throws TesseractException quando a leitura falha
     */
    private String executarOcr(BufferedImage imagemPreProcessada, ITesseract tesseract)
            throws TesseractException {
        return tesseract.doOCR(imagemPreProcessada);
    }

    /**
     * Remove ruido de OCR e recompoe a estrutura textual do documento.
     *
     * @param textoBruto texto original extraido pela engine
     * @return texto limpo e reorganizado para consulta
     */
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

    /**
     * Corrige colagens recorrentes observadas em decisoes OCRizadas.
     *
     * @param texto texto de entrada
     * @return texto com ajustes pontuais de palavras coladas
     */
    private String corrigirPalavrasColadasComuns(String texto) {
        String ajustado = texto;
        ajustado = ajustado.replaceAll("(?iu)Estadomembro", "Estado-membro");
        ajustado = ajustado.replaceAll("(?iu)atividadefim", "atividade fim");
        ajustado = ajustado.replaceAll("(?iu)cabendome", "cabendo-me");
        return ajustado;
    }

    /**
     * Une linhas quebradas no meio da frase e preserva blocos semanticos.
     *
     * @param texto texto pos-OCR com quebras de linha misturadas
     * @return texto reorganizado em blocos mais legiveis
     */
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

    /**
     * Insere quebras antes de rotulos relevantes para facilitar a leitura.
     *
     * @param texto texto a ser ajustado
     * @return texto com marcadores destacados em novas linhas
     */
    private String reintroduzirQuebrasPorMarcadores(String texto) {
        String resultado = texto;
        for (String marcador : MARCADORES_DECISAO) {
            resultado = resultado.replaceAll("\\s+(" + Pattern.quote(marcador) + ")\\s+", "\\n$1 ");
        }
        return resultado;
    }

    /**
     * Detecta se uma linha inicia com um marcador conhecido do documento.
     *
     * @param linha linha textual a ser avaliada
     * @return true quando a linha comeca com um marcador conhecido
     */
    private boolean comecaComMarcador(String linha) {
        String normalizada = linha.toUpperCase(Locale.ROOT);
        for (String marcador : MARCADORES_DECISAO) {
            if (normalizada.startsWith(marcador)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converte Mat do OpenCV em BufferedImage para uso no Tesseract.
     *
     * @param imagemMat matriz OpenCV de entrada
     * @return imagem convertida para BufferedImage
     */
    private static BufferedImage matToBufferedImage(Mat imagemMat) {
        try (OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
             Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter()) {
            return converterToBufferedImage.convert(converterToMat.convert(imagemMat));
        }
    }

    /**
     * Converte BufferedImage em Mat do OpenCV para aplicar os filtros.
     *
     * @param bufferedImage imagem em memoria no formato BufferedImage
     * @return matriz OpenCV correspondente
     */
    private static Mat bufferedImageToMat(BufferedImage bufferedImage) {
        try (Java2DFrameConverter converterToFrame = new Java2DFrameConverter();
             OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat()) {
            Mat converted = converterToMat.convert(converterToFrame.convert(bufferedImage));
            return converted.clone();
        }
    }
}
