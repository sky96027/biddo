package com.biddo.domain.report.repository;

import com.biddo.domain.report.entity.Report;
import com.biddo.domain.report.entity.ReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query("SELECT r FROM Report r JOIN FETCH r.reported WHERE r.reporter.id = :reporterId AND r.id < :cursor ORDER BY r.id DESC")
    List<Report> findByReporterIdWithCursor(@Param("reporterId") Long reporterId,
                                            @Param("cursor") Long cursor,
                                            Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reported WHERE r.reporter.id = :reporterId ORDER BY r.id DESC")
    List<Report> findByReporterIdFirstPage(@Param("reporterId") Long reporterId, Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reported WHERE r.status = :status AND r.id < :cursor ORDER BY r.id DESC")
    List<Report> findByStatusWithCursor(@Param("status") ReportStatus status,
                                        @Param("cursor") Long cursor,
                                        Pageable pageable);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter JOIN FETCH r.reported WHERE r.status = :status ORDER BY r.id DESC")
    List<Report> findByStatusFirstPage(@Param("status") ReportStatus status, Pageable pageable);
}