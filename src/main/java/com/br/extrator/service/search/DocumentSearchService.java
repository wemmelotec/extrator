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

    public DocumentSearchService(ElasticsearchClient elasticsearchClient,
                                 @Value("${app.elasticsearch.index-name}") String indexName) {
        this.elasticsearchClient = elasticsearchClient;
        this.indexName = indexName;
    }

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

    public void remover(String id) {
        try {
            elasticsearchClient.delete(delete -> delete.index(indexName).id(id));
        } catch (Exception e) {
            log.warn("Falha ao remover documento {} do Elasticsearch: {}", id, e.getMessage());
        }
    }

    private void adicionarFiltroStatus(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder bool,
                                       String status) {
        if (status == null || status.isBlank()) {
            return;
        }

        bool.filter(filter -> filter.term(term -> term.field("status").value(status.toUpperCase())));
    }

    private void adicionarFiltroTextoExato(co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder bool,
                                           String field,
                                           String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        bool.filter(filter -> filter.term(term -> term.field(field).value(value)));
    }

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
