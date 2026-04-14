package com.br.extrator.service;

import org.apache.coyote.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Locale;

@Service
public class ExtratorService {

	@Value("${ocr.tesseract.datapath}")
	private String tessdataPath;

	@Value("${ocr.tesseract.language:eng}")
	private String language;

	@Value("${ocr.tesseract.page-seg-mode:6}")
	private int pageSegMode;

	@Value("${ocr.pdf.render-dpi:300}")
	private int pdfRenderDpi;
	
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

	private String extrairTextoDeImagem(MultipartFile file, ITesseract tesseract) throws IOException, TesseractException {
		Mat imagemOriginal = carregarImagemDoUpload(file);
		BufferedImage imagemPreProcessada = preProcessarImagemParaOcr(matToBufferedImage(imagemOriginal));
		return executarOcr(imagemPreProcessada, tesseract);
	}

	private String extrairTextoDePdf(MultipartFile file, ITesseract tesseract) throws IOException, TesseractException {
		StringBuilder texto = new StringBuilder();

		try (PDDocument documento = Loader.loadPDF(file.getBytes())) {
			PDFRenderer renderer = new PDFRenderer(documento);

			for (int pagina = 0; pagina < documento.getNumberOfPages(); pagina++) {
				BufferedImage paginaRenderizada = renderer.renderImageWithDPI(pagina, pdfRenderDpi);
				BufferedImage paginaPreProcessada = preProcessarImagemParaOcr(paginaRenderizada);
				String textoPagina = executarOcr(paginaPreProcessada, tesseract);

				if (!textoPagina.isBlank()) {
					if (texto.length() > 0) {
						texto.append("\n\n");
					}
					texto.append(textoPagina);
				}
			}
		}

		return texto.toString();
	}

	private Mat carregarImagemDoUpload(MultipartFile file) throws IOException, BadRequestException {
		Mat image = imdecode(new Mat(file.getBytes()), opencv_imgcodecs.IMREAD_COLOR);
		if (image.empty()) {
			throw new BadRequestException("Formato de arquivo nao suportado");
		}
		return image;
	}

	private boolean ehPdf(MultipartFile file) {
		String fileName = file.getOriginalFilename();
		String contentType = file.getContentType();
		return (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("pdf"))
				|| (fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf"));
	}

	private ITesseract criarTesseract() {
		ITesseract tesseract = new Tesseract();
		tesseract.setDatapath(tessdataPath);
		tesseract.setLanguage(language);
		tesseract.setPageSegMode(pageSegMode);
		tesseract.setTessVariable("user_defined_dpi", "300");
		return tesseract;
	}

	// Etapa de pre-processamento para aumentar contraste e legibilidade do OCR.
	private BufferedImage preProcessarImagemParaOcr(BufferedImage imagemOriginal) {
		Mat imagemMat = bufferedImageToMat(imagemOriginal);
		Mat imagemCinza = converterParaEscalaCinza(imagemMat);
		Mat imagemSuavizada = reduzirRuido(imagemCinza);
		Mat imagemBinarizada = binarizarParaRealcarTexto(imagemSuavizada);
		Mat imagemEscalada = escalarParaOcr(imagemBinarizada);
		return matToBufferedImage(imagemEscalada);
	}

	private Mat converterParaEscalaCinza(Mat imagemColorida) {
		Mat imagemCinza = new Mat();
		opencv_imgproc.cvtColor(imagemColorida, imagemCinza, opencv_imgproc.COLOR_BGR2GRAY);
		return imagemCinza;
	}

	private Mat reduzirRuido(Mat imagemCinza) {
		Mat imagemSuavizada = new Mat();
		opencv_imgproc.GaussianBlur(imagemCinza, imagemSuavizada, new Size(3, 3), 0);
		return imagemSuavizada;
	}

	private Mat binarizarParaRealcarTexto(Mat imagemSuavizada) {
		Mat imagemBinarizada = new Mat();
		opencv_imgproc.adaptiveThreshold(
				imagemSuavizada,
				imagemBinarizada,
				255,
				opencv_imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
				opencv_imgproc.THRESH_BINARY,
				31,
				15
		);
		return imagemBinarizada;
	}

	private Mat escalarParaOcr(Mat imagemBinarizada) {
		Mat imagemEscalada = new Mat();
		opencv_imgproc.resize(
				imagemBinarizada,
				imagemEscalada,
				new Size(imagemBinarizada.cols() * 2, imagemBinarizada.rows() * 2),
				0,
				0,
				opencv_imgproc.INTER_CUBIC
		);
		return imagemEscalada;
	}

	// Momento em que a imagem pre-processada e enviada ao Tesseract para OCR.
	private String executarOcr(BufferedImage imagemPreProcessada, ITesseract tesseract) throws TesseractException {
		return tesseract.doOCR(imagemPreProcessada);
	}

	// Etapa de pos-processamento para limpar ruido de OCR e normalizar o texto final.
	private String posProcessarTexto(String textoBruto) {
		if (textoBruto == null || textoBruto.isBlank()) {
			return "";
		}

		String textoSemHifenQuebra = textoBruto.replaceAll("-\\R\\s*", "");
		String textoComQuebraPadrao = textoSemHifenQuebra.replace("\r\n", "\n").replace("\r", "\n");
		String textoSemEspacosDuplicados = textoComQuebraPadrao.replaceAll("[ \\t]{2,}", " ");
		String textoSemLinhasVaziasExcesso = textoSemEspacosDuplicados.replaceAll("\\n{3,}", "\n\n");
		return textoSemLinhasVaziasExcesso.trim();
	}

	private static BufferedImage matToBufferedImage(Mat imagemMat) {
		try (OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat();
				 Java2DFrameConverter converterToBufferedImage = new Java2DFrameConverter()) {
			return converterToBufferedImage.convert(converterToMat.convert(imagemMat));
		}
	}

	private static Mat bufferedImageToMat(BufferedImage bufferedImage) {
		try (Java2DFrameConverter converterToFrame = new Java2DFrameConverter();
				 OpenCVFrameConverter.ToMat converterToMat = new OpenCVFrameConverter.ToMat()) {
			Mat converted = converterToMat.convert(converterToFrame.convert(bufferedImage));
			return converted.clone();
		}
	}

}
