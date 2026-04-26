package com.br.extrator.service;

import com.br.extrator.config.DocumentStorageConfig;
import com.br.extrator.model.Document;
import com.br.extrator.model.StatusDocumento;
import com.br.extrator.repository.DocumentRepository;
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
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorageConfig storageConfig;

    @Autowired
    private ExtratorDecisaoService extratorDecisaoService;

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
        documento.setNomeOriginal(file.getOriginalFilename());
        documento.setCaminhoArquivo(caminhoRelativo);
        documento.setDataUpload(LocalDateTime.now());
        documento.setStatus(StatusDocumento.UPLOADING);
        documento.setTextoExtraido("");

        // Salvar documento no banco antes de processar OCR
        documento = documentRepository.save(documento);

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
            documentRepository.save(documento);

            // Chamar serviço de extração
            String textoExtraido = extratorDecisaoService.extrairDeArquivoLocal(caminhoArquivo.toString());

            documento.setTextoExtraido(textoExtraido);
            preencherMetadados(documento, textoExtraido);
            documento.setDataProcessamento(LocalDateTime.now());
            documento.setStatus(StatusDocumento.COMPLETED);
            documento.setTextoNativo(false);

        } catch (Exception e) {
            documento.setStatus(StatusDocumento.ERROR);
            documento.setTextoExtraido("Erro no processamento: " + e.getMessage());
        }

        documentRepository.save(documento);
    }

    /**
     * Busca todos os documentos
     */
    public List<Document> listarTodos() {
        return documentRepository.findAll();
    }

    /**
     * Busca documento por ID
     */
    public Optional<Document> buscarPorId(Long id) {
        return documentRepository.findById(id);
    }

    /**
     * Deleta um documento (arquivo e registro)
     */
    public boolean deletarDocumento(Long id) {
        Optional<Document> documento = documentRepository.findById(id);
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
            documentRepository.deleteById(id);
            return true;
        }
        return false;
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
