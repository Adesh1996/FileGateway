package ExcelPropertiesUpdater;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class ExcelPropertiesUpdater {

    public static void main(String[] args) {
        String excelFilePath = "F:\\Barclays BFG Project\\FileGateway\\output\\pain.001.001.03\\pain.001.001.03.xlsx"; // **Change this to your Excel file path**
        String propertiesFilePath = "F:\\Barclays BFG Project\\FileGateway\\mapping-properties\\pain.001.001.03.properties"; // **Change this to your properties file path**

        try {
            // 1. Read Excel Headers
            Set<String> excelHeaders = getExcelHeaders(excelFilePath);
            System.out.println("Excel Headers found: " + excelHeaders);

            // 2. Read Existing Properties
            Properties properties = new Properties();
            Set<String> existingPropertyKeys = new HashSet<>();
            try (FileInputStream fis = new FileInputStream(propertiesFilePath)) {
                properties.load(fis);
                existingPropertyKeys.addAll(properties.stringPropertyNames());
            } catch (IOException e) {
                System.out.println("Properties file not found or cannot be read. A new one will be created if needed.");
                // If file doesn't exist, existingPropertyKeys will remain empty, which is fine.
            }
            System.out.println("Existing Property Keys: " + existingPropertyKeys);

            // 3. Add New Headers to Properties File
            boolean propertiesUpdated = false;
            for (String header : excelHeaders) {
                if (!existingPropertyKeys.contains(header)) {
                    String value = header.replace("_", "/");
                    properties.setProperty(header, value);
                    System.out.println("Adding new property: " + header + "=" + value);
                    propertiesUpdated = true;
                }
            }

            // 4. Save Updated Properties File
            if (propertiesUpdated) {
                try (FileOutputStream fos = new FileOutputStream(propertiesFilePath)) {
                    properties.store(fos, "Updated from Excel headers");
                    System.out.println("Properties file updated successfully at: " + propertiesFilePath);
                }
            } else {
                System.out.println("No new headers to add. Properties file remains unchanged.");
            }

        } catch (IOException e) {
            System.err.println("An I/O error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Set<String> getExcelHeaders(String excelFilePath) throws IOException {
        Set<String> headers = new HashSet<>();
        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) { // Use XSSFWorkbook for .xlsx
            Sheet sheet = workbook.getSheetAt(0); // Get the first sheet
            Row firstRow = sheet.getRow(0); // Get the first row (column headers)

            if (firstRow != null) {
                for (Cell cell : firstRow) {
                    // Ensure cell type is string or get as string
                    String header = cell.getStringCellValue().trim();
                    if (!header.isEmpty()) {
                        headers.add(header);
                    }
                }
            }
        }
        return headers;
    }
}