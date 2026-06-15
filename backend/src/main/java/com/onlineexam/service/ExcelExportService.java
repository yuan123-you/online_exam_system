package com.onlineexam.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelExportService {

  public byte[] generateScoreExcel(String examName, List<Map<String, Object>> rows) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("成绩表");

      // 标题样式
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setBorderBottom(BorderStyle.THIN);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      // 数据样式
      CellStyle dataStyle = workbook.createCellStyle();
      dataStyle.setBorderBottom(BorderStyle.THIN);
      dataStyle.setBorderTop(BorderStyle.THIN);
      dataStyle.setBorderLeft(BorderStyle.THIN);
      dataStyle.setBorderRight(BorderStyle.THIN);

      // 及格样式（绿色）
      CellStyle passStyle = workbook.createCellStyle();
      passStyle.cloneStyleFrom(dataStyle);
      Font passFont = workbook.createFont();
      passFont.setColor(IndexedColors.DARK_GREEN.getIndex());
      passStyle.setFont(passFont);

      // 不及格样式（红色）
      CellStyle failStyle = workbook.createCellStyle();
      failStyle.cloneStyleFrom(dataStyle);
      Font failFont = workbook.createFont();
      failFont.setColor(IndexedColors.RED.getIndex());
      failStyle.setFont(failFont);

      // 表头
      String[] headers = {"排名", "学号", "姓名", "班级", "状态", "得分", "总分", "及格线", "得分率"};
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // 数据行
      int rowNum = 1;
      for (Map<String, Object> row : rows) {
        Row dataRow = sheet.createRow(rowNum++);

        // 排名
        Cell rankCell = dataRow.createCell(0);
        Object rank = row.get("rank");
        if (rank != null) {
          rankCell.setCellValue(((Number) rank).intValue());
        } else {
          rankCell.setCellValue("-");
        }
        rankCell.setCellStyle(dataStyle);

        // 学号
        Cell usernameCell = dataRow.createCell(1);
        usernameCell.setCellValue(str(row, "username"));
        usernameCell.setCellStyle(dataStyle);

        // 姓名
        Cell nameCell = dataRow.createCell(2);
        nameCell.setCellValue(str(row, "studentName"));
        nameCell.setCellStyle(dataStyle);

        // 班级
        Cell classCell = dataRow.createCell(3);
        classCell.setCellValue(str(row, "className"));
        classCell.setCellStyle(dataStyle);

        // 状态
        Cell statusCell = dataRow.createCell(4);
        statusCell.setCellValue(str(row, "status"));
        statusCell.setCellStyle(dataStyle);

        // 得分
        Cell scoreCell = dataRow.createCell(5);
        Object score = row.get("score");
        int totalScore = asInt(row.get("totalScore"));
        int passScore = asInt(row.get("passScore"));
        if (score != null) {
          int scoreVal = ((Number) score).intValue();
          scoreCell.setCellValue(scoreVal);
          // 根据是否及格设置样式
          scoreCell.setCellStyle(scoreVal >= passScore ? passStyle : failStyle);
        } else {
          scoreCell.setCellValue("-");
          scoreCell.setCellStyle(dataStyle);
        }

        // 总分
        Cell totalCell = dataRow.createCell(6);
        totalCell.setCellValue(totalScore);
        totalCell.setCellStyle(dataStyle);

        // 及格线
        Cell passCell = dataRow.createCell(7);
        passCell.setCellValue(passScore);
        passCell.setCellStyle(dataStyle);

        // 得分率
        Cell rateCell = dataRow.createCell(8);
        if (score != null && totalScore > 0) {
          double rate = ((Number) score).doubleValue() / totalScore * 100;
          rateCell.setCellValue(Math.round(rate * 10.0) / 10.0 + "%");
        } else {
          rateCell.setCellValue("-");
        }
        rateCell.setCellStyle(dataStyle);
      }

      // 自动调整列宽
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
        sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 6000));
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private String str(Map<String, Object> map, String key) {
    Object v = map == null ? null : map.get(key);
    return v == null ? "" : String.valueOf(v);
  }

  private int asInt(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null || String.valueOf(value).isBlank()) return 0;
    try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { return 0; }
  }
}
