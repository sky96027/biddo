package com.biddo.infra.elasticsearch;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Configuration
@EnableElasticsearchRepositories(basePackages = "com.biddo.infra.elasticsearch.repository")
public class ElasticsearchConfig {
}