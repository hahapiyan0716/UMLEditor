package shape;

import java.awt.Graphics;
import java.awt.Rectangle;

/* =========================================
 * ClassFactory - 具體建立者 (Concrete Creator)
 * =========================================
 * 跟 RectFactory / OvalFactory 是同一層的兄弟：覆寫工廠方法，只負責 new 出「UML 類別」的 BasicObject。
 *
 * 這正是 Factory Method 模式的好處 (開放封閉原則 Open-Closed Principle)：
 *   新增一種圖形，只要「新增」一個具體工廠類別，
 *   CreateObjectMode、ShapeFactory.orderShape() 這些既有的程式碼一行都不用改。
 */
public class ClassFactory extends ShapeFactory {

    @Override
    protected BasicObject createShape(int x, int y) {
        return new BasicObject(x, y, ObjectType.CLASS);
    }

    // 預覽框也畫成「分三格的矩形」，讓使用者拖曳時就看得出畫的是 Class，而不是一般的 Rect
    @Override
    public void drawPreview(Graphics g, Rectangle box) {
        g.drawRect(box.x, box.y, box.width, box.height);
        int firstLineY = box.y + box.height / 3;
        int secondLineY = box.y + box.height * 2 / 3;
        g.drawLine(box.x, firstLineY, box.x + box.width, firstLineY);
        g.drawLine(box.x, secondLineY, box.x + box.width, secondLineY);
    }
}
