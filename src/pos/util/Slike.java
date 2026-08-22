package pos.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class Slike {

    private static final Map<String, Image> kes = new HashMap<>();

    private Slike() { }

    // trazi images/<sifra>.png pa .jpg, ako nema nista crta sivu kutiju
    public static synchronized Image artikal(String sifra, String naziv, int vel) {
        String k = sifra + "@" + vel;
        Image img = kes.get(k);
        if (img == null) {
            img = izFajla(sifra, vel);
            if (img == null) {
                img = kutija(vel);
            }
            kes.put(k, img);
        }
        return img;
    }

    private static Image izFajla(String sifra, int vel) {
        File f = new File("images", sifra + ".png");
        if (!f.exists()) {
            f = new File("images", sifra + ".jpg");
        }
        if (!f.exists()) {
            return null;
        }
        try {
            BufferedImage org = ImageIO.read(f);
            if (org == null) {
                return null;
            }
            BufferedImage rez = new BufferedImage(vel, vel, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = rez.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(org, 0, 0, vel, vel, null);
            g.dispose();
            return rez;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage kutija(int vel) {
        BufferedImage img = new BufferedImage(vel, vel, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int m = Math.max(2, vel / 8);
        int luk = vel / 6;
        g.setColor(new Color(238, 240, 242));
        g.fillRoundRect(0, 0, vel - 1, vel - 1, luk, luk);
        g.setColor(new Color(160, 168, 176));
        g.setStroke(new BasicStroke(Math.max(1.5f, vel / 24f),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawRoundRect(0, 0, vel - 1, vel - 1, luk, luk);
        int l = 2 * m, d = vel - 2 * m;
        int vrh = 2 * m + m / 2, dno = vel - 2 * m;
        g.drawRect(l, vrh, d - l, dno - vrh);
        g.drawLine(l, vrh + (dno - vrh) / 4, d, vrh + (dno - vrh) / 4);
        g.drawLine(vel / 2, vrh, vel / 2, dno);
        g.dispose();
        return img;
    }

    // FET monogram za pdf racun
    public static BufferedImage logo(int vel) {
        BufferedImage img = new BufferedImage(vel, vel, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int luk = vel / 4;
        g.setColor(new Color(46, 78, 126));
        g.fillRoundRect(0, 0, vel - 1, vel - 1, luk, luk);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (vel * 0.34)));
        FontMetrics fm = g.getFontMetrics();
        int x = (vel - fm.stringWidth("FET")) / 2;
        int y = (vel - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString("FET", x, y);
        g.dispose();
        return img;
    }
}
