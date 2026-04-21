package com.biddo.domain.search.port;

import com.biddo.domain.search.dto.AuctionSearchCondition;
import com.biddo.domain.search.dto.AuctionSearchResult;

import java.util.List;

public interface AuctionSearchPort {

    List<AuctionSearchResult> search(AuctionSearchCondition condition);
}