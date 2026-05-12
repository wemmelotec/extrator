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
     * Upload de novo documento com persistência e OCR
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
     * Lista todos os documentos com paginação básica
     */
    @GetMapping
    public ResponseEntity<List<DocumentoListaDTO>> listarDocumentos() {
        return ResponseEntity.ok(toDtoList(documentService.listarTodos()));
    }

    /**
     * Pesquisa documentos no Elasticsearch usando texto livre e filtros.
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
     * Busca documento pelo ID
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
     * Faz download do PDF original associado ao documento.
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
     * Deleta um documento
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletarDocumento(@PathVariable String id) {
        if (documentService.deletarDocumento(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private List<DocumentoListaDTO> toDtoList(List<Document> documentos) {
        return documentos.stream().map(this::toDto).collect(Collectors.toList());
    }

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

    private LocalDateTime parseDataInicio(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value + "T00:00:00");
    }

    private LocalDateTime parseDataFim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDateTime.parse(value + "T23:59:59");
    }
}
