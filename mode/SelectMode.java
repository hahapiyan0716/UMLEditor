package mode;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import ui.Canvas;
import shape.Shape;

public class SelectMode extends Mode {
    private Shape currentHovered = null;        // 記住現在滑鼠「路過」哪個圖形
    private Shape draggedShape = null;          // 記錄目前被拖曳的物件 (移動用)
    private shape.Port draggedPort = null;      // 記錄目前被拖曳的控制點 (縮放用)

    private Point startPoint = null;            // 記住滑鼠剛按下去的起始座標
    private Rectangle selectionBox = null;      // 記住拖曳出來的「藍色虛擬選取框」

    // 建構子
    public SelectMode(Canvas c) { super(c); }

    // 懸停偵測
    @Override
    public void mouseMoved(MouseEvent e) {
        // 用來獲取在滑鼠滑動到的那個位置上最頂層的圖形物件
        Shape target = canvas.getTopShapeAt(e.getPoint());

        // 如果滑鼠移動到了跟前一個的不同的東西上，前一個可能是空白畫布或是一個圖形
        if (target != currentHovered) {
            if (currentHovered != null)
                currentHovered.setHovered(false);       // 原本滑動到的圖形關掉其懸停
            if (target != null)                           // 新的滑動到的圖形亮起
                target.setHovered(true);
            currentHovered = target;                      // 更新 currentHovered
            canvas.repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        startPoint = e.getPoint();
        draggedShape = null;
        draggedPort = null;
        selectionBox = null;

        // ==========================================
        // 優先檢查：是否點擊到了某個圖形的 Port (準備縮放)
        // 低耦合改進：使用 Shape 的 getPortAt 方法，不使用 instanceof
        // ==========================================
        for (Shape s : canvas.getShapes()) {
            // 只有在顯示 Port 的狀態 (被選取或懸停) 才能拖曳
            if ((s.isSelected() || s.isHovered())) {
                // 詢問圖形：這個座標有落在你的任何一個 Port 裡面嗎？
                // 大多數圖形返回 null，只有 BasicObject 返回實際的 Port
                shape.Port p = s.getPortAt(startPoint);

                // 真的點到了控制點
                if (p != null) {
                    // 系統要先把畫面上其他被選取的圖形全部取消，只留下你現在點擊的這個圖形，確保畫面乾淨且焦點明確。
                    canvas.clearSelection();
                    s.setSelected(true); // 確保現在該物件正式被選取
                    /*
                    如果圖形只有 hovered = true，一旦滑鼠移出範圍，系統就會判定懸停結束，把 hovered 設為 false。
                    接著 draw 方法發現它既沒被 select 也沒被 hover，就會把黑色的 Port 隱藏起來！
                    這會導致使用者明明還在拖曳縮放，畫面上的控制點卻突然消失了，視覺體驗會非常奇怪。
                    所以強制設定 s.setSelected(true) 可以「鎖死」這個物件的選取狀態，保證在整個拖曳過程中，那 8 個黑點絕對不會消失。
                    */

                    draggedPort = p;     // 記錄抓到的 Port
                    draggedShape = s;    // 記錄被縮放的主人(哪個圖形)
                    canvas.repaint();
                    return; // 成功抓到 Port，提早結束方法
                }
            }
        }

        // 在使用者點擊的座標 (startPoint) 上，有沒有任何圖形？如果有重疊，請給我最上層的那一個。
        // 如果有圖形，draggedShape 就會裝著那個圖形（可能是 BasicObject 或 CompositeObject）。
        // 如果點在空白處，draggedShape 就會是 null。
        draggedShape = canvas.getTopShapeAt(startPoint);

        // 準備搬移
        if (draggedShape != null) {
            if (!draggedShape.isSelected()) {
                // 點到尚未被選取的物件：清除選取、選這一個，並請 Canvas 把它浮到最上層
                // (floatToFront 內部會先還原上一個浮起的物件、記住新物件的原始位置)
                canvas.clearSelection();
                draggedShape.setSelected(true);
                canvas.floatToFront(draggedShape);
            }
            // 若點到的物件已在選取中（單選或多選），保留所有選取，準備整批移動
        }
        // 準備框選
        else {
            // 點到空白：請 Canvas 還原被浮起的物件，再清除選取
            canvas.restoreFloating();
            canvas.clearSelection();
            selectionBox = new Rectangle(startPoint);
        }
        canvas.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // 因為剛才 Pressed 的時候，已經把選取到的 port 給存下來了
        // 所以現在使用的時候就不需要再去檢查一次滑鼠位置了，直接對著這個 port 進行縮放就對了
        if (draggedPort != null) {
            // 狀態 A：正在縮放 (低耦合改進：使用多態調用，不用 instanceof)
            draggedShape.resize(draggedPort, e.getPoint());
        }
        else if (draggedShape != null && startPoint != null) {
            // 狀態 B：正在移動（支援多選一起移動）
            int dx = e.getX() - startPoint.x;
            int dy = e.getY() - startPoint.y;
            for (Shape s : canvas.getShapes()) {
                if (s.isSelected()) {
                    s.move(dx, dy);
                }
            }
            startPoint = e.getPoint();
        }
        else if (selectionBox != null) {
            // 狀態 C：正在框選
            int x = Math.min(startPoint.x, e.getX());
            int y = Math.min(startPoint.y, e.getY());
            int width = Math.abs(startPoint.x - e.getX());
            int height = Math.abs(startPoint.y - e.getY());
            selectionBox.setBounds(x, y, width, height);
        }
        canvas.repaint(); // 即時更新畫面，讓使用者看到縮放/移動的過程
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // 狀態 A：縮放結束，沒有額外的後續動作需要處理，因為 resize() 已經在拖曳過程中即時更新圖形的大小了。
        // 狀態 B：移動結束，沒有額外的後續動作需要處理，因為 move() 已經在拖曳過程中即時更新圖形的位置了。
        // 狀態 C：框選結束，需要根據 selectionBox 的範圍來決定哪些圖形被選取。
        if (selectionBox != null) {
            for (Shape s : canvas.getShapes()) {
                if (selectionBox.contains(s.getBounds())) {
                    s.setSelected(true);
                }
            }
        }
        // 清理所有狀態
        selectionBox = null;
        draggedShape = null;
        draggedPort = null;
        canvas.repaint();       // 畫出最後的結果給使用者看到
    }

    // 當畫布呼叫 repaint() 的時候，這個 draw() 就會被呼叫到，負責把「藍色選取框」畫出來
    @Override
    public void draw(Graphics g) {
        if (selectionBox != null) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(new Color(100, 150, 255, 80));
            g2.fill(selectionBox);
            g2.setColor(Color.BLUE);
            g2.draw(selectionBox);
        }
    }
}