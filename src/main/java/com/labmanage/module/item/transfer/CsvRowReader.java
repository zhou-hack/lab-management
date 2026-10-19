package com.labmanage.module.item.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * CSV 行读取器（委托 CsvUtils）
 */
public class CsvRowReader implements RowReader {

    @Override
    public List<List<String>> read(InputStream in, int maxDataRows) throws IOException {
        return CsvUtils.parse(in, maxDataRows);
    }
}
