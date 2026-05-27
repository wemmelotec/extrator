package com.br.extrator.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticsearchConfig {

    /**
     * Cria o cliente REST baixo nivel usado na comunicacao com o Elasticsearch.
     *
     * @param elasticsearchUris endereco do cluster configurado no application.properties
     * @return instancia do cliente REST
     */
    @Bean(destroyMethod = "close")
    public RestClient restClient(@Value("${spring.elasticsearch.uris}") String elasticsearchUris) {
        return RestClient.builder(HttpHost.create(elasticsearchUris)).build();
    }

    /**
     * Cria o transporte JSON entre o RestClient e o ElasticsearchClient.
     *
     * @param restClient cliente REST base
     * @param objectMapper mapper Jackson usado na serializacao
     * @return transporte configurado para JSON
     */
    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient, ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    /**
     * Cria o cliente de alto nivel usado pelo restante da aplicacao.
     *
     * @param elasticsearchTransport transporte JSON configurado
     * @return cliente Elasticsearch pronto para uso
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport elasticsearchTransport) {
        return new ElasticsearchClient(elasticsearchTransport);
    }
}
