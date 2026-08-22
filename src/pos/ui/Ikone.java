package pos.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

// male ikonice nacrtane u kodu, da ne vucemo png fajlove za svako dugme
public final class Ikone {

    private static final Map<String, ImageIcon> kes = new HashMap<>();

    private static final Color SIVA = new Color(90, 100, 110);
    private static final Color ZELENA = new Color(56, 130, 60);
    private static final Color CRVENA = new Color(178, 60, 50);
    private static final Color ZUTA = new Color(226, 168, 43);
    private static final Color PLAVA = new Color(46, 78, 126);

    private Ikone() { }

    public static ImageIcon ikona(String tip, int v) {
        String k = tip + "@" + v;
        ImageIcon gotova = kes.get(k);
        if (gotova != null) {
            return gotova;
        }
        BufferedImage img = new BufferedImage(v, v, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(Math.max(1.4f, v / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        nacrtaj(g, tip, v);
        g.dispose();
        gotova = new ImageIcon(img);
        kes.put(k, gotova);
        return gotova;
    }

    private static void nacrtaj(Graphics2D g, String tip, int v) {
        int c = v / 2;
        int m = Math.max(1, v / 8);
        switch (tip) {
        case "plus":
            g.setColor(ZELENA);
            g.drawLine(c, m, c, v - m);
            g.drawLine(m, c, v - m, c);
            break;
        case "olovka":
            g.setColor(SIVA);
            g.drawLine(m, v - m, v - 3 * m, 3 * m);
            g.drawLine(v - 3 * m, 3 * m, v - 2 * m, 2 * m);
            g.drawLine(m, v - m, m + m / 2, v - 2 * m);
            break;
        case "kanta":
            g.setColor(CRVENA);
            g.drawRect(2 * m, 2 * m + m / 2, v - 4 * m, v - 3 * m - m / 2);
            g.drawLine(m, 2 * m + m / 2, v - m, 2 * m + m / 2);
            g.drawLine(c - m, 2 * m + m / 2, c - m / 2, m);
            g.drawLine(c + m, 2 * m + m / 2, c + m / 2, m);
            break;
        case "stampac":
            g.setColor(SIVA);
            g.drawRect(m, c - m, v - 2 * m, 2 * m);
            g.drawRect(2 * m, m, v - 4 * m, c - 2 * m);
            g.drawRect(2 * m, c + m, v - 4 * m, v - c - 2 * m);
            break;
        case "povrat":
            g.setColor(PLAVA);
            g.drawArc(m, m, v - 2 * m, v - 2 * m, 30, 270);
            g.drawLine(c + m / 2, m, c + 2 * m, m / 2);
            g.drawLine(c + m / 2, m, c + 2 * m, 2 * m);
            break;
        case "storno":
            g.setColor(CRVENA);
            g.drawOval(m, m, v - 2 * m, v - 2 * m);
            g.drawLine(2 * m, v - 2 * m, v - 2 * m, 2 * m);
            break;
        case "osoba":
            g.setColor(SIVA);
            g.drawOval(c - 3 * m / 2, m, 3 * m, 3 * m);
            g.drawArc(2 * m, c, v - 4 * m, v - c, 0, 180);
            break;
        case "kutija":
            g.setColor(SIVA);
            g.drawRect(m, 2 * m, v - 2 * m, v - 3 * m);
            g.drawLine(m, 2 * m, c, m);
            g.drawLine(v - m, 2 * m, c, m);
            g.drawLine(c, 2 * m + m / 2, c, v - m);
            break;
        case "upozorenje": {
            GeneralPath t = new GeneralPath();
            t.moveTo(c, m);
            t.lineTo(v - m, v - m);
            t.lineTo(m, v - m);
            t.closePath();
            g.setColor(new Color(252, 240, 199));
            g.fill(t);
            g.setColor(ZUTA);
            g.draw(t);
            g.setColor(SIVA.darker());
            g.drawLine(c, 3 * m, c, v - 3 * m);
            break;
        }
        case "katanac":
            g.setColor(PLAVA);
            g.fillRoundRect(2 * m, c - m / 2, v - 4 * m, v - c - m / 2, m, m);
            g.drawArc(c - 3 * m / 2, m, 3 * m, 3 * m, 0, 180);
            g.setColor(Color.WHITE);
            g.fillOval(c - m / 2, c + m / 2, m, m);
            break;
        case "odjava":
            g.setColor(SIVA);
            g.drawRect(m, m, c - m, v - 2 * m);
            g.drawLine(c, c, v - m, c);
            g.drawLine(v - 2 * m, c - m, v - m, c);
            g.drawLine(v - 2 * m, c + m, v - m, c);
            break;
        case "otpis":
            g.setColor(CRVENA);
            g.drawRect(m, 2 * m, v - 2 * m, v - 3 * m);
            g.drawLine(m, 2 * m, c, m);
            g.drawLine(v - m, 2 * m, c, m);
            g.drawLine(2 * m, c, v - 2 * m, c + 2 * m);
            g.drawLine(2 * m, c + 2 * m, v - 2 * m, c);
            break;
        default:
            g.setColor(SIVA);
            g.drawOval(m, m, v - 2 * m, v - 2 * m);
        }
    }
}
