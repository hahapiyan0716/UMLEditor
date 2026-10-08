package shape;

import java.awt.Graphics;
import java.awt.Rectangle;

/* =========================================
 * ShapeFactory - 工廠方法模式 (Factory Method Pattern)
 * =========================================
 * 抽象建立者 (Abstract Creator)，對應披薩範例中的 PizzaStore。
 *
 * 設計精神：
 *   - 核心流程 orderShape() 固定不變 (對應 orderPizza)，
 *     它只認得抽象的 BasicObject，完全不知道會拿到 Rect 還是 Oval。
 *   - 將實體化 (new) 的責任「延遲」交給子類別 (RectFactory / OvalFactory) 決定，
 *     所有的 new 關鍵字都被推到具體建立者中。
 */
public abstract class ShapeFactory {

    // ==========================================
    // 核心流程 (對應 orderPizza)：建立圖形 → 設定邊界
    // 這段流程是固定的，不因 Rect 或 Oval 而改變
    // ==========================================
    public BasicObject orderShape(int x, int y, int width, int height) {
        // 呼叫工廠方法來建立圖形，此時完全不知道會拿到哪種具體圖形
        // 這邊有多型的魔法：createShape() 的實際版本會在執行時根據工廠子類別的不同而不同，
        // 可能是 RectFactory 的 createShape()，也可能是 OvalFactory 的 createShape()。
        BasicObject obj = createShape(x, y);
        // 設定圖形的邊界，這樣它就會知道自己有多大，內部也會自動算好 Ports 的位置
        obj.setBounds(x, y, width, height);
        return obj;
    }

    // ==========================================
    // 工廠方法 (對應 createPizza)
    // 宣告為 abstract，迫使子類別必須實作它來決定 new 什麼圖形
    // ==========================================
    protected abstract BasicObject createShape(int x, int y);

    // ==========================================
    // 工廠方法：交由子類別決定預覽框要畫成方形還是橢圓
    // 把最後一個型別判斷也推進子類別，呼叫端 (CreateObjectMode) 完全不再認得型別字串
    // ==========================================
    public abstract void drawPreview(Graphics g, Rectangle box);
}
