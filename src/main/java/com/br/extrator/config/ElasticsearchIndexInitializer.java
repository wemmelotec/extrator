package com.br.extrator.config;

import com.br.extrator.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "app.elasticsearch.validate-on-startup", havingValue = "true")
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final boolean validateOnStartup;

    public ElasticsearchIndexInitializer(
            ElasticsearchOperations elasticsearchOperations,
            @Value("${app.elasticsearch.validate-on-startup:true}") boolean validateOnStartup) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.validateOnStartup = validateOnStartup;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!validateOnStartup) {
            return;
        }

        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(Document.class);
            if (indexOperations.exists()) {
                log.info("Indice Elasticsearch '{}' ja existe", indexOperations.getIndexCoordinates().getIndexName());
                return;
            }

            boolean created = indexOperations.create();
            if (created) {
                indexOperations.putMapping(indexOperations.createMapping());
            }
            log.info("Indice Elasticsearch '{}' criado: {}", indexOperations.getIndexCoordinates().getIndexName(), created);
        } catch (Exception e) {
            log.warn("Elasticsearch indisponivel ou nao inicializado para o indice de documentos: {}", e.getMessage());
        }
    }
}
