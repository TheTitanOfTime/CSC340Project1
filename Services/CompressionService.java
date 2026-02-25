package CSC340Project1.Services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class CompressionService {
    private static final int BUFFER_SIZE = 8192; // default size for buffering 8 kb

    public byte[] compress(byte[] input){
        if(input == null){
            throw new IllegalArgumentException("There has to be a input");
        }

        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);  //the default size is -1
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream output = new ByteArrayOutputStream(); //what will be returned
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            while(!deflater.finished()){
                int count = deflater.deflate(buffer); //count is equal to how many bytes is returned from each call
                output.write(buffer,0,count); //write the count amount of bytes 
            }
            return output.toByteArray(); //returns a new byte array
        } finally{
            deflater.end();
        }
    }

    public byte[] decompress(byte[] compressedInput) throws IOException{
        if(compressedInput == null){
            throw new IllegalArgumentException("There has to be a input");
        }

        Inflater inflater = new Inflater();
        inflater.setInput(compressedInput);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];

        try {
            while(!inflater.finished()){
                int count = inflater.inflate(buffer);

                if (count == 0){
                    if(inflater.needsInput()){
                        throw new IOException("Incomplete compressed payload");
                    }

                    if(inflater.needsDictionary()){
                        throw new IOException("Need a preset dictionary");
                    }
                } else{
                    output.write(buffer,0,count);
                }
            }
            return output.toByteArray();
        } catch (DataFormatException e) {
            throw new IOException("Invalid compressed format",e);
        }finally{
            inflater.end();
        }
    }
}
