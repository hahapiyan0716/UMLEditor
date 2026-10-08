package shape;

import java.awt.Graphics;
import java.awt.Rectangle;

/* =========================================
 * RectFactory - 具體建立者 (Concrete Creator)
 * =========================================
 * 對應披薩範例中的 NYPizzaStore。
 * 覆寫工廠方法，只負責 new 出「矩形」的 BasicObject。
 * 所有的 new (耦合點) 都被集中、推到了這個角落。
 */
public class RectFactory extends ShapeFactory {

    @Override
    protected BasicObject createShape(int x, int y) {
        return new BasicObject(x, y, ObjectType.RECT);
    }

    @Override
    public void drawPreview(Graphics g, Rectangle box) {
        g.drawRect(box.x, box.y, box.width, box.height);
    }
}
