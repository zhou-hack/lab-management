package com.labmanage.module.item.controller;

import com.labmanage.common.result.PageResult;
import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.common.utils.Ids;
import com.labmanage.module.item.dto.ClaimAvailabilityQuery;
import com.labmanage.module.item.dto.ClaimCreateDTO;
import com.labmanage.module.item.dto.ClaimDelegationDTO;
import com.labmanage.module.item.dto.ClaimPageQuery;
import com.labmanage.module.item.dto.ClaimReasonDTO;
import com.labmanage.module.item.dto.ClaimUpdateDTO;
import com.labmanage.module.item.service.ClaimService;
import com.labmanage.module.item.vo.ClaimAvailabilityVO;
import com.labmanage.module.item.vo.ClaimDetailVO;
import com.labmanage.module.item.vo.ClaimDocumentChainVO;
import com.labmanage.module.item.vo.ClaimSummaryVO;
import com.labmanage.module.item.vo.ClaimTemplateVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/item/claims")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @RequirePermission("item:claim:create")
    public ResponseEntity<Result<ClaimDetailVO>> create(@Valid @RequestBody ClaimCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(claimService.create(dto)));
    }

    @GetMapping
    @RequirePermission("item:claim:list")
    public Result<PageResult<ClaimSummaryVO>> pageMine(@Valid @ModelAttribute ClaimPageQuery query) {
        return Result.success(claimService.pageMine(query));
    }

    @GetMapping("/availability")
    @RequirePermission("item:claim:create")
    public Result<PageResult<ClaimAvailabilityVO>> availability(@Valid @ModelAttribute ClaimAvailabilityQuery query) {
        return Result.success(claimService.availability(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("item:claim:list")
    public Result<ClaimDetailVO> detail(@PathVariable String id) {
        return Result.success(claimService.detailMine(Ids.parse(id)));
    }

    @PutMapping("/{id}")
    @RequirePermission("item:claim:update")
    public Result<ClaimDetailVO> update(@PathVariable String id,
                                        @Valid @RequestBody ClaimUpdateDTO dto) {
        return Result.success(claimService.update(Ids.parse(id), dto));
    }

    @PutMapping("/{id}/delegation")
    @RequirePermission("item:claim:delegate")
    public Result<ClaimDetailVO> delegate(@PathVariable String id,
                                          @Valid @RequestBody ClaimDelegationDTO dto) {
        return Result.success(claimService.delegate(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("item:claim:cancel")
    public Result<ClaimDetailVO> cancel(@PathVariable String id,
                                        @Valid @RequestBody(required = false) ClaimReasonDTO dto) {
        return Result.success(claimService.cancel(Ids.parse(id), dto));
    }

    @PostMapping("/{id}/abandon")
    @RequirePermission("item:claim:abandon")
    public Result<ClaimDetailVO> abandon(@PathVariable String id,
                                         @Valid @RequestBody(required = false) ClaimReasonDTO dto) {
        return Result.success(claimService.abandon(Ids.parse(id), dto));
    }

    @GetMapping("/{id}/documents")
    @RequirePermission("item:claim:list")
    public Result<ClaimDocumentChainVO> documents(@PathVariable String id) {
        return Result.success(claimService.documents(Ids.parse(id)));
    }

    @GetMapping("/{id}/template")
    @RequirePermission("item:claim:create")
    public Result<ClaimTemplateVO> template(@PathVariable String id) {
        return Result.success(claimService.template(Ids.parse(id)));
    }
}
