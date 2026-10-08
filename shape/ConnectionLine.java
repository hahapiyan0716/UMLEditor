package shape;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.Set;

public class ConnectionLine extends Shape {
    // 點選連線的「容許誤差」(像素)：滑鼠距離線段 5 像素以內就算點中。
    // 線條只有 1 像素寬，若要求滑鼠「剛好」點在線上，使用者幾乎點不到，所以要給一點寬容範圍。
    private static final double HIT_TOLERANCE = 5.0;

    // 被選取時的粗線筆觸 (static final：所有連線共用同一支筆，不必每次重畫都 new 一個)
    private static final Stroke SELECTED_STROKE = new BasicStroke(2.5f);

    // Dependency 線身用的虛線筆觸
    // BasicStroke 完整建構子的參數：(線寬, 端點樣式, 轉角樣式, 斜接上限, 虛線樣式陣列, 虛線起始偏移)
    //   CAP_BUTT    ：線段端點平切，不額外延伸 (每一小段虛線長度才會精準等於設定值)
    //   JOIN_MITER  ：轉角用尖角接合 (直線沒有轉角，填預設值即可)
    //   10f         ：斜接上限 (miter limit)，搭配 JOIN_MITER 使用的預設值
    //   {6f, 4f}    ：虛線樣式「畫 6px、空 4px」不斷重複
    //   0f          ：從虛線樣式的第 0px 開始畫
    private static final float[] DASH_PATTERN = { 6f, 4f };
    private static final Stroke DASHED_STROKE = new BasicStroke(
        1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH_PATTERN, 0f);
    // 被選取時的粗虛線 (跟 SELECTED_STROKE 同樣粗 2.5px，只是改成虛線)
    private static final Stroke SELECTED_DASHED_STROKE = new BasicStroke(
        2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH_PATTERN, 0f);
    // 選取/懸停時的高亮顏色
    private static final Color HIGHLIGHT_COLOR = new Color(30, 90, 220);

    // 連接線的起點
    private Port startPort;
    // 連接線的終點
    private Port endPort;
    // 連接線的類型: ASSOCIATION、GENERALIZATION、COMPOSITION、DEPENDENCY
    private LinkType linkType;

    public ConnectionLine(Port start, Port end, LinkType type) {
        this.startPort = start;
        this.endPort = end;
        this.linkType = type;
    }

    // 連線屬於背景層：加入畫布時會被放到清單最前面，永遠畫在物件底下，不會蓋住圖形
    @Override
    public boolean isBackgroundLayer() { return true; }

    // ==========================================
    // 連線分成「兩層」繪製：
    //   draw()        → 線身 (背景層)：跟著清單順序最先畫，被物件蓋住，所以線不會穿過物件表面
    //   drawOverlay() → 箭頭 (前景層)：Canvas 畫完所有物件後才畫，永遠浮在物件上方
    // 為什麼要拆開？箭頭的尖端就落在物件的 Port 上 (物件邊緣)。
    // 當連線以很斜的角度靠近物件時，菱形/三角形箭頭會有一半落在物件內部；
    // 若箭頭跟線身一起畫在背景層，接著畫物件時就會把那一半蓋掉，箭頭看起來殘缺。
    // ==========================================
    @Override
    public void draw(Graphics g) {
        // 因為線是綁定在 Port 上，所以每次重畫都會即時抓取 Port 的最新座標
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();     // 備份原本的筆觸，畫完要還原，以免影響後面其他圖形
        applyLineStyle(g2d);
        // Dependency 的線身改用虛線 (選取時用粗虛線)。
        // 只有「線身」換成虛線，箭頭 (drawOverlay) 仍沿用 applyLineStyle 設的實線筆觸：
        // 箭頭的兩條翅膀只有 15px 長，若也套用「畫 6 空 4」的虛線，會被切成零碎的小段，看起來像箭頭斷掉。
        if (linkType == LinkType.DEPENDENCY) {
            g2d.setStroke(selected ? SELECTED_DASHED_STROKE : DASHED_STROKE);
        }
        g2d.drawLine(startPort.getX(), startPort.getY(), endPort.getX(), endPort.getY());
        g2d.setStroke(oldStroke);               // 還原筆觸
    }

    // 前景層：畫出終點的箭頭 (利用三角函數計算角度)，沿用與線身相同的顏色與筆觸
    @Override
    public void drawOverlay(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Stroke oldStroke = g2d.getStroke();
        Color lineColor = applyLineStyle(g2d);
        drawArrow(g2d, startPort.getX(), startPort.getY(), endPort.getX(), endPort.getY(), lineColor);
        g2d.setStroke(oldStroke);
    }

    // 依照選取/懸停狀態設定畫筆的顏色與筆觸，回傳所用的顏色 (箭頭的空心三角形需要再用到它)
    // 被選取或懸停時，用藍色高亮，讓使用者知道「這條線目前被點中了」
    // 選取：藍色粗線；懸停：藍色細線；平常：黑色細線
    // 線身與箭頭都呼叫這個方法，確保兩層的樣式永遠一致 (只寫一份，不會改了一邊忘了另一邊)
    private Color applyLineStyle(Graphics2D g2d) {
        Color lineColor = (selected || hovered) ? HIGHLIGHT_COLOR : Color.BLACK;
        if (selected) {
            g2d.setStroke(SELECTED_STROKE);
        }
        g2d.setColor(lineColor);
        return lineColor;
    }

    // lineColor：箭頭的線條顏色 (平常是黑色，選取/懸停時是藍色，跟主線條保持一致)
    private void drawArrow(Graphics g, int x1, int y1, int x2, int y2, Color lineColor) {
        // 箭頭的規格
        //int arrowWidth = 10;
        int arrowLength = 15;

        // 算出主線條的傾斜角
        double dx = x2 - x1;
        double dy = y2 - y1;
        double angle = Math.atan2(dy, dx);      // 回傳這條線與水平線之間的絕對角度（以弧度 Radian 表示，範圍從 -pi 到 pi）
                                                // 可以知道這條線是朝著哪個方向射過去的。

        // 計算箭頭左右兩個尖端的點
        // x2 - ...: 是從線條的終點（箭頭尖端）往反方向（線條的方向）推算箭頭翅膀的位置
        // pi 等於 180 度，所以 pi / 6 就是 30 度。這代表箭頭的翅膀會從主線條的中心角度，向外擴張 30 度
        // angle - Math.PI / 6 算出了右邊翅膀的絕對角度
        int xRight = (int) (x2 - arrowLength * Math.cos(angle - Math.PI / 6));
        int yRight = (int) (y2 - arrowLength * Math.sin(angle - Math.PI / 6));
        int xLeft = (int) (x2 - arrowLength * Math.cos(angle + Math.PI / 6));
        int yLeft = (int) (y2 - arrowLength * Math.sin(angle + Math.PI / 6));

        // 依據規格畫出不同的箭頭形狀 (switch 窮舉 enum，未來新增線型編譯器會提醒)
        switch (linkType) {
            case ASSOCIATION:
            case DEPENDENCY:
                // 一般箭頭：兩條線 (V型)
                // Dependency 的箭頭跟 Association 長得一樣，兩者的差別只在線身是實線還是虛線 (見 draw())。
                // 這裡利用 switch 的「貫穿 (fall-through)」：case ASSOCIATION 底下沒有 break，
                // 所以 ASSOCIATION 和 DEPENDENCY 會執行同一段程式碼，不必把畫 V 型箭頭的程式寫兩次。
                g.drawLine(x2, y2, xRight, yRight);
                g.drawLine(x2, y2, xLeft, yLeft);
                break;
            case GENERALIZATION: {
                // 空心三角形
                Polygon triangle = new Polygon();
                triangle.addPoint(x2, y2);
                triangle.addPoint(xRight, yRight);
                triangle.addPoint(xLeft, yLeft);
                g.setColor(Color.WHITE);
                g.fillPolygon(triangle);
                g.setColor(lineColor);
                g.drawPolygon(triangle);    // 劃出三角形的框線
                break;
            }
            case COMPOSITION: {
                // 實心菱形
                int xBack = (int) (x2 - 2 * arrowLength * Math.cos(angle));
                int yBack = (int) (y2 - 2 * arrowLength * Math.sin(angle));
                Polygon diamond = new Polygon();
                diamond.addPoint(x2, y2);
                diamond.addPoint(xRight, yRight);
                diamond.addPoint(xBack, yBack);
                diamond.addPoint(xLeft, yLeft);
                g.fillPolygon(diamond);
                break;
            }
        }
    }

    // 判斷滑鼠是否點中這條連線
    // Line2D.ptSegDist(x1, y1, x2, y2, px, py) 會算出「點 (px, py) 到線段 (x1,y1)-(x2,y2) 的最短距離」。
    // 注意是「線段 (Segment)」不是「無限長的直線」：點在線段延長線上但超出兩端，距離會以端點計算，
    // 因此不會發生「點在線的延長線上也被判定點中」的誤判。
    // 距離在 HIT_TOLERANCE 以內就算點中 → 回傳 true。
    // 因為連線位在清單最前面 (最底層)，Canvas.getTopShapeAt() 從上層往下找，
    // 所以當線與物件重疊時，會優先點中物件，只有點在「線露出來的地方」才會選到線。
    @Override
    public boolean contains(Point p) {
        double distance = Line2D.ptSegDist(
            startPort.getX(), startPort.getY(),
            endPort.getX(),   endPort.getY(),
            p.x, p.y
        );
        return distance <= HIT_TOLERANCE;
    }

    // 連線不能自己移動：它的位置完全由兩端的 Port 決定，物件移動時線就會自動跟著走。
    // 若沿用 Shape 預設的 move()，只會改到沒有意義的 x/y 欄位，所以覆寫成空實作，語意更明確。
    // (在 SelectMode 中，物件和連線一起被選取並拖曳時，會對每個選取的圖形呼叫 move()，這裡就會安全地什麼都不做)
    @Override
    public void move(int dx, int dy) {
        // 刻意不做任何事
    }

    // 只要起點或終點所屬的圖形，出現在「即將被刪除的集合」中，這條線就依附在被刪除的物件上，必須一起刪除
    @Override
    public boolean isAttachedToAny(Set<Shape> removedShapes) {
        return removedShapes.contains(startPort.getParent())
            || removedShapes.contains(endPort.getParent());
    }

    // 連線的邊界 = 兩個 Port 圍成的矩形 (不再沿用 Shape 預設的幽靈邊界 0,0,100,100)
    @Override
    public Rectangle getBounds() {
        int x1 = startPort.getX(), y1 = startPort.getY();
        int x2 = endPort.getX(),   y2 = endPort.getY();
        return new Rectangle(
            Math.min(x1, x2), Math.min(y1, y2),
            Math.abs(x1 - x2), Math.abs(y1 - y2)
        );
    }

    @Override
    public void resize(Port p, Point pt) {
        // 連接線不支援縮放，因為線條本身沒有 Port 和邊界
        // 這個方法作為 Liskov Substitution Principle 的一部分
        // 無需特殊處理
    }
}