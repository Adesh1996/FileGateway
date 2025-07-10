package XMLToExcelGeminiCode;


import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter; // Import FilenameFilter
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A Java application to merge multiple large Excel (.xlsx) files into a single Excel file.
 * It uses Apache POI's SAX parser for efficient reading of large files and
 * SXSSFWorkbook for efficient writing, minimizing memory consumption.
 * The header row is copied only from the first processed Excel file.
 */
public class ExcelMerger {

    /**
     * Main method to demonstrate the usage of the ExcelMerger.
     * Please replace the 'inputDirectory' and 'outputFilePath' with your actual paths.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // --- CONFIGURATION ---
        // IMPORTANT: Replace these paths with your actual input directory and desired output file path.
        String inputDirectory = "C:\\Users\\YourUser\\Documents\\InputExcels"; // e.g., "C:\\ExcelFiles"
        String outputFilePath = "C:\\Users\\YourUser\\Documents\\MergedExcel.xlsx"; // e.g., "C:\\MergedOutput.xlsx"
        // ---------------------

        try {
            System.out.println("Starting Excel merge process...");
            mergeExcelFiles(inputDirectory, outputFilePath);
            System.out.println("Excel files merged successfully to: " + outputFilePath);
        } catch (Exception e) {
            System.err.println("An error occurred during merging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Merges all .xlsx files found in the specified input directory into a single output Excel file.
     * The header row is taken only from the first file.
     *
     * @param inputDirectoryPath The path to the directory containing the Excel files to merge.
     * @param outputFilePath     The path where the merged Excel file will be saved.
     * @throws Exception If any error occurs during file processing or I/O.
     */
    public static void mergeExcelFiles(String inputDirectoryPath, String outputFilePath) throws Exception {
        File inputDir = new File(inputDirectoryPath);

        // Validate input directory
        if (!inputDir.exists() || !inputDir.isDirectory()) {
            throw new IllegalArgumentException("Input directory does not exist or is not a directory: " + inputDirectoryPath);
        }

        // Get all .xlsx files from the directory
        // Replaced lambda with anonymous inner class for Java 7 compatibility or preference
        File[] excelFiles = inputDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xlsx");
            }
        });

        if (excelFiles == null || excelFiles.length == 0) {
            System.out.println("No .xlsx files found in the specified directory. Exiting merge process.");
            return;
        }

        // Create a new streaming workbook for the output file.
        // SXSSFWorkbook keeps a configurable number of rows in memory (e.g., 100)
        // and flushes the rest to disk, which is crucial for large files.
        SXSSFWorkbook outputWorkbook = new SXSSFWorkbook(100);
        SXSSFSheet outputSheet = outputWorkbook.createSheet("Merged Data");

        int outputRowNum = 0; // Tracks the current row number in the output sheet
        boolean firstFile = true; // Flag to ensure header is copied only from the first file

        // Iterate through each Excel file found in the input directory
        for (File inputFile : excelFiles) {
            System.out.println("Processing file: " + inputFile.getName());

            // Use try-with-resources to ensure the OPC package is closed properly
            try (OPCPackage opcPackage = OPCPackage.open(inputFile.getAbsolutePath())) {
                XSSFReader xssfReader = new XSSFReader(opcPackage);
                // SharedStringsTable stores all unique strings in the workbook, optimizing memory.
                SharedStringsTable sst = xssfReader.getSharedStringsTable();
                // StylesTable stores cell styling information.
                StylesTable styles = xssfReader.getStylesTable();

                // Get an iterator for the sheet data streams.
                // We assume data is in the first sheet of each workbook.
                Iterator<InputStream> sheetsData = xssfReader.getSheetsData();
                if (sheetsData.hasNext()) {
                    InputStream sheetInputStream = sheetsData.next();

                    // Create an XMLReader to parse the sheet's XML content
                    XMLReader sheetParser = SAXParserFactory.newInstance().newSAXParser().getXMLReader();

                    // Our custom handler that processes sheet contents row by row
                    ExcelSheetHandler handler = new ExcelSheetHandler(sst, styles, outputSheet, outputRowNum, firstFile);

                    // XSSFSheetXMLHandler bridges the SAX parser events to our SheetContentsHandler
                    sheetParser.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false));

                    // Parse the input stream of the current sheet
                    sheetParser.parse(new InputSource(sheetInputStream));

                    // Update the output row number based on rows added by the handler
                    outputRowNum = handler.getCurrentRowNum();
                    // After the first file, set this flag to false so subsequent files skip their headers
                    firstFile = false;
                }
            } catch (Exception e) {
                // Log the error but continue processing other files if one fails
                System.err.println("Error processing file " + inputFile.getName() + ": " + e.getMessage());
                e.printStackTrace(); // Print stack trace for debugging
            }
        }

        // Write the merged data from the SXSSFWorkbook to the final output file
        try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
            outputWorkbook.write(fos);
        } finally {
            // IMPORTANT: Dispose of the SXSSFWorkbook to ensure all temporary files
            // are written to the final output and then cleaned up from disk.
            outputWorkbook.dispose();
        }
    }

    /**
     * Custom SAX handler for processing the contents of an Excel sheet.
     * It implements SheetContentsHandler to receive callbacks for rows and cells.
     */
    private static class ExcelSheetHandler implements SheetContentsHandler {
        private SharedStringsTable sst;
        private StylesTable styles;
        private SXSSFSheet outputSheet;
        private int currentRowNum; // Current row index in the output sheet
        private boolean isFirstFile; // True if this is the first input file being processed
        private boolean isHeaderRow = true; // Internal flag to identify the header row of the current input file
        private SXSSFRow currentOutputRow; // The row currently being built in the output sheet
        private int currentCellIdx; // Current cell index within the current output row

        /**
         * Constructor for ExcelSheetHandler.
         *
         * @param sst         The SharedStringsTable from the input workbook.
         * @param styles      The StylesTable from the input workbook.
         * @param outputSheet The SXSSFSheet to write data to.
         * @param startRowNum The starting row number in the output sheet for this file's data.
         * @param isFirstFile True if this is the first input file, false otherwise.
         */
        public ExcelSheetHandler(SharedStringsTable sst, StylesTable styles, SXSSFSheet outputSheet, int startRowNum, boolean isFirstFile) {
            this.sst = sst;
            this.styles = styles;
            this.outputSheet = outputSheet;
            this.currentRowNum = startRowNum;
            this.isFirstFile = isFirstFile;
        }

        /**
         * Called when a new row starts in the input Excel sheet.
         *
         * @param rowNum The 0-based index of the row in the input sheet.
         */
        @Override
        public void startRow(int rowNum) {
            // If it's not the first file and the current row is the first row (header, rowNum == 0),
            // mark it as a header row to be skipped.
            if (!isFirstFile && rowNum == 0) {
                isHeaderRow = true;
                return; // Do not create a row in the output sheet for this header
            }
            // Otherwise, it's a data row or the header of the first file, so process it.
            isHeaderRow = false;
            currentOutputRow = outputSheet.createRow(currentRowNum++); // Create a new row in the output sheet
            currentCellIdx = 0; // Reset cell index for the new row
        }

        /**
         * Called when a row ends in the input Excel sheet.
         *
         * @param rowNum The 0-based index of the row in the input sheet.
         */
        @Override
        public void endRow(int rowNum) {
            // No specific action needed at the end of a row for this merging logic.
        }

        /**
         * Called for each cell in the input Excel sheet.
         *
         * @param cellReference The A1-style reference for the cell (e.g., "A1", "B5").
         * @param formattedValue The formatted string value of the cell. XSSFSheetXMLHandler
         * handles resolving shared strings and applying basic formatting.
         * @param comments       Any comments associated with the cell (not used in this merger).
         */
        @Override
        public void cell(String cellReference, String formattedValue, XSSFSheetXMLHandler.XSSFCellComments comments) {
            // If the current row is identified as a header row to be skipped, do nothing.
            if (isHeaderRow) {
                return;
            }

            // Ensure currentOutputRow is not null. This is a safeguard; it should be created in startRow.
            if (currentOutputRow == null) {
                System.err.println("Warning: currentOutputRow is null for cell " + cellReference + ". Skipping cell.");
                return;
            }

            // Create a new cell in the current output row
            SXSSFCell cell = currentOutputRow.createCell(currentCellIdx++);
            // Set the cell value using the formatted string value provided by POI's handler.
            // This approach treats all cell data as strings for simplicity in merging.
            // If preserving original cell types (numeric, boolean, date) is critical,
            // more complex logic to parse raw values and types from SAX events would be needed.
            cell.setCellValue(formattedValue);
        }

        /**
         * Called for header/footer text (not relevant for data merging).
         *
         * @param text     The header/footer text.
         * @param isHeader True if it's a header, false if it's a footer.
         * @param tagName  The XML tag name for the header/footer.
         */
        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not relevant for data merging, so no implementation needed.
        }

        /**
         * Returns the current row number in the output sheet.
         *
         * @return The current row number.
         */
        public int getCurrentRowNum() {
            return currentRowNum;
        }
    }
}
