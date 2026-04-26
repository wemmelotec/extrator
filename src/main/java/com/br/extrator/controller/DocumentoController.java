package com.br.extrator.controller;

import com.br.extrator.dto.DocumentoListaDTO;
import com.br.extrator.model.Document;
import com.br.extrator.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
     * Busca documento pelo ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> buscarDocumento(@PathVariable Long id) {
        Optional<Document> documento = documentService.buscarPorId(id);
        if (documento.isPresent()) {
            return ResponseEntity.ok(toDto(documento.get()));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * Deleta um documento
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletarDocumento(@PathVariable Long id) {
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
                doc.getTextoExtraido()
        );
    }
}
