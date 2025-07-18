package NFT;



import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PainFileGenerator {

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("Enter template file path: ");
        String templatePath = reader.readLine();

        System.out.print("Enter total number of transactions: ");
        int totalTransactions = Integer.parseInt(reader.readLine());

        System.out.print("Enter number of batches per file: ");
        int batchesPerFile = Integer.parseInt(reader.readLine());

        System.out.print("Enter total number of files: ");
        int totalFiles = Integer.parseInt(reader.readLine());

        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        for (int fileIndex = 1; fileIndex <= totalFiles; fileIndex++) {
            final int fileNum = fileIndex;
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        generateFile(templatePath, totalTransactions, batchesPerFile, fileNum);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(500);
        }

        long endTime = System.currentTimeMillis();
        System.out.println("\nAll files generated in " + (endTime - startTime) / 1000.0 + " seconds.");
    }

    private static void generateFile(String templatePath, int totalTransactions, int batchesPerFile, int fileIndex) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(templatePath));

        Element documentEl = (Element) doc.getElementsByTagNameNS("*", "Document").item(0);
        Element cstmrEl = (Element) documentEl.getElementsByTagNameNS("*", "CstmrCdtTrfInitn").item(0);

        NodeList pmtInfs = cstmrEl.getElementsByTagNameNS("*", "PmtInf");
        Element templatePmtInf = (Element) pmtInfs.item(0);
        Element templateTx = (Element) templatePmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf").item(0);

        while (pmtInfs.getLength() > 0) {
            templatePmtInf = (Element) cstmrEl.removeChild(pmtInfs.item(0));
        }

        int transactionsRemaining = totalTransactions;
        double ctrlSum = 0.0;
        int totalTx = 0;

        for (int batchIndex = 1; batchIndex <= batchesPerFile; batchIndex++) {
            Element newBatch = (Element) templatePmtInf.cloneNode(true);
            NodeList txList = newBatch.getElementsByTagNameNS("*", "CdtTrfTxInf");
            while (txList.getLength() > 0) newBatch.removeChild(txList.item(0));

            int transactionsInBatch = transactionsRemaining / (batchesPerFile - batchIndex + 1);
            for (int tx = 1; tx <= transactionsInBatch; tx++) {
                Element txClone = (Element) templateTx.cloneNode(true);
                setTagValue(txClone, "InstrId", "MSG-F" + fileIndex + "-B" + batchIndex + "-T" + tx);
                double amt = Double.parseDouble(getTagValue(txClone, "InstdAmt"));
                ctrlSum += amt;
                newBatch.appendChild(txClone);
                totalTx++;
            }

            setTagValue(newBatch, "PmtInfId", "MSG-F" + fileIndex + "-B" + batchIndex);
            setTagValue(newBatch, "CtrlSum", String.format("%.2f", ctrlSum));
            setTagValue(newBatch, "NbOfTxs", String.valueOf(transactionsInBatch));

            cstmrEl.appendChild(newBatch);
            transactionsRemaining -= transactionsInBatch;
        }

        Element grpHdr = (Element) cstmrEl.getElementsByTagNameNS("*", "GrpHdr").item(0);
        setTagValue(grpHdr, "MsgId", generateMsgId(fileIndex));
        setTagValue(grpHdr, "CreDtTm", getCurrentDateTime());
        setTagValue(grpHdr, "CtrlSum", String.format("%.2f", ctrlSum));
        setTagValue(grpHdr, "NbOfTxs", String.valueOf(totalTx));

        String fileVersion = detectVersion(templatePath);
        String outFile = "pain_output_F" + fileIndex + "_" + fileVersion + ".xml";
        writeToFile(doc, outFile);
        System.out.println("Generated: " + outFile);
    }

    private static void setTagValue(Element parent, String tagName, String value) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) list.item(0).setTextContent(value);
    }

    private static String getTagValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) return list.item(0).getTextContent();
        return "0.0";
    }

    private static String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
    }

    private static String generateMsgId(int fileIndex) {
        return "MSG-F" + fileIndex + "-" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    private static void writeToFile(Document doc, String filePath) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(doc), new StreamResult(new File(filePath)));
    }

    private static String detectVersion(String path) {
        if (path.contains("pain.001.001.03")) return "pain1v3";
        if (path.contains("pain.002.001.03")) return "pain2v3";
        if (path.contains("pain.008.001.02")) return "pain8v2";
        if (path.contains("pain.007.001.02")) return "pain7v2";
        return "unknown";
    }
}
