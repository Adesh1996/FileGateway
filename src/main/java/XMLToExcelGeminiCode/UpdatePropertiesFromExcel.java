package XMLToExcelGeminiCode;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.util.*;


/**
 * 
 * This class is used for ordering of pain files sequence.
 * @author ADMIN
 *
 */
public class UpdatePropertiesFromExcel {

    public static void main(String[] args) throws Exception {
        String excelPath = "input.xlsx";
        String propertiesPath = "pain.001.001.03.properties";

        Properties props = new Properties();
        FileInputStream fis = new FileInputStream(propertiesPath);
        props.load(fis);
        fis.close();

        // Load existing mappings and input_order
        String inputOrderStr = props.getProperty("input_order", "");
        List<String> inputOrderList = new ArrayList<String>(Arrays.asList(inputOrderStr.split(",")));
        LinkedHashMap<String, String> tagPathToColumnMap = new LinkedHashMap<String, String>();
        LinkedHashMap<String, String> columnToTagPathMap = new LinkedHashMap<String, String>();

        for (String key : props.stringPropertyNames()) {
            if (!"input_order".equals(key)) {
                String col = props.getProperty(key).trim();
                tagPathToColumnMap.put(key.trim(), col);
                columnToTagPathMap.put(col, key.trim());
            }
        }

        // Read Excel headers
        List<String> excelHeaders = readExcelHeaders(excelPath);

        for (String header : excelHeaders) {
            if (!columnToTagPathMap.containsKey(header)) {
                // No mapping exists → create a tag path dynamically
                String guessedTagPath = guessPathFromColumn(header);
                int suffix = 1;
                while (tagPathToColumnMap.containsKey(guessedTagPath)) {
                    guessedTagPath = guessedTagPath + "_" + suffix;
                    suffix++;
                }
                tagPathToColumnMap.put(guessedTagPath, header);
                columnToTagPathMap.put(header, guessedTagPath);
                props.setProperty(guessedTagPath, header);
                System.out.println("Added new mapping: " + guessedTagPath + "=" + header);
            }
        }

        // Rebuild input_order with sorted column names based on tag path
        List<String> orderedColumns = rebuildInputOrder(tagPathToColumnMap);
        props.setProperty("input_order", String.join(",", orderedColumns));

        FileOutputStream fos = new FileOutputStream(propertiesPath);
        props.store(fos, "Updated dynamically with missing headers and proper order");
        fos.close();

        System.out.println("Updated: " + propertiesPath);
    }

    private static List<String> readExcelHeaders(String excelFilePath) throws IOException {
        List<String> headers = new ArrayList<String>();
        FileInputStream fis = new FileInputStream(excelFilePath);
        Workbook workbook = new XSSFWorkbook(fis);
        Sheet sheet = workbook.getSheetAt(0);
        Row row = sheet.getRow(0);
        if (row != null) {
            Iterator<Cell> cellIterator = row.cellIterator();
            while (cellIterator.hasNext()) {
                Cell cell = cellIterator.next();
                cell.setCellType(CellType.STRING);
                headers.add(cell.getStringCellValue().trim());
            }
        }
        workbook.close();
        fis.close();
        return headers;
    }

    // Guess XPath-like tag path from column name (e.g. PmtInf_CdtTrfTxInf_Amt_InstdAmt)
    private static String guessPathFromColumn(String colName) {
        return colName.replace("__", "#").replace("_", "/").replace("#", "_");
    }

    // Sort tagPathToColMap entries by tag path (ignoring numeric suffixes)
    private static List<String> rebuildInputOrder(Map<String, String> tagPathToColMap) {
        List<Map.Entry<String, String>> sortedEntries = new ArrayList<Map.Entry<String, String>>(tagPathToColMap.entrySet());

        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, String>>() {
            public int compare(Map.Entry<String, String> e1, Map.Entry<String, String> e2) {
                String key1 = stripNumbers(e1.getKey());
                String key2 = stripNumbers(e2.getKey());
                return key1.compareTo(key2);
            }
        });

        List<String> ordered = new ArrayList<String>();
        for (Map.Entry<String, String> entry : sortedEntries) {
            ordered.add(entry.getValue());
        }
        return ordered;
    }

    // Remove digits from tag path to normalize it (for sorting)
    private static String stripNumbers(String input) {
        return input.replaceAll("\\d+", "");
    }
}
