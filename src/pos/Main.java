package pos;

import pos.ui.PrijavaFrame;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignorisano) {
        }
        SwingUtilities.invokeLater(() -> new PrijavaFrame().setVisible(true));
    }
}
