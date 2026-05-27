package com.br.extrator.controller;

import com.br.extrator.dto.DocumentoListaDTO;
import com.br.extrator.model.Document;
import com.br.extrator.service.DocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controlador para gerenciamento de documentos (upload, listagem, detalhe e exclusão).
 */
@RestController
@RequestMapping("/api/documentos")
public class DocumentoController {

    @Autowired
    private DocumentService documentService;

    /**
     * Recebe um arquivo enviado pelo front, aciona o processamento de upload
     * e devolve a representacao resumida do documento salvo.
     *
     * @param file arquivo PDF ou imagem enviado no multipart form-data
     * @return resposta HTTP com o documento resumido ou mensagem de erro
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocumento(@RequestParam("file") MultipartFile file) {
        try {
        	
            Document documento = documentService.processarUpload(file);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(toDto(documento));
            
        } catch (IOException e) {
        	
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao processar arquivo: " + e.getMessage());
            
        } catch (IllegalArgumentException e) {
        	
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Erro de validação: " + e.getMessage());
            
        }
        
    }

    /**
     * Lista os documentos disponiveis no indice para alimentar a tela de consulta.
     *
     * @return lista resumida de documentos retornada em HTTP 200
     */
    @GetMapping
    public ResponseEntity<List<DocumentoListaDTO>> listarDocumentos() {
        return ResponseEntity.ok(toDtoList(documentService.listarTodos()));
    }

    /**
     * Pesquisa documentos no Elasticsearch usando texto livre, status e filtros
     * por processo, materia e intervalo de datas.
     *
     * @param termo termo livre para busca textual
     * @param status status do documento a filtrar
     * @param numeroProcesso numero do processo a filtrar
     * @param materia materia do documento a filtrar
     * @param dataInicio data inicial da janela de consulta no formato yyyy-MM-dd
     * @param dataFim data final da janela de consulta no formato yyyy-MM-dd
     * @return lista filtrada de documentos resumidos
     */
    @GetMapping("/busca")
    public ResponseEntity<List<DocumentoListaDTO>> buscarDocumentos(
            @RequestParam(required = false) String termo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String numeroProcesso,
            @RequestParam(required = false) String materia,
            @RequestParam(required = false) String dataInicio,
            @RequestParam(required = false) String dataFim) {

        return ResponseEntity.ok(toDtoList(documentService.buscarDocumentos(
                termo,
                status,
                numeroProcesso,
                materia,
                parseDataInicio(dataInicio),
                parseDataFim(dataFim)
        )));
    }

    /**
     * Busca um documento pelo identificador unico.
     *
     * @param id identificador do documento no indice
     * @return documento encontrado ou HTTP 404 caso nao exista
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarDocumento(@PathVariable String id) {
        Optional<Document> documento = documentService.buscarPorId(id);
        if (documento.isPresent()) {
            return ResponseEntity.ok(toDto(documento.get()));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Faz o download do arquivo original associado ao documento.
     *
     * @param id identificador do documento
     * @return recurso binario do arquivo ou HTTP 404 se nao existir
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> baixarPdf(@PathVariable String id) {
        Optional<Document> documento = documentService.buscarPorId(id);
        if (documento.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Path caminho = Paths.get(documento.get().getCaminhoArquivo()).toAbsolutePath();
        if (!Files.exists(caminho)) {
            return ResponseEntity.notFound().build();
        }

        String nomeArquivo = documento.get().getNomeOriginal() != null
                ? documento.get().getNomeOriginal()
                : caminho.getFileName().toString();

        MediaType mediaType = MediaTypeFactory.getMediaType(nomeArquivo)
                .orElse(MediaType.APPLICATION_PDF);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .body(new FileSystemResource(caminho));
    }

    /**
     * Remove um documento do indice e tenta apagar o arquivo fisico associado.
     *
     * @param id identificador do documento a excluir
     * @return HTTP 204 quando a exclusao ocorre, ou HTTP 404 se nao encontrar
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletarDocumento(@PathVariable String id) {
        if (documentService.deletarDocumento(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Converte uma lista de entidades de documento em DTOs de listagem.
     *
     * @param documentos documentos carregados do indice
     * @return lista de DTOs prontos para a camada web
     */
    private List<DocumentoListaDTO> toDtoList(List<Document> documentos) {
        return documentos.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Transforma a entidade persistida em um DTO enxuto para a interface.
     *
     * @param doc documento completo obtido do Elasticsearch
     * @return DTO resumido com os campos exibidos na tela
     */
    private DocumentoListaDTO toDto(Document doc) {
        return new DocumentoListaDTO(
                doc.getId(),
                doc.getNomeOriginal(),
                doc.getDataUpload(),
                doc.getDataProcessamento(),
                doc.getStatus(),
                doc.getNumeroProcesso(),
                doc.getMateria(),
                doc.isTextoNativo(),
                doc.getOrigemExtracao(),
                doc.getTextoExtraido()
        );
    }

    /**
     * Converte a data inicial recebida da consulta para o inicio do dia.
     *
     * @param value data em formato yyyy-MM-dd
     * @return data/hora normalizada para 00:00:00 ou null quando vazia
     */
    private LocalDateTime parseDataInicio(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value + "T00:00:00");
    }

    /**
     * Converte a data final recebida da consulta para o fim do dia.
     *
     * @param value data em formato yyyy-MM-dd
     * @return data/hora normalizada para 23:59:59 ou null quando vazia
     */
    private LocalDateTime parseDataFim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value + "T23:59:59");
    }
}
