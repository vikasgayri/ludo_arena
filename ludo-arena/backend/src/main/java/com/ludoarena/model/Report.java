package com.ludoarena.model;

import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Enums.ReportType;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reports")
@CompoundIndex(name = "status_createdAt", def = "{'status': 1, 'createdAt': -1}")
public class Report {
    @Id
    public String id;
    public String reporterId;
    public ReportType type;
    public String targetUserId;
    public String matchId;
    public String description;
    public ReportStatus status = ReportStatus.OPEN;
    public String adminNote;
    public String resolvedBy;
    public Instant createdAt;
    public Instant updatedAt;
}
