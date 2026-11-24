package ro.receipt.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CuiExtractor {

    // Matches common OCR variations of CUI ("C.U.I.", "C.U.T.", "C,U,1") and CIF ("C.I.F.", "C, I.F,")
    private static final String CUI_TOKEN = "C[.,]?\\s*[UV][.,]?\\s*[IT1l][.,]?";
    private static final String CIF_TOKEN = "C[.,]?\\s*[IT1l][.,]?\\s*F[.,]?";

    // Optional CLIENT / BENEFICIAR / CUMPARATOR word, then a fiscal label,
    // optional ":" or "-", optional RO prefix (OCR may read O as 0), then 2-10 digits.
    // 3 capturing groups: 1 = client prefix (null if absent), 2 = label, 3 = digits
    private static final Pattern PATTERN = Pattern.compile(
            "(?i)\\b((?:CLIENT|BENEFICIAR|CUMPARATOR)\\s*:?\\s*)?("
                    + CUI_TOKEN + "\\s*/\\s*" + CIF_TOKEN       // CUI/CIF (and OCR typos like C.U.T./C, I.F,)
                    + "|" + CIF_TOKEN + "\\s*/\\s*" + CUI_TOKEN // CIF/CUI
                    + "|" + CIF_TOKEN                           // CIF
                    + "|" + CUI_TOKEN                           // CUI
                    + "|COD\\s+FISCAL"                          // COD FISCAL
                    + "|CF"                                     // CF
                    + ")\\s*[:\\-]?\\s*(?:R[O0])?\\s*(\\d{2,10})\\b");

    //Utility class, do not allow any instace of it (private constructor)
    private CuiExtractor(){

    }

    public static final class Candidate{

        public final String label; //label/text before the number - like "CIF"
        public final String cui; //digits only, no RO prefix
        public final int position; //position the text - we will have both seller and client cuis on the receipt, seller first then client, by comparing position we know which one is which
        public final boolean valid; //checksum digit verification
        public final boolean clientLabeled; // true if the word CLIENT came right before the label


        public Candidate(String label, String cui, int position, boolean valid, boolean clientLabeled) {
            this.label = label;
            this.cui = cui;
            this.position = position;
            this.valid = valid;
            this.clientLabeled = clientLabeled;
        }
    }

    //Extracts the CUIs (seller and client), in the order found, from the text extracted through OCR
    public static List<Candidate> extract(String text){
        List<Candidate> result = new ArrayList<>();
        if(text == null)
            return result;

        //regex Matcher that finds every pattern match to search for CUIs
        Matcher m = PATTERN.matcher(text);
        while(m.find()){ //the while advances the matcher through the input text to the next match
            //capturing group 2 = the digits of the cui
            String cui = m.group(3);
            //capuring group 1 = label and prefix
            String label = m.group(2).trim();
            String clientLabel = m.group(1);
            Candidate candidateCui = new Candidate(label, cui, m.start(), CuiValidator.isValid(cui), clientLabel != null);
            result.add(candidateCui);
        }

        return result;
    }
}
