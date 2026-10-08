package shape;

import java.awt.*;
import java.io.Serializable;

// Port 也必須可序列化：BasicObject 持有一組 Port，ConnectionLine 也參照兩端的 Port，
// 序列化會沿著參照一路把 Port 寫進檔案。只要有任何一個被參照到的物件不是 Serializable，
// 存檔就會丟出 NotSerializableException。
public class Port implements Serializable {
    private static final long serialVersionUID = 1L;    // 序列化版本號 (說明見 Shape)

    // 低耦合改進：改為依賴 Shape 抽象類別，而非具體的 BasicObject 類別
    // 這樣可以減少循環依賴，提高模組的獨立性
    private Shape parent;                   // 記住這個 Port 屬於哪一個圖形
    private int x, y;                        // Port 位於畫布上的絕對座標，從左上角算起 (外部一律走 getX/getY)
    private static final int portSize = 10;     // Port 的正方形大小

    // 建構子：接受 Shape 抽象類別而非具體實現
    public Port(Shape parent) {
        this.parent = parent;
    }

    // 返回 Shape 抽象類別，提高了通用性
    public Shape getParent() {
        return parent;
    }
    public int getX() {
        return x;
    }
    public int getY() {
        return y;
    }

    // 設定 Port 的起始點的絕對座標
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // 檢查滑鼠座標是否在 Port 的「範圍內」
    public boolean contains(Point p) {
        // 以 Port 的中心點為基準，往外擴張產生一個「隱形的正方形」
        Rectangle rect = new Rectangle(x - portSize / 2, y - portSize / 2, portSize, portSize);

        // 利用 Java 內建的 Rectangle 功能，直接判斷滑鼠座標 p 有沒有掉進這個正方形裡
        if(rect.contains(p))
            return true;
        return false;
    }

    public void draw(Graphics g) {
        // 設定畫筆的顏色為黑色，拿來畫 Port
        g.setColor(Color.BLACK);
        // 畫出並填滿一個矩形區域，因為一開始就有設定畫筆的顏色，所以直接用來畫矩形，填滿成黑色，大小為 10 * 10
        // 因為 x y 是指畫筆畫圖形會從左上角開始畫
        // 所以為了置中 Ports 在圖形的邊界上
        // x 的部分要把 Ports 移動到寬度一半的地方，但是這樣會偏右，所以要往回調動一半的像素
        // y 的部分就往上調整一半的像素
        g.fillRect(x - portSize / 2, y - portSize / 2, portSize, portSize);
    }
}
