package shape;

import java.awt.*;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/* =========================================
 * 基礎圖形介面 (Composite Pattern)
 * ========================================= */

// implements Serializable：讓圖形可以被「序列化」——把記憶體中的物件連同它參照的其他物件，
// 轉成一串位元組寫進檔案 (存檔)，之後再從檔案還原成一模一樣的物件 (讀檔)。
// Serializable 是一個「標記介面 (Marker Interface)」：裡面沒有任何方法，只是告訴 JVM「這個類別允許被序列化」。
// 子類別 (BasicObject、CompositeObject、ConnectionLine) 會自動繼承這個身分，不需要各自再宣告一次。
public abstract class Shape implements Serializable {
    // serialVersionUID：序列化的「版本號」。
    // 讀檔時，JVM 會比對「檔案裡記錄的版本號」與「目前類別的版本號」，不一致就拒絕讀取 (InvalidClassException)。
    // 若不手動宣告，JVM 會依類別的欄位、方法自動算一個，只要稍微改動類別 (甚至只加一個方法) 就會變，
    // 導致舊檔案讀不回來。手動固定成 1L，代表「只要我沒刻意改版號，就視為相容的同一版」。
    private static final long serialVersionUID = 1L;

    // protected 允許繼承它的「子類別」(例如 BasicObject 或 CompositeObject) 直接存取並修改這些變數。

    // 定義了圖形在畫布上的「邊界框 (Bounding Box)」。
    // x 與 y 代表圖形左上角的座標，而 width(寬度) 與 height(高度) 預設為 100 像素。
    protected int x, y, width = 100, height = 100;
    // 記錄該圖形目前是否處於被選取的狀態。
    protected boolean selected = false;          // 預設是不被選取
    // 記錄圖形上的文字標籤（預設為 ""）以及圖形預設的背景填充顏色（淺灰色）。
    // protected：只開放給子類別直接存取，外部一律走 getLabel/setLabel
    protected String label = "";
    protected Color color = Color.LIGHT_GRAY;
    protected boolean hovered = false; // 記錄滑鼠是否懸停在上方

    // 建構子，但什麼都不做
    public Shape() {}

    // 抽象方法：父類別 Shape 規定了所有圖形「必須具備」這兩種行為
    // 強迫繼承它的子類別必須自己撰寫繪製形狀的程式碼。
    public abstract void draw(Graphics g);

    // 前景層繪製：Canvas 會在「所有圖形的 draw() 都畫完之後」，再依序呼叫每個圖形的 drawOverlay()。
    // 因此這裡畫的東西會浮在所有物件上方，不會被任何物件蓋住。
    // 大多數圖形沒有前景層的東西 → 預設空實作；ConnectionLine 覆寫它來畫箭頭。
    // (用多型掛勾 (Hook) 取代 instanceof：Canvas 不必知道誰有前景層，一律呼叫即可)
    public void drawOverlay(Graphics g) {}
    // 用來判斷使用者的游標座標 (Point p) 是否落在圖形範圍內。
    public abstract boolean contains(Point p);

    // 具體方法：這類行為的邏輯對所有圖形來說都是一模一樣的
    // 無論是方塊、圓形還是由多個圖形組成的群組，在畫布上移動的數學邏輯都是統一的：
    // 將目前的 x 座標加上水平位移量 (dx)，y 座標加上垂直位移量 (dy)
    public void move(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }

    /*
    當你在畫布上用滑鼠點擊某個圖形時，程式（具體來說是你的 SelectMode）就會呼叫這個方法，並傳入 true（例如：shape.setSelected(true);）。
    這就像是打開這個圖形的開關。一旦開關被打開（selected 變成 true），
    下次畫布呼叫 draw 重新繪圖時，圖形就會在自己的上下左右畫出那 4 個黑色的控制點（Ports），讓使用者知道「我被選中了」。
    */
    public void setSelected(boolean b) { this.selected = b; }

    // 畫布或其他物件可以隨時問這個圖形：「嘿，你現在是被選中的狀態嗎？」
    public boolean isSelected() { return selected; }
    public boolean isHovered() { return hovered; }

    // ==========================================
    // z-order 分層提示 (Factory 之外的低耦合設計)
    // ==========================================
    // 圖層順序完全由 Canvas 的清單順序決定（清單越後面 = 越上層）。
    // 這個方法只回答一個問題：「我該被放在背景層嗎？」
    //   - 回傳 true：屬於背景層（例如連線 ConnectionLine），加入畫布時會被放到清單最前面（最底層）。
    //   - 回傳 false：一般物件，加入時放到清單尾端（最上層）。
    // 用多型取代 instanceof，Canvas 不需要知道具體型別就能正確分層。
    public boolean isBackgroundLayer() { return false; }

    // ==========================================
    // 型別查詢 (用多型取代 instanceof)
    // ==========================================
    // 是否為群組 (Composite)？預設 false，CompositeObject 覆寫為 true
    public boolean isGroup() { return false; }

    // 是否可自訂標籤樣式 (名稱/顏色)？預設 false，BasicObject 覆寫為 true
    public boolean canCustomizeLabel() { return false; }

    // Composite Pattern 透明介面：回傳子圖形清單
    // 非群組預設回傳空清單，CompositeObject 覆寫回傳真正的成員
    public List<Shape> getComponents() { return Collections.emptyList(); }

    // 我是否「依附」在這群即將被刪除的圖形身上？
    // 用途：刪除物件時，Canvas 要順便清掉「連在被刪物件上」的連線，否則會留下一條兩端懸空的線。
    // 一般圖形不依附任何人 → 預設 false；ConnectionLine 覆寫：只要起點或終點的主人在集合裡就回傳 true。
    // 同樣是用多型取代 instanceof：Canvas 不必知道「誰是連線」，只要逐一詢問即可。
    public boolean isAttachedToAny(Set<Shape> removedShapes) { return false; }

    // 設定鼠標是否懸停在這個圖形上
    public void setHovered(boolean b) { this.hovered = b; }

    // 新增取得圖形邊界(Bounding Box)的方法
    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // 低耦合改進：提供默認 getPortAt 方法
    // 大多數圖形沒有 Port，所以返回 null
    // BasicObject 覆蓋此方法以返回實際的 Port
    public Port getPortAt(Point p) {
        return null;
    }

    // 低耦合改進：提供默認 resize 方法
    // 大多數圖形不支援縮放，所以默認為空實作
    // BasicObject 覆蓋此方法以實現實際的縮放功能
    public void resize(Port p, Point pt) {
        // 默認空實作，不支援縮放
    }


    // ==========================================
    // Label & Color Getters/Setters:
    // 負責拿出現在圖形的 label 與 color
    // 和設定圖形 label 與 color
    // ==========================================
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Color getColor() { return color; }
    public void setColor(Color color) { this.color = color; }
}
