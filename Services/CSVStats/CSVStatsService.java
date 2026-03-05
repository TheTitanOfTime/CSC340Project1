package Services.CSVStats;

import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class CSVStatsService {

    public static byte[] get_csv_stats(byte[] data){
        ArrayList<ArrayList<String>> input_file = UTF_to_CSV_list(data);
        ArrayList<ArrayList<String>> output_file = find_stats(input_file);
        String output_string = create_csv_string(output_file);
        byte[] file_bytes = output_string.getBytes(StandardCharsets.UTF_8);
        return file_bytes;
    }


    private static ArrayList<ArrayList<String>> find_stats(ArrayList<ArrayList<String>> data) {
        ArrayList<ArrayList<String>> results = new ArrayList<>();

        // Iterate over each column
        for (int column = 0; column < data.size(); column++) {
            String title = data.get(column).get(0); // Header is at row 0

            // Try to parse all non-header rows as doubles
            ArrayList<Double> col = new ArrayList<>();
            boolean is_numeric = true;

            for (int row = 1; row < data.get(column).size(); row++) {
                String cell = data.get(column).get(row).trim();
                try {
                    col.add(Double.parseDouble(cell));
                } catch (NumberFormatException e) {
                    is_numeric = false;
                    break;
                }
            }

            if (!is_numeric || col.isEmpty()) continue;

            // Sort a copy for median (doesn't affect mode/other calcs)
            ArrayList<Double> sorted = new ArrayList<>(col);
            Collections.sort(sorted);

            // Build the result column: [label, mean, median, mode, std, min, max]
            ArrayList<String> stat_column = new ArrayList<>();
            stat_column.add(title);
            stat_column.add(String.valueOf(mean(col)));
            stat_column.add(String.valueOf(median(sorted)));  // pass sorted copy
            stat_column.add(String.valueOf(mode(col)));
            stat_column.add(String.valueOf(standard_dev(col)));
            stat_column.add(String.valueOf(min(col)));
            stat_column.add(String.valueOf(max(col)));

            results.add(stat_column);
        }

        return results;
    }


    public static ArrayList<ArrayList<String>> UTF_to_CSV_list(byte[] file_bytes){
        byte[] decoded_bytes = Base64.getDecoder().decode(file_bytes);
        String csv_text = new String(decoded_bytes, StandardCharsets.UTF_8);
    
        ArrayList<ArrayList<String>> data = new ArrayList<>();
    
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
    

    private static String create_csv_string(ArrayList<ArrayList<String>> csv_list){
        String output = "";

        for(int i = 0; i < csv_list.size(); i ++){
            for(int j = 0; j < csv_list.get(i).size(); j++){
                output += csv_list.get(i).get(j);
                output += ",";
            }
            output += "\n";
        }

        return output;
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

    private static Double median(ArrayList<Double> column) {
        int n = column.size();
        if (n % 2 == 0) {
            return (column.get(n / 2 - 1) + column.get(n / 2)) / 2.0;
        } else {
            return column.get(n / 2);
        }
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


    /*
    -----------------TESTING ONLY-------------------
    The following functions are set up for testing this in a controlled environment
    */
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
