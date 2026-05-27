package com.br.extrator.service.search;

import com.br.extrator.model.Document;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Service
@ConditionalOnBean(ElasticsearchClient.class)
public class DocumentSearchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final String indexName;

    /**
     * Cria o service de busca com o cliente Elasticsearch e o nome do indice.
     *
     * @param elasticsearchClient cliente de alto nivel do Elasticsearch
     * @param indexName nome do indice configurado para documentos
     */
    public DocumentSearchService(ElasticsearchClient elasticsearchClient,
                                 @Value("${app.elasticsearch.index-name}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

    /**
     * Executa uma busca composta no indice com termo livre e filtros exatos.
     *
     * @param termo termo livre para a consulta textual
     * @param status status do documento a filtrar
     * @param numeroProcesso numero do processo a filtrar
     * @param materia materia do documento a filtrar
     * @param dataInicio data inicial do filtro
     * @param dataFim data final do filtro
     * @return lista de documentos encontrados ou lista vazia em caso de erro
     */
    public List<Document> buscar(String termo,
                                 String status,
                                 String numeroProcesso,
                                 String materia,
                                 LocalDateTime dataInicio,
                                 LocalDateTime dataFim) {
        try {
            SearchResponse<Document> response = elasticsearchClient.search(search -> {
                search.index(indexName);
                search.size(500);
                search.sort(sort -> sort.field(field -> field.field("dataUpload").order(SortOrder.Desc)));
                search.query(query -> query.bool(bool -> {
                    if (termo != null && !termo.isBlank()) {
                        bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                                .query(termo)
                                .fields("nomeOriginal", "textoExtraido", "numeroProcesso", "materia")));
                    } else {
                        bool.must(must -> must.matchAll(matchAll -> matchAll));
                    }

                    adicionarFiltroStatus(bool, status);
                    adicionarFiltroTextoExato(bool, "numeroProcesso.keyword", numeroProcesso);
                    adicionarFiltroTextoExato(bool, "materia.keyword", materia);

                    return bool;
                }));
                return search;
            }, Document.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .filter(document -> filtrarData(document, dataInicio, dataFim))
                    .toList();
        } catch (Exception e) {
            log.warn("Falha ao pesquisar documentos no Elasticsearch: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Persiste ou atualiza um documento no indice usando o id como chave.
     *
     * @param document documento a ser gravado no Elasticsearch
     */
    public void salvarOuAtualizar(Document document) {
        try {
            elasticsearchClient.index(index -> index
                    .index(indexName)
                    .id(document.getId())
                    .document(document));
        } catch (Exception e) {
            log.warn("Falha ao salvar documento {} no Elasticsearch: {}", document.getId(), e.getMessage());
        }
    }

    /**
     * Busca um documento pelo identificador unico no indice.
     *
     * @param id identificador do documento
     * @return documento encontrado ou Optional vazio
     */
    public Optional<Document> buscarPorId(String id) {
        try {
            GetResponse<Document> response = elasticsearchClient.get(get -> get
                    .index(indexName)
                    .id(id), Document.class);
            return response.found() ? Optional.ofNullable(response.source()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Falha ao buscar documento {} no Elasticsearch: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Remove um documento do indice Elasticsearch pelo id.
     *
     * @param id identificador do documento a remover
     */
    public void remover(String id) {
        try {
            elasticsearchClient.delete(delete -> delete.index(indexName).id(id));
        } catch (Exception e) {
            log.warn("Falha ao remover documento {} do Elasticsearch: {}", id, e.getMessage());
        }
    }

    /**
     * Adiciona filtro exato de status a consulta bool.
     *
     * @param bool builder da consulta bool
     * @param status status do documento
     */
    private void adicionarFiltroStatus(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder bool,
                                       String status) {
        if (status == null || status.isBlank()) {
            return;
        }

        bool.filter(filter -> filter.term(term -> term.field("status").value(status.toUpperCase())));
    }

    /**
     * Adiciona filtro exato para um campo de texto.
     *
     * @param bool builder da consulta bool
     * @param field nome do campo no indice
     * @param value valor desejado para o filtro
     */
    private void adicionarFiltroTextoExato(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder bool,
                                           String field,
                                           String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        bool.filter(filter -> filter.term(term -> term.field(field).value(value)));
    }

    /**
     * Aplica o recorte de datas apos a busca no Elasticsearch.
     *
     * @param document documento carregado do indice
     * @param dataInicio data inicial do filtro
     * @param dataFim data final do filtro
     * @return true quando o documento esta dentro do intervalo desejado
     */
    private boolean filtrarData(Document document,
                                LocalDateTime dataInicio,
                                LocalDateTime dataFim) {
        if (dataInicio == null && dataFim == null) {
            return true;
        }

        LocalDateTime dataUpload = document.getDataUpload();
        if (dataUpload == null) {
            return false;
        }

        if (dataInicio != null && dataUpload.isBefore(dataInicio)) {
            return false;
        }

        if (dataFim != null && dataUpload.isAfter(dataFim)) {
            return false;
        }

        return true;
    }
}
