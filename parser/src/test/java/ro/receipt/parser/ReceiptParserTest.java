package ro.receipt.parser;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReceiptParserTest {

    private static String loadReceipt(String filePath){
        Path path = Path.of("..", "testdata", "receipts", filePath);

        StringBuilder content = new StringBuilder();
        try(FileReader fr = new FileReader(path.toFile());
            BufferedReader br = new BufferedReader(fr)) {

            String line;
            while((line = br.readLine()) != null){
                content.append(line).append("\n");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return content.toString();
    }

    @Test
    void realCuiPassCheckDigit(){
        assertTrue(CuiValidator.isValid("11997009"));
        assertTrue(CuiValidator.isValid("34265371"));
    }

    @Test
    void parseRealReceipt(){
        String receiptText = loadReceipt("002.txt");
        Map<String, String> r = ReceiptParser.parse(receiptText, "RO11997009");
        assertEquals("34265371", r.get("clientCui"));
        assertEquals("600.27", r.get("total"));              // gross from the VAT row
        assertEquals("104.18", r.get("vat"));                // handles "104, 18"
        assertEquals("2026-11-04", r.get("date"));
    }

    //Test that the CLIENT label alone is enough for a candidate CUI to win and be the chosen client CUI
    @Test
    void clientWinsEvenWithoutKnowingOwnCui() {
        String receiptText = loadReceipt("002.txt");
        Map<String, String> r = ReceiptParser.parse(receiptText, null);
        assertEquals("34265371", r.get("clientCui"));
    }

}
