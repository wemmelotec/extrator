package com.br.extrator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;

@SpringBootTest(properties = {
	"app.elasticsearch.enabled=false",
	"app.elasticsearch.validate-on-startup=false"
})
@ImportAutoConfiguration(exclude = {
	ElasticsearchClientAutoConfiguration.class,
	ElasticsearchRestClientAutoConfiguration.class,
	ElasticsearchDataAutoConfiguration.class,
	ElasticsearchRepositoriesAutoConfiguration.class
})
class ExtratorApplicationTests {

	@Test
	void contextLoads() {
	}

}
