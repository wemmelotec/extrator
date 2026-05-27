package com.br.extrator.dto;

import com.br.extrator.model.StatusDocumento;
import java.time.LocalDateTime;

/**
 * DTO de listagem usado pelas telas web para exibir o resumo do documento sem
 * trafegar o texto completo extraido.
 */
public class DocumentoListaDTO {
    
    private String id;
    private String nomeOriginal;
    private LocalDateTime dataUpload;
    private LocalDateTime dataProcessamento;
    private StatusDocumento status;
    private String numeroProcesso;
    private String materia;
    private boolean textoNativo;
    private String origemExtracao;
    private int tamanhoTexto;

    /**
     * Construtor padrao necessario para serializacao e desserializacao JSON.
     */
    public DocumentoListaDTO() {}

    /**
     * Monta o DTO resumido a partir dos campos principais do documento.
     *
     * @param id identificador do documento
     * @param nomeOriginal nome original do arquivo
     * @param dataUpload data/hora do upload
     * @param dataProcessamento data/hora do processamento final
     * @param status status atual do documento
     * @param numeroProcesso numero do processo identificado
     * @param materia materia identificada
     * @param textoNativo indica se a extração veio do texto nativo do PDF
     * @param origemExtracao origem da extração textual
     * @param textoExtraido texto completo usado apenas para calcular o tamanho
     */
    public DocumentoListaDTO(String id, String nomeOriginal, LocalDateTime dataUpload,
                            LocalDateTime dataProcessamento, StatusDocumento status,
                            String numeroProcesso, String materia, boolean textoNativo,
                            String origemExtracao,
                            String textoExtraido) {
        this.id = id;
        this.nomeOriginal = nomeOriginal;
        this.dataUpload = dataUpload;
        this.dataProcessamento = dataProcessamento;
        this.status = status;
        this.numeroProcesso = numeroProcesso;
        this.materia = materia;
        this.textoNativo = textoNativo;
        this.origemExtracao = origemExtracao;
        this.tamanhoTexto = textoExtraido != null ? textoExtraido.length() : 0;
    }

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

    public int getTamanhoTexto() {
        return tamanhoTexto;
    }

    public void setTamanhoTexto(int tamanhoTexto) {
        this.tamanhoTexto = tamanhoTexto;
    }
}
