package com.biddo.infra.elasticsearch.repository;

import com.biddo.infra.elasticsearch.document.AuctionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface AuctionDocumentRepository extends ElasticsearchRepository<AuctionDocument, Long> {
}