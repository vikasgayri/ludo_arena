package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.AdminLogDto;
import com.ludoarena.dto.Dtos.AdminUserDto;
import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.DashboardDto;
import com.ludoarena.dto.Dtos.EventDto;
import com.ludoarena.dto.Dtos.GameStatsDto;
import com.ludoarena.dto.Dtos.MatchStateDto;
import com.ludoarena.dto.Dtos.MatchSummaryDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.ReportDto;
import com.ludoarena.dto.Dtos.SettingsDto;
import com.ludoarena.dto.Dtos.StoreItemDto;
import com.ludoarena.dto.Dtos.TransactionDto;
import com.ludoarena.dto.Requests.AdminCredit;
import com.ludoarena.dto.Requests.StoreItemRequest;
import com.ludoarena.dto.Requests.UpdateReport;
import com.ludoarena.dto.Requests.UpdateSettings;
import com.ludoarena.dto.Requests.UpdateUserRole;
import com.ludoarena.dto.Requests.UpdateUserStatus;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.StoreItem;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.AdminLogService;
import com.ludoarena.service.AdminService;
import com.ludoarena.service.MatchService;
import com.ludoarena.service.PlatformSettingsService;
import com.ludoarena.service.ReportService;
import com.ludoarena.service.StoreService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every route here requires ADMIN or SUPER_ADMIN: enforced by the security filter chain, by @PreAuthorize on the
 * class, and again inside the services. Mutations are written to the immutable admin log.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminController {
    private final AdminService admin;
    private final MatchService matches;
    private final StoreService store;
    private final ReportService reports;
    private final PlatformSettingsService settings;
    private final AdminLogService adminLog;

    public AdminController(AdminService admin, MatchService matches, StoreService store, ReportService reports,
                           PlatformSettingsService settings, AdminLogService adminLog) {
        this.admin = admin;
        this.matches = matches;
        this.store = store;
        this.reports = reports;
        this.settings = settings;
        this.adminLog = adminLog;
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardDto> dashboard(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(admin.dashboard(me));
    }

    @GetMapping("/stats")
    public ApiResponse<GameStatsDto> stats(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(admin.stats(me));
    }

    // ------------------------------------------------------------------ users

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserDto>> users(@AuthenticationPrincipal AuthUser me,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(required = false) AccountStatus status,
                                                         @RequestParam(required = false) Role role,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(admin.users(me, q, status, role, page, size));
    }

    @PutMapping("/users/{id}/status")
    public ApiResponse<AdminUserDto> status(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                            @Valid @RequestBody UpdateUserStatus req) {
        return ApiResponse.ok(admin.updateStatus(me, id, req), "User updated");
    }

    @PutMapping("/users/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminUserDto> role(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                          @Valid @RequestBody UpdateUserRole req) {
        return ApiResponse.ok(admin.updateRole(me, id, req), "Role updated");
    }

    @PostMapping("/users/{id}/credit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<TransactionDto> credit(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                              @Valid @RequestBody AdminCredit req) {
        return ApiResponse.ok(admin.creditCoins(me, id, req), "Wallet adjusted");
    }

    // ---------------------------------------------------------------- matches

    @GetMapping("/matches")
    public ApiResponse<PageResponse<MatchSummaryDto>> matches(@RequestParam(required = false) MatchStatus status,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(matches.listAll(status, page, size));
    }

    @GetMapping("/matches/{id}")
    public ApiResponse<MatchStateDto> match(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return ApiResponse.ok(matches.stateFor(id, me.id(), true));
    }

    @GetMapping("/matches/{id}/events")
    public ApiResponse<PageResponse<EventDto>> matchEvents(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.ok(matches.events(id, me.id(), true, page, size));
    }

    // ----------------------------------------------------------- transactions / logs

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<TransactionDto>> transactions(@AuthenticationPrincipal AuthUser me,
                                                                  @RequestParam(required = false) String userId,
                                                                  @RequestParam(required = false) TxType type,
                                                                  @RequestParam(required = false) TxStatus status,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(admin.transactions(me, userId, type, status, page, size));
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<AdminLogDto>> logs(@AuthenticationPrincipal AuthUser me,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(admin.logs(me, page, size));
    }

    // ------------------------------------------------------------------ store

    @GetMapping("/store/items")
    public ApiResponse<List<StoreItemDto>> storeItems(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(store.catalog(me.id(), true));
    }

    @PostMapping("/store/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoreItemDto> createItem(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody StoreItemRequest req) {
        StoreItem s = store.create(req);
        adminLog.log(me, "STORE_ITEM_CREATED", "storeItem", s.id, Map.of("sku", s.sku, "price", s.priceCoins));
        return ApiResponse.ok(StoreItemDto.from(s, false, false), "Item created");
    }

    @PutMapping("/store/items/{id}")
    public ApiResponse<StoreItemDto> updateItem(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                                @Valid @RequestBody StoreItemRequest req) {
        StoreItem s = store.update(id, req);
        adminLog.log(me, "STORE_ITEM_UPDATED", "storeItem", s.id, Map.of("sku", s.sku, "price", s.priceCoins, "active", s.active));
        return ApiResponse.ok(StoreItemDto.from(s, false, false), "Item updated");
    }

    // ---------------------------------------------------------------- reports

    @GetMapping("/reports")
    public ApiResponse<PageResponse<ReportDto>> reports(@RequestParam(required = false) ReportStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reports.adminList(status, page, size));
    }

    @PutMapping("/reports/{id}")
    public ApiResponse<ReportDto> updateReport(@AuthenticationPrincipal AuthUser me, @PathVariable String id,
                                               @Valid @RequestBody UpdateReport req) {
        return ApiResponse.ok(reports.update(me, id, req), "Report updated");
    }

    // --------------------------------------------------------------- settings

    @GetMapping("/settings")
    public ApiResponse<SettingsDto> getSettings() {
        return ApiResponse.ok(settings.toDto(settings.get()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<SettingsDto> updateSettings(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateSettings req) {
        PlatformSettings s = settings.update(req, me.id());
        adminLog.log(me, "SETTINGS_UPDATED", "settings", PlatformSettings.ID, Map.of("changed", req.toString()));
        return ApiResponse.ok(settings.toDto(s), "Settings saved");
    }
}
