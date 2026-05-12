package com.br.extrator.service;

import com.br.extrator.config.DocumentStorageConfig;
import com.br.extrator.model.Document;
import com.br.extrator.model.StatusDocumento;
import com.br.extrator.service.search.DocumentSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para gerenciar documentos (armazenamento, persistência, busca)
 */
@Service
public class DocumentService {

    private static final Pattern PROCESSO_PATTERN = Pattern.compile(
        "(?iu)PROCESSO\\s*N[\\u00BA\\u00B0o]?\\s*[:\\-]?\\s*([0-9./\\-]{5,})"
    );

    @Autowired
    private DocumentStorageConfig storageConfig;

    @Autowired
    private ExtratorDecisaoService extratorDecisaoService;

    @Autowired(required = false)
    private DocumentSearchService documentSearchService;

    /**
     * Processa upload de arquivo: salva em disco e cria registro no banco
     */
    public Document processarUpload(MultipartFile file) throws IOException {
    	
        if (file.isEmpty()) {
        	
            throw new IllegalArgumentException("Arquivo vazio");
        }

        // Gerar caminho único para o arquivo
        String caminhoRelativo = storageConfig.generateUniqueFilePath(file.getOriginalFilename());
        Path caminhoAbsoluto = Paths.get(caminhoRelativo).toAbsolutePath();

        // Garantir que diretório existe
        storageConfig.ensureStorageDirectory();

        // Salvar arquivo em disco
        try {
            Files.createDirectories(caminhoAbsoluto.getParent());
            file.transferTo(caminhoAbsoluto.toFile());
        } catch (IOException e) {
            throw new IOException("Erro ao salvar arquivo: " + e.getMessage(), e);
        }

        // Criar registro de documento
        Document documento = new Document();
        documento.setId(UUID.randomUUID().toString());
        documento.setNomeOriginal(file.getOriginalFilename());
        documento.setCaminhoArquivo(caminhoRelativo);
        documento.setDataUpload(LocalDateTime.now());
        documento.setStatus(StatusDocumento.UPLOADING);
        documento.setTextoExtraido("");

        // Salvar documento no Elasticsearch antes de processar OCR
        salvarOuAtualizar(documento);

        // Processar OCR de forma assíncrona (por enquanto síncrono)
        processarOCR(documento, caminhoAbsoluto);

        return documento;
    }

    /**
     * Extrai texto via OCR e atualiza o documento
     */
    private void processarOCR(Document documento, Path caminhoArquivo) {
        try {
            documento.setStatus(StatusDocumento.PROCESSING);
            salvarOuAtualizar(documento);

            // Chamar serviço de extração
            ExtratorDecisaoService.ResultadoExtracao resultadoExtracao =
                    extratorDecisaoService.extrairDeArquivoLocal(caminhoArquivo.toString());

            documento.setTextoExtraido(resultadoExtracao.getTexto());
            documento.setOrigemExtracao(resultadoExtracao.getOrigem().name());
            documento.setTextoNativo(resultadoExtracao.getOrigem() == ExtratorDecisaoService.OrigemExtracao.PDFBOX);
            preencherMetadados(documento, resultadoExtracao.getTexto());
            documento.setDataProcessamento(LocalDateTime.now());
            documento.setStatus(StatusDocumento.COMPLETED);

        } catch (Exception e) {
            documento.setStatus(StatusDocumento.ERROR);
            documento.setTextoExtraido("Erro no processamento: " + e.getMessage());
            documento.setOrigemExtracao(null);
        }

        salvarOuAtualizar(documento);
    }

    /**
     * Busca todos os documentos
     */
    public List<Document> listarTodos() {
        return buscarDocumentos(null, null, null, null, null, null);
    }

    /**
     * Busca documento por ID
     */
    public Optional<Document> buscarPorId(String id) {
        if (documentSearchService != null) {
            return documentSearchService.buscarPorId(id);
        }

        return Optional.empty();
    }

    /**
     * Deleta um documento (arquivo e registro)
     */
    public boolean deletarDocumento(String id) {
        Optional<Document> documento = buscarPorId(id);
        if (documento.isPresent()) {
            try {
                Path caminhoArquivo = Paths.get(documento.get().getCaminhoArquivo()).toAbsolutePath();
                if (Files.exists(caminhoArquivo)) {
                    Files.delete(caminhoArquivo);
                }
            } catch (IOException e) {
                // Log erro mas continua com exclusão do registro
                System.err.println("Erro ao deletar arquivo: " + e.getMessage());
            }
            removerDocumento(id);
            return true;
        }
        return false;
    }

    public List<Document> buscarDocumentos(String termo,
                                           String status,
                                           String numeroProcesso,
                                           String materia,
                                           LocalDateTime dataInicio,
                                           LocalDateTime dataFim) {
        if (documentSearchService != null) {
            return documentSearchService.buscar(termo, status, numeroProcesso, materia, dataInicio, dataFim);
        }

        return List.of();
    }

    private void salvarOuAtualizar(Document documento) {
        if (documentSearchService != null) {
            documentSearchService.salvarOuAtualizar(documento);
        }
    }

    private void removerDocumento(String id) {
        if (documentSearchService != null) {
            documentSearchService.remover(id);
        }
    }

    /**
     * Extrai os metadados necessários para listagem/consulta geral da versão atual.
     */
    private void preencherMetadados(Document documento, String textoExtraido) {
        if (textoExtraido == null || textoExtraido.isBlank()) {
            return;
        }

        documento.setNumeroProcesso(extrairPrimeiro(PROCESSO_PATTERN, textoExtraido));
        documento.setMateria(extrairMateria(textoExtraido));
    }

    /**
     * Retorna o primeiro grupo encontrado no regex informado.
     */
    private String extrairPrimeiro(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return limparValor(matcher.group(1));
        }
        return null;
    }

    /**
     * Tenta identificar a linha de matéria principal no cabeçalho da decisão.
     */
    private String extrairMateria(String texto) {
        String[] linhas = texto.split("\\R");
        for (String linha : linhas) {
            String normalizada = limparValor(linha).toUpperCase();
            if (normalizada.length() < 30) {
                continue;
            }

            boolean ehCabecalhoMateria = normalizada.contains("ICMS")
                    && (normalizada.contains("ALIQUOTA")
                    || normalizada.contains("RECOLHIMENTO")
                    || normalizada.contains("CONSTRUCAO CIVIL"));

            if (ehCabecalhoMateria && !normalizada.startsWith("AUTO DE INFRACAO")) {
                return limparValor(linha);
            }
        }
        return null;
    }

    /**
     * Normaliza espaços para evitar metadados com ruído de OCR.
     */
    private String limparValor(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.replaceAll("\\s+", " ").trim();
    }

}
