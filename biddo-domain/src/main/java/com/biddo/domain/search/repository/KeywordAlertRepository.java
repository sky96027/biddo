package com.biddo.domain.search.repository;

import com.biddo.domain.search.entity.KeywordAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KeywordAlertRepository extends JpaRepository<KeywordAlert, Long> {

    List<KeywordAlert> findByMemberIdOrderByIdDesc(Long memberId);

    Optional<KeywordAlert> findByMemberIdAndKeyword(Long memberId, String keyword);

    boolean existsByMemberIdAndKeyword(Long memberId, String keyword);

    @Query("SELECT ka FROM KeywordAlert ka JOIN FETCH ka.member WHERE ka.isActive = true")
    List<KeywordAlert> findAllActive();
}