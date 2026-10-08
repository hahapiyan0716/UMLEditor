# 繪圖機制筆記：paintComponent / repaint / draw

> 搞懂 Swing 的繪圖機制：什麼時候用、誰呼叫、差別在哪。
> 以本專案 `Canvas` / `Shape` / `Mode` 為例。

---

## 目錄

- [0. 一句話總覽](#0-一句話總覽)
- [1. 預備名詞：Component 與 Graphics g](#1-預備名詞component-與-graphics-g)
- [2. repaint()：請求重畫（你呼叫）](#2-repaint請求重畫你呼叫)
- [3. paintComponent()：重畫的入口（系統呼叫）](#3-paintcomponent重畫的入口系統呼叫)
- [4. draw()：每個圖形畫自己（專案自訂）](#4-draw每個圖形畫自己專案自訂)
- [5. paintComponent 畫的兩種東西：真實圖形 vs 暫時預覽](#5-paintcomponent-畫的兩種東西真實圖形-vs-暫時預覽)
- [5.5 兩輪繪製：draw 與 drawOverlay（連線箭頭為什麼要分開畫）](#55-兩輪繪製draw-與-drawoverlay連線箭頭為什麼要分開畫)
- [6. 三者怎麼串起來](#6-三者怎麼串起來)
- [7. 常見疑問](#7-常見疑問)
- [8. 口試速答](#8-口試速答)

---

## 0. 一句話總覽

| 名稱 | 是誰的方法 | 誰呼叫 | 做什麼 | 你會不會直接呼叫 |
|---|---|---|---|---|
| `repaint()` | Swing（Component，繼承來的） | **你** | 「**請求**」重畫（排程，不立刻畫） | ✅ 常呼叫 |
| `paintComponent(Graphics g)` | Swing（JComponent，你 override） | **系統 EDT** | 真正「**重畫整個畫布**」的入口 | ❌ 永遠不要直接呼叫 |
| `draw(Graphics g)` | **專案自訂**（Shape、Mode） | `paintComponent` 內部 | 單一圖形/模式「**畫自己**」 | 由 paintComponent 呼叫 |

比喻：
- `repaint()`：你跟畫家喊「**畫面舊了，重畫一下**」。
- `paintComponent()`：畫家**真正動筆**重畫整張畫布。
- `draw()`：畫布上每個物件告訴畫家「**我長這樣，這樣畫我**」。

---

## 1. 預備名詞：Component 與 Graphics g

### Component（元件）

`Component`（`java.awt.Component`）是**所有「看得見的 UI 元件」的祖先類別**。畫面上任何東西——按鈕、面板、視窗、你的畫布——都是它的子孫。

繼承鏈：
```
java.awt.Component          ← 老祖宗（提供 repaint()、setBackground()、addMouseListener()…）
  └ java.awt.Container
       └ javax.swing.JComponent   ← Swing 元件基底（提供 paintComponent()）
            └ javax.swing.JPanel
                 └ Canvas（你的畫布）extends JPanel
```

所以你的 `Canvas` **本身就是一個 Component**（透過繼承）。`repaint()` 來自 Component、`paintComponent()` 來自 JComponent——都是**繼承來的**，不是你發明的。

### Graphics g（畫筆）

`Graphics`（`java.awt.Graphics`）是**「畫筆 + 畫布表面」打包成的繪圖工具**。你對它下指令來畫東西：

```java
g.setColor(Color.RED);          // 換顏色
g.fillRect(x, y, w, h);         // 填滿矩形
g.drawLine(x1, y1, x2, y2);     // 畫線
g.drawString("文字", x, y);     // 寫字
```

重點：
- **你不用自己 new 它**，是 Swing 建好、傳進 `paintComponent(Graphics g)` 給你。
- 它身上記著：目前顏色、字體、實際的像素緩衝區、可畫範圍（clip）。
- 當你把同一支 `g` 一路傳給 `s.draw(g)`，所有圖形就是**用同一支筆畫在同一塊畫布上**，所以它們會出現在同一個畫面。

---

## 2. repaint()：請求重畫（你呼叫）

- **是什麼**：`Component` 提供的方法，Canvas 繼承到。
- **做什麼**：告訴 Swing「我的資料改了，畫面過期了，**請你安排重畫**」。
- **重點**：它**不會自己畫圖**，也**不會立刻畫**。它只是把「待重畫」這件事排進 EDT 的待辦，之後系統才會呼叫 `paintComponent`。
- **何時呼叫**：只要你**改了會影響畫面的資料**，就呼叫它。本專案的例子：

```java
// CreateObjectMode.mouseReleased：加了新圖形 → 要求重畫
canvas.addShape(obj);
canvas.repaint();

// SelectMode.mouseDragged：移動了圖形 → 要求重畫
for (Shape s : canvas.getShapes()) if (s.isSelected()) s.move(dx, dy);
canvas.repaint();

// UMLEditor.customizeLabel：改了標籤/顏色 → 要求重畫
target.setLabel(...); target.setColor(...);
canvas.repaint();
```

- **在 Canvas 內 vs 外**：
  - 在 Canvas 自己裡面：直接寫 `repaint();`
  - 在外部類別（Mode、UMLEditor）：要寫 `canvas.repaint();`

> 關鍵心法：**你只負責「改資料 + 喊 repaint」，畫圖交給系統。**

---

## 3. paintComponent()：重畫的入口（系統呼叫）

- **是什麼**：`JComponent` 的方法，你在 Canvas 裡 **override** 它，把自訂的繪圖邏輯寫進去。
- **誰呼叫**：**Swing 的 EDT（事件分派執行緒）自動呼叫**，你**永遠不要自己呼叫它**。
- **何時被自動呼叫**（三種時機）：
  1. **視窗初次顯示**（`setVisible(true)` 後第一次畫）。
  2. **作業系統觸發**：視窗被縮放、被別的視窗蓋住後又露出來、從最小化還原…系統要你「補畫」。
  3. **你呼叫 repaint() 之後**：系統擇時來呼叫它。
- **本專案的實作**：

```java
@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);              // ① 一定要先呼叫父類別 → 清空畫布、塗背景色
    drawAllShapes(g);                     // ② 畫所有圖形（分兩輪，見下方與 5.5 節）
    if (currentMode != null) {            // ③ 最後讓目前模式畫暫時性 UI（選取框/預覽線）
        currentMode.draw(g);
    }
}

private void drawAllShapes(Graphics g) {
    for (Shape s : shapes) {              // 第一輪：依清單順序，叫每個圖形畫自己的本體
        s.draw(g);
    }
    for (Shape s : shapes) {              // 第二輪：前景層（連線的箭頭），浮在所有物件上方
        s.drawOverlay(g);
    }
}
```

- **`Graphics g` 哪來的**：是 Swing **自己建立好、傳進來**的「畫筆」。你不用自己 new，直接拿來用、或往下傳給 `draw(g)`。
- **為什麼第一行要 `super.paintComponent(g)`**：父類別負責把畫布**清乾淨並塗上背景色**。少了這行，上一幀的殘影會留著（畫面變髒）。

---

## 4. draw()：每個圖形畫自己（專案自訂）

- **是什麼**：**這是我們專案自己定義的方法**，不是 Swing 的東西。定義在 `Shape`（抽象）、`Mode` 裡。
- **做什麼**：每個圖形/模式拿到畫筆 `g`，**畫出自己的樣子**。
- **誰呼叫**：被 `Canvas.paintComponent` 呼叫（`s.draw(g)`、`currentMode.draw(g)`）。
- **多型重點**：`paintComponent` 只寫 `s.draw(g)`，不管 s 是矩形、橢圓、群組還是連線——**各自的 `draw` 各畫各的**。例如：

```java
// BasicObject.draw：畫矩形/橢圓 + 文字 +（被選取時）畫 Port
@Override
public void draw(Graphics g) {
    g.setColor(color);
    if (type == ObjectType.RECT) { g.fillRect(x, y, width, height); ... }
    else { g.fillOval(x, y, width, height); ... }
    g.drawString(label, ...);
    if (selected || hovered) { /* 畫出 8/4 個 Port */ }
}

// ConnectionLine.draw：只畫線身（箭頭改由 drawOverlay 在第二輪畫，見 5.5 節）
// CompositeObject.draw：先畫子圖形，再（被選取時）畫虛線外框
// SelectMode.draw：畫半透明藍色選取框
```

> 小提醒：Swing 自訂繪圖要 override 的是 `paintComponent`（不是舊 AWT 的 `paint`）。我們的 `draw` 只是**自己取的方法名**，跟 Swing 的 `paint/paintComponent` 無關，別搞混。

---

## 5. paintComponent 畫的兩種東西：真實圖形 vs 暫時預覽

`paintComponent` 其實畫**兩類**完全不同性質的東西：

| 類別 | 誰畫 | 是什麼 | 會留在畫布上嗎 |
|---|---|---|---|
| **常駐圖形** | `for (Shape s : shapes) s.draw(g)` | 真實存在的矩形/橢圓/線/群組 | ✅ 會 |
| **暫時預覽 UI** | `currentMode.draw(g)` | 操作過程中的**預覽**，放開後就消失 | ❌ 不會（不是真物件） |

這就是最後那段 `if (currentMode != null) currentMode.draw(g);` 在做的事——畫「暫時預覽」。各模式畫的預覽：

| 模式 | `currentMode.draw` 畫什麼 | 何時看得到 |
|---|---|---|
| CreateObjectMode | 灰色的矩形/橢圓**預覽框** | **拖曳建立物件中** |
| SelectMode | 半透明藍色**框選框** | 拖曳框選中 |
| CreateLinkMode | 黑色**預覽連線** | 拖曳拉線中 |

### 用「畫一個矩形」走一遍（看清楚預覽的時機）

**預覽是在「拖曳中」出現，不是「放開後」**——放開後它就被真正的矩形取代了：

```
① 按下滑鼠     → 記住起點
② 拖曳中 ←←←   → previewBox 不斷更新 → repaint → paintComponent：
                    for shapes：畫所有「真實」圖形
                    currentMode.draw(g) → 畫出「灰色預覽框」 ← 這就是暫時圖形/預覽圖！
                    （你看到一個灰框跟著滑鼠變大變小，告訴你矩形會多大）
③ 放開滑鼠     → 建立「真的」BasicObject 加入 shapes 清單，previewBox = null
                  → repaint → paintComponent：
                    for shapes：畫所有真實圖形（現在多了那個新矩形）
                    currentMode.draw(g) → previewBox 是 null → 什麼都不畫
```

看 `CreateObjectMode.draw` 就很清楚：

```java
@Override
public void draw(Graphics g) {
    if (previewBox != null) {              // 只有「拖曳中」previewBox 才有值
        g.setColor(Color.DARK_GRAY);
        factory.drawPreview(g, previewBox); // 畫灰色預覽框
    }
    // 放開後 previewBox = null → 這個 draw 什麼都不做
}
```

### 為什麼要把「預覽」跟「真實圖形」分開畫？

1. **預覽不是真物件**：拖曳時的灰框只是「示意」，不該被加進 `shapes` 清單（不然會留下一堆殘影灰框）。所以它由 mode **暫時**畫，不進清單。
2. **要畫在最上面**：預覽框/框選框必須蓋在所有圖形之上才看得清楚 → 所以 `currentMode.draw(g)` 排在 `paintComponent` 的**最後一行**（最後畫 = 最上層）。

---

## 5.5 兩輪繪製：draw 與 drawOverlay（連線箭頭為什麼要分開畫）

### 遇到的問題

z-order 規則是「連線在最底層、物件疊在上面」，好處是連線不會穿過物件表面。但箭頭的尖端剛好落在物件邊緣的 Port 上：

```
連線斜斜地接近物件時：

        ┌──────────────┐
        │   物件 A      │
  ──────◆──────────────┘   ← 菱形箭頭有一半在 A 的框內
```

如果箭頭跟線身一起在「底層」畫，接著畫 A 的時候，A 的灰色填色就會把菱形的上半部蓋掉，箭頭看起來殘缺。

### 解法：把連線拆成兩層

| 層 | 方法 | 什麼時候畫 | 畫什麼 |
|---|---|---|---|
| 本體層 | `draw(g)` | 第一輪，依清單順序 | 物件本體、連線的**線身** |
| 前景層 | `drawOverlay(g)` | 第二輪，所有本體畫完之後 | 連線的**箭頭** |

- 線身仍在物件底下 → 「連線不蓋過物件」的規則不變。
- 箭頭在所有物件之上 → 永遠完整可見。

### 用多型掛勾（Hook）實作，不用 instanceof

```java
// Shape（基底）：預設什麼都不畫
public void drawOverlay(Graphics g) {}

// ConnectionLine：覆寫它來畫箭頭
@Override
public void drawOverlay(Graphics g) {
    Color lineColor = applyLineStyle(g2d);   // 與線身共用同一個樣式設定，選取時兩層都是藍色
    drawArrow(g2d, ..., lineColor);
}

// CompositeObject：Composite Pattern，轉交給每個子圖形
@Override
public void drawOverlay(Graphics g) {
    for (Shape s : components) s.drawOverlay(g);
}
```

`Canvas` 只要「對每個圖形呼叫 `drawOverlay`」，不需要知道誰有前景層——沒有前景層的圖形（BasicObject）會執行到基底的空實作，什麼也不做。

### 完整的繪製順序（由下到上）

```
① super.paintComponent(g)        白色背景
② 第一輪 draw()                  連線線身 → 物件（依清單順序）
③ 第二輪 drawOverlay()           連線箭頭
④ currentMode.draw(g)            預覽框 / 框選框 / 預覽線
```

### 匯出 PNG 也共用同一套

`Canvas.renderToImage()` 在 `BufferedImage` 上作畫時，也是呼叫同一個 `drawAllShapes(g2)`。`BufferedImage.createGraphics()` 給的是同一套 `Graphics` API，所以每個 `draw` / `drawOverlay` 完全不用改，就能畫進圖片裡；兩邊共用同一個方法，也保證螢幕和匯出的圖長得一樣。

---

## 6. 三者怎麼串起來

完整一輪（以「畫完一個矩形」為例）：

```
① 使用者放開滑鼠 → CreateObjectMode.mouseReleased
      canvas.addShape(obj)          // 改資料：清單多了一個矩形
      canvas.repaint()              // 喊「請重畫」（只排程，方法馬上返回）
                ↓ （EDT 稍後處理）
② Swing 自動呼叫 Canvas.paintComponent(g)      // 系統動手，不是你
      super.paintComponent(g)       // 清背景
      drawAllShapes(g)
            for s in shapes: s.draw(g)         // ← 這時才呼叫每個圖形的 draw
                  obj.draw(g)                  // 新矩形畫出來
            for s in shapes: s.drawOverlay(g)  // 連線箭頭（矩形沒有前景層，什麼都不畫）
      currentMode.draw(g)           // 模式畫暫時 UI（此時 previewBox 已 null，不畫）
                ↓
③ 畫面更新：使用者看到新矩形
```

一句話：**改資料 →（你）repaint →（系統）paintComponent →（每個圖形）draw**。

---

## 7. 常見疑問

### Q1：為什麼不能直接呼叫 `paintComponent(g)`？
因為那個 `g`（畫筆）是 Swing 配好的（含雙緩衝、裁切範圍）。你手上根本沒有正確的 `g`，硬呼叫會出錯或畫錯。**正確做法永遠是 `repaint()`**，讓 Swing 用對的 `g` 來呼叫 `paintComponent`。

### Q2：為什麼 `repaint()` 不立刻畫？
為了**效能**。你可能一次改了好幾樣東西（移動 + 改色），呼叫多次 `repaint()`，Swing 會**合併成一次** `paintComponent`，避免重複畫。而且繪圖必須在 EDT 上有秩序地進行。

### Q3：我改了資料，畫面卻沒變？
最常見的原因：**忘了呼叫 `repaint()`**。資料改了但沒通知系統重畫，畫面就停在舊狀態。改完資料記得 `canvas.repaint()`。

### Q4：`draw()` 和 `paintComponent()` 到底差在哪？
- `paintComponent`：**整個畫布**的繪圖入口，Swing 呼叫，只有 Canvas 有。
- `draw`：**單一物件**畫自己，由 `paintComponent` 呼叫，每個 Shape/Mode 各自實作。
- 關係：`paintComponent` 是「總指揮」，`draw` 是「各個演員」。

### Q5：「暫時預覽」跟「真實圖形」差在哪？
- 真實圖形在 `shapes` 清單裡，由 `for shape: s.draw(g)` 畫，會留著。
- 暫時預覽（拖曳時的灰框/藍框/預覽線）由 `currentMode.draw(g)` 畫，**不在清單裡**，放開就消失。詳見第 5 節。

### Q6：`repaint()` 在哪裡呼叫？
- 在 Canvas 內部：直接 `repaint();`
- 在外部（Mode、UMLEditor）：`canvas.repaint();`

---

## 8. 口試速答

- **這三個差在哪？**
  > `repaint()` 是你呼叫、**請求**重畫（排程，不立刻畫）；`paintComponent(g)` 是 **Swing 系統自動呼叫**、真正重畫整個畫布的入口（你 override，但絕不直接呼叫）；`draw(g)` 是**專案自訂**、由 paintComponent 呼叫、讓每個圖形畫自己。

- **流程？**
  > 改資料 → 呼叫 `repaint()` → 系統擇時呼叫 `paintComponent(g)` → 內部 `super.paintComponent` 清背景、再 `for shape: shape.draw(g)` 讓每個圖形畫自己、接著 `for shape: shape.drawOverlay(g)` 畫前景層（連線箭頭）、最後 `currentMode.draw(g)` 畫暫時 UI。

- **為什麼連線箭頭要另外用 drawOverlay 畫？**
  > 連線在最底層，但箭頭尖端落在物件邊緣，斜向靠近時有一半在物件內部，會被後畫的物件蓋住。所以把連線拆成兩層：線身用 `draw` 留在底層，箭頭用 `drawOverlay` 在所有物件畫完後才畫。`drawOverlay` 是 `Shape` 上的多型掛勾，預設空實作，只有 `ConnectionLine` 覆寫，Canvas 不需要 instanceof。

- **Component 與 Graphics 是什麼？**
  > Component 是所有 UI 元件的祖先（Canvas 繼承它，repaint/paintComponent 都來自它）；Graphics 是 Swing 傳進來的「畫筆 + 畫布表面」，你用它畫圖、並往下傳給每個 draw。

- **最後那段 currentMode.draw 在畫什麼？**
  > 畫「暫時預覽」——拖曳建立物件時的灰色預覽框、框選的藍框、拉線的預覽線。它不是真物件、不進 shapes 清單，放開就消失；排在最後畫，所以一定在最上層。

- **為什麼不直接呼叫 paintComponent？**
  > 因為 `Graphics g` 由 Swing 控管（緩衝、裁切），要透過 `repaint()` 讓系統用正確的 g 來呼叫；自己呼叫會出錯。
