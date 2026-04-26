package com.br.extrator.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;

/**
 * Entidade para representar um documento PDF com metadados associados
 */
@Entity
@Table(name = "documento")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nomeOriginal;

    @Column(nullable = false, unique = true)
    private String caminhoArquivo;

    @Column(columnDefinition = "TEXT")
    private String textoExtraido;

    @Column(nullable = false)
    private LocalDateTime dataUpload;

    @Column()
    private LocalDateTime dataProcessamento;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusDocumento status;

    // Metadados extraídos do documento
    @Column()
    private String numeroProcesso;

    @Column()
    private String materia;

    @Column()
    private boolean textoNativo;

    // ==================== Getters e Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNomeOriginal() {
        return nomeOriginal;
    }

    public void setNomeOriginal(String nomeOriginal) {
        this.nomeOriginal = nomeOriginal;
    }

    public String getCaminhoArquivo() {
        return caminhoArquivo;
    }

    public void setCaminhoArquivo(String caminhoArquivo) {
        this.caminhoArquivo = caminhoArquivo;
    }

    public String getTextoExtraido() {
        return textoExtraido;
    }

    public void setTextoExtraido(String textoExtraido) {
        this.textoExtraido = textoExtraido;
    }

    public LocalDateTime getDataUpload() {
        return dataUpload;
    }

    public void setDataUpload(LocalDateTime dataUpload) {
        this.dataUpload = dataUpload;
    }

    public LocalDateTime getDataProcessamento() {
        return dataProcessamento;
    }

    public void setDataProcessamento(LocalDateTime dataProcessamento) {
        this.dataProcessamento = dataProcessamento;
    }

    public StatusDocumento getStatus() {
        return status;
    }

    public void setStatus(StatusDocumento status) {
        this.status = status;
    }

    public String getNumeroProcesso() {
        return numeroProcesso;
    }

    public void setNumeroProcesso(String numeroProcesso) {
        this.numeroProcesso = numeroProcesso;
    }

    public String getMateria() {
        return materia;
    }

    public void setMateria(String materia) {
        this.materia = materia;
    }

    public boolean isTextoNativo() {
        return textoNativo;
    }

    public void setTextoNativo(boolean textoNativo) {
        this.textoNativo = textoNativo;
    }
}
