package shape;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CompositeObject extends Shape {
    private List<Shape> components = new ArrayList<>();

    public CompositeObject() {
        // 群組為一般物件，加入畫布時放到清單尾端（最上層）
    }

    // 將單一圖形加入到這個群組中
    public void addComponent(Shape s) {
        components.add(s);
        updateBounds();             // 每次加入新成員，都要重新計算群組的總邊界
    }

    @Override
    public List<Shape> getComponents() {
        return components;
    }

    // 這是群組，用多型取代 instanceof
    @Override
    public boolean isGroup() { return true; }

    // 計算這個群組的「最小包圍盒 (Bounding Box)」
    private void updateBounds() {
        if (components.isEmpty())
            return;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (Shape s : components) {
            if (s.x < minX)
                minX = s.x;
            if (s.y < minY)
                minY = s.y;
            if (s.x + s.width > maxX)
                maxX = s.x + s.width;
            if (s.y + s.height > maxY)
                maxY = s.y + s.height;
        }

        // 更新群組自己的座標與寬高
        this.x = minX;              // 一樣從左上角開始算起，紀錄起始的 X 座標
        this.y = minY;              // 一樣從左上角開始算起，紀錄起始的 Y 座標
        this.width = maxX - minX;
        this.height = maxY - minY;
    }

    @Override
    public void move(int dx, int dy) {
        // 群組移動時，叫裡面的所有小弟一起移動
        for (Shape s : components) {
            s.move(dx, dy);
        }

        updateBounds(); // 移動完更新邊界
    }

    @Override
    public void draw(Graphics g) {
        // 先畫出裡面的所有元件
        for (Shape s : components) {
            s.draw(g);
        }

        // 如果是 Composite 物件被選取，僅顯示外框 (虛線)
        if (selected || hovered) {
            // 也是一個畫筆，是 Graphics 的升級版，可以設定線條粗細、畫出各種間距的虛線、設定半透明度 (Alpha)、甚至做漸層填色。它就像是一個專業的數位繪圖板。
            Graphics2D g2d = (Graphics2D) g;
            // Stroke (筆觸)，粗細 (Width)，虛線樣式 (Dash Pattern)，端點樣式 (End Cap)
            // 備份原本的畫筆設定
            Stroke oldStroke = g2d.getStroke();

            // 設定畫筆為「虛線 (Dash)」
            float[] dashPattern = { 5.0f, 5.0f };       // 設定虛線樣式: [線段長度, 空白長度]，先畫 5 個像素的實線，再畫 5 個像素的空白線段
            /*
            1.5f (線條粗細 Width)：設定這條虛線的寬度為 1.5 像素。
            CAP_BUTT (端點樣式 Cap)：決定每一截短虛線的「頭尾」長什麼樣子。BUTT 代表平切（像刀切一樣齊），另外還有 CAP_ROUND 可以讓虛線的兩端變成圓潤的膠囊狀。
            JOIN_MITER (轉角樣式 Join)：決定兩條線交會（例如矩形的四個角落）時，轉角要怎麼處理。MITER 代表「尖角」，也就是保留銳利的 90 度角。
            10.0f (尖角限制 Miter Limit)：這是配合 JOIN_MITER 使用的防呆機制。如果兩條線交會的角度太小（例如極銳角），尖端會飆得無限長。這個數值限制了尖端延伸的最大長度，超過就會自動被切平。
            dashPattern (虛線陣列)：把剛剛設定好的 {5.0f, 5.0f} 節奏交給畫筆。
            0.0f (起始偏移量 Phase)：這是做動畫的關鍵！ 它決定了虛線從陣列的哪裡開始畫。
                如果配合一個 Timer 讓這個數值不斷增加（例如 0, 1, 2, 3...），虛線框看起來就會像是「螞蟻在走路 (Marching Ants)」一樣順著邊框繞圈圈！
            */
            g2d.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dashPattern, 0.0f));

            g2d.setColor(new Color(0, 0, 100)); // 設定為深藍色虛線 (符合多數 UML 編輯器習慣)

            // 畫出虛線外框，並稍微往外擴張 5 像素當作 Padding，才不會跟裡面的圖形貼太緊
            g2d.drawRect(x - 5, y - 5, width + 10, height + 10);

            // 還原畫筆設定，以免影響後面其他圖形的繪製
            g2d.setStroke(oldStroke);
        }
    }

    // 群組的前景層 = 所有子圖形的前景層 (Composite Pattern：對群組的操作，遞迴轉交給每個成員)
    // 目前連線不會被收進群組，所以子圖形實際上沒有前景層內容；
    // 但照著 Composite 的規則轉交，未來若有其他帶前景層的圖形被群組，也能正確畫出來。
    @Override
    public void drawOverlay(Graphics g) {
        for (Shape s : components) {
            s.drawOverlay(g);
        }
    }

    @Override
    public boolean contains(Point p) {
        // 點擊群組的包圍盒範圍內，就算點中這個群組
        return (p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height);
    }

    @Override
    public void resize(Port p, Point pt) {
        // 群組物件通常不支援直接縮放，因為群組本身沒有 Port
        // 這個方法作為 Liskov Substitution Principle 的一部分，
        // 儘管群組無法被縮放，但 SelectMode 可以統一透過多態調用
        // 無需特殊處理，讓每個 Shape 子類決定自己的行為
    }
}