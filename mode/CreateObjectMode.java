package mode;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import ui.Canvas;
import shape.BasicObject;
import shape.ShapeFactory;

public class CreateObjectMode extends Mode {
    private ShapeFactory factory;           // 工廠方法模式：由具體工廠決定要建立 Rect 還是 Oval
    private Point startPoint = null;        // 開始拖拽的起始座標
    private Rectangle previewBox = null;    // 拖曳時的預覽框

    // 建構子：傳入具體建立者 (RectFactory 或 OvalFactory)
    public CreateObjectMode(Canvas c, ShapeFactory factory) {
        super(c);
        this.factory = factory;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        startPoint = e.getPoint();
        previewBox = new Rectangle(startPoint);     // 畫出一個隱形的預覽框，初始位置和大小都從 startPoint 開始
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (startPoint != null) {
            // 支援反向拖曳的數學計算
            int x = Math.min(startPoint.x, e.getX());
            int y = Math.min(startPoint.y, e.getY());
            int width = Math.abs(startPoint.x - e.getX());
            int height = Math.abs(startPoint.y - e.getY());
            previewBox.setBounds(x, y, width, height);      // 根據滑鼠拖曳到的位置更新預覽框的大小和位置
            canvas.repaint();                               // 重畫畫布，畫出預覽框，然後就會呼叫到 CreateObjectMode.draw() 方法
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (previewBox != null) {
            int finalWidth = previewBox.width;
            int finalHeight = previewBox.height;

            // 如果使用者只是「輕輕點一下」沒有拖曳，給予預設大小 80x80
            if (finalWidth < 10 || finalHeight < 10) {
                finalWidth = 80;
                finalHeight = 80;
            }

            // 工廠方法模式：呼叫核心流程 orderShape()，由具體工廠決定要 new 哪種圖形
            // CreateObjectMode 完全不知道拿到的是 Rect 還是 Oval，只認得抽象的 BasicObject
            BasicObject obj = factory.orderShape(previewBox.x, previewBox.y, finalWidth, finalHeight);

            canvas.addShape(obj);
        }

        // 清理狀態
        startPoint = null;
        previewBox = null;
        canvas.repaint();       // 這裡畫出圖形給使用者看到

        // 一次性動作完成：請畫布自動回到預設模式 (Select)
        // (Canvas 內部會切換模式並發出事件，讓 UMLEditor 把按鈕高亮切回 Select)
        canvas.returnToDefaultMode();
    }

    // 畫出拖曳時暫時出現的「灰色預覽框」，讓使用者知道圖形會畫多大
    @Override
    public void draw(Graphics g) {
        if (previewBox != null) {
            g.setColor(Color.DARK_GRAY);
            // 工廠方法模式：把預覽框畫成方形還是橢圓，交由具體工廠決定
            // 這裡不再出現任何 "Rect" / "Oval" 的型別判斷
            factory.drawPreview(g, previewBox);
        }
    }
}