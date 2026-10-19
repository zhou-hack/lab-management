package com.labmanage.module.item.transfer;

import java.io.IOException;

/**
 * 导出文件行写出器：按行写字符串单元格，最终生成文件字节
 */
public interface RowWriter extends AutoCloseable {

    /**
     * 写一行（首行为表头，xlsx 实现会加表头样式）
     */
    void writeRow(String... cells) throws IOException;

    /**
     * 生成最终文件字节（xlsx 内部执行 workbook 写出并释放临时资源）
     */
    byte[] toBytes() throws IOException;

    @Override
    void close();
}
