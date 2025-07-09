package XMLToExcelGeminiCode;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet; 

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


/**
 * 
 * ADESH : This is the final code which is converting xml to excel in proper way 
 * @author ADMIN
 *
 */
public class Pain001ToExcelAndVersionCSVs_Properties {

    // Tags that, even if they repeat, should not be numbered in the path
    private static final Set<String> ROW_GENERATING_TAGS = new HashSet<>(Arrays.asList("CdtTrfTxInf", "DrctDbtTxInf", "TxInf", "OrgnlPmtInfAndRvsl"));


    // Corrected method to dynamically track and suffix specific repeated tags
    private void collectTextElements(Map<String, String> data, String path, Element element, boolean stopAtRowGeneratingTags) {
        if (element == null) return;

        NodeList children = element.getChildNodes();
        Map<String, Integer> tagOccurrences = new HashMap<>();
        boolean hasElementChild = false;

        // First pass: Count occurrences of each direct child element tag
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                hasElementChild = true;
                String tag = ((Element) node).getLocalName();
                tagOccurrences.put(tag, tagOccurrences.getOrDefault(tag, 0) + 1);
            }
        }

        // Second pass: Process children, applying numbering for specific tags
        Map<String, Integer> tagIndexMap = new HashMap<>(); // To keep track of current index for numbering
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element) {
                Element child = (Element) node;
                String tag = child.getLocalName();

                // NEW LOGIC: If we need to stop at row-generating tags and this is one, don't recurse.
                if (stopAtRowGeneratingTags && ROW_GENERATING_TAGS.contains(tag)) {
                    continue; // Skip processing children of this tag
                }

                int totalOccurrences = tagOccurrences.get(tag);

                String newPath;
                // Dynamically apply numbering if the tag repeats AND is not a row-generating tag
                if (totalOccurrences > 1 && !ROW_GENERATING_TAGS.contains(tag)) {
                    int currentIndex = tagIndexMap.getOrDefault(tag, 1);
                    if (currentIndex == 1) {
                        newPath = path + "_" + tag; // First instance gets no suffix
                    } else {
                        newPath = path + "_" + tag + "_" + currentIndex; // Subsequent instances get numbered (e.g., _2, _3)
                    }
                    tagIndexMap.put(tag, currentIndex + 1);
                } else {
                    // For tags that do not repeat, or are row-generating tags, just append the tag name
                    newPath = path + "_" + tag;
                }
                collectTextElements(data, newPath, child, stopAtRowGeneratingTags); // Pass the flag down
            }
        }

        // Add leaf value only if it's a true leaf (no element children)
        // For leaf nodes, we always put the value, effectively overwriting if a key exists
        // This ensures the latest encountered value for a path is stored.
        // This is crucial for fields like EmailAdr which should be singular per context.
        if (!hasElementChild && element.getTextContent() != null && !element.getTextContent().trim().isEmpty()) {
            data.put(path, element.getTextContent().trim());
        }


        // Add attributes
        if (element.hasAttributes()) {
            NamedNodeMap attributes = element.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                data.put(path + "_@" + attr.getName(), attr.getValue());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Pain001ToExcelAndVersionCSVs_Properties converter = new Pain001ToExcelAndVersionCSVs_Properties();
        converter.processFiles("input", "output", "mapping-properties", "custom-columns-properties",
                "order.properties");
    }

    public void processFiles(String inputFolderPath, String outputFolderPath, String mappingDirPath,
            String customDirPath, String columnOrderFilePath) throws Exception {

        File inputFolder = new File(inputFolderPath);
        File outputFolder = new File(outputFolderPath);
        if (!outputFolder.exists())
            outputFolder.mkdirs();

        Set<String> unmappedTags = new TreeSet<>();
        File[] xmlFiles = listXmlFiles(inputFolder);
        if (xmlFiles == null || xmlFiles.length == 0) {
            System.out.println("No XML File FOund");
            return;}

        Workbook combinedWorkbook = new XSSFWorkbook();
        Workbook unmappedWorkbook = new XSSFWorkbook();
        Map<String, List<Map<String, String>>> versionDataMap = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> versionHeadersMap = new LinkedHashMap<>();
        Map<String, List<String>> columnOrder = loadColumnOrder(columnOrderFilePath);
        Map<String, String> fileScenarioMap = new HashMap<>();

        int scenarioCounter = 1;
        for (File xmlFile : xmlFiles) {
            String scenarioId = "SCENARIO_" + scenarioCounter++;
            fileScenarioMap.put(xmlFile.getName(), scenarioId);
            processXmlFile(xmlFile, mappingDirPath, customDirPath, versionDataMap, versionHeadersMap, unmappedTags,
                    fileScenarioMap);
        }

        Map<String, Map<String, String>> columnMappings = loadAllMappings(mappingDirPath);
        Map<String, String> customColumns = loadAllCustomColumns(customDirPath);

// Ensure all mapped and custom columns are present and ordered
        ensureAllMappedColumnsPresent(versionDataMap, versionHeadersMap, columnMappings, customColumns, columnOrder);

        for (String version : versionDataMap.keySet()) {
            Workbook versionWorkbook = new XSSFWorkbook();
            new File(outputFolderPath + "/" + version).mkdirs();
            writeExcelSheet(outputFolderPath + "/" + version, versionWorkbook, version, versionDataMap,
                    versionHeadersMap, columnMappings, customColumns, columnOrder);

            try (FileOutputStream out = new FileOutputStream(
                    outputFolderPath + "/" + version + "/" + version + ".xlsx")) {
                versionWorkbook.write(out);
            }
            versionWorkbook.close();

            writeExcelSheet(null, combinedWorkbook, version, versionDataMap, versionHeadersMap, columnMappings,
                    customColumns, columnOrder);
        }

        try (FileOutputStream combinedOut = new FileOutputStream(outputFolderPath + "/AllFormats.xlsx")) {
            combinedWorkbook.write(combinedOut);
        }
        combinedWorkbook.close();

        writeCSVFiles(outputFolderPath, versionDataMap, versionHeadersMap, columnMappings, customColumns, columnOrder);
        writeUnmappedTagsExcel(outputFolderPath, unmappedWorkbook, unmappedTags);
    }

    private Map<String, Map<String, String>> loadAllMappings(String dirPath) throws Exception {
        Map<String, Map<String, String>> allMappings = new HashMap<>();
        File dir = new File(dirPath);
        if (!dir.exists())
            return allMappings;

        for (File file : dir.listFiles((d, name) -> name.endsWith(".properties"))) {
            String version = file.getName().replace(".properties", "");
            allMappings.put(version, loadProperties(file));
        }
        return allMappings;
    }

    private Map<String, String> loadAllCustomColumns(String dirPath) throws Exception {
        Map<String, String> allCustom = new HashMap<>();
        File dir = new File(dirPath);
        if (!dir.exists())
            return allCustom;

        for (File file : dir.listFiles((d, name) -> name.endsWith(".properties"))) {
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(file);
            props.load(fis);
            fis.close();
            for (Object k : props.keySet()) {
                String key = (String) k;
                allCustom.put(key, props.getProperty(key));
            }
        }
        return allCustom;
    }

    public File[] listXmlFiles(File folder) {
        return folder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });
    }

    public void processXmlFile(File xmlFile, String mappingDir, String customDir,
            Map<String, List<Map<String, String>>> versionDataMap, Map<String, LinkedHashSet<String>> versionHeadersMap,
            Set<String> unmappedTags, Map<String, String> fileScenarioMap) throws Exception {

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlFile);
        doc.getDocumentElement().normalize();

        String namespace = doc.getDocumentElement().getNamespaceURI();
        String version = extractVersionFromNamespace(namespace);

        Map<String, String> formatColumnMappings = loadMappingForVersion(mappingDir, version);
        Map<String, String> customColumns = loadCustomColumnsForVersion(customDir, version);

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
        if (initn == null)
            return;

        String rootName = initn.getLocalName();

        if ("CstmrPmtRvsl".equals(rootName)) {
            Element grpHdr = getChildByLocalName(initn, "GrpHdr");
            NodeList pmtRevslList = initn.getElementsByTagNameNS("*", "OrgnlPmtInfAndRvsl");

            for (int i = 0; i < pmtRevslList.getLength(); i++) {
                Element pmtRevsl = (Element) pmtRevslList.item(i);
                Map<String, String> base = new LinkedHashMap<>();
                collectTextElements(base, "GrpHdr", grpHdr, true); // Pass true to stop at row-generating tags
                collectTextElements(base, "OrgnlPmtInfAndRvsl", pmtRevsl, true); // Pass true to stop at row-generating tags


                NodeList txs = pmtRevsl.getElementsByTagNameNS("*", "TxInf");
                for (int j = 0; j < txs.getLength(); j++) {
                    Element tx = (Element) txs.item(j);
                    Map<String, String> row = new LinkedHashMap<>(base); // Start with common GrpHdr/PmtInf data
                    if (customColumns.containsKey(version + "||ScenarioID")) {
                        row.put("ScenarioID", fileScenarioMap.get(xmlFile.getName()));
                    }

                    Map<String, String> transactionSpecificData = new LinkedHashMap<>(); // Temp map for current transaction's data
                    // Pass false to collect all details within this specific transaction
                    collectTextElements(transactionSpecificData, "OrgnlPmtInfAndRvsl_TxInf", tx, false);
                    row.putAll(transactionSpecificData); // Merge transaction-specific data

                    row.put("SourceFile", xmlFile.getName());

                    if (!versionDataMap.containsKey(version))
                        versionDataMap.put(version, new ArrayList<>());
                    versionDataMap.get(version).add(row);

                    if (!versionHeadersMap.containsKey(version))
                        versionHeadersMap.put(version, new LinkedHashSet<>());
                    versionHeadersMap.get(version).addAll(row.keySet());

                    for (String key : row.keySet()) {
                        if (!formatColumnMappings.containsKey(key)) {
                            unmappedTags.add(version + "||" + key);
                        }
                    }
                }
            }
        } else { // Handles pain.001, pain.008 etc.
            NodeList pmtInfs = initn.getElementsByTagNameNS("*", "PmtInf");
            for (int i = 0; i < pmtInfs.getLength(); i++) {
                Element pmtInf = (Element) pmtInfs.item(i);
                Map<String, String> base = new LinkedHashMap<>();

                Element grpHdr = getChildByLocalName(initn, "GrpHdr");
                collectTextElements(base, "GrpHdr", grpHdr, true); // Pass true to stop at row-generating tags
                collectTextElements(base, "PmtInf", pmtInf, true); // Pass true to stop at row-generating tags


                NodeList txs = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
                // Add this to count number of transactions per batch
                base.put("NumberOfTransactions", String.valueOf(txs.getLength()));

                if (txs.getLength() == 0) // Check for direct debit transactions if no credit transfers found
                    txs = pmtInf.getElementsByTagNameNS("*", "DrctDbtTxInf");
                int totalTransactionCount = 0;
                totalTransactionCount += txs.getLength();

                for (int j = 0; j < txs.getLength(); j++) {
                    Element tx = (Element) txs.item(j);
                    Map<String, String> row = new LinkedHashMap<>(base); // Start with common GrpHdr/PmtInf data

                    Map<String, String> transactionSpecificData = new LinkedHashMap<>(); // Temp map for current transaction's data
                    // Use a consistent base path for transaction details. The numbering for CdtTrfTxInf/DrctDbtTxInf itself is handled by ROW_GENERATING_TAGS.
                    String txBasePath = "";
                    if ("CdtTrfTxInf".equals(tx.getLocalName())) {
                        txBasePath = "PmtInf_CdtTrfTxInf";
                    } else if ("DrctDbtTxInf".equals(tx.getLocalName())) {
                        txBasePath = "PmtInf_DrctDbtTxInf";
                    }
                    collectTextElements(transactionSpecificData, txBasePath, tx, false); // Pass false to collect all details within this specific transaction
                    row.putAll(transactionSpecificData); // Merge transaction-specific data

                    row.put("TotalNumberOfTransactions", String.valueOf(totalTransactionCount));
                    row.put("SourceFile", xmlFile.getName());

                    if (!versionDataMap.containsKey(version))
                        versionDataMap.put(version, new ArrayList<>());
                    versionDataMap.get(version).add(row);
                    if (!versionHeadersMap.containsKey(version))
                        versionHeadersMap.put(version, new LinkedHashSet<>());
                    versionHeadersMap.get(version).addAll(row.keySet());

                    for (String key : row.keySet()) {
                        if (!formatColumnMappings.containsKey(key)) {
                            unmappedTags.add(version + "||" + key);
                        }
                    }
                }
            }
        }
    }

    public void writeExcelSheet(String folder, Workbook workbook, String version,
            Map<String, List<Map<String, String>>> versionDataMap, Map<String, LinkedHashSet<String>> versionHeadersMap,
            Map<String, Map<String, String>> columnMappings, Map<String, String> customColumns,
            Map<String, List<String>> columnOrder) throws Exception {

        Map<String, String> mappings = columnMappings.getOrDefault(version, new HashMap<>());
        List<Map<String, String>> rows = versionDataMap.get(version);
        List<String> headersList = new ArrayList<>(versionHeadersMap.get(version));

        for (String key : customColumns.keySet()) {
            if (key.startsWith(version + "||")) {
                String field = key.substring((version + "||").length());
                if (!headersList.contains(field))
                    headersList.add(field);
            }
        }

        List<String> ordering = columnOrder.get(version);
        if (ordering != null) {
            List<String> orderedList = new ArrayList<>();
            for (String col : ordering)
                if (headersList.contains(col))
                    orderedList.add(col);
            for (String col : headersList)
                if (!orderedList.contains(col))
                    orderedList.add(col);
            headersList = orderedList;
        }

        Sheet sheet = workbook.createSheet(version.length() > 31 ? version.substring(0, 31) : version);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headersList.size(); i++) {
            String mapped = mappings.getOrDefault(headersList.get(i), headersList.get(i));
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
                if (colorIndex < 8)
                    colorIndex = 40;
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

    public void writeCSVFiles(String outputFolderPath, Map<String, List<Map<String, String>>> versionDataMap,
            Map<String, LinkedHashSet<String>> versionHeadersMap, Map<String, Map<String, String>> formatColumnMappings,
            Map<String, String> customColumns, Map<String, List<String>> columnOrderMappedNames) throws Exception {

        for (String version : versionDataMap.keySet()) {
            Map<String, String> mappings = formatColumnMappings.getOrDefault(version, new HashMap<>());
            Map<String, String> reverseMappings = new HashMap<>();
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                reverseMappings.put(entry.getValue(), entry.getKey()); // mapped -> raw
            }

            List<Map<String, String>> rows = versionDataMap.get(version);
            LinkedHashSet<String> discoveredRawFields = new LinkedHashSet<>();
            for (Map<String, String> row : rows) {
                discoveredRawFields.addAll(row.keySet());
            }

            List<String> orderedMappedColumns = columnOrderMappedNames.getOrDefault(version, new ArrayList<>());
            LinkedHashSet<String> finalRawHeaderOrder = new LinkedHashSet<>();

// Step 1: Add fields from order.properties (if present in mapping or custom)
            for (String mapped : orderedMappedColumns) {
                if (reverseMappings.containsKey(mapped)) {
                    finalRawHeaderOrder.add(reverseMappings.get(mapped));
                } else if (customColumns.containsKey(version + "||" + mapped)) {
                    finalRawHeaderOrder.add(mapped); // custom columns use mapped name directly
                }
            }

// Step 2: Add remaining fields from actual XML data
            for (String raw : discoveredRawFields) {
                if (!finalRawHeaderOrder.contains(raw)) {
                    finalRawHeaderOrder.add(raw);
                }
            }

            FileWriter fw = new FileWriter(outputFolderPath + "/" + version + ".csv");
            BufferedWriter bw = new BufferedWriter(fw);

// Step 3: Write header row
            List<String> finalRawList = new ArrayList<>(finalRawHeaderOrder);
            for (int i = 0; i < finalRawList.size(); i++) {
                String raw = finalRawList.get(i);
                String mapped = mappings.getOrDefault(raw, raw);
                bw.write(mapped);
                if (i < finalRawList.size() - 1) {
                    bw.write(",");
                }
            }
            bw.newLine();

// Step 4: Write data rows
            for (Map<String, String> row : rows) {
                for (int i = 0; i < finalRawList.size(); i++) {
                    String val = row.getOrDefault(finalRawList.get(i), "");
                    bw.write("\"" + val.replace("\"", "\"\"") + "\"");
                    if (i < finalRawList.size() - 1) {
                        bw.write(",");
                    }
                }
                bw.newLine();
            }

            bw.close();
        }
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
        if (ns == null)
            return "UnknownFormat";

        String[] supported = { "pain.001.001.", "pain.001.002.", "pain.001.003.", "pain.008.002.", "pain.008.003.",
                "pain.008.001.", "pain.007.001", "pain.008.001.02", "pain8v8", "payext", "paymul" };

        for (int i = 0; i < supported.length; i++) {
            if (ns.contains(supported[i])) {
                int idx = ns.indexOf(supported[i]);
                return ns.substring(idx);
            }
        }

        return "UnknownFormat";
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
        if (!orderFile.exists())
            return columnOrder;

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

    private Map<String, String> loadMappingForVersion(String dirPath, String version) throws Exception {
        File f = new File(dirPath, version + ".properties");
        if (!f.exists())
            return new HashMap<>();
        return loadProperties(f);
    }

    private Map<String, String> loadCustomColumnsForVersion(String dirPath, String version) throws Exception {
        File f = new File(dirPath, version + ".properties");
        if (!f.exists())
            return new HashMap<>();
        return loadProperties(f);
    }

    private Map<String, String> loadProperties(File file) throws Exception {
        Properties props = new Properties();
        FileInputStream fis = new FileInputStream(file);
        props.load(fis);
        fis.close();
        Map<String, String> map = new HashMap<>();
        for (Object k : props.keySet()) {
            String key = (String) k;
            map.put(key, props.getProperty(key));
        }
        return map;
    }

    // Helper method to count number of transactions per batch
    private int countTransactions(NodeList txs) {
        return txs != null ? txs.getLength() : 0;
    }

    public void writeUnmappedTagsExcel(String outputFolderPath, Workbook unmappedWorkbook, Set<String> unmappedTags) throws Exception {
        // Map: version -> list of unmapped raw field names
        Map<String, List<String>> tagsByVersion = new HashMap<>();

        for (String fullTag : unmappedTags) {
            int sepIndex = fullTag.indexOf("||");
            if (sepIndex > 0) {
                String version = fullTag.substring(0, sepIndex);
                String tag = fullTag.substring(sepIndex + 2);
                tagsByVersion.computeIfAbsent(version, k -> new ArrayList<>()).add(tag);
            }
        }

        for (Map.Entry<String, List<String>> entry : tagsByVersion.entrySet()) {
            String version = entry.getKey();
            List<String> tags = entry.getValue();

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


    /*
     * Final Method :
     */

    public void ensureAllMappedColumnsPresent(
            Map<String, List<Map<String, String>>> versionDataMap,
            Map<String, LinkedHashSet<String>> versionHeadersMap,
            Map<String, Map<String, String>> columnMappings,
            Map<String, String> customColumns,
            Map<String, List<String>> columnOrderMappedNames) {

        for (String version : versionDataMap.keySet()) {
            List<Map<String, String>> rows = versionDataMap.get(version);
            Map<String, String> mappings = columnMappings.getOrDefault(version, new HashMap<>());

            // First, get all raw fields discovered from XML data for this version
            Set<String> discoveredRawFields = new LinkedHashSet<>();
            for (Map<String, String> row : rows) {
                discoveredRawFields.addAll(row.keySet());
            }

            // Get the desired order of raw paths from order.properties
            List<String> desiredRawOrder = columnOrderMappedNames.getOrDefault(version, new ArrayList<>());
            System.out.println("desiredRawOrder" + desiredRawOrder);
            
            System.out.println("discoveredRawFields" + discoveredRawFields);
            LinkedHashSet<String> finalRawHeaders = new LinkedHashSet<>();

            // Step 1: Add fields from order.properties in their specified order,
            // but only if they exist in discoveredRawFields or are custom columns.
            for (String rawPath : desiredRawOrder) {
                if (discoveredRawFields.contains(rawPath)) {
                    finalRawHeaders.add(rawPath);
                } else if (customColumns.containsKey(version + "||" + rawPath)) {
                    finalRawHeaders.add(rawPath);
                }
            }
            System.out.println("Final Raw Headers : " + finalRawHeaders);

            // Step 2: Add any remaining discovered raw fields that were not in order.properties, at the end.
            for (String rawField : discoveredRawFields) {
                if (!finalRawHeaders.contains(rawField)) {
                    finalRawHeaders.add(rawField);
                }
            }

            // Step 3: Fill missing fields in data rows with empty strings to ensure all rows have all headers.
            for (Map<String, String> row : rows) {
                for (String field : finalRawHeaders) {
                    row.putIfAbsent(field, "");
                }
            }

            // Step 4: Store the final ordered and complete set of raw headers for this version.
            versionHeadersMap.put(version, finalRawHeaders);
            System.out.println("Final Raw Headers for version " + version + ": " + finalRawHeaders);
        }
    }
}