package com.ludoarena.repository;

import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReportRepository extends MongoRepository<Report, String> {
    Page<Report> findByReporterId(String reporterId, Pageable pageable);

    Page<Report> findByStatus(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);
}
