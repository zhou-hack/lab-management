package com.labmanage.module.item.controller;

import com.labmanage.common.result.Result;
import com.labmanage.common.security.RequirePermission;
import com.labmanage.module.booking.mapper.ClassPeriodMapper;
import com.labmanage.module.booking.vo.PeriodVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/item/periods")
public class ItemPeriodController {

    private final ClassPeriodMapper periodMapper;

    public ItemPeriodController(ClassPeriodMapper periodMapper) {
        this.periodMapper = periodMapper;
    }

    @GetMapping
    @RequirePermission(value = {"item:claim:create", "item:claim:review"}, mode = RequirePermission.Mode.OR)
    public Result<List<PeriodVO>> periods() {
        return Result.success(periodMapper.listEnabled().stream()
                .map(period -> new PeriodVO(String.valueOf(period.getId()), period.getPeriodNo(),
                        period.getPeriodName(), PeriodVO.format(period.getStartClock()),
                        PeriodVO.format(period.getEndClock())))
                .toList());
    }
}
