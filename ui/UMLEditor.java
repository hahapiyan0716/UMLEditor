package ui;

import mode.*;
import shape.*;
import javax.swing.*;           // 表示導入 Swing 庫中的所有類別和方法
                                // Swing 是 Java 的 GUI 工具包，用於創建圖形用戶界面
import java.awt.*;              // AWT 是 Java 的抽象窗口工具包，提供了 GUI 組件和事件處理的基礎功能
                                // 視覺與排版工具箱，Graphics (畫筆)：當你在 BasicObject 裡面寫 g.drawRect(...) 或 g.fillOval(...) 時，這個 g 就是 AWT 借給你的畫筆。
                                // Color (顏料)：設定按鈕變成 Color.DARK_GRAY，或是畫出黑色的框線。
                                // Point (座標)：紀錄滑鼠目前停在畫面上的哪個 X, Y 座標位置。
                                // BorderLayout / GridLayout (空間設計師)：幫你把工具列整齊地排在左邊，把畫布放在正中央。
import java.awt.event.*;        // 導入 AWT 事件處理庫，包含了處理用戶交互事件的類別和接口(神經與動作感測系統)
                                // MouseEvent (滑鼠事件)：當你在畫布上按下滑鼠 (mousePressed)、拖曳 (mouseDragged)、放開 (mouseReleased) 時，這個庫會把「你點了左鍵還是右鍵、點在哪個座標點」打包成一個包裹（就是你程式碼裡的那個 e）傳送給系統。
                                // ActionListener (按鈕傾聽器)：當你在選單點擊 "Group" 或點擊 "Select" 按鈕時，負責聽到那聲「喀噠」點擊聲，並觸發後續的切換模式或打包群組動作。
import javax.swing.filechooser.FileNameExtensionFilter;    // 檔案對話框的副檔名過濾器 (只顯示 .uml / .png)
import java.io.File;            // File：代表硬碟上的一個檔案路徑
import java.io.IOException;     // IOException：讀寫檔案失敗時丟出的例外
import java.util.Map;           // Map：用來建立「模式 → 按鈕」的對照表
import java.util.LinkedHashMap; // LinkedHashMap：保留按鈕的加入順序 (Select, Association, ... Oval)


/*UML Editor 是整個系統的入口。負責擺放按鈕、選單，並把 Canvas 放在正中央。
核心職責只有一個：「當使用者點擊按鈕時，幫 Canvas 換上對應的大腦（Mode），並改變按鈕的顏色」。
*/

// 先寫出整個應用程式的主框架，包含 UMLEditor 類和 Canvas 類，然後再逐步實作各個功能 (Use Cases)。
// 定義 UMLEditor 類，繼承自 JFrame，表示這是一個圖形用戶界面窗口，代表它就是我們執行程式時看到的那個「大視窗」。
// JFrame 是 Swing 中的一個頂級容器，用於創建應用程序的主窗口。
// 它提供了標題欄、邊框和關閉按鈕等基本功能，並且可以包含其他 Swing 組件，如按鈕、面板等。
// 在這裡，我們將在 UMLEditor 中添加一個 Canvas(畫布) 作為繪圖區域，以及一些工具按鈕和菜單來實現 UML 編輯器的功能。
public class UMLEditor extends JFrame {
    private Canvas canvas;
    // 「模式 → 對應的工具按鈕」對照表。
    // 有了它，當 Canvas 廣播「模式換成 X」時，UMLEditor 就能立刻找到 X 對應的按鈕並高亮。
    private Map<Mode, JButton> modeButtons = new LinkedHashMap<>();

    // 按鈕樣式常數
    private static final Color BTN_INACTIVE_BG = Color.WHITE;
    private static final Color BTN_INACTIVE_FG = Color.BLACK;
    private static final Color BTN_ACTIVE_BG = Color.BLACK;
    private static final Color BTN_ACTIVE_FG = Color.WHITE;

    // UMLEditor 的建構子(初始化)
    public UMLEditor() {
        // 設定窗口標題為 "UML Editor"，setTitle 是 JFrame 的方法，用於設置窗口的標題欄文字。
        setTitle("UML Editor");
        // 設定窗口大小為 800x600，setSize 是 JFrame 的方法，用於設置窗口的大小。
        setSize(800, 600);
        // 設定當用戶關閉窗口時，程序將退出，EXIT_ON_CLOSE 是 JFrame 的一個常量，表示當窗口被關閉時應該終止程序。
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
         // 設定窗口的佈局管理器為 BorderLayout，這個佈局可以將主視窗分為五個區域：North、South、East、West 和 Center。
        setLayout(new BorderLayout());

        // 建立畫布，Canvas 是一個自定義的類別，繼承自 JPanel，用於作為繪圖區域，它將佔據視窗剩餘的最大空間，負責繪製所有圖形與處理滑鼠事件。
        canvas = new Canvas();

        // 註冊「模式變更」監聽器：
        // 只要 Canvas 切換模式 (不論是使用者點按鈕、還是畫完自動回 Select)，
        // 都會回呼到 highlightActiveButton()，由它統一負責「把對應的按鈕高亮」。
        // 觀察者必須在建立按鈕之前先註冊，這樣等一下 setDefaultMode 觸發事件時，highlight 才能正常運作。
        // Observer（UMLEditor）負責「呼叫」這個管道，把自己登記進去，當事件發生時，Canvas 就會「回呼」這個管道，通知 UMLEditor 去更新按鈕高亮。
        canvas.setModeChangeListener(this::highlightActiveButton);
        // 等價上面的方法參考寫法，Lambda 版本更簡潔：
        //canvas.setModeChangeListener((Mode newMode) -> { highlightActiveButton(newMode); });

        // 將畫布添加到窗口的中心區域，BorderLayout.CENTER 表示將組件放置在 BorderLayout 的中心位置。
        add(canvas, BorderLayout.CENTER);

        // 建立左側工具列
        JPanel toolPanel = new JPanel();
        // 設定其佈局為 GridLayout(6, 1, 5, 5)。這代表面板會被強制劃分為 6 row 1 col 的網格，後面的兩個 5 代表網格之間有 5 像素的水平與垂直間距，確保按鈕排列整齊且美觀。
        toolPanel.setLayout(new GridLayout(6, 1, 5, 5));

        // 建立按鈕與對應的 Mode
        // Select 模式要留一個參考，等一下指定它當「預設模式」
        SelectMode selectMode = new SelectMode(canvas);
        addModeButton(toolPanel, "Select", selectMode);
        addModeButton(toolPanel, "Association", new CreateLinkMode(canvas, LinkType.ASSOCIATION));
        addModeButton(toolPanel, "Generalization", new CreateLinkMode(canvas, LinkType.GENERALIZATION));
        addModeButton(toolPanel, "Composition", new CreateLinkMode(canvas, LinkType.COMPOSITION));
        addModeButton(toolPanel, "Rect", new CreateObjectMode(canvas, new RectFactory()));
        addModeButton(toolPanel, "Oval", new CreateObjectMode(canvas, new OvalFactory()));
        // 將工具面板添加到窗口的左側，BorderLayout.WEST 表示將組件放置在 BorderLayout 的西邊位置。
        add(toolPanel, BorderLayout.WEST);

        // 建立上方選單
        // 建立選單列：這是最底層的長條形基座，通常橫跨整個視窗的最上方。
        JMenuBar menuBar = new JMenuBar();
        // 建立主選單：這是放在選單列上面的大分類，例如這裡建立的「File（檔案）」。
        JMenu fileMenu = new JMenu("File");
        // File 選單的三個項目：開啟舊檔、儲存、匯出成 PNG 圖片
        // 快捷鍵用 InputEvent.CTRL_DOWN_MASK 表示「搭配 Ctrl 鍵」，例如 Ctrl+S 存檔。
        JMenuItem openItem = new JMenuItem("Open...");
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        openItem.addActionListener(e -> openDiagram());

        JMenuItem saveItem = new JMenuItem("Save...");
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        saveItem.addActionListener(e -> saveDiagram());

        JMenuItem exportItem = new JMenuItem("Export PNG...");
        exportItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        exportItem.addActionListener(e -> exportPng());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(exportItem);

        // 建立主選單：這是放在選單列上面的大分類，例如這裡建立的「Edit（編輯）」。當你點擊它時，它會往下展開。
        JMenu editMenu = new JMenu("Edit");
        // 建立選單項目：這是真正可以被點擊執行的最小單位。
        // 這裡分別建立了三個項目：「Group（群組）」、「Ungroup（解散群組）」與「Label（標籤）」。
        JMenuItem groupItem = new JMenuItem("Group");
        // 加入監聽器，把事件傳給畫布做群組的動作
        groupItem.addActionListener(e -> canvas.groupSelected());

        JMenuItem ungroupItem = new JMenuItem("Ungroup");
        ungroupItem.addActionListener(e -> canvas.ungroupSelected());

        JMenuItem labelItem = new JMenuItem("Label");
        labelItem.addActionListener(e -> customizeLabel());
        // 注意：每個選單項目的 ActionListener 只能註冊一次。
        // addActionListener 是「累加」而不是「覆蓋」，重複註冊會讓同一次點擊觸發兩次動作。

        JMenuItem deleteItem = new JMenuItem("Delete");
        deleteItem.addActionListener(e -> canvas.deleteSelected());
        // 快捷鍵 (Accelerator)：不必打開選單，直接按鍵盤 Delete 就會觸發這個選單項目。
        // KeyStroke.getKeyStroke(按鍵代碼, 修飾鍵)：VK_DELETE 是 Delete 鍵，0 代表不需要搭配 Ctrl/Shift/Alt。
        // 好處：快捷鍵掛在 JMenuBar 上，只要主視窗有焦點就有效，不需要另外在 Canvas 上寫 KeyListener，
        // 也不用煩惱「Canvas 有沒有拿到鍵盤焦點」的問題；選單上還會自動顯示快捷鍵提示。
        deleteItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));

        // 先把四個小項目（Group, Ungroup, Label, Delete）加入到「Edit」主選單中
        editMenu.add(groupItem);
        editMenu.add(ungroupItem);
        editMenu.add(labelItem);
        editMenu.addSeparator();        // 分隔線：把「刪除」這種破壞性操作與其他項目隔開，避免誤點
        editMenu.add(deleteItem);

        // 再把「File」、「Edit」主選單加入到選單列中
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        // 在 JFrame (主視窗) 中，選單列不像是普通按鈕用 add() 隨便塞，它擁有專屬的 VIP 座位。
        // 呼叫這個方法，系統就會自動把這個選單列完美地鑲嵌在視窗的最頂端。
        // 跟 setTitle ... 一樣，放在主視窗上面
        setJMenuBar(menuBar);

        // 指定 Select 為「預設模式」。
        // setDefaultMode 內部會 setCurrentMode(selectMode) → 觸發模式變更事件
        // → highlightActiveButton(selectMode) → 開機時 Select 按鈕就是亮的。
        canvas.setDefaultMode(selectMode);
    }

    // ========== 共用 UI 工具方法 ==========
    private JButton createBaseButton(String name) {
        // 建立按鈕，JButton 是 Swing 中的一個按鈕組件，name 參數用於設置按鈕上的文字。
        JButton btn = new JButton(name);

        btn.setContentAreaFilled(false);        // 關閉系統預設的點擊/懸停特效 (消滅淺藍色)
        btn.setOpaque(true);             // 設定按鈕為不透明，這樣背景顏色才會顯示出來。
        btn.setBorderPainted(true);             // 繪製出按鈕邊框
        btn.setFocusPainted(false);             // 關閉點擊時文字周圍出現的虛線

        btn.setBackground(BTN_INACTIVE_BG);    // 設定按鈕的背景顏色為白色，這樣在工具面板上會有統一的外觀。
        btn.setForeground(BTN_INACTIVE_FG);    // 設定按鈕的文字顏色為黑色
        // 職責分離：只負責樣式設定，列表管理由呼叫端負責
        return btn;
    }

    // ========== 模式切換按鈕 ==========
    private void addModeButton(JPanel panel, String name, Mode mode) {
        JButton btn = createBaseButton(name);

        // 把「模式 → 按鈕」記進對照表，之後事件回來時才找得到要高亮哪顆按鈕
        modeButtons.put(mode, btn);

        // 按鈕被點擊時：只要叫 Canvas 切換模式即可。
        // 「按鈕變色」不在這裡做 —— 切換模式會觸發 ModeChangeListener 事件，
        // 由 highlightActiveButton() 統一處理高亮。這樣「使用者點按鈕」和「畫完自動回 Select」
        // 兩條路徑共用同一段高亮邏輯，不再重複。
        // e 是 ActionEvent 事件對象，代表這次點擊事件的相關資訊，例如是哪個按鈕被點擊了，
        // 但在這裡我們不需要使用 e，因為這裡的事件處理非常簡單，只需要切換模式，不需要額外的邏輯，
        // 所以使用 Lambda 表達式可以讓代碼更簡潔易讀。
        btn.addActionListener(e -> canvas.setCurrentMode(mode));

        panel.add(btn);
    }

    // ==========================================
    // 模式變更事件的處理：把目前模式對應的按鈕高亮，其餘恢復原色
    // (這是 setModeChangeListener 註冊進 Canvas 的回呼，所有模式切換最後都會走到這裡)
    // ==========================================
    private void highlightActiveButton(Mode activeMode) {
        // 先把所有按鈕恢復成「未選取」樣式 (白底黑字)
        for (JButton b : modeButtons.values()) {
            b.setBackground(BTN_INACTIVE_BG);
            b.setForeground(BTN_INACTIVE_FG);
        }
        // 再把目前模式對應的按鈕高亮成「選取中」樣式 (黑底白字)
        JButton active = modeButtons.get(activeMode);
        if (active != null) {
            active.setBackground(BTN_ACTIVE_BG);
            active.setForeground(BTN_ACTIVE_FG);
        }
    }

    // ==========================================
    // File 選單：開啟 / 儲存 / 匯出
    // (檔案對話框、錯誤訊息屬於 UI 職責，放在這裡；真正的讀寫交給 DiagramFileIO)
    // ==========================================

    // 記住上次使用的檔案，下次開啟對話框時會直接停在同一個資料夾，並預填檔名
    private File lastFile = null;

    private void saveDiagram() {
        File file = chooseFile(true, "UML Diagram (*.uml)", DiagramFileIO.DIAGRAM_EXTENSION);
        if (file == null)
            return;     // 使用者取消
        try {
            DiagramFileIO.save(file, canvas.getShapes());
            lastFile = file;
        } catch (IOException ex) {
            showError("存檔失敗", ex);
        }
    }

    private void openDiagram() {
        File file = chooseFile(false, "UML Diagram (*.uml)", DiagramFileIO.DIAGRAM_EXTENSION);
        if (file == null)
            return;
        try {
            canvas.replaceAllShapes(DiagramFileIO.load(file));
            lastFile = file;
        } catch (IOException | ClassNotFoundException ex) {
            // multi-catch：一個 catch 同時接住兩種例外，處理方式相同時可避免重複程式碼
            // 常見原因：檔案不是本程式存的、檔案損毀、或是包含白名單以外的類別而被拒絕
            showError("讀檔失敗（檔案格式不符或已損毀）", ex);
        }
    }

    private void exportPng() {
        File file = chooseFile(true, "PNG Image (*.png)", DiagramFileIO.IMAGE_EXTENSION);
        if (file == null)
            return;
        try {
            DiagramFileIO.exportPng(file, canvas.renderToImage());
        } catch (IOException ex) {
            showError("匯出失敗", ex);
        }
    }

    // 共用的檔案選擇流程
    // forSave：true = 儲存對話框 (會補副檔名、確認覆寫)；false = 開啟對話框
    // 回傳使用者選的檔案；取消時回傳 null
    private File chooseFile(boolean forSave, String description, String extension) {
        JFileChooser chooser = new JFileChooser();
        // 只顯示指定副檔名的檔案，讓使用者不會選到無關的檔案
        chooser.setFileFilter(new FileNameExtensionFilter(description, extension));
        if (lastFile != null) {
            chooser.setCurrentDirectory(lastFile.getParentFile());
        }

        // showSaveDialog / showOpenDialog 是「模態 (modal) 對話框」：
        // 呼叫後程式會停在這一行，直到使用者按下確定或取消，才回傳結果。
        int result = forSave ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION)
            return null;

        File file = chooser.getSelectedFile();
        if (forSave) {
            // 使用者只輸入「diagram」時自動補成「diagram.uml」，避免存出沒有副檔名的檔案
            if (!file.getName().toLowerCase().endsWith("." + extension)) {
                file = new File(file.getParentFile(), file.getName() + "." + extension);
            }
            // 檔案已存在 → 先確認是否覆寫，避免不小心蓋掉舊檔
            if (file.exists()) {
                int overwrite = JOptionPane.showConfirmDialog(
                    this, "「" + file.getName() + "」已存在，要覆寫嗎？", "確認覆寫",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                );
                if (overwrite != JOptionPane.YES_OPTION)
                    return null;
            }
        }
        return file;
    }

    // 統一的錯誤訊息視窗
    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    // ==========================================
    // Customize Label Style: 修改標籤文字與顏色
    // (對話框屬於 UI 層職責，從 Canvas 搬到這裡，讓 Canvas 專心管圖形與繪圖)
    // ==========================================
    private void customizeLabel() {
        // 找出目前被選取、且「可自訂標籤」的圖形 (用多型 canCustomizeLabel，不用 instanceof)
        shape.Shape target = null;
        for (shape.Shape s : canvas.getShapes()) {
            if (s.isSelected() && s.canCustomizeLabel()) {
                target = s;
                break;      // 只針對單一物件處理
            }
        }
        // 沒有符合條件的物件就直接結束
        if (target == null)
            return;

        // 名稱輸入框
        JTextField nameField = new JTextField(target.getLabel(), 10);

        // 顏色按鈕：顯示目前顏色，點擊開啟調色盤
        JButton colorButton = new JButton();
        colorButton.setBackground(target.getColor());
        colorButton.setOpaque(true);
        colorButton.setBorderPainted(false);

        // 用長度 1 的陣列讓 Lambda 內可以改值 (pass by reference)
        Color[] selectedColor = { target.getColor() };
        colorButton.addActionListener(ev -> {
            Color newColor = JColorChooser.showDialog(this, "Select a Color", selectedColor[0]);
            if (newColor != null) {
                selectedColor[0] = newColor;
                colorButton.setBackground(newColor);
            }
        });

        // 排版：兩列兩欄
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));
        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Color:"));
        panel.add(colorButton);

        // 彈出確認對話框 (父元件為主視窗 this)
        int result = JOptionPane.showConfirmDialog(
            this, panel, "Customize Label Style",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        );

        // 按下 OK 才套用，並請畫布重畫
        if (result == JOptionPane.OK_OPTION) {
            target.setLabel(nameField.getText());
            target.setColor(selectedColor[0]);
            canvas.repaint();
        }
    }
}