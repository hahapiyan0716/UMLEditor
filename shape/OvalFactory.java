package shape;

import java.awt.Graphics;
import java.awt.Rectangle;

/* =========================================
 * OvalFactory - 具體建立者 (Concrete Creator)
 * =========================================
 * 對應披薩範例中的 ChicagoPizzaStore。
 * 覆寫工廠方法，只負責 new 出「橢圓」的 BasicObject。
 */
public class OvalFactory extends ShapeFactory {

    @Override
    protected BasicObject createShape(int x, int y) {
        return new BasicObject(x, y, ObjectType.OVAL);
    }

    @Override
    public void drawPreview(Graphics g, Rectangle box) {
        g.drawOval(box.x, box.y, box.width, box.height);
    }
}
