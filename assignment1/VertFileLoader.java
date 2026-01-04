import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class VertFileLoader {
    public static class CurveComponent {
        public final List<Point2D.Double> vertices = new ArrayList<>();
    }

    public static List<CurveComponent> LoadFromVertFile(String filename) throws IOException {
        List<CurveComponent> components = new ArrayList<>();

        try(Scanner sc = new Scanner(new File(filename))){
            int numComponents = sc.nextInt();

            for(int i = 0; i < numComponents; i++){
                CurveComponent comp = new CurveComponent();
                int numVertices = sc.nextInt();

                for(int j = 0; j < numVertices; j++){
                    double x = sc.nextDouble();
                    double y = sc.nextDouble();
                    comp.vertices.add(new Point2D.Double(x, y));
                }
                components.add(comp);
            }
        }
        return components;
    }
}
