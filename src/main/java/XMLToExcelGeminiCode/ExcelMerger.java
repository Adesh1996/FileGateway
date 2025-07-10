

package XMLToExcelGeminiCode;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.Iterator;

public class ExcelMerger {

    public static void main(String[] args) {
        String inputDirectory = "C:\\Users\\YourUser\\Documents\\InputExcels";
        String outputFilePath = "C:\\Users\\YourUser\\Documents\\MergedExcel.xlsx";

        try {
            System.out.println("Starting Excel merge process...");
            mergeExcelFiles(inputDirectory, outputFilePath);
            System.out.println("Excel files merged successfully to: " + outputFilePath);
        } catch (Exception e) {
            System.err.println("An error occurred during merging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void mergeExcelFiles(String inputDirectoryPath, String outputFilePath) throws Exception {
        File inputDir = new File(inputDirectoryPath);

        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input directory does not exist or is not a directory: " + inputDirectoryPath);
        }

        File[] excelFiles = inputDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xlsx");
            }
        });

        if (excelFiles == null || excelFiles.length == 0) {
            System.out.println("No .xlsx files found in the specified directory. Exiting merge process.");
            return;
        }

        SXSSFWorkbook outputWorkbook = new SXSSFWorkbook(100);
        SXSSFSheet outputSheet = outputWorkbook.createSheet("Merged Data");

        int outputRowNum = 0;
        boolean firstFile = true;

        for (File inputFile : excelFiles) {
            System.out.println("Processing file: " + inputFile.getName());

            try (OPCPackage opcPackage = OPCPackage.open(inputFile.getAbsolutePath())) {
                XSSFReader xssfReader = new XSSFReader(opcPackage);
                ReadOnlySharedStringsTable sst = new ReadOnlySharedStringsTable(opcPackage);
                StylesTable styles = xssfReader.getStylesTable();

                Iterator<InputStream> sheetsData = xssfReader.getSheetsData();
                if (sheetsData.hasNext()) {
                    InputStream sheetInputStream = sheetsData.next();

                    XMLReader sheetParser = SAXParserFactory.newInstance().newSAXParser().getXMLReader();

                    ExcelSheetHandler handler = new ExcelSheetHandler(sst, styles, outputSheet, outputRowNum, firstFile);
                    sheetParser.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false));
                    sheetParser.parse(new InputSource(sheetInputStream));

                    outputRowNum = handler.getCurrentRowNum();
                    firstFile = false;
                }
            } catch (Exception e) {
                System.err.println("Error processing file " + inputFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            outputWorkbook.write(fos);
        } finally {
            outputWorkbook.dispose();
        }
    }

    private static class ExcelSheetHandler implements SheetContentsHandler {
        private final ReadOnlySharedStringsTable sst;
        private final StylesTable styles;
        private final SXSSFSheet outputSheet;
        private int currentRowNum;
        private final boolean isFirstFile;
        private boolean isHeaderRow = true;
        private SXSSFRow currentOutputRow;
        private int currentCellIdx;

        public ExcelSheetHandler(ReadOnlySharedStringsTable sst, StylesTable styles,
                                 SXSSFSheet outputSheet, int startRowNum, boolean isFirstFile) {
            this.sst = sst;
            this.styles = styles;
            this.outputSheet = outputSheet;
            this.currentRowNum = startRowNum;
            this.isFirstFile = isFirstFile;
        }

        @Override
        public void startRow(int rowNum) {
            if (!isFirstFile && rowNum == 0) {
                isHeaderRow = true;
                return;
            }
            isHeaderRow = false;
            currentOutputRow = outputSheet.createRow(currentRowNum++);
            currentCellIdx = 0;
        }

        @Override
        public void endRow(int rowNum) {
            // No action needed
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (isHeaderRow) {
                return;
            }
            if (currentOutputRow == null) {
                System.err.println("Warning: currentOutputRow is null for cell " + cellReference);
                return;
            }
            SXSSFCell cell = currentOutputRow.createCell(currentCellIdx++);
            cell.setCellValue(formattedValue);
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // No action needed
        }

        public int getCurrentRowNum() {
            return currentRowNum;
        }
    }
}
