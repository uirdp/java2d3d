import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public class VertViewer extends JPanel {

    private final List<List<Point2D.Double>> original = new ArrayList<>();
    private final List<List<Point2D.Double>> current  = new ArrayList<>();

    private boolean showTangent = false;
    private boolean showNormal  = false;

    // Curve shortening flow
    private boolean flowRunning = false;
    private double dt = 0.0001;
    private final Timer timer;

    // ---- Fixed view transform (computed once from ORIGINAL) ----
    private boolean viewInitialized = false;
    private int lastW = -1, lastH = -1;

    private double fixedScale;
    private double dataCenterX, dataCenterY; // center of ORIGINAL bbox
    private double dataSpanX;                // width of ORIGINAL bbox (for vecLen)
    private double dataSpanY;

    public VertViewer(List<VertFileLoader.CurveComponent> components) {
        for (var comp : components) {
            List<Point2D.Double> o = new ArrayList<>();
            for (var p : comp.vertices) o.add(new Point2D.Double(p.x, p.y));
            original.add(o);

            List<Point2D.Double> c = new ArrayList<>();
            for (var p : comp.vertices) c.add(new Point2D.Double(p.x, p.y));
            current.add(c);
        }

        setFocusable(true);

        timer = new Timer(16, e -> {
            if (flowRunning) {
                stepCurveShorteningFlow();
                repaint();
            }
        });
        timer.start();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_T -> { showTangent = !showTangent; repaint(); }
                    case KeyEvent.VK_N -> { showNormal  = !showNormal;  repaint(); }

                    case KeyEvent.VK_F -> flowRunning = !flowRunning;
                    case KeyEvent.VK_S -> { stepCurveShorteningFlow(); repaint(); }
                    case KeyEvent.VK_R -> { resetToOriginal(); repaint(); }

                    case KeyEvent.VK_EQUALS, KeyEvent.VK_PLUS -> dt *= 1.2;
                    case KeyEvent.VK_MINUS -> dt /= 1.2;
                }
            }
        });
    }

    private void resetToOriginal() {
        current.clear();
        for (var o : original) {
            List<Point2D.Double> c = new ArrayList<>();
            for (var p : o) c.add(new Point2D.Double(p.x, p.y));
            current.add(c);
        }
    }

    // Compute fixed view transform ONCE using ORIGINAL curve size
    private void ensureFixedViewTransform(int w, int h) {
        // if you want to recompute on window resize, keep this condition;
        // if you want truly "only once", remove the resize part.
        if (viewInitialized && w == lastW && h == lastH) return;

        lastW = w;
        lastH = h;

        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;

        for (var comp : original) {
            for (var p : comp) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
            }
        }

        dataSpanX = maxX - minX;
        dataSpanY = maxY - minY;
        if (dataSpanX == 0 || dataSpanY == 0) {
            fixedScale = 1.0;
            dataCenterX = (minX + maxX) * 0.5;
            dataCenterY = (minY + maxY) * 0.5;
            viewInitialized = true;
            return;
        }

        dataCenterX = (minX + maxX) * 0.5;
        dataCenterY = (minY + maxY) * 0.5;

        fixedScale = 0.8 * Math.min(w / dataSpanX, h / dataSpanY);

        viewInitialized = true;
    }

    private void stepCurveShorteningFlow() {
        for (int ci = 0; ci < current.size(); ci++) {
            List<Point2D.Double> v = current.get(ci);
            int n = v.size();
            if (n < 3) continue;

            List<Point2D.Double> next = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                Point2D.Double pm = v.get((i - 1 + n) % n);
                Point2D.Double p  = v.get(i);
                Point2D.Double pp = v.get((i + 1) % n);

                // w = |P_{i+1} - P_{i-1}|
                double wx = pp.x - pm.x;
                double wy = pp.y - pm.y;
                double w = Math.hypot(wx, wy);

                if (w == 0) {
                    next.add(new Point2D.Double(p.x, p.y));
                    continue;
                }

                // unit tangent (central)
                double tx = wx / w;
                double ty = wy / w;

                // unit normal (left normal)
                double nx = -ty;
                double ny =  tx;

                // theta between vectors (pm->p) and (p->pp)
                double ax = p.x - pm.x, ay = p.y - pm.y;
                double bx = pp.x - p.x, by = pp.y - p.y;
                double la = Math.hypot(ax, ay);
                double lb = Math.hypot(bx, by);

                if (la == 0 || lb == 0) {
                    next.add(new Point2D.Double(p.x, p.y));
                    continue;
                }

                double dot = (ax * bx + ay * by) / (la * lb);
                dot = Math.max(-1.0, Math.min(1.0, dot));
                double theta = Math.acos(dot);

                // ---- SIGN FIX (as requested) ----
                // With inward normals and update: P^{t+1} = P^t - dt*K*N,
                // disk.vert should have NEGATIVE curvature.
                double cross = ax * by - ay * bx;
                if (cross > 0) theta = -theta;  // <-- changed from (cross < 0)

                // curvature (kd-style)
                double kappa = (2.0 * Math.sin(theta)) / w;

                // Update: P_{i,t+1} = P_{i,t} - dt * K_i * N_i
                double newX = p.x - dt * kappa * nx;
                double newY = p.y - dt * kappa * ny;

                next.add(new Point2D.Double(newX, newY));
            }

            current.set(ci, next);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // ---- FIX: compute scale/center ONCE from ORIGINAL ----
        ensureFixedViewTransform(w, h);

        // Apply fixed transform (do NOT change per iteration)
        g2.translate(w / 2.0, h / 2.0);
        g2.scale(fixedScale, -fixedScale);
        g2.translate(-dataCenterX, -dataCenterY);

        // draw curve
        g2.setStroke(new BasicStroke((float)(1.5 / fixedScale)));
        g2.setColor(Color.RED);

        for (var comp : current) {
            if (comp.size() < 2) continue;

            Path2D path = new Path2D.Double();
            path.moveTo(comp.get(0).x, comp.get(0).y);
            for (int i = 1; i < comp.size(); i++) {
                path.lineTo(comp.get(i).x, comp.get(i).y);
            }
            path.closePath();
            g2.draw(path);
        }

        // tangent/normal viz (length based on ORIGINAL span, not shrinking span)
        double vecLen = 0.05 * dataSpanX;

        for (var comp : current) {
            int n = comp.size();
            if (n < 3) continue;

            for (int i = 0; i < n; i += 5) {
                Point2D.Double pm = comp.get((i - 1 + n) % n);
                Point2D.Double p  = comp.get(i);
                Point2D.Double pp = comp.get((i + 1) % n);

                double tx = pp.x - pm.x;
                double ty = pp.y - pm.y;
                double tl = Math.hypot(tx, ty);
                if (tl == 0) continue;
                tx /= tl; ty /= tl;

                double nx = -ty;
                double ny =  tx;

                if (showTangent) {
                    g2.setColor(Color.BLUE);
                    g2.draw(new Line2D.Double(
                            p.x, p.y,
                            p.x + tx * vecLen,
                            p.y + ty * vecLen
                    ));
                }

                if (showNormal) {
                    g2.setColor(Color.GREEN.darker());
                    g2.draw(new Line2D.Double(
                            p.x, p.y,
                            p.x + nx * vecLen,
                            p.y + ny * vecLen
                    ));
                }
            }
        }

        // HUD (screen space)
        AffineTransform saved = g2.getTransform();
        g2.setTransform(new AffineTransform());
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));
        g2.drawString("F: run/pause   S: step   R: reset   +/-: dt  (dt=" + String.format("%.5f", dt) + ")", 10, 20);
        g2.drawString("T: tangent   N: normal", 10, 38);
        g2.setTransform(saved);
    }

    public static void main(String[] args) {
        try {
            var components = VertFileLoader.LoadFromVertFile(args[0]);

            JFrame frame = new JFrame("Curve Shortening Flow");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);

            VertViewer viewer = new VertViewer(components);
            frame.add(viewer);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            viewer.requestFocusInWindow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
