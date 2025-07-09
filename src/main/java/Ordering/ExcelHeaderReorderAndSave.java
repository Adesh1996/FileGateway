package Ordering;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;

public class ExcelHeaderReorderAndSave {

    public static void main(String[] args) throws Exception {
        String excelPath = "input.xlsx"; // Update with your actual path
        String orderString = "PmtInf_PmtInfId,PmtInf_CdtTrfTxInf_Amt_InstdAmt"; // Can also load from .properties
        String outputPath = "updated_order.properties"; // or use .txt

        List<String> originalOrder = new ArrayList<>(Arrays.asList(orderString.split(",")));
        Set<String> currentSet = new LinkedHashSet<>(originalOrder);

        List<String> excelHeaders = readExcelHeaders(excelPath);

        for (String header : excelHeaders) {
            if (!currentSet.contains(header)) {
                String parentPath = getParentPath(header);
                int insertIndex = findInsertIndexByParent(originalOrder, parentPath);
                if (insertIndex >= 0) {
                    originalOrder.add(insertIndex + 1, header);
                } else {
                    originalOrder.add(header); // Append if no parent match
                }
                currentSet.add(header);
            }
        }

        // Print result
        System.out.println("Final Ordered Headers:");
        for (String tag : originalOrder) {
            System.out.println(tag);
        }

        // Save to file
        writeOrderToFile(originalOrder, outputPath);
        System.out.println("\nSaved updated order to: " + outputPath);
    }

    private static List<String> readExcelHeaders(String excelFilePath) throws IOException {
        List<String> headers = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow != null) {
                for (Cell cell : headerRow) {
                    cell.setCellType(CellType.STRING);
                    headers.add(cell.getStringCellValue().trim());
                }
            }
        }
        return headers;
    }

    private static String getParentPath(String fullPath) {
        int lastUnderscore = fullPath.lastIndexOf('_');
        return (lastUnderscore > 0) ? fullPath.substring(0, lastUnderscore) : "";
    }

    private static int findInsertIndexByParent(List<String> orderList, String parent) {
        for (int i = orderList.size() - 1; i >= 0; i--) {
            if (orderList.get(i).startsWith(parent)) {
                return i;
            }
        }
        return -1;
    }

    private static void writeOrderToFile(List<String> orderList, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write("order=");
            writer.write(String.join(",", orderList));
        }
    }
}

