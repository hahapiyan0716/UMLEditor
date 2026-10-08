package shape;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BasicObject extends Shape {
    // 記錄物件到底是什麼類型（RECT、OVAL 或 CLASS）。
    private ObjectType type;
    private List<Port> ports = new ArrayList<>();
    private Port currentDraggingPort = null;
    private int fixedAnchorX = -1;
    private int fixedAnchorY = -1;

    // 建構子
    public BasicObject(int x, int y, ObjectType type) {
        // 畫圖形都是從左上角開始畫，所以都是從左上角開始計算相對座標
        this.x = x;
        this.y = y;
        this.type = type;

        // Rect 與 Class 外形都是矩形，產生 8 個 ports；Oval 產生 4 個 ports
        int portCount;
        if(type == ObjectType.OVAL)
            portCount = 4;
        else
            portCount = 8;

        // 確實把 Port 實體塞進清單裡
        // 低耦合改進：傳入 this（Shape 的子類別），而非硬編碼 BasicObject
        for (int i = 0; i < portCount; i++) {
            ports.add(new Port(this));  // this 是 BasicObject，也是 Shape 的實例
        }
        updatePorts();              // 初始化 Port 位置
    }

    // 允許外部設定圖形(Bounding boxes)的大小與位置，並同步更新 Ports
    @Override
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        updatePorts();              // 圖形大小改變了，控制點一定要跟著重算！
    }

    // 計算並更新每個 Port 的精確座標
    private void updatePorts() {
        // Port 的位置都是根據圖形來計算的，所以都是用圖形的資訊來算出座標
        int midX = x + width/2;             // 圖形的中心點座標 X
        int midY = y + height/2;            // 圖形的中心點座標 Y
        int right = x + width;              // 圖形的右邊界座標
        int bottom = y + height;            // 圖形的底部座標

        // Rect 與 Class 都是矩形外框，共用同一套 8 個點的配置
        if(type != ObjectType.OVAL){
            // 8 個點：上中下左右 ＋ 四個角落
            // 設定 Ports 的起始點座標，之後畫圖會用到
            ports.get(0).setPosition(midX, y);       // 上中
            ports.get(1).setPosition(midX, bottom);  // 下中
            ports.get(2).setPosition(x, midY);       // 左中
            ports.get(3).setPosition(right, midY);   // 右中
            ports.get(4).setPosition(x, y);          // 左上
            ports.get(5).setPosition(right, y);      // 右上
            ports.get(6).setPosition(x, bottom);     // 左下
            ports.get(7).setPosition(right, bottom); // 右下
        }
        else{
            // Oval 只有 4 個點 (上下左右)
            ports.get(0).setPosition(midX, y);
            ports.get(1).setPosition(midX, bottom);
            ports.get(2).setPosition(x, midY);
            ports.get(3).setPosition(right, midY);
        }
    }

    @Override
    public void move(int dx, int dy) {
        super.move(dx, dy);
        updatePorts(); // 圖形移動時，所有 Port 也要跟著移動！
    }

    // 讓外部 (CreateLinkMode) 可以詢問：這個座標有戳中我的任何一個 Port 嗎？
    public Port getPortAt(Point p) {
        for (Port port : ports) {
            if (port.contains(p))
                return port;
        }
        return null;
    }


    // 這裡是在 Canvas 呼叫 paintComponent 或是 主動呼叫 repaint() 的時候會呼叫 draw()
    @Override
    // g 是一個畫筆的意思
    public void draw(Graphics g) {
        // 先設定畫筆的顏色為灰色，然後去畫矩形的顏色
        g.setColor(color);
        // labelAreaHeight：標籤文字要在多高的範圍內垂直置中。
        // Rect / Oval 是整個圖形的高度；Class 只放在最上面的「名稱格」裡，所以是高度的 1/3。
        int labelAreaHeight = height;
        switch (type) {
            case RECT:
                // java.awt.Graphics 已經寫好的函式
                // 畫出並填滿一個矩形區域，因為一開始就有設定畫筆的顏色，所以直接用來畫矩形，填滿成灰色
                g.fillRect(x, y, width, height);

                // 重新設定畫筆的顏色為黑色，拿來畫邊框
                g.setColor(Color.BLACK);
                g.drawRect(x, y, width, height);
                break;
            case OVAL:
                g.fillOval(x, y, width, height);
                g.setColor(Color.BLACK);
                g.drawOval(x, y, width, height);
                break;
            case CLASS: {
                // UML 類別圖的 Class：一個矩形，用兩條水平線平分成三格
                //   第一格：類別名稱 (Label)
                //   第二格：屬性 (Attributes)
                //   第三格：方法 (Methods / Operations)
                g.fillRect(x, y, width, height);
                g.setColor(Color.BLACK);
                g.drawRect(x, y, width, height);
                int firstLineY = y + height / 3;
                int secondLineY = y + height * 2 / 3;
                g.drawLine(x, firstLineY, x + width, firstLineY);
                g.drawLine(x, secondLineY, x + width, secondLineY);
                labelAreaHeight = height / 3;
                break;
            }
        }
        // 負責把字印在畫好的圖形上，並且「真正」置中
        // drawString(text, x, y) 的 (x, y) 不是文字的左上角，而是：
        //   x = 文字最左端
        //   y = 文字的「基線 (baseline)」，也就是英文字母 a、b、c 底部所坐的那條線
        // 所以若直接傳圖形中心點，文字會從中心點「往右」延伸、而且整體偏上，看起來不置中。
        // FontMetrics (字型量尺) 可以量出目前字型下，這段文字實際佔多寬、多高：
        //   stringWidth(label)：整段文字的像素寬度
        //   getHeight()        ：一行文字的總高度 (ascent + descent + leading)
        //   getAscent()        ：基線以上的高度 (字母往上長多高)
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        // 水平置中：從圖形左邊界往右推「(圖形寬 - 文字寬) / 2」
        int textX = x + (width - fm.stringWidth(label)) / 2;
        // 垂直置中：先算出「文字框」的頂端要放在哪 (標籤區高 - 文字高) / 2，
        // 再往下加 ascent 換算成 drawString 需要的基線位置
        // (Rect / Oval 的標籤區就是整個圖形；Class 則只有最上面的名稱格)
        int textY = y + (labelAreaHeight - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(label, textX, textY);

        // 若被選取或是滑鼠經過(懸停)，畫出 Ports，表示基本物件處於被 select 的狀態
        if (selected || hovered) {
            g.setColor(Color.BLACK);
            for (Port port : ports) {
                port.draw(g);
            }
        }
    }

    // 點擊的 X 座標有沒有大於圖形左邊緣（p.x >= x）？
    // 點擊的 X 座標有沒有小於圖形右邊緣（p.x <= x + width）？
    // 點擊的 Y 座標有沒有介於圖形的上下邊緣之間？
    // 代表使用者的游標精準地落在了這個圖形的矩形範圍內，就會回傳 true，否則回傳 false。
    @Override
    public boolean contains(Point p) {
        if(p.x >= x && p.x <= x + width && p.y >= y && p.y <= y + height)
            return true;
        return false;
    }

    // ==========================================
    // 處理物件的縮放與邊界重算，更新縮放之後的圖形的屬性
    // ==========================================
    // p 是被拖曳的 Port，mousePt 是目前滑鼠拖曳到的座標
    public void resize(Port p, Point mousePt) {
        // 取得拖拽點的 port 索引
        int index = ports.indexOf(p);
        if (index == -1)
            return;

        if (currentDraggingPort != p) {
            currentDraggingPort = p;

            // 0: 上中，1: 下中，2: 左中，3: 右中，4: 左上，5: 右上，6: 左下，7: 右下
            // 當接到一個新的 Port 拖曳請求時，鎖定對面的座標作為絕對錨點

            // ==========================================
            // 決定 X 軸的釘子 (固定不動的邊界)
            // ==========================================
            // 只要你動到「左邊的點 (2, 4, 6)」，釘子就釘在「右邊界 (x + width)」
            if (index == 2 || index == 4 || index == 6) {
                fixedAnchorX = x + width;
            } else {
                // 動右邊的點、或是純上下拉，釘子都釘在「左邊界 (x)」
                fixedAnchorX = x;
            }

            // ==========================================
            // 決定 Y 軸的釘子 (固定不動的邊界)
            // ==========================================
            // 只要你動到「上面的點 (0, 4, 5)」，釘子就釘在「下邊界 (y + height)」
            if (index == 0 || index == 4 || index == 5) {
                fixedAnchorY = y + height;
            } else {
                // 動下面的點、或是純左右拉，釘子都釘在「上邊界 (y)」
                fixedAnchorY = y;
            }
        }

        boolean modifyLeft = (index == 2 || index == 4 || index == 6);
        boolean modifyRight = (index == 3 || index == 5 || index == 7);
        boolean modifyTop = (index == 0 || index == 4 || index == 5);
        boolean modifyBottom = (index == 1 || index == 6 || index == 7);

        int targetX = mousePt.x;        // 拖拽到的新座標 X
        int targetY = mousePt.y;        // 拖拽到的新座標 Y

        // 重新設定拖拽和反向拖拽之後的圖形大小(因為有可能因為反向拖拽而產生不同的值)
        int minSize = 20;
        if (modifyLeft || modifyRight) {
            // 依據絕對錨點計算最小尺寸排斥力
            if (Math.abs(targetX - fixedAnchorX) < minSize) {
                if (mousePt.x >= fixedAnchorX) {
                    // 如果滑鼠在錨點的右邊（或重疊），目標位置就是錨點往右加 minSize
                    targetX = fixedAnchorX + minSize;
                } else {
                    // 如果滑鼠在錨點的左邊，目標位置就是錨點往左減 minSize
                    targetX = fixedAnchorX - minSize;
                }
                //targetX = fixedAnchorX + (mousePt.x >= fixedAnchorX ? minSize : -minSize);
            }
            // 因為畫圖都是從左上角開始畫就算反向拖拽之後也是從左上角開始畫，所以要更新圖形的 x, y (起始座標)
            this.x = Math.min(fixedAnchorX, targetX);
            this.width = Math.abs(fixedAnchorX - targetX);
        }

        if (modifyTop || modifyBottom) {
            if (Math.abs(targetY - fixedAnchorY) < minSize) {
                if (mousePt.y >= fixedAnchorY) {
                    // 如果滑鼠在錨點的下方（或重疊），目標位置就是錨點往下加 minSize
                    targetY = fixedAnchorY + minSize;
                } else {
                    // 如果滑鼠在錨點的上方，目標位置就是錨點往上減 minSize
                    targetY = fixedAnchorY - minSize;
                }
                //targetY = fixedAnchorY + (mousePt.y >= fixedAnchorY ? minSize : -minSize);
            }
            this.y = Math.min(fixedAnchorY, targetY);
            this.height = Math.abs(fixedAnchorY - targetY);
        }

        // 拖拽完之後也要更新 Ports 位置
        updatePorts();
    }

    // 基本物件可以自訂標籤樣式 (名稱/顏色)，用多型取代 instanceof
    @Override
    public boolean canCustomizeLabel() { return true; }
}
