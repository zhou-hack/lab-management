package com.labmanage.module.item.transfer;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * xlsx 行写出器：SXSSF 滑动窗口生成，全文本单元格（防前导零丢失与科学计数法），
 * 表头加粗灰底，固定列宽。toBytes 内部写出并 dispose 临时资源。
 */
public class XlsxRowWriter implements RowWriter {

    private static final int WINDOW_SIZE = 200;
    private static final int COLUMN_WIDTH = 20 * 256;

    private final SXSSFWorkbook workbook = new SXSSFWorkbook(WINDOW_SIZE);
    private final SXSSFSheet sheet;
    private final CellStyle headerStyle;
    private int rowIndex = 0;
    private byte[] bytes;
    private boolean finished = false;

    public XlsxRowWriter() {
        sheet = workbook.createSheet("数据");
        headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    @Override
    public void writeRow(String... cells) {
        SXSSFRow row = sheet.createRow(rowIndex);
        boolean header = rowIndex == 0;
        for (int c = 0; c < cells.length; c++) {
            org.apache.poi.ss.usermodel.Cell cell = row.createCell(c);
            cell.setCellValue(cells[c] == null ? "" : cells[c]);
            if (header) {
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(c, COLUMN_WIDTH);
            }
        }
        rowIndex++;
    }

    @Override
    public byte[] toBytes() throws IOException {
        if (bytes != null) {
            return bytes;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        bytes = out.toByteArray();
        return bytes;
    }

    @Override
    public void close() {
        if (!finished) {
            finished = true;
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // 临时资源已 dispose，关闭失败可忽略
            }
        }
    }
}
