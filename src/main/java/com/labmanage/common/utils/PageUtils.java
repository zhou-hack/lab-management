package com.labmanage.common.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.labmanage.common.core.BasePageQuery;
import com.labmanage.common.result.PageResult;

/**
 * 分页工具
 */
public final class PageUtils {

    private PageUtils() {
    }

    /**
     * 转换为 MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toPage(BasePageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /**
     * 转换为统一分页返回体
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.of(
                page.getRecords(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize()
        );
    }
}
