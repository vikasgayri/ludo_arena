package com.ludoarena.service;

import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.ReportDto;
import com.ludoarena.dto.Requests.CreateReport;
import com.ludoarena.dto.Requests.UpdateReport;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Report;
import com.ludoarena.model.User;
import com.ludoarena.repository.ReportRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.AuthUser;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    private static final int MAX_OPEN_PER_USER = 10;

    private final ReportRepository reports;
    private final UserRepository users;
    private final MongoTemplate mongo;
    private final NotificationService notifications;
    private final AdminLogService adminLog;

    public ReportService(ReportRepository reports, UserRepository users, MongoTemplate mongo,
                         NotificationService notifications, AdminLogService adminLog) {
        this.reports = reports;
        this.users = users;
        this.mongo = mongo;
        this.notifications = notifications;
        this.adminLog = adminLog;
    }

    public ReportDto create(String reporterId, CreateReport req) {
        long open = mongo.count(new org.springframework.data.mongodb.core.query.Query(
                Criteria.where("reporterId").is(reporterId).and("status").is(ReportStatus.OPEN)), Report.class);
        if (open >= MAX_OPEN_PER_USER) {
            throw ApiException.conflict("TOO_MANY_REPORTS", "You have too many open reports. Please wait for them to be reviewed.");
        }
        if (req.targetUserId() != null && !req.targetUserId().isBlank() && !users.existsById(req.targetUserId())) {
            throw ApiException.notFound("USER_NOT_FOUND", "The reported user does not exist");
        }
        Report r = new Report();
        r.reporterId = reporterId;
        r.type = req.type();
        r.targetUserId = blankToNull(req.targetUserId());
        r.matchId = blankToNull(req.matchId());
        r.description = req.description().trim();
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return ReportDto.from(reports.save(r), null);
    }

    public PageResponse<ReportDto> mine(String userId, int page, int size) {
        Page<Report> p = reports.findByReporterId(userId, MongoPaging.newestFirst(page, size));
        return PageResponse.of(p, r -> ReportDto.from(r, null));
    }

    public PageResponse<ReportDto> adminList(ReportStatus status, int page, int size) {
        Page<Report> p = status == null
                ? reports.findAll(MongoPaging.newestFirst(page, size))
                : reports.findByStatus(status, MongoPaging.newestFirst(page, size));
        List<String> ids = p.getContent().stream().map(r -> r.reporterId).distinct().collect(Collectors.toList());
        Map<String, String> names = new HashMap<>();
        for (User u : users.findAllById(ids)) names.put(u.id, u.name);
        return PageResponse.of(p, r -> ReportDto.from(r, names.get(r.reporterId)));
    }

    public ReportDto update(AuthUser admin, String id, UpdateReport req) {
        Report r = reports.findById(id).orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "Report not found"));
        ReportStatus before = r.status;
        r.status = req.status();
        r.adminNote = req.adminNote();
        r.resolvedBy = admin.id();
        r.updatedAt = Instant.now();
        r = reports.save(r);
        adminLog.log(admin, "REPORT_" + r.status, "report", r.id, Map.of("from", before.name(), "to", r.status.name()));
        if (r.status == ReportStatus.RESOLVED || r.status == ReportStatus.DISMISSED) {
            notifications.notify(r.reporterId, "REPORT_UPDATE", "Your report was reviewed",
                    "Status: " + r.status + (r.adminNote == null || r.adminNote.isBlank() ? "" : " - " + r.adminNote));
        }
        return ReportDto.from(r, users.findById(r.reporterId).map(u -> u.name).orElse(null));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
