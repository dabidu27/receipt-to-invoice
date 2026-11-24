package ro.receipt.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    // Amount as printed: "600,27" or "104, 18" (this printer puts a space after the comma)
    private static final String AMOUNT = "\\d+[.,]\\s?\\d{2}";

    // VAT row: handles both "A-21,00% 104,18 600,27" (2 amounts) AND "A-21, 00% 600, 27" (1 amount when VAT shifted to SUMA TAXE)
    private static final Pattern VAT_ROW = Pattern.compile(
            "\\b([A-Z])\\s*-\\s*(\\d{1,2}(?:[.,]\\s?\\d{1,2})?)\\s*%\\s+(?:("
                    + AMOUNT + ")\\s+)?(" + AMOUNT + ")");

    // Total VAT line: handles OCR typos like "SUMA TAXE:", "SUMA TAYC:", "SUMA TAXA:"
    private static final Pattern VAT_TOTAL = Pattern.compile(
            "(?i)SUMA\\s+TA[A-Z]{2}\\s*:?\\s*(" + AMOUNT + ")");

    // Date printed year first: 2026.11.04
    private static final Pattern DATE_YMD = Pattern.compile("\\b(\\d{4})\\.\\s?(\\d{2})\\.\\s?(\\d{2})\\b");
    // Date printed day first: 04.11.2026
    private static final Pattern DATE_DMY = Pattern.compile("\\b(\\d{2})\\.\\s?(\\d{2})\\.\\s?(\\d{4})\\b");

    //Utility class => No instance
    private ReceiptParser(){

    }

    public static Map<String, String> parse(String text, String ownCui){ //own cui = firm's own cui, the same on every invoice, no need to extract from the invoice each time
        Map<String, String> out = new LinkedHashMap<>(); //LinkedHashMap keeps the order of the fields
        String own = ownCui == null ? null : ownCui.replaceAll("\\D", ""); //take only digits

        //Get candidate cuis
        List<CuiExtractor.Candidate> cuiCandidates = CuiExtractor.extract(text);
        //Pick the 1 that is the client cui from them
        CuiExtractor.Candidate clientCui = pickClient(cuiCandidates, own);
        out.put("clientCui", clientCui == null ? null : clientCui.cui);


        //Extract Total and VAT from the VAT rows
        BigDecimal total = BigDecimal.ZERO;                    // sum of the gross column
        BigDecimal rowVat = BigDecimal.ZERO;                   // sum of the VAT column
        boolean hasRows = false;                               // true once a row matched
        Matcher rows = VAT_ROW.matcher(text);
        while (rows.find()) {
            if (rows.group(3) != null) {
                rowVat = rowVat.add(toDecimal(rows.group(3))); // VAT value of this rate
            }
            total = total.add(toDecimal(rows.group(4)));       // gross sum of this rate
            hasRows = true;
        }
        out.put("total", hasRows ? format(total) : null);

        //VAT: prefer the printed "SUMA TAXE" line, else the sum of the rows
        Matcher vat = VAT_TOTAL.matcher(text);
        if (vat.find()) {
            out.put("vat", format(toDecimal(vat.group(1))));
        } else {
            out.put("vat", hasRows ? format(rowVat) : null);
        }

        // 3. Date as yyyy-MM-dd
        out.put("date", findDate(text));
        return out;
    }

    //Scoring system to choose the cui that is the client cui
    //If it has a "CLIENT" label => +100 points - strongest signal this is the client CUI
    //If it is a valid cui => +10 points
    //If it is equal to the seller cui => skip
    //Pick the best candidate
    private static CuiExtractor.Candidate pickClient(List<CuiExtractor.Candidate> allCuis, String ownCui){

        CuiExtractor.Candidate bestCandidate = null;
        int bestScore = Integer.MIN_VALUE;
        for(CuiExtractor.Candidate c: allCuis){

            int score = 0;
            if(c.cui.equals(ownCui))
                continue;
            if(c.clientLabeled)
                score += 100;
            if(c.valid)
                score += 10;
            if(score > bestScore){
                bestScore = score;
                bestCandidate = c;
            }
        }
        return bestCandidate;
    }

    private static String findDate(String text) {
        Matcher ymd = DATE_YMD.matcher(text);
        if (ymd.find()) {
            return ymd.group(1) + "-" + ymd.group(2) + "-" + ymd.group(3);
        }
        Matcher dmy = DATE_DMY.matcher(text);
        if (dmy.find()) {
            return dmy.group(3) + "-" + dmy.group(2) + "-" + dmy.group(1);
        }
        return null;                                           // no date found
    }

    // "104, 18" -> 104.18
    private static BigDecimal toDecimal(String s) {
        return new BigDecimal(s.replace(" ", "").replace(',', '.'));
    }

    // 600.27 with exactly 2 decimals, as a plain string
    private static String format(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
