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
     * Processa o upload: valida o arquivo, persiste o binario em disco,
     * cria o registro inicial no Elasticsearch e dispara a extração de texto.
     *
     * @param file arquivo recebido no multipart form-data
     * @return documento atualizado ao final do fluxo de processamento
     * @throws IOException quando o arquivo nao pode ser gravado em disco
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
     * Extrai texto do arquivo salvo em disco, atualiza os metadados do
     * documento e persiste o resultado final no Elasticsearch.
     *
     * @param documento documento que sera enriquecido com o texto extraido
     * @param caminhoArquivo caminho absoluto do arquivo salvo em disco
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
     * Carrega todos os documentos disponiveis no indice usando a busca padrao.
     *
     * @return lista de documentos encontrados
     */
    public List<Document> listarTodos() {
        return buscarDocumentos(null, null, null, null, null, null);
    }

    /**
     * Busca um documento pelo identificador unico.
     *
     * @param id identificador do documento no indice
     * @return documento encontrado ou Optional vazio
     */
    public Optional<Document> buscarPorId(String id) {
        if (documentSearchService != null) {
            return documentSearchService.buscarPorId(id);
        }

        return Optional.empty();
    }

    /**
     * Remove o arquivo fisico e o documento correspondente do indice.
     *
     * @param id identificador do documento a excluir
     * @return true quando a exclusao foi executada, false quando nao encontrou
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

    /**
     * Pesquisa documentos usando os filtros informados pela interface.
     *
     * @param termo termo livre para busca textual
     * @param status status do documento
     * @param numeroProcesso numero do processo a localizar
     * @param materia materia a localizar
     * @param dataInicio data inicial do filtro
     * @param dataFim data final do filtro
     * @return lista de documentos que atendem aos filtros
     */
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

    /**
     * Persiste o documento atual no indice Elasticsearch.
     *
     * @param documento documento que deve ser gravado ou atualizado
     */
    private void salvarOuAtualizar(Document documento) {
        if (documentSearchService != null) {
            documentSearchService.salvarOuAtualizar(documento);
        }
    }

    /**
     * Remove um documento do indice Elasticsearch.
     *
     * @param id identificador do documento a remover
     */
    private void removerDocumento(String id) {
        if (documentSearchService != null) {
            documentSearchService.remover(id);
        }
    }

    /**
     * Extrai os metadados utilizados nas telas de listagem e consulta.
     *
     * @param documento documento que recebera os metadados
     * @param textoExtraido texto obtido da extração principal
     */
    private void preencherMetadados(Document documento, String textoExtraido) {
        if (textoExtraido == null || textoExtraido.isBlank()) {
            return;
        }

        documento.setNumeroProcesso(extrairPrimeiro(PROCESSO_PATTERN, textoExtraido));
        documento.setMateria(extrairMateria(textoExtraido));
    }

    /**
     * Retorna o primeiro grupo encontrado para o padrao informado.
     *
     * @param pattern expressao regular usada na busca
     * @param texto texto de entrada a ser analisado
     * @return primeiro grupo capturado ou null quando nao houver correspondencia
     */
    private String extrairPrimeiro(Pattern pattern, String texto) {
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return limparValor(matcher.group(1));
        }
        return null;
    }

    /**
     * Tenta identificar a linha de materia principal no cabecalho do texto.
     *
     * @param texto texto extraido do documento
     * @return linha que aparenta conter a materia ou null quando nao identificar
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
     * Normaliza espacos para reduzir ruido de OCR e padronizar os valores.
     *
     * @param valor valor original a ser limpo
     * @return valor sem espacos repetidos ou null quando a entrada for nula
     */
    private String limparValor(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.replaceAll("\\s+", " ").trim();
    }

}
