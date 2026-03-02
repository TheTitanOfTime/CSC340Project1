package Node;

import Services.Base64Service;

public class Base64Thread implements Runnable {

    private final String input;

    public Base64Thread(String userInput) {
        this.input = userInput;
    }

    // what the thread will actually execute
    @Override
    public void run() {
        
        String output = Base64Service.encodeToString(input);
        
        System.out.printf("Encoding %s to Base64. Output is %s", input, output);

    }
}
