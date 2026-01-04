import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;

public class SimpleCurve extends JPanel {
    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setColor(Color.lightGray);
        g2.drawLine(0, h / 2, w, h / 2); // x軸

         g2.drawLine(w / 2, 0, w / 2, h); // y軸

        // =========================
        // 曲線 y = sin(x)
        // =========================
        g2.setColor(Color.BLUE);
        g2.setStroke(new BasicStroke(2.0f));

        Path2D path = new Path2D.Double();

        double scaleX = 60.0;  // x方向の拡大
        double scaleY = 60.0;  // y方向の拡大

        boolean first = true;

        for (double x = -Math.PI * 2; x <= Math.PI * 2; x += 0.01) {
            double y = Math.sin(x);

            // 数学座標 → 画面座標
            double sx = w / 2 + x * scaleX;
            double sy = h / 2 - y * scaleY;

            if (first) {
                path.moveTo(sx, sy);
                first = false;
            } else {
                path.lineTo(sx, sy);
            }
        }

        g2.draw(path);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Simple Java2D Curve");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.add(new SimpleCurve());
        frame.setLocationRelativeTo(null); // 画面中央
        frame.setVisible(true);
    }
}
