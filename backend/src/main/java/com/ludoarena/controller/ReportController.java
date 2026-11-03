package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.ReportDto;
import com.ludoarena.dto.Requests.CreateReport;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReportDto> create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateReport req) {
        return ApiResponse.ok(reports.create(me.id(), req), "Report submitted");
    }

    @GetMapping("/mine")
    public ApiResponse<PageResponse<ReportDto>> mine(@AuthenticationPrincipal AuthUser me,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reports.mine(me.id(), page, size));
    }
}
