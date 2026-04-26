package com.br.extrator.model;

/**
 * Estados possíveis de um documento durante o processamento
 */
public enum StatusDocumento {
    UPLOADING("Enviando"),
    PROCESSING("Processando"),
    COMPLETED("Concluído"),
    ERROR("Erro");

    private final String descricao;

    StatusDocumento(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
