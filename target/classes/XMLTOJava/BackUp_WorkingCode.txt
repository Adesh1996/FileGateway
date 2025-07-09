package XMLTOJava;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Pain001ToExcelAndVersionCSVs {

    public static void main(String[] args) throws Exception {
        Pain001ToExcelAndVersionCSVs converter = new Pain001ToExcelAndVersionCSVs();
        String op= "F:\\Barclays BFG Project\\FileGateway\\custom-columns.properties";
        converter.processFiles("input", "output", "column-mapping.properties", op, "order.properties");
    }

    public void processFiles(String inputFolderPath, String outputFolderPath, String mappingFilePath, String customColumnsFilePath, String columnOrderFilePath) throws Exception {
        File inputFolder = new File(inputFolderPath);
        File outputFolder = new File(outputFolderPath);
        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        Map<String, Map<String, String>> formatColumnMappings = loadColumnMappingsByFormat(mappingFilePath);
        Map<String, String> customColumns = loadCustomColumns(customColumnsFilePath);
        Set<String> unmappedTags = new TreeSet<String>();

        File[] xmlFiles = listXmlFiles(inputFolder);
        if (xmlFiles == null || xmlFiles.length == 0) {
            System.out.println("No XML files found.");
            return;
        }

        Workbook workbook = new XSSFWorkbook();
        Workbook unmappedWorkbook = new XSSFWorkbook();

        Map<String, List<Map<String, String>>> versionDataMap = new LinkedHashMap<String, List<Map<String, String>>>();
        Map<String, LinkedHashSet<String>> versionHeadersMap = new LinkedHashMap<String, LinkedHashSet<String>>();

        Map<String, List<String>> columnOrder = loadColumnOrder(columnOrderFilePath);
        Map<String, String> fileScenarioMap = new HashMap<>();
        int scenarioCounter = 1;
        for (File xmlFile : xmlFiles) {
            String scenarioId = "SCENARIO_" + scenarioCounter++;
            fileScenarioMap.put(xmlFile.getName(), scenarioId);
            processXmlFile(xmlFile, formatColumnMappings, versionDataMap, versionHeadersMap, unmappedTags, fileScenarioMap, customColumns);
        }

        writeExcelSheets(outputFolderPath, workbook, versionDataMap, versionHeadersMap, formatColumnMappings, customColumns,columnOrder);
        writeCSVFiles(outputFolderPath, versionDataMap, versionHeadersMap, formatColumnMappings,customColumns,columnOrder);
        writeUnmappedTagsExcel(outputFolderPath, unmappedWorkbook, unmappedTags);

        System.out.println("✅ All Excel, CSV, and unmapped tag files created in '" + outputFolderPath + "' folder.");
    }


   public File[] listXmlFiles(File folder) {
        return folder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });
    }


   public void processXmlFile(File xmlFile,
		    Map<String, Map<String, String>> formatColumnMappings,
		    Map<String, List<Map<String, String>>> versionDataMap,
		    Map<String, LinkedHashSet<String>> versionHeadersMap,
		    Set<String> unmappedTags,
		    Map<String, String> fileScenarioMap,
		    Map<String, String> customColumns) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlFile);
        doc.getDocumentElement().normalize();

        String namespace = doc.getDocumentElement().getNamespaceURI();
        String version = extractVersionFromNamespace(namespace);

        Element root = doc.getDocumentElement();
        Element initn = null;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                initn = (Element) n;
                break;
            }
        }
        if (initn == null) return;

        NodeList pmtInfs = initn.getElementsByTagNameNS("*", "PmtInf");
        for (int i = 0; i < pmtInfs.getLength(); i++) {
            Element pmtInf = (Element) pmtInfs.item(i);
            Map<String, String> base = new LinkedHashMap<>();

            Element grpHdr = getChildByLocalName(initn, "GrpHdr");
            collectTextElements(base, "GrpHdr", grpHdr);
            collectTextElements(base, "PmtInf", pmtInf);

            NodeList txs = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
            if (txs.getLength() == 0) {
                txs = pmtInf.getElementsByTagNameNS("*", "DrctDbtTxInf");
            }
            
          //  String txNodeName = version.startsWith("pain.001.") ? "CdtTrfTxInf" : "DrctDbtTxInf";
           // NodeList txs = pmtInf.getElementsByTagNameNS("*", txNodeName);
            for (int j = 0; j < txs.getLength(); j++) {
                Element tx = (Element) txs.item(j);
                Map<String, String> row = new LinkedHashMap<>(base);
                
                if (customColumns.containsKey(version + "||ScenarioID")) {
                    row.put("ScenarioID", fileScenarioMap.get(xmlFile.getName()));
                }
                collectTextElements(row, "CdtTrfTxInf", tx);
                row.put("SourceFile", xmlFile.getName());

                versionDataMap.computeIfAbsent(version, k -> new ArrayList<>()).add(row);
                versionHeadersMap.computeIfAbsent(version, k -> new LinkedHashSet<>()).addAll(row.keySet());

                Map<String, String> columnMapping = formatColumnMappings.getOrDefault(version, new HashMap<>());
                for (String key : row.keySet()) {
                    if (!columnMapping.containsKey(key)) {
                        unmappedTags.add(version + "||" + key);
                    }
                }
            }
        }
    }

     public void writeExcelSheets(String outputFolderPath, Workbook workbook,
                                 Map<String, List<Map<String, String>>> versionDataMap,
                                 Map<String, LinkedHashSet<String>> versionHeadersMap,
                                 Map<String, Map<String, String>> formatColumnMappings,
                                 Map<String, String> customColumns,
                                 Map<String, List<String>> columnOrder) throws Exception {

        for (String version : versionDataMap.keySet()) {
            Map<String, String> columnMappings = formatColumnMappings.getOrDefault(version, new HashMap<String, String>());
            List<Map<String, String>> rows = versionDataMap.get(version);
            List<String> headersList =new ArrayList<> (versionHeadersMap.get(version));

            // Include custom columns
            for (String key : customColumns.keySet()) {
                if (key.startsWith(version + "||")) {
                    String field = key.substring((version + "||").length());
                    if (!headersList.contains(field)) {
                    System.out.println(field);
                    headersList.add(field);}
                }
            }

		
            
            List<String> orderedList = new ArrayList<>();
            List<String> ordering = columnOrder.get(version);
            if (ordering != null) {
                for (String col : ordering) {
                    if (headersList.contains(col)) orderedList.add(col);
                }
                for (String col : headersList) {
                    if (!orderedList.contains(col)) orderedList.add(col);
                }
                headersList = orderedList;
            }

            Sheet sheet = workbook.createSheet(version.length() > 31 ? version.substring(0, 31) : version);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headersList.size(); i++) {
                String mapped = columnMappings.getOrDefault(headersList.get(i), headersList.get(i));
                headerRow.createCell(i).setCellValue(mapped);
            }

            Map<String, CellStyle> styleMap = new HashMap<>();
            short colorIndex = 50;

            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> rowData = rows.get(i);
                String fileName = rowData.get("SourceFile");

                CellStyle style = styleMap.get(fileName);
                if (style == null) {
                    style = workbook.createCellStyle();
                    style.setFillForegroundColor(colorIndex);
                    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    styleMap.put(fileName, style);
                    colorIndex = (short) ((colorIndex + 1) % 65);
                    if (colorIndex < 8) colorIndex = 40;
                }

                Row row = sheet.createRow(i + 1);
                for (int j = 0; j < headersList.size(); j++) {
                    String key = headersList.get(j);
                    String val = rowData.getOrDefault(key, customColumns.getOrDefault(version + "||" + key, ""));
                    Cell cell = row.createCell(j);
                    cell.setCellValue(val);
                    cell.setCellStyle(style);
                }
            }
        }

        FileOutputStream excelOut = new FileOutputStream(outputFolderPath + "/AllPain001_Formats.xlsx");
        workbook.write(excelOut);
        workbook.close();
        excelOut.close();
    }

    public void writeCSVFiles(String outputFolderPath,
                              Map<String, List<Map<String, String>>> versionDataMap,
                              Map<String, LinkedHashSet<String>> versionHeadersMap,
                              Map<String, Map<String, String>> formatColumnMappings,
                              Map<String, String> customColumns,
                              Map<String, List<String>> columnOrder) throws Exception {

        for (String version : versionDataMap.keySet()) {
            Map<String, String> columnMappings = formatColumnMappings.getOrDefault(version, new HashMap<>());
            List<Map<String, String>> rows = versionDataMap.get(version);
            List<String> headers = new ArrayList<>(versionHeadersMap.get(version));

         // Add custom columns
         for (String key : customColumns.keySet()) {
             if (key.startsWith(version + "||")) {
                 String field = key.substring((version + "||").length());
                 if (!headers.contains(field)) {
                     headers.add(field);
                 }
             }
         }
         List<String> orderedList = new ArrayList<>();
         List<String> ordering = columnOrder.get(version);
         if (ordering != null) {
             for (String col : ordering) {
                 if (headers.contains(col)) orderedList.add(col);
             }
             for (String col : headers) {
                 if (!orderedList.contains(col)) orderedList.add(col);
             }
             headers = orderedList;
         }

            FileWriter fw = new FileWriter(outputFolderPath + "/" + version + ".csv");
            BufferedWriter bw = new BufferedWriter(fw);

            for (int i = 0; i < headers.size(); i++) {
                String mapped = columnMappings.getOrDefault(headers.get(i), headers.get(i));
                bw.write(mapped);
                if (i < headers.size() - 1) bw.write(",");
            }
            bw.newLine();

            for (Map<String, String> row : rows) {
                for (int j = 0; j < headers.size(); j++) {
                    String val = row.getOrDefault(headers.get(j), "");
                    bw.write("\"" + val.replace("\"", "\"\"") + "\"");
                    if (j < headers.size() - 1) bw.write(",");
                }
                bw.newLine();
            }

            bw.close();
        }
    }

    public void writeUnmappedTagsExcel(String outputFolderPath, Workbook unmappedWorkbook, Set<String> unmappedTags) throws Exception {
        Sheet unmappedSheet = unmappedWorkbook.createSheet("UnmappedTags");
        int rowIdx = 0;
        for (String tag : unmappedTags) {
            Row row = unmappedSheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(tag);
        }
        FileOutputStream unmappedOut = new FileOutputStream(outputFolderPath + "/unmapped-tags.xlsx");
        unmappedWorkbook.write(unmappedOut);
        unmappedWorkbook.close();
        unmappedOut.close();
    }
	
	 private Map<String, String> loadCustomColumns(String filePath) throws Exception {
        Properties props = new Properties();
        Map<String, String> customColumns = new HashMap<>();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("No custom columns file found at: " + filePath);
            return customColumns;
        }

        FileInputStream fis = new FileInputStream(file);
        props.load(fis);
        fis.close();

        for (Object keyObj : props.keySet()) {
            String key = (String) keyObj;
            System.out.println(key + "****");
            customColumns.put(key, props.getProperty(key));
        }

        return customColumns;
    }

    private Map<String, Map<String, String>> loadColumnMappingsByFormat(String filePath) throws Exception {
        Properties props = new Properties();
        Map<String, Map<String, String>> formatMappings = new HashMap<>();
        File mappingFile = new File(filePath);

        if (!mappingFile.exists()) {
            System.out.println("❗ Mapping file not found: " + filePath);
            return formatMappings;
        }

        FileInputStream fis = new FileInputStream(mappingFile);
        props.load(fis);
        fis.close();

        for (Object obj : props.keySet()) {
            String key = (String) obj;
            String value = props.getProperty(key);
            if (key.contains("||")) {
                String[] parts = key.split("||", 2);
                String version = parts[0];
                String field = parts[1];
                formatMappings.computeIfAbsent(version, k -> new HashMap<>()).put(field, value);
            }
        }

        return formatMappings;
    }

    private String extractVersionFromNamespace(String ns) {
        if (ns == null) return "UnknownFormat";

        String[] supported = {
            "pain.001.001.", "pain.001.002.", "pain.001.003.",
            "pain.008.002.", "pain.008.003.",
            "pain.008.001.", "pain7v9", "pain.008.001.02", "pain8v8", "payext", "paymul"
        };

        for (int i = 0; i < supported.length; i++) {
            if (ns.contains(supported[i])) {
                int idx = ns.indexOf(supported[i]);
                return ns.substring(idx);
            }
        }

        return "UnknownFormat";
    }

    private void collectTextElements(Map<String, String> data, String path, Element element) {
        if (element == null) return;

        NodeList children = element.getChildNodes();
        boolean hasElement = false;

        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                hasElement = true;
                String tagName = ((Element) n).getLocalName();
                collectTextElements(data, path + "_" + tagName, (Element) n);
            }
        }

        if (!hasElement && element.getTextContent() != null && !element.getTextContent().trim().isEmpty()) {
            data.put(path, element.getTextContent().trim());
        }

        if (element.hasAttributes()) {
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Attr attr = (Attr) attrs.item(i);
                data.put(path + "_@" + attr.getName(), attr.getValue());
            }
        }
    }

    private Element getChildByLocalName(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element) {
                String local = ((Element) n).getLocalName();
                if (local != null && local.equals(name)) {
                    return (Element) n;
                }
            }
        }
        return null;
    }
    
    private static Map<String, List<String>> loadColumnOrder(String filePath) throws Exception {
        Properties props = new Properties();
        Map<String, List<String>> columnOrder = new HashMap<>();
        File orderFile = new File(filePath);
        if (!orderFile.exists()) return columnOrder;

        FileInputStream fis = new FileInputStream(orderFile);
        props.load(fis);
        fis.close();

        for (Object key : props.keySet()) {
            String version = (String) key;
            String[] fields = props.getProperty(version).split(",");
            List<String> ordered = new ArrayList<>();
            for (String f : fields) {
                ordered.add(f.trim());
            }
            columnOrder.put(version, ordered);
        }
        return columnOrder;
    }
    
    public void writeUnmappedTagsExcel_old(String outputFolderPath, Workbook unmappedWorkbook, Set<String> unmappedTags) throws Exception {
        Map<String, List<String>> tagsByVersion = new HashMap<>();
        for (String fullTag : unmappedTags) {
            int sepIndex = fullTag.indexOf("||");
            if (sepIndex > 0) {
                String version = fullTag.substring(0, sepIndex);
                String tag = fullTag.substring(sepIndex + 2);
                List<String> list = tagsByVersion.get(version);
                if (list == null) {
                    list = new ArrayList<>();
                    tagsByVersion.put(version, list);
                }
                list.add(tag);
            }
        }

        for (String version : tagsByVersion.keySet()) {
            List<String> tags = tagsByVersion.get(version);
            Sheet sheet = unmappedWorkbook.createSheet(version.length() > 31 ? version.substring(0, 31) : version);
            int rowIdx = 0;
            for (String tag : tags) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tag);
            }
        }

        try (FileOutputStream unmappedOut = new FileOutputStream(outputFolderPath + "/unmapped-tags.xlsx")) {
            unmappedWorkbook.write(unmappedOut);
        }
        unmappedWorkbook.close();
    }

}
