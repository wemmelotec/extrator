package com.br.extrator.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

/**
 * Representa o documento persistido no Elasticsearch com os metadados
 * utilizados para consulta, listagem e enriquecimento por OCR.
 */
@org.springframework.data.elasticsearch.annotations.Document(indexName = "${app.elasticsearch.index-name}")
public class Document {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String nomeOriginal;

    @Field(type = FieldType.Keyword)
    private String caminhoArquivo;

    @Field(type = FieldType.Text)
    private String textoExtraido;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private LocalDateTime dataUpload;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private LocalDateTime dataProcessamento;

    @Field(type = FieldType.Keyword)
    private StatusDocumento status;

    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = {
        @InnerField(suffix = "keyword", type = FieldType.Keyword)
    })
    private String numeroProcesso;

    @MultiField(mainField = @Field(type = FieldType.Text), otherFields = {
        @InnerField(suffix = "keyword", type = FieldType.Keyword)
    })
    private String materia;

    @Field(type = FieldType.Boolean)
    private boolean textoNativo;

    @Field(type = FieldType.Keyword)
    private String origemExtracao;

    // ==================== Getters e Setters ====================

    public String getId() {
        return id;
    }

    public void setId(String id) {
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

    public String getOrigemExtracao() {
        return origemExtracao;
    }

    public void setOrigemExtracao(String origemExtracao) {
        this.origemExtracao = origemExtracao;
    }
}
