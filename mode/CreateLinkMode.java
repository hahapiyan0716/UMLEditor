package mode;

import ui.Canvas;
import shape.Shape;
import shape.Port;
import shape.ConnectionLine;
import shape.LinkType;
import java.awt.Point;
import java.awt.*;
import java.awt.event.MouseEvent;

public class CreateLinkMode extends Mode {
    private LinkType linkType;
    // 因為拉線是一個「拖曳（Drag）」的過程，滑鼠按下去的瞬間和放開的瞬間有時間差。
    // startPort 就是用來記住使用者在第一秒鐘按下去的那個小黑點（Port）是誰。
    private Port startPort = null;
    protected int currentX;
    protected int currentY;
    protected int startX;
    protected int startY;
    protected Shape sourceShape = null;
    private Shape hoveredShape = null;      // 記住滑鼠目前路過的圖形 (連線模式也要顯示 Port)

    // 建構子
    public CreateLinkMode(Canvas c, LinkType type) {
        super(c);
        this.linkType = type;
        this.sourceShape = null;
    }

    // 連線模式下，滑鼠移到哪個圖形就亮起它的 Port，讓使用者看得到可連接的控制點
    @Override
    public void mouseMoved(MouseEvent e) {
        Shape target = canvas.getTopShapeAt(e.getPoint());
        if (target != hoveredShape) {
            if (hoveredShape != null) hoveredShape.setHovered(false);
            if (target != null) target.setHovered(true);
            hoveredShape = target;
            canvas.repaint();
        }
    }

    // 當使用者在畫布上按下滑鼠左鍵的那一瞬間，這段程式碼會啟動
    @Override
    public void mousePressed(MouseEvent e) {
        startPort = null;           // 每次按下 Port 時都先清空舊的 Port 的資訊
        Point p = e.getPoint();     // 取得滑鼠點擊的 X, Y 座標

        // 低耦合改進：走訪所有圖形，使用 Shape 的 getPortAt 方法
        // 不使用 instanceof BasicObject，提高抽象性
        for (Shape s : canvas.getShapes()) {
            // 詢問每個圖形：這個座標有落在你的任何一個 Port 裡面嗎？
            Port port = s.getPortAt(p);
            if (port != null) {
                startPort = port;       // 抓到了起點，把這個 Port 存進記憶體
                sourceShape = s;        // 記錄起點圖形的資訊，後面拖線的時候可以用來檢查終點圖形是否跟起點圖形一樣（不允許自己連自己）
                startX = startPort.getX();
                startY = startPort.getY();
                currentX = startX;
                currentY = startY;
                break;                  // 找到了起點，就不需要再檢查其他圖形了，直接跳出迴圈
            }
        }
        // 如果 startPort 還是 null，代表點在空白處，表示這次的起點無效
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // 如果沒有有效的起點Port，直接放棄，不進行任何拖線操作
        if (startPort == null)
            return;

        // 1. 更新預覽線的終點座標 (讓線跟著滑鼠跑)
        currentX = e.getX();
        currentY = e.getY();

        // 先把畫布上所有圖形的 hovered 狀態強制關閉！
        // 如果不做這一步，滑鼠掃過的地方，圖形的 Port 會永遠亮著關不掉。
        for (Shape s : canvas.getShapes()) {
            s.setHovered(false);
        }

        // 詢問畫布：現在滑鼠這個座標，有沒有碰到任何圖形？
        Shape targetShape = canvas.getTopShapeAt(e.getPoint());

        // 如果有碰到圖形，而且碰到的「不是」我們拉線的起點圖形 (不能自己連自己)
        if (targetShape != null && targetShape != sourceShape) {
            // 命令該圖形：把你的控制點亮起來！
            targetShape.setHovered(true);
        }

        // 不管有沒有碰到圖形，畫布都要重畫一次，讓線跟著滑鼠跑，並且讓被掃過的圖形亮起來
        canvas.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // 如果根本沒有成功的起點，直接放棄這次的連線
        if (startPort == null)
            return;

        Point p = e.getPoint();         // 取得滑鼠放開的 X, Y 座標
        Port endPort = null;            // 用來記住使用者最後放開滑鼠停留的那個小黑點（Port）是誰。

        // 尋找放開的位置是否在某個 Port 範圍內 (低耦合改進：使用多態調用，不用 instanceof)
        for (Shape s : canvas.getShapes()) {
            Port port = s.getPortAt(p);  // 相信每個 Shape 的 getPortAt() 實作
            if (port != null) {
                endPort = port;         // 抓到終點了
                break;
            }
        }

        // 檢查是否有終點，以及起點和終點是否屬於同一個圖形（不允許自己連自己）
        // 1. 必須有終點 (endPort != null)
        // 2. 起點跟終點不能屬於同一個圖形 (不能自己連自己)
        if (endPort != null && startPort.getParent() != endPort.getParent()) {
            // 符合所有條件，建立連線！
            // 傳進去的是 startPort 和 endPort 這兩個「物件」，而不是「死座標」！
            // 也可以這樣寫：
            //ConnectionLine connectionline = new ConnectionLine(startPort, endPort, linkType);
            //canvas.addShape(connectionline);
            canvas.addShape(new ConnectionLine(startPort, endPort, linkType));
            canvas.repaint();       // 畫出連線給使用者看到
        }

        // 拖曳結束，清空起點
        startPort = null;

        // 狀態清理: 拉線結束，把所有圖形的 hovered 狀態清空！
        for (Shape s : canvas.getShapes()) {
            s.setHovered(false);
        }
        hoveredShape = null;        // 重置懸停快取，避免下次 mouseMoved 判斷失準
        // 申請最後一次重畫，確保畫面乾淨
        canvas.repaint();

        // 一次性動作完成：請畫布自動回到預設模式 (Select)
        // (Canvas 內部會切換模式並發出事件，讓 UMLEditor 把按鈕高亮切回 Select)
        canvas.returnToDefaultMode();
    }

    @Override
    public void draw(Graphics g) {
        // 同時檢查起點Port和源圖形，確保必須從有效的Port開始拖線
        // 如果startPort為null，代表沒有點到任何Port，不應該顯示預覽線
        if (startPort != null && sourceShape != null) {
            // 1. 設定畫筆顏色 (用黑色代表這是一條暫時的預覽線)
            g.setColor(Color.BLACK);

            // 2. 把起點和目前的滑鼠座標連起來！
            g.drawLine(startX, startY, currentX, currentY);
        }
    }
}