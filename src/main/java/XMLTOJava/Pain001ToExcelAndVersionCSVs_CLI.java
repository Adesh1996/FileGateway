package XMLTOJava;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.*;

public class Pain001ToExcelAndVersionCSVs_CLI {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java Pain001ToExcelAndVersionCSVs <input-folder> <output-folder> <column-mapping.properties>");
            return;
        }

        File inputFolder = new File(args[0]);
        File outputFolder = new File(args[1]);
        String mappingFilePath = args[2];

        if (!outputFolder.exists()) {
            outputFolder.mkdirs();
        }

        Map<String, String> columnMappings = loadColumnMappings(mappingFilePath);
        Set<String> unmappedTags = new TreeSet<String>();

        File[] xmlFiles = inputFolder.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xml");
            }
        });

        if (xmlFiles == null || xmlFiles.length == 0) {
            System.out.println("No XML files found.");
            return;
        }

        Workbook workbook = new XSSFWorkbook();
        Workbook unmappedWorkbook = new XSSFWorkbook();

        Map<String, List<Map<String, String>>> versionDataMap = new LinkedHashMap<String, List<Map<String, String>>>();
        Map<String, LinkedHashSet<String>> versionHeadersMap = new LinkedHashMap<String, LinkedHashSet<String>>();

        for (int fileIndex = 0; fileIndex < xmlFiles.length; fileIndex++) {
            File xmlFile = xmlFiles[fileIndex];
            System.out.println("Processing: " + xmlFile.getName());

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(xmlFile);
            doc.getDocumentElement().normalize();

            String namespace = doc.getDocumentElement().getNamespaceURI();
            String version = extractVersionFromNamespace(namespace);

            Element root = doc.getDocumentElement();
            Element initn = getChildByLocalName(root, "CstmrCdtTrfInitn");
            if (initn == null) continue;

            NodeList pmtInfs = initn.getElementsByTagNameNS("*", "PmtInf");
            for (int i = 0; i < pmtInfs.getLength(); i++) {
                Element pmtInf = (Element) pmtInfs.item(i);
                Map<String, String> base = new LinkedHashMap<String, String>();

                Element grpHdr = getChildByLocalName(initn, "GrpHdr");
                collectTextElements(base, "GrpHdr", grpHdr);
                collectTextElements(base, "PmtInf", pmtInf);

                NodeList txs = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");
                for (int j = 0; j < txs.getLength(); j++) {
                    Element tx = (Element) txs.item(j);
                    Map<String, String> row = new LinkedHashMap<String, String>(base);
                    collectTextElements(row, "CdtTrfTxInf", tx);
                    row.put("SourceFile", xmlFile.getName());

                    if (!versionDataMap.containsKey(version)) {
                        versionDataMap.put(version, new ArrayList<Map<String, String>>());
                        versionHeadersMap.put(version, new LinkedHashSet<String>());
                    }
                    versionDataMap.get(version).add(row);
                    versionHeadersMap.get(version).addAll(row.keySet());

                    for (Iterator<String> it = row.keySet().iterator(); it.hasNext(); ) {
                        String key = it.next();
                        if (!columnMappings.containsKey(key)) {
                            unmappedTags.add(key);
                        }
                    }
                }
            }
        }

        // Write Excel sheets per version
        for (Iterator<String> it = versionDataMap.keySet().iterator(); it.hasNext(); ) {
            String version = it.next();
            List<Map<String, String>> rows = versionDataMap.get(version);
            List<String> headers = new ArrayList<String>(versionHeadersMap.get(version));

            Sheet sheet = workbook.createSheet(version.length() > 31 ? version.substring(0, 31) : version);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                String mapped = columnMappings.containsKey(headers.get(i)) ? columnMappings.get(headers.get(i)) : headers.get(i);
                headerRow.createCell(i).setCellValue(mapped);
            }

            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, String> rowData = rows.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    String val = rowData.containsKey(headers.get(j)) ? rowData.get(headers.get(j)) : "";
                    row.createCell(j).setCellValue(val);
                }
            }
        }

        FileOutputStream excelOut = new FileOutputStream(new File(outputFolder, "AllPain001_Formats.xlsx"));
        workbook.write(excelOut);
        workbook.close();
        excelOut.close();

        // Write CSVs per version
        for (Iterator<String> it = versionDataMap.keySet().iterator(); it.hasNext(); ) {
            String version = it.next();
            List<Map<String, String>> rows = versionDataMap.get(version);
            List<String> headers = new ArrayList<String>(versionHeadersMap.get(version));

            FileWriter fw = new FileWriter(new File(outputFolder, version + ".csv"));
            BufferedWriter bw = new BufferedWriter(fw);

            for (int i = 0; i < headers.size(); i++) {
                String mapped = columnMappings.containsKey(headers.get(i)) ? columnMappings.get(headers.get(i)) : headers.get(i);
                bw.write(mapped);
                if (i < headers.size() - 1) bw.write(",");
            }
            bw.newLine();

            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);
                for (int j = 0; j < headers.size(); j++) {
                    String val = row.containsKey(headers.get(j)) ? row.get(headers.get(j)) : "";
                    bw.write("\"" + val.replace("\"", "\"\"") + "\"");
                    if (j < headers.size() - 1) bw.write(",");
                }
                bw.newLine();
            }

            bw.close();
        }

        // Write unmapped tags to Excel file
        Sheet unmappedSheet = unmappedWorkbook.createSheet("UnmappedTags");
        int rowIdx = 0;
        for (Iterator<String> it = unmappedTags.iterator(); it.hasNext(); ) {
            Row row = unmappedSheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(it.next());
        }
        FileOutputStream unmappedOut = new FileOutputStream(new File(outputFolder, "unmapped-tags.xlsx"));
        unmappedWorkbook.write(unmappedOut);
        unmappedWorkbook.close();
        unmappedOut.close();

        System.out.println("\u2705 All Excel, CSV, and unmapped tag files created in '" + outputFolder.getPath() + "' folder.");
    }

    private static Map<String, String> loadColumnMappings(String filePath) throws Exception {
        Properties props = new Properties();
        File mappingFile = new File(filePath);
        Map<String, String> mapping = new HashMap<String, String>();
        if (!mappingFile.exists()) {
            System.out.println("❗ Mapping file not found: " + filePath);
            return mapping;
        }

        FileInputStream fis = new FileInputStream(mappingFile);
        props.load(fis);
        fis.close();

        for (Iterator<Object> it = props.keySet().iterator(); it.hasNext(); ) {
            String key = (String) it.next();
            mapping.put(key, props.getProperty(key));
        }
        return mapping;
    }

    private static String extractVersionFromNamespace(String ns) {
        if (ns == null) return "UnknownFormat";
        if (ns.contains("pain.001.001.")) {
            int idx = ns.indexOf("pain.001.001.");
            return ns.substring(idx);
        }
        return "UnknownFormat";
    }

    private static void collectTextElements(Map<String, String> data, String path, Element element) {
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

    private static Element getChildByLocalName(Element parent, String name) {
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
}
