package ExcelPropertiesUpdater;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook; // For .xlsx files
// import org.apache.poi.hssf.usermodel.HSSFWorkbook; // For .xls files

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ExcelProcessor {
	
	
	

	// You can set default values here or make them configurable in main
	private static final String DEFAULT_DELIMITER = "|";
	private static final String DEFAULT_GROUPING_COLUMN_NAME = "ScenarioID";
	private static final String DEFAULT_BASE_OUTPUT_DIRECTORY = "./grouped_excel_output/";

	/**
	 * Converts an Excel file into pipe-delimited text files. It creates a folder
	 * for each sheet, and within each sheet's folder, it creates subfolders for
	 * unique values in a specified grouping column (e.g., "DL key ID"). Each
	 * subfolder will contain a text file with rows corresponding to that unique
	 * grouping value.
	 *
	 * @param excelFilePath       The path to the input Excel file (.xlsx or .xls).
	 * @param baseOutputDirectory The root directory where all processed data
	 *                            folders will be created.
	 * @param groupingColumnName  The name of the column to group rows by (e.g., "DL
	 *                            key ID").
	 * @param delimiter           The delimiter to use for separating data in the
	 *                            text files (e.g., "|").
	 * @throws IOException              If there's an error reading the Excel file
	 *                                  or writing text files.
	 * @throws IllegalArgumentException If the grouping column is not found in a
	 *                                  sheet.
	 */
	public static void convertExcelToGroupedPipeDelimited(String excelFilePath, String baseOutputDirectory,
			String groupingColumnName, String delimiter) throws IOException, IllegalArgumentException {

		// Create the base output directory if it doesn't exist
		File baseOutputDir = new File(baseOutputDirectory);
		if (!baseOutputDir.exists()) {
			baseOutputDir.mkdirs();
			System.out.println("Created base output directory: " + baseOutputDir.getAbsolutePath());
		}

		try (FileInputStream excelFile = new FileInputStream(excelFilePath);
				Workbook workbook = new XSSFWorkbook(excelFile)) { // For .xls, use new HSSFWorkbook(excelFile)

			DataFormatter dataFormatter = new DataFormatter();
			FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();

			// Iterate through each sheet in the workbook
			for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
				Sheet sheet = workbook.getSheetAt(i);
				String sheetName = sheet.getSheetName();

				// Sanitize sheet name for file system
				String sanitizedSheetFolderName = sanitizeFileName(sheetName);
				String sheetOutputDirPath = baseOutputDirectory + sanitizedSheetFolderName + "/";
				File sheetOutputDir = new File(sheetOutputDirPath);
				if (!sheetOutputDir.exists()) {
					sheetOutputDir.mkdirs();
				}

				System.out.println("\n--- Processing Sheet: '" + sheetName + "' ---");

				// Get header row to find grouping column index
				Row headerRow = sheet.getRow(sheet.getFirstRowNum());
				if (headerRow == null) {
					System.out.println("  Skipping sheet '" + sheetName + "': No header row found.");
					continue;
				}

				int groupingColumnIndex = -1;
				for (Cell cell : headerRow) {
					if (dataFormatter.formatCellValue(cell).trim().equalsIgnoreCase(groupingColumnName)) {
						groupingColumnIndex = cell.getColumnIndex();
						break;
					}
				}

				if (groupingColumnIndex == -1) {
					System.err.println("  Warning: Column '" + groupingColumnName + "' not found in sheet '" + sheetName
							+ "'. " + "Writing entire sheet content to a single file in its sheet folder.");
					// Fallback: If grouping column is not found, write all data of the sheet to one
					// file
					writeEntireSheetToSingleFile(sheet, sheetOutputDir, headerRow, delimiter, dataFormatter,
							formulaEvaluator);
					continue;
				}

				// Map to store rows grouped by the specified column
				Map<String, List<Row>> groupedRows = new HashMap<>();

				// Iterate through data rows, skipping the header row
				Iterator<Row> rowIterator = sheet.iterator();
				if (rowIterator.hasNext()) { // Skip header row
					rowIterator.next();
				}

				while (rowIterator.hasNext()) {
					Row currentRow = rowIterator.next();
					Cell groupingCell = currentRow.getCell(groupingColumnIndex);

					String groupValue = "";
					if (groupingCell != null && groupingCell.getCellType() != CellType.BLANK) {
						groupValue = dataFormatter.formatCellValue(groupingCell, formulaEvaluator).trim();
					} else {
						// Handle rows with blank or missing grouping ID
						System.out.println("  Warning: Row " + (currentRow.getRowNum() + 1) + " in sheet '" + sheetName
								+ "' has a blank or missing '" + groupingColumnName
								+ "'. Grouping as 'UNDEFINED_GROUP_VALUE'.");
						groupValue = "UNDEFINED_GROUP_VALUE"; // Assign a default group or skip as needed
					}

					groupedRows.computeIfAbsent(groupValue, k -> new ArrayList<>()).add(currentRow);
				}

				// Write grouped data to separate files within their respective subfolders
				for (Map.Entry<String, List<Row>> entry : groupedRows.entrySet()) {
					String currentGroupValue = entry.getKey();
					List<Row> rowsInGroup = entry.getValue();

					// Sanitize group value for file system
					String sanitizedGroupFolderName = sanitizeFileName(currentGroupValue);
					String groupOutputDirPath = sheetOutputDirPath + sanitizedGroupFolderName + "/";
					File groupOutputDir = new File(groupOutputDirPath);
					if (!groupOutputDir.exists()) {
						groupOutputDir.mkdirs();
					}

					// Output file name
					String outputTxtFileName = sanitizedGroupFolderName + ".txt"; // Example: "DLKeyID123.txt"
					File outputTxtFile = new File(groupOutputDir, outputTxtFileName);

					System.out.println("    Writing group '" + currentGroupValue + "' (" + rowsInGroup.size()
							+ " rows) to: " + outputTxtFile.getAbsolutePath());

					try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputTxtFile))) {
						// Write header row to each group file
						writeRowToWriter(headerRow, writer, delimiter, dataFormatter, formulaEvaluator);

						// Write data rows for the current group
						for (Row row : rowsInGroup) {
							writeRowToWriter(row, writer, delimiter, dataFormatter, formulaEvaluator);
						}
					} catch (IOException e) {
						System.err.println(
								"      Error writing data for group '" + currentGroupValue + "': " + e.getMessage());
						// Don't re-throw, continue processing other groups/sheets
					}
				}
				System.out.println("--- Finished processing sheet: '" + sheetName + "' ---");
			}
			System.out.println("\nAll Excel data processed and grouped successfully!");

		} catch (IOException e) {
			throw new IOException("Failed to read Excel file or write output: " + e.getMessage(), e);
		} catch (Exception e) {
			throw new RuntimeException("An unexpected error occurred during processing: " + e.getMessage(), e);
		}
	}

	/**
	 * Helper method to write a single row to the BufferedWriter.
	 * 
	 * @param row              The Excel row to write.
	 * @param writer           The BufferedWriter to write to.
	 * @param delimiter        The delimiter to use between cell values.
	 * @param dataFormatter    DataFormatter instance for consistent cell value
	 *                         formatting.
	 * @param formulaEvaluator FormulaEvaluator instance for evaluating formula
	 *                         cells.
	 * @throws IOException If an I/O error occurs.
	 */
	private static void writeRowToWriter(Row row, BufferedWriter writer, String delimiter, DataFormatter dataFormatter,
			FormulaEvaluator formulaEvaluator) throws IOException {
		StringBuilder line = new StringBuilder();
		int lastColumn = row.getLastCellNum(); // This gets the "number of cells" or "index of last cell + 1"

		for (int k = 0; k < lastColumn; k++) { // Loop iterates from the first column (0) up to (lastColumn - 1)
			Cell cell = row.getCell(k);
			if (cell != null) {
				line.append(dataFormatter.formatCellValue(cell, formulaEvaluator));
			} else {
				line.append(""); // Append empty string for null cells
			}

// --- THIS IS THE KEY CONDITION ---
			if (k < lastColumn - 1) { // If it's NOT the last column, add the delimiter
				line.append(delimiter);
			}
		} 
		writer.write(line.toString());
		writer.newLine();
	}

	/**
	 * Helper method to sanitize string for use as a file or folder name. Replaces
	 * invalid characters with underscores.
	 * 
	 * @param name The original name.
	 * @return The sanitized name.
	 */
	private static String sanitizeFileName(String name) {
		// Replace characters not allowed in file paths on most OS (e.g., /, \, ?, %, *,
		// :, |, ", <, >)
		// Keep alphanumeric, hyphen, dot, and underscore.
		String sanitized = name.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
		// Also remove leading/trailing spaces or dots that might cause issues on some
		// systems
		sanitized = sanitized.replaceAll("^[._\\s]+|[._\\s]+$", "");
		// If after sanitization, the name is empty, provide a default
		if (sanitized.isEmpty()) {
			sanitized = "empty_name";
		}
		return sanitized;
	}

	/**
	 * Fallback method to write an entire sheet to a single file if the grouping
	 * column is not found.
	 * 
	 * @param sheet            The sheet to write.
	 * @param sheetFolder      The folder where the file should be created.
	 * @param headerRow        The header row.
	 * @param delimiter        The delimiter to use.
	 * @param dataFormatter    The DataFormatter instance.
	 * @param formulaEvaluator The FormulaEvaluator instance.
	 */
	private static void writeEntireSheetToSingleFile(Sheet sheet, File sheetFolder, Row headerRow, String delimiter,
			DataFormatter dataFormatter, FormulaEvaluator formulaEvaluator) {
		String outputFileName = sanitizeFileName(sheet.getSheetName()) + "_full_sheet.txt";
		File outputFile = new File(sheetFolder, outputFileName);
		System.out
				.println("    Writing entire sheet '" + sheet.getSheetName() + "' to: " + outputFile.getAbsolutePath());

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
			// Write header
			writeRowToWriter(headerRow, writer, delimiter, dataFormatter, formulaEvaluator);

			// Write all data rows
			for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
				Row currentRow = sheet.getRow(rowNum);
				if (currentRow == null) {
					continue; // Skip physically empty rows
				}
				writeRowToWriter(currentRow, writer, delimiter, dataFormatter, formulaEvaluator);
			}
			System.out.println(
					"    Successfully wrote full sheet '" + sheet.getSheetName() + "' to " + outputFile.getName());
		} catch (IOException e) {
			System.err
					.println("    Error writing full sheet '" + sheet.getSheetName() + "' to file: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Example main method to demonstrate usage
	public static void main(String[] args) {
		// --- Configuration for the generic function ---
		String excelFilePath = "F:\\Barclays BFG Project\\FileGateway\\Txt File Output\\AllFormats.xlsx"; // !!!
																											// IMPORTANT:
																											// Update
																											// this path
																											// !!!
		String baseOutputDirectory = "./output_data_by_dl_key/"; // Output will be here
		String groupingColumn = "ScenarioID"; // The column name to group by
		String customDelimiter = "|"; // The delimiter for text files

		// For demonstration purposes, you might want to uncomment and use a dummy file
		// excelFilePath = "C:/Users/YourUser/Desktop/SampleData.xlsx"; // Example
		// absolute path

		try {
			convertExcelToGroupedPipeDelimited(excelFilePath, baseOutputDirectory, groupingColumn, customDelimiter);
			System.out.println("\nProcess completed successfully!");
		} catch (IOException e) {
			System.err.println("File I/O error during conversion: " + e.getMessage());
		} catch (IllegalArgumentException e) {
			System.err.println("Configuration error: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("An unexpected error occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}
}