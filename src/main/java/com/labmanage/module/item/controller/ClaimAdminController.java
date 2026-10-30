package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.ClaimIssueCreateDTO;
import com.labmanage.module.item.dto.ClaimPageQuery;
import com.labmanage.module.item.dto.ClaimRejectDTO;
import com.labmanage.module.item.dto.ClaimReviewDTO;
import com.labmanage.module.item.service.ClaimIssueService;
import com.labmanage.module.item.service.ClaimService;
import com.labmanage.module.item.vo.ClaimDetailVO;
import com.labmanage.module.item.vo.ClaimIssueContextVO;
import com.labmanage.module.item.vo.ClaimPreviewVO;
import com.labmanage.module.item.vo.ClaimSummaryVO;
import com.labmanage.module.item.vo.StockDocumentDetailVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/admin/claims")
public class ClaimAdminController {

    private final ClaimService claimService;
    private final ClaimIssueService issueService;

    public ClaimAdminController(ClaimService claimService, ClaimIssueService issueService) {
        this.claimService = claimService;
        this.issueService = issueService;
    }

    @GetMapping
    @RequirePermission({"item:claim:list", "item:claim:review"})
    public Result<PageResult<ClaimSummaryVO>> page(@Valid @ModelAttribute ClaimPageQuery query) {
        return Result.success(claimService.pageAdmin(query));
    }

    @GetMapping("/{id}")
    @RequirePermission({"item:claim:list", "item:claim:review"})
    public Result<ClaimDetailVO> detail(@PathVariable String id) {
        return Result.success(claimService.detailAdmin(Ids.parse(id)));
    }

    @PostMapping("/{id}/check")
    @RequirePermission("item:claim:review")
    public Result<ClaimPreviewVO> check(@PathVariable String id) {
        return Result.success(claimService.check(Ids.parse(id)));
    }

    @PostMapping("/{id}/approve")
    @RequirePermission("item:claim:review")
    public Result<ClaimDetailVO> approve(@PathVariable String id,
                                         @Valid @RequestBody ClaimReviewDTO dto) {
        return Result.success(claimService.approve(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/reject")
    @RequirePermission("item:claim:review")
    public Result<ClaimDetailVO> reject(@PathVariable String id,
                                        @Valid @RequestBody ClaimRejectDTO dto) {
        return Result.success(claimService.reject(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/close")
    @RequirePermission({"item:claim:abandon", "item:claim:review"})
    public Result<ClaimDetailVO> close(@PathVariable String id) {
        return Result.success(claimService.close(Ids.parse(id)));
    }

    @GetMapping("/{id}/issue_context")
    @RequirePermission("item:claim:issue")
    public Result<ClaimIssueContextVO> issueContext(@PathVariable String id,
                                                    @RequestParam String locationId) {
        return Result.success(issueService.issueContext(Ids.parse(id), Ids.parse(locationId)));
    }

    @PostMapping("/{id}/issues")
    @RequirePermission("item:claim:issue")
    public Result<StockDocumentDetailVO> issue(@PathVariable String id,
                                               @Valid @RequestBody ClaimIssueCreateDTO dto) {
        return Result.success(issueService.issue(Ids.parse(id), dto));
    }
}
