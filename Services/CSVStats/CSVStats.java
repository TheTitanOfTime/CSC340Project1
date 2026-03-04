package Services.CSVStats;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CSVStats {

    public static ArrayList<ArrayList<String>> find_stats(ArrayList<ArrayList<String>> data) {
        double column_mean;
        double column_median;
        double column_mode;
        double standard_dev;

        int mode_count;
        int min;
        int max;


        //go by column, not by row for calculations
        //start at row one since that is the label section
        for(int row = 1; row < data.get(0).size(); row++) {
            for(int column = 0; column < data.size(); column ++) {
                
            }
        }
        return null;
    }


    public static ArrayList<ArrayList<String>> UTF_to_CSV_list(byte[] file_bytes){
        String base64 = new String(file_bytes, StandardCharsets.US_ASCII);
        byte[] decoded_bytes = Base64.getDecoder().decode(base64);
        String csv_text = new String(decoded_bytes, StandardCharsets.UTF_8);

        ArrayList<ArrayList<String>> data = new ArrayList<>();

        //line reader code from https://codingtechroom.com/question/read-string-line-by-line-java
        try {
            BufferedReader reader = new BufferedReader(new StringReader(csv_text));
                String line;
                while ((line = reader.readLine()) != null) {
                    data.add(parse_line(line));
                }
            } catch (IOException e){
                System.out.println("Failed to parse csv String" + e);
            }
        return data;
    }
    





    private static ArrayList<String> parse_line(String line){
        ArrayList<String> result = new ArrayList<>();
        boolean in_quotes = false;
        String value = "";

        for(int i = 0; i < line.length(); i++ ){
            char c = line.charAt(i);

            if(c == '"') {
                in_quotes = !in_quotes;
            } else if (c == ',' && !in_quotes){
                result.add(value);
                value = "";
            } else {
                value += c;
            }
        }
        result.add(value);

        return result;   
    }



    private static Double mean(ArrayList<Double> column){
        double total = 0.0;
        for(int i = 0; i < column.size(); i++){
            total += column.get(i);
        }
        return (total / column.size());
    }

    private static Double median(ArrayList<Double> column){
        //I fuckin love ternary operators!!!!
        return (column.size() % 2 == 0) ? column.get(column.size()/2) : column.get((column.size() / 2) + 1);
    }

    private static Double mode(ArrayList<Double> column){
        Map<Double, Integer> counts = new HashMap<>();
        for (Double v : column) {
            if (v == null) continue;
                counts.put(v, counts.getOrDefault(v, 0) + 1);
            }

            if (counts.isEmpty()) return null;

            Double best_val = null;
            int best_count = -1;
        for (Map.Entry<Double, Integer> e : counts.entrySet()) {
            int c = e.getValue();
            if (c > best_count) {
                best_count = c;
                best_val = e.getKey();
            }
        }
        return best_val;
    }

    private static Double standard_dev(ArrayList<Double> column){
        if(column.size() < 2) return 0.0;
        double numerator = 0;
        double mean = mean(column);

        for(int i = 0; i < column.size(); i ++){
            numerator += (column.get(i) - mean) * (column.get(i) - mean);
        }

        double variance = numerator / column.size();
        double std = (double)Math.sqrt(variance);
        return (double) std;
    }

    private static Double min(ArrayList<Double> column){
        Double current_min = column.get(0);
        for(int i = 1; i < column.size(); i++){
            if(current_min > column.get(i)) current_min = column.get(i);
        }
        return current_min;
    }

    private static Double max(ArrayList<Double> column){
        Double current_max = column.get(0);
        for(int i = 1; i < column.size(); i++){
            if(current_max < column.get(i)) current_max = column.get(i);
        }
        return current_max;
    }


    //TESTING ONLY
    public static byte[] test_byte_generator(String csv_file){
        byte[] utf8 = csv_file.getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encode(utf8); // returns ASCII bytes of base64 text
    }

    private static void printArray(ArrayList<ArrayList<String>> list){
        for(int i = 0; i < list.size(); i++){
            for(int j = 0; j < list.get(i).size(); j++){
                System.out.print(list.get(i).get(j) + " ");
            }
            System.out.println("");
        }
    }


    private static void print1dArray(ArrayList<String> list){
        for(int i = 0; i < list.size(); i++){
            System.out.print(list.get(i) + " ");
        }
        System.out.println("");
    }

    
}
