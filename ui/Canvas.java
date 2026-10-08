package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mode.*;
import shape.CompositeObject;
import shape.Shape;

/* =========================================
 * 畫布系統: 是系統的樞紐。它不負責具體的繪圖邏輯，也不負責判斷滑鼠行為。
它只做兩件事：
    * 維護一個清單 List<Shape>（記住畫布上有哪些演員）。
    * 把收到的滑鼠事件，「盲目地」轉交給現在的大腦（currentMode）去處理。
 * ========================================= */
public class Canvas extends JPanel {
    // ==========================================
    // z-order (圖層順序) 的唯一真理就是這個清單的順序：
    //   index 越小 → 越早繪製 → 越底層
    //   index 越大 → 越晚繪製 → 越上層
    // 不再使用 depth 欄位，所有疊放順序都由清單位置決定。
    // ==========================================
    private List<Shape> shapes = new ArrayList<>();
    private Mode currentMode;

    // 「暫時浮起」機制：floatingShape 記住目前被浮到最上層的物件，以及它浮起前的原始位置(floatingSavedIndex)，以便還原
    private Shape floatingShape = null;
    private int floatingSavedIndex = -1;

    // ==========================================
    // 模式管理 (預設模式 + 模式變更事件)
    // ==========================================
    // defaultMode：預設模式 (= SelectMode)。畫圖/連線這類「一次性動作」做完後會自動回到這個模式。
    private Mode defaultMode;
    // modeListener：模式變更的監聽者 (由 UMLEditor 註冊)。每次切換模式就通知它去更新按鈕高亮。
    private ModeChangeListener modeListener;

    public Canvas() {
        setBackground(Color.WHITE);
        // 可以不用特別宣告目前模式，因為 UMLEditor 一開始會強制設為 Select 模式

        // MouseAdapter 是一個抽象類別（abstract class）
        // 已經實作了 MouseListener、MouseMotionListener、MouseWheelListener 的所有方法，並且這些方法預設是「空實作」。
        // 可以繼承 MouseAdapter，只覆寫自己需要的那些方法，例如只覆寫 mousePressed、mouseDragged。
        // 其他不需要的事件方法就不用寫了，避免了直接 implements MouseListener 時必須把所有方法都寫出來的麻煩。
        // 這個 MouseAdapter 是「轉發站 (dispatcher)」：
        // Canvas 自己「不解讀」e 的內容（不讀座標），只是把整個 MouseEvent e 原封不動轉交給 currentMode。
        // e 並沒有被丟掉——它的座標等資訊會在 currentMode 那邊才真正被用到
        // （例如 SelectMode.mousePressed 裡的 startPoint = e.getPoint()）。
        // 這就是 State Pattern：Canvas 當總機只負責轉接，mode 才是真正處理事件、拆開 e 來用的人。
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e)  { currentMode.mousePressed(e); }   // 按下 → 轉交給 mode
            @Override
            public void mouseDragged(MouseEvent e)  { currentMode.mouseDragged(e); }   // 拖曳 → 轉交給 mode
            @Override
            public void mouseReleased(MouseEvent e) { currentMode.mouseReleased(e); }  // 放開 → 轉交給 mode
            @Override
            public void mouseMoved(MouseEvent e)    { currentMode.mouseMoved(e); }     // 移動 → 轉交給 mode
        };

        // 加入監聽器以偵測行為
        // MouseListener (靜態與瞬間的動作)
        // 負責監聽：Pressed (按下)、Released (放開)、Clicked (點擊)、Entered (游標進入元件)、Exited (游標離開元件)。
        // 特徵：這些都是「發生在瞬間」或「座標不需要連續改變」的動作。
        addMouseListener(ma);
        // MouseMotionListener (動態與連續的動作)
        // 負責監聽：Dragged (按住拖曳)、Moved (純粹移動)。
        // 特徵：這些動作會產生「大量的連續座標變化」。
        addMouseMotionListener(ma);
    }
    /*
    MouseListener 是一個介面（interface）。
    定義了 5 個方法：mouseClicked, mousePressed, mouseReleased, mouseEntered, mouseExited。
    直接 implements 時必須把這 5 個全部寫出來；
    所以才有 MouseAdapter（抽象類別）幫你把這些方法空實作好，讓你只覆寫需要的那幾個。
    */
    /*
    Listener / Adapter 本身「就是」監聽器（監聽事件的物件）：
      - Listener 是介面（契約，規定要有哪些方法）
      - Adapter 是把該介面方法全部空實作的類別（方便只覆寫需要的）
    add...Listener() 只負責「註冊」：把監聽器登記到元件上。
    註冊之後，是由元件 / Swing 系統偵測到行為（按下、拖曳…），才回頭呼叫這個監聽器對應的方法。
    （也就是「偵測」是系統做的，addXxxListener 只做「登記」這一步）
    */

    // 設定目前模式，是畫圖形、畫連結...等
    // 重點：所有模式切換都會經過這裡，並在切換後「廣播」模式變更事件，
    //       這樣 UMLEditor 只要監聽一次，就能統一處理按鈕高亮 (不管是使用者點按鈕、還是畫完自動回 Select)。
    public void setCurrentMode(Mode mode) {
        this.currentMode = mode;
        // 通知監聽者 (UMLEditor)：模式換了，請更新按鈕高亮
        // 也就是廣播給所有監聽者：嘿！我剛剛切換到 newMode 了，
        // 你們要不要跟著更新一下？
        // （例如 UMLEditor 就會收到這個事件，然後去更新工具按鈕的高亮）
        if (modeListener != null) {
            modeListener.onModeChanged(mode);
        }
    }

    // 註冊模式變更的監聽者 (UMLEditor 在啟動時會呼叫，把「更新按鈕」的邏輯掛進來)
    // 被觀察者 (Canvas) 提供一個管道(方法)，讓觀察者 (UMLEditor) 可以登記自己進來，
    // 當事件發生時，Canvas 就會回呼這個管道，通知 UMLEditor 去更新按鈕高亮。
    public void setModeChangeListener(ModeChangeListener listener) {
        this.modeListener = listener;
    }

    // 設定預設模式 (= SelectMode)，並立刻切換到它 (順帶觸發一次事件，讓 Select 按鈕初始就亮起)
    public void setDefaultMode(Mode mode) {
        this.defaultMode = mode;
        setCurrentMode(mode);
    }

    // 一次性動作 (畫圖/連線) 完成後呼叫：自動回到預設模式 (Select)
    // 語意直覺，取代舊的 resetToSelectMode_Notify()
    public void returnToDefaultMode() {
        setCurrentMode(defaultMode);
    }

    // 管理畫布的內部清單資料
    // 依圖形自己回報的分層，決定加到清單的底層或上層 (用多型，不用 instanceof)
    public void addShape(Shape s) {
        if (s.isBackgroundLayer()) {
            shapes.add(0, s);   // 背景層 (連線)：放到清單最前面 → 最先畫 → 在最底層
        } else {
            shapes.add(s);      // 一般物件：放到清單尾端 → 最後畫 → 在最上層
        }
    }

    // 強制呼叫 s.setSelected(false)，確保畫面上沒有任何一個物件處於被選取（顯示控制點或反白）的狀態。
    public void clearSelection() {
        for (Shape s : shapes) {
            s.setSelected(false);
        }
    }

    // 讓外部可以「唯讀」取得圖形清單
    // 回傳不可修改的檢視，避免外部直接 add/remove 破壞 z-order 的封裝
    // (z-order 的調整一律走 bringToFront / floatToFront / restoreFloating 這些方法)
    public List<Shape> getShapes() {
        return Collections.unmodifiableList(shapes);
    }

    // ==========================================
    // z-order 操作 (全部收進 Canvas，外部只呼叫，不直接動清單)
    // shapes 清單的順序就是圖層順序，所以所有調整圖層的操作都必須透過這裡的方法來完成，確保封裝性與一致性。
    // shapes = [A, B, C, D]
    //           ↑        ↑
    //      index 0     index 3
    //        最先畫       最後畫
    //        最底層       最上層
    // ==========================================

    // 永久把指定圖形移到清單末端 → 視覺最上層 (可供未來「Bring to Front」選單使用)
    public void bringToFront(Shape s) {
        shapes.remove(s);
        shapes.add(s);
    }

    // 暫時把某物件浮到最上層，並記住它原本的位置以便之後還原
    // (先還原上一個浮起的物件，確保同時只有一個處於浮起狀態)
    public void floatToFront(Shape s) {
        restoreFloating();                          // 先把上一個浮起的物件還原回原位，確保同時只有一個處於浮起狀態
        // 背景層 (連線) 不浮起：連線現在可以被點選了，但它必須永遠待在物件底下，
        // 否則選取一條線時，它會暫時蓋到物件上面，破壞「連線永遠在底層」的規則。
        if (s.isBackgroundLayer()) {
            return;
        }
        floatingSavedIndex = shapes.indexOf(s);     // 記住現在被點選的物件原本在清單中的位置，以便之後還原
        floatingShape = s;                          // 記住現在被點選的物件，以便之後還原
        bringToFront(s);                            // 把它永久移到最上層 (清單末端)，讓它暫時浮起來
    }

    // 把先前浮起的物件還原回它原本的層數
    public void restoreFloating() {
        if (floatingShape != null) {        // 如果有物件正在浮起，才要做這件事
            // 只有當浮起的物件「仍在清單中」時才還原位置
            // .remove() 回傳值是 boolean，表示是否成功移除
            // 如果扶起的物件還在清單中，就會回傳 true，這時浮起的物件被從清單中移除，然後我們就會把它加回去原本的位置
            // 如果已經不在清單中，例如在浮起期間被「群組」收編，已經從畫布清單移走了，這時會回傳 false，就不做任何事。
            // （否則 C 會憑空在畫布上多出來一個，變成 bug），只清掉浮起記憶即可。
            if (shapes.remove(floatingShape)) {
                // 把它加回原本的位置。注意：如果原本的位置超過了目前清單的末端（例如在浮起後被刪除了），就直接加到末端。
                shapes.add(Math.min(floatingSavedIndex, shapes.size()), floatingShape);
            }
            floatingShape = null;       // 清掉浮起記憶
            floatingSavedIndex = -1;    // 重設浮起位置記憶 (雖然這行不是必要的，但寫了比較乾淨)
        }
    }

    // 當滑鼠點擊畫布時，找出位於「最上層」且被點擊到的圖形。
    public Shape getTopShapeAt(Point p) {
        // z-order 即清單順序：從清單尾端往前找，第一個命中的就是最上層的圖形
        for (int i = shapes.size() - 1; i >= 0; i--) {
            Shape s = shapes.get(i);
            if (s.contains(p)) {
                return s;
            }
        }
        return null;
    }

    /*
    這個方法由 Java 底層系統（Event Dispatch Thread, EDT）在特定時機自動呼叫的。
    1. 視窗初次亮相 (Initial Display)
        當你的程式剛啟動，主視窗（JFrame）被設定為可見（setVisible(true)），且畫布被加進視窗並顯示在螢幕上的那個瞬間，系統會自動呼叫它來畫出第一眼看到的初始畫面。
    2. 作業系統被動觸發 (OS Triggers)
        * 當使用者的操作導致畫面發生變化，需要「填補空白」或「重新整理」時，作業系統會強制通知 Java 系統去呼叫這個方法。常見的情境包含：
        * 使用者用滑鼠拖曳改變了視窗的長寬大小。
        * 你的視窗原本被另一個視窗（例如瀏覽器或資料夾）遮蓋住，然後你把上層的視窗移開，讓畫布重新露出來時。
        * 視窗從「最小化」狀態恢復成原狀時。
    3. 程式設計師「間接」要求 repaint()，呼叫 repaint()，但其並沒有親自下去畫圖，
       只是告訴 Java 系統：「嘿！我剛剛改了底層的資料（例如加了新圖形、改了圖形顏色），
       現在的畫面已經過期了，請你盡快安排重畫！」
    * repaint() 只有 Canvas 才有，所以在自己裡面就不用特別 canvas.repaint()，直接 repaint()
       但是在外部函式要重畫布的話就要呼叫 canvas.repaint()，讓畫布重畫，這時候就會呼叫 paintComponent()
    */
    @Override
    protected void paintComponent(Graphics g) {
        // 呼叫父類別的方法來繪製畫布，也就是清空畫布，要求作業系統將整個面板塗上預設的背景色
        super.paintComponent(g);

        // z-order 即清單順序：直接按清單先後繪製 (前面的先畫=底層，後面的後畫=上層)
        // 不需要每幀排序，順序在 addShape / bringToFront / floatToFront 時就已維護好
        // 這邊畫的是其他圖形（連線、類別框、選取框...），但不包含目前模式的暫時性 UI（例如建立圖形時的預覽虛線、框選多個圖形時的半透明選取框）
        // ，這些暫時性 UI 由 currentMode.draw(g) 負責在最上層繪製。
        drawAllShapes(g);

        // 讓目前的 Mode 可以在最上層畫出暫時的 UI (例如選取框，或是圖形示意圖)
        // 負責處理非持久性的視覺元素。例如建立圖形時的「灰色預覽虛線」，或是框選多個圖形時的「半透明選取框」，這些元素不屬於常駐的形狀清單，而是屬於目前操作模式 (Mode) 的暫時性 UI。
        // 這段程式碼被安排在常規圖形繪製完畢之後才執行，所以這些預覽與選取框絕對會被繪製在所有圖形的最上層，不會被任何物件遮擋，確保了最清晰的操作視覺回饋。
        if (currentMode != null) {
            currentMode.draw(g);
        }
    }

    // ==========================================
    // 畫出所有圖形 (螢幕繪製 paintComponent 與匯出 renderToImage 共用，確保兩邊畫出來的結果一致)
    // 分兩輪：
    //   第一輪 draw()       ：依清單順序 (z-order) 畫出每個圖形的本體 —— 連線線身在最底層，物件疊在上面
    //   第二輪 drawOverlay()：所有本體都畫完後，再畫前景層 (例如連線的箭頭)，保證不會被任何物件蓋住
    // ==========================================
    private void drawAllShapes(Graphics g) {
        for (Shape s : shapes) {
            s.draw(g);
        }
        for (Shape s : shapes) {
            s.drawOverlay(g);
        }
    }

    // ==========================================
    // Group (群組)的動作
    // ==========================================
    public void groupSelected() {
        // 先把畫布上所有被「選取」的圖形找出來
        List<Shape> selectedShapes = new ArrayList<>();

        for (Shape s : shapes) {
            if (s.isSelected()) {
                // 連線 (背景層) 不收進群組：
                // 框選時，兩端都落在選取框內的連線也會被選取；若把連線收進群組，
                // CompositeObject.updateBounds() 會讀到連線沒有意義的 x/y/width/height，算出錯誤的群組外框。
                // 連線本來就綁在 Port 上，物件被群組後一起移動時，線自然會跟著走，不需要被群組管理。
                if (s.isBackgroundLayer()) {
                    s.setSelected(false);
                    continue;
                }
                selectedShapes.add(s);
            }
        }

        // 判斷選取的數量是否 >= 2
        if (selectedShapes.size() >= 2) {
            CompositeObject composite = new CompositeObject();

            for (Shape s : selectedShapes) {
                s.setSelected(false);         // 把單獨圖形的選取狀態拔掉
                composite.addComponent(s);      // 把群組的圖形收編進群組裡
                shapes.remove(s);               // 從畫布的總清單中剔除（因為他們現在歸群組管了）
            }

            // 把新建立的群組設定為選取狀態，並加回畫布中
            composite.setSelected(true);
            shapes.add(composite);

            // 更新畫面
            repaint();
        }
        // 如果選取的物件數量 < 2，什麼事都不會發生
    }

    // ==========================================
    // Ungroup (解散群組)的動作
    // ==========================================
    public void ungroupSelected() {
        List<Shape> selectedShapes = new ArrayList<>();

        // 先把選取的物件找出來
        for (Shape s : shapes) {
            if (s.isSelected()) {
                selectedShapes.add(s);
            }
        }

        //  選取到的物件數量必須「剛好是 1」，而且這個物件必須「是群組」(用多型 isGroup，不用 instanceof)
        if (selectedShapes.size() == 1 && selectedShapes.get(0).isGroup()) {
            Shape composite = selectedShapes.get(0);

            shapes.remove(composite);       // 把群組從畫布上消滅

            // 解構最外層：把群組肚子裡的小弟全部放回畫布上 (getComponents 走多型，不用轉型)
            for (Shape child : composite.getComponents()) {
                shapes.add(child);
            }

            // 更新畫面
            repaint();
        }
        // 如果選取數量不對，或是選到的不是群組，則什麼事都不會發生
    }

    // ==========================================
    // Delete (刪除)的動作：刪除所有被選取的圖形，並連帶刪除依附在它們身上的連線
    // ==========================================
    public void deleteSelected() {
        // 先把「暫時浮起」的物件還原回原本的圖層。
        // 如果浮起的物件等一下會被刪除，restoreFloating 之後它也會跟著被刪；
        // 若沒被刪除，則它回到原位，避免刪除後 floatingSavedIndex 指到錯誤的位置。
        restoreFloating();

        // 1. 找出所有被選取的「頂層」圖形 (畫布清單裡直接看得到的那一層)
        List<Shape> selectedShapes = new ArrayList<>();
        for (Shape s : shapes) {
            if (s.isSelected()) {
                selectedShapes.add(s);
            }
        }
        // 沒有選取任何東西就直接結束，連 repaint 都省下
        if (selectedShapes.isEmpty()) {
            return;
        }

        // 2. 收集「所有會消失的圖形」，包含群組裡面的子孫
        // 為什麼要遞迴？因為連線的 Port 屬於群組「裡面」的 BasicObject，而不是群組本身。
        // 例如：A、B 被群組成 G，C 連到 A。刪除 G 時，A 也跟著消失，C→A 的線必須一起刪掉，
        // 所以集合裡要有 A，才能讓 C→A 那條線的 isAttachedToAny() 回傳 true。
        // 用 HashSet：contains() 查詢是 O(1)，比 List 的 O(n) 快。
        Set<Shape> removedShapes = new HashSet<>();
        for (Shape s : selectedShapes) {
            collectWithDescendants(s, removedShapes);
        }

        // 3. 從畫布移除被選取的圖形
        shapes.removeAll(selectedShapes);

        // 4. 移除「依附在被刪除圖形上」的連線，避免留下兩端懸空的線
        // removeIf：Java 8 提供的集合方法，傳入一個判斷條件 (Predicate)，符合條件的元素會被移除。
        // 這裡用 Lambda 寫判斷條件：「這個圖形是否依附在被刪除的圖形上？」(多型查詢，不用 instanceof)
        shapes.removeIf(s -> s.isAttachedToAny(removedShapes));

        repaint();
    }

    // ==========================================
    // 讀檔：用檔案讀回來的圖形清單，整個取代目前畫布上的內容
    // ==========================================
    public void replaceAllShapes(List<Shape> newShapes) {
        shapes.clear();
        shapes.addAll(newShapes);   // 檔案裡的清單順序就是存檔當時的 z-order，直接照順序放回即可

        // 清掉「浮起」記憶：舊的 floatingShape 已經不在畫布上了，留著會讓 restoreFloating 誤判
        floatingShape = null;
        floatingSavedIndex = -1;

        // 存檔當下可能有物件正被選取或懸停，這些狀態也被一起寫進了檔案。
        // 讀回來時統一清除 (包含群組裡的子圖形)，畫面才會是乾淨的初始狀態。
        for (Shape s : allShapesIncludingDescendants()) {
            s.setSelected(false);
            s.setHovered(false);
        }
        repaint();
    }

    // ==========================================
    // 匯出：把畫布上的圖形畫進一張記憶體中的影像 (BufferedImage)，供 DiagramFileIO 寫成 PNG
    // ==========================================
    public BufferedImage renderToImage() {
        // 影像大小：至少是目前畫布的大小；若有圖形被拖到畫布可視範圍之外，就把影像往右/下延伸，確保整張圖都匯出
        int imageWidth = Math.max(getWidth(), 1);
        int imageHeight = Math.max(getHeight(), 1);
        final int margin = 20;      // 圖形最外緣到影像邊界的留白
        for (Shape s : shapes) {
            Rectangle b = s.getBounds();
            imageWidth = Math.max(imageWidth, b.x + b.width + margin);
            imageHeight = Math.max(imageHeight, b.y + b.height + margin);
        }

        // BufferedImage：一張存在記憶體裡的點陣圖，TYPE_INT_RGB 代表每個像素用 RGB 三色表示 (不含透明度)
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        // createGraphics()：取得「在這張影像上作畫」的畫筆。
        // 這是同一套 Graphics API，所以 Shape.draw(g) 完全不用修改，就能畫進影像裡而不是螢幕上——這就是抽象化的威力。
        Graphics2D g2 = image.createGraphics();
        try {
            // 先塗滿白色背景 (新建的 BufferedImage 預設是全黑)
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, imageWidth, imageHeight);

            // 匯出的圖片不應該出現「控制點 (Port)」與「選取高亮」，所以暫時清除選取/懸停狀態，畫完再還原。
            // 先記住誰原本是選取/懸停中，才能還原使用者的操作狀態。
            List<Shape> all = allShapesIncludingDescendants();
            List<Shape> wasSelected = new ArrayList<>();
            List<Shape> wasHovered = new ArrayList<>();
            for (Shape s : all) {
                if (s.isSelected()) wasSelected.add(s);
                if (s.isHovered()) wasHovered.add(s);
                s.setSelected(false);
                s.setHovered(false);
            }

            // 只畫圖形本身，不呼叫 currentMode.draw()，所以預覽虛線、框選框這類暫時性 UI 不會被匯出
            drawAllShapes(g2);

            // 還原狀態
            for (Shape s : wasSelected) s.setSelected(true);
            for (Shape s : wasHovered) s.setHovered(true);
        } finally {
            // 用完一定要 dispose() 釋放畫筆佔用的系統資源。
            // (paintComponent 的 g 是 Swing 借給我們的，不需要也不應該 dispose；但這支是我們自己 create 的，要自己收拾)
            g2.dispose();
        }
        return image;
    }

    // 取得畫布上「所有」圖形，包含群組裡層層巢狀的子圖形
    private List<Shape> allShapesIncludingDescendants() {
        Set<Shape> result = new HashSet<>();
        for (Shape s : shapes) {
            collectWithDescendants(s, result);
        }
        return new ArrayList<>(result);
    }

    // 遞迴收集：把自己，以及自己肚子裡的所有子圖形 (子群組的子圖形也算)，全部放進 result
    // 一般圖形的 getComponents() 回傳空清單，遞迴自然就停下來了 —— 這正是 Composite Pattern 的透明性帶來的好處。
    private void collectWithDescendants(Shape s, Set<Shape> result) {
        result.add(s);
        for (Shape child : s.getComponents()) {
            collectWithDescendants(child, result);
        }
    }

}