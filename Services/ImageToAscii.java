package CSC340Project1.Services;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;

public class ImageToAscii {
//#%=+-*. 42.5
    private final int VERTICAL_SCALAR = 48;
    private final int HORIZONTAL_SCALAR = 176;

    public BufferedImage grayscale_conversion(BufferedImage image){
        for(int j = 0; j < image.getWidth(); j++){
            for(int k = 0; k < image.getHeight(); k++){
                int rgb = image.getRGB(j, k);
                Color c = new Color(rgb, false);
                int gray = (int)( 0.299 * c.getRed()
                                + 0.587 * c.getGreen()
                                + 0.114 * c.getBlue());

                c = new Color(gray, gray, gray);
                image.setRGB(j, k, c.getRGB());
            }

        }
        return image;
    }
      
    public BufferedImage resize(BufferedImage image){
        double vertical_scale = image.getHeight() / VERTICAL_SCALAR;
        double horizontal_scale = image.getWidth() / HORIZONTAL_SCALAR;

        BufferedImage resizedImage = new BufferedImage((int)(image.getWidth()/horizontal_scale),
                                                        (int)(image.getHeight()/vertical_scale),
                                                        BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(image, 0, 0, resizedImage.getWidth(), resizedImage.getHeight(), null);

        return resizedImage;
    }

    public String convert_to_ascii(BufferedImage image){
        String output = "";
        for(int j = 0; j < image.getHeight(); j ++){
            for(int i = 0; i < image.getWidth(); i++){
                Color c = new Color(image.getRGB(i, j));
                int shade = c.getBlue();

                if(shade < 52){
                    output += ".";
                } else if(shade < 70){
                    output += "*";
                } else if(shade < 90){
                    output += "+";
                } else if(shade < 110){
                    output += "=";
                } else if(shade < 190){
                    output += "%";
                } else if(shade <= 255){
                    output += "#";
                }
                
            }
            output += "\n";
        }
        System.out.println(output);
        return output;
    }

}
