package MASSPAY_PAINRFT;


import org.w3c.dom.*;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class MT101Generator {

    public void generateMT101File(String groupKey, List<Element> pmtInfList, String version) throws IOException {
        String filename = "MT101_" + groupKey.replaceAll("[^a-zA-Z0-9]", "_") + ".txt";
        try (FileWriter writer = new FileWriter(filename)) {
            writer.write(generateSwiftHeader(groupKey));
            writer.write(generateMT101Body(pmtInfList, version));
            System.out.println("Written MT101 file: " + filename);
        }
    }

    private String generateSwiftHeader(String bic) {
        String timestamp = new SimpleDateFormat("yyMMddHHmm").format(new Date());
        return "{1:F01" + bic + "XXXX0000000000}{2:I101" + bic + "XXXXN}{3:{108:MT101}}\n";
    }

    private String generateMT101Body(List<Element> pmtInfList, String version) {
        StringBuilder sb = new StringBuilder();
        int txIndex = 1;

        for (Element pmtInf : pmtInfList) {
            NodeList txList = pmtInf.getElementsByTagNameNS("*", "CdtTrfTxInf");

            for (int i = 0; i < txList.getLength(); i++) {
                Element tx = (Element) txList.item(i);

                String instrId = getElementText(tx, "InstrId");
                String endToEndId = getElementText(tx, "EndToEndId");
                String amount = getElementText(tx, "InstdAmt");
                String currency = getAttribute(tx, "InstdAmt", "Ccy");

                String creditorName = getElementText(tx, "Nm", "Cdtr");
                String creditorIban = getElementText(tx, "IBAN", "CdtrAcct");
                String remittanceInfo = getElementText(tx, "Ustrd", "RmtInf");

                // SWIFT MT101 Structure
                sb.append(":20:REF").append(txIndex++).append("\n"); // Transaction Reference
                sb.append(":28D:1/1\n"); // Page/Sequence
                sb.append(":50H:/").append(getElementText(pmtInf, "IBAN", "DbtrAcct")).append("\n");
                sb.append(getElementText(pmtInf, "Nm", "Dbtr")).append("\n"); // Ordering Customer
                sb.append(":52A:").append(getElementText(pmtInf, "BIC", "DbtrAgt")).append("\n"); // Ordering Institution
                sb.append(":57A:").append(getElementText(tx, "BIC", "CdtrAgt")).append("\n"); // Account With Institution
                sb.append(":59:/").append(creditorIban).append("\n");
                sb.append(creditorName).append("\n"); // Beneficiary
                sb.append(":70:").append(remittanceInfo != null ? remittanceInfo : "PAYMENT").append("\n"); // Remittance Info
                sb.append(":71A:SHA\n"); // Details of Charges (SHA = Shared)
                sb.append(":32B:").append(currency).append(amount).append("\n\n"); // Currency and Amount
            }
        }

        sb.append("-}\n");
        return sb.toString();
    }

    private String getElementText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent().trim();
        }
        return "";
    }

    private String getElementText(Element parent, String tagName, String contextTag) {
        NodeList contextList = parent.getElementsByTagNameNS("*", contextTag);
        if (contextList.getLength() > 0) {
            Element contextElem = (Element) contextList.item(0);
            return getElementText(contextElem, tagName);
        }
        return "";
    }

    private String getAttribute(Element parent, String tagName, String attrName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) {
            Node node = list.item(0);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                return element.getAttribute(attrName);
            }
        }
        return "";
    }
}

