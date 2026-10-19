package com.labmanage.module.item.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 导入文件行读取器：把 xlsx / csv 统一解析为字符串矩阵
 * <p>
 * 返回结果首行为表头，后续为数据行；行号与物理行对齐（空行保留）。
 */
public interface RowReader {

    /**
     * @param maxDataRows 数据行上限（不含表头），超限抛 40000
     */
    List<List<String>> read(InputStream in, int maxDataRows) throws IOException;
}
