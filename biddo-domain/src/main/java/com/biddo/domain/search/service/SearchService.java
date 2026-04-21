package com.biddo.domain.search.service;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;
import com.biddo.domain.search.port.AuctionSearchPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final AuctionSearchPort auctionSearchPort;

    public List<AuctionSearchResult> search(AuctionSearchCondition condition) {
        return auctionSearchPort.search(condition);
    }
}