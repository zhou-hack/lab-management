package com.labmanage.module.item.transfer;

import com.labmanage.common.exception.BusinessException;
import com.labmanage.common.result.ResultCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * xlsx 行读取器：仅读第一个 sheet，用 DataFormatter 读单元格显示值
 * （数值格按显示格式还原，如 0.5 读作 "0.5"；公式格取缓存结果值），空单元格读空串。
 * 中间物理空行保留为单空字段行，行号与 Excel 行号对齐。
 */
public class XlsxRowReader implements RowReader {

    private static final DataFormatter FORMATTER = new DataFormatter();

    @Override
    public List<List<String>> read(InputStream in, int maxDataRows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            int headerCellNum = 0;
            for (int r = 0; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    rows.add(List.of(""));
                    continue;
                }
                int lastCellNum = row.getLastCellNum();
                if (r == 0 && lastCellNum > headerCellNum) {
                    headerCellNum = lastCellNum;
                }
                // 列数与表头对齐（缺失单元格读空串），保证数据行字段数不低于表头
                int colCount = Math.max(Math.max(lastCellNum, 1), Math.max(headerCellNum, 1));
                List<String> fields = new ArrayList<>(colCount);
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    fields.add(cell == null ? "" : FORMATTER.formatCellValue(cell));
                }
                rows.add(fields);
                if (r > maxDataRows) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                            "单次导入最多 " + maxDataRows + " 行，请分批导入");
                }
            }
            if (rows.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                        "文件内容为空，请下载最新模板填写后导入");
            }
            return rows;
        }
    }
}
