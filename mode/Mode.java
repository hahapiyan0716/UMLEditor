package mode;

import java.awt.*;
import java.awt.event.*;
import ui.Canvas;

/* =========================================
 * 狀態模式 (State Pattern)
 * =========================================
*/

// 當使用者在畫面上點擊滑鼠時，程式到底該畫圖、拉線，還是選取物件？
public abstract class Mode {
    // protected: 繼承它的子類別（例如 SelectMode）就可以直接拿 canvas 這個變數來用（例如呼叫 canvas.repaint()），而不用一直寫 getter。
    protected Canvas canvas;
    // 建構子：在主程式寫下 new SelectMode(canvas) 時，這塊畫布的記憶體位址就會被傳進來，從此這個 Mode 就跟這塊畫布綁定了！
    // 之後的所有都會在這個畫布上進行
    public Mode(Canvas c) {
        this.canvas = c;
    }
    // MouseEvent 用來包含所有滑鼠的事件
    // MouseEvent e 想像成一份「案發現場報告」。
    // 當使用者點下或移動滑鼠的那一瞬間，Java 系統會立刻寫好這份報告，然後把它塞給你的監聽器（例如 mousePressed(MouseEvent e)）。
    public void mousePressed(MouseEvent e) {}
    public void mouseDragged(MouseEvent e) {}
    public void mouseReleased(MouseEvent e) {}
    // 處理滑鼠懸停移動
    public void mouseMoved(MouseEvent e) {}

    // 允許 Mode 在畫布上畫出自己專屬的暫時圖形 (例如拖曳的選取框)
    public void draw(Graphics g) {}
}