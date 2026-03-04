package Services.Compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class CompressionService {
    private static final int BUFFER_SIZE = 8192; // default size for buffering 8 kb

    public byte[] compress(byte[] input) throws IOException {
        if(input == null){
            throw new IllegalArgumentException("There has to be a input");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("data"));
            zip.write(input);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    public byte[] decompress(byte[] compressedInput) throws IOException {
        if(compressedInput == null){
            throw new IllegalArgumentException("There has to be a input");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(compressedInput))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) {
                throw new IOException("No entries found in ZIP data");
            }
            int count;
            while ((count = zip.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }
}
