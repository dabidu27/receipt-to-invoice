package ro.receipt.parser;

public class CuiValidator {

    //official 9-digit positional weight key defined by Romanian fiscal regulations
    private static final int[] KEY = {7, 5, 3, 2, 1, 7, 5, 3, 2};

    //isValid method is static, objects of the class should not be instantiated => private constructor
    private CuiValidator(){

    }

    //ANAF checksum algorithm to verify that the cui is mathematically valid
    public static boolean isValid(String cui){

        if(cui == null || !cui.matches("\\d{2,10}"))
            return false;
        if (cui.chars().allMatch(c -> c == '0')) return false;

        //last digit
        int control = cui.charAt(cui.length() - 1) - '0';
        //everything except last digit
        String body = cui.substring(0, cui.length() - 1);
        //older cuis may have 6 or 7 digits, pad with 0s to get 9 digits for every cui
        StringBuilder paddedBuilder = new StringBuilder();
        for(int i = 0; i < 9 - body.length(); i++){
            paddedBuilder.append("0");
        }
        paddedBuilder.append(body);
        String padded = paddedBuilder.toString();

        //Check sum verification
        //multiply each digit with its weight
        int sum = 0;
        for(int i = 0; i < 9; i++){
            sum += (padded.charAt(i) - '0') * KEY[i];
        }

        //multiply the sum by 10 and take the remainder to 11
        int remainder = (sum * 10) % 11;
        //remainder = 10 => the control digit wraps to 0
        int expected = (remainder == 10) ? 0 : remainder;
        return expected == control;
    }
}
