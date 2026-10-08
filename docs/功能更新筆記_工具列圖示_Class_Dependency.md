# 功能更新筆記：工具列圖示、Class 與 Dependency

> 記錄 2026-10-08 第二輪的改動：**工具列改用圖示、新增 Class 物件與 Dependency 連線**，以及每個決策背後的理由與用到的 Java 機制。
> 啟動流程的變化見〈程式執行流程與函式呼叫順序報告〉1.4、4.A、4.B；按鈕高亮的事件鏈見〈事件監聽機制筆記〉Part 1。

---

## 目錄

- [0. 一覽表](#0-一覽表)
- [1. 工具列改用圖示](#1-工具列改用圖示)
- [2. 選取樣式：從「整顆塗黑」改成「換邊框」](#2-選取樣式從整顆塗黑改成換邊框)
- [3. 新功能：Class 物件](#3-新功能class-物件)
- [4. 新功能：Dependency 連線](#4-新功能dependency-連線)
- [5. 小改動：GridLayout(0, 1)](#5-小改動gridlayout0-1)
- [6. 已知問題](#6-已知問題)
- [7. 這次用到的 Java 機制速查](#7-這次用到的-java-機制速查)
- [8. 怎麼驗證的](#8-怎麼驗證的)
- [9. 口試速答](#9-口試速答)

---

## 0. 一覽表

| 類型 | 項目 | 主要檔案 |
|---|---|---|
| 新功能 | 工具列按鈕改用 `icon/` 下的圖示，按鈕名稱移到 Tooltip | `UMLEditor.addModeButton`、`UMLEditor.loadIcon` |
| 新功能 | Class 物件（名稱 / 屬性 / 方法三格） | `ObjectType.CLASS`、`ClassFactory`、`BasicObject.draw` |
| 新功能 | Dependency 連線（虛線 + V 型箭頭） | `LinkType.DEPENDENCY`、`ConnectionLine.draw` |
| 行為改動 | 選取中的按鈕改用黑色粗框標示，背景維持白色 | `UMLEditor.highlightActiveButton` |
| 行為改動 | 工具列改用 `GridLayout(0, 1)`，列數自動決定 | `UMLEditor` 建構子 |
| 資源 | 新增 `icon/` 資料夾（8 張圖示） | `icon/` |

工具列順序：Select → Association → Generalization → Composition → Dependency → Class → Rect → Oval。

---

## 1. 工具列改用圖示

### 要解決的問題

圖示檔來源不一，直接 `new ImageIcon(路徑)` 塞給按鈕會出三個問題：

| 問題 | 例子 | 後果 |
|---|---|---|
| 尺寸不一 | 最小 28×31、最大 1200×1200 | 大圖把按鈕撐爆，小圖看不清楚 |
| 長寬比不一 | Composition 是扁長的 80×24 | 硬壓成正方形會變形 |
| 留白不一 | Association 原圖 100×100，箭頭只佔中間 80×13 | 連留白一起縮小，箭頭細到幾乎看不見 |
| 背景不一 | JPG 一定是白底，PNG 可能是透明底 | 按鈕背景一變色，透明底的黑線就會跟背景融在一起 |

### 做法：`loadIcon()` 的四個步驟

```
loadIcon(iconName, contentSize)
 ① 找檔案：依序嘗試 icon/<名稱>.png / .jpg / .jpeg / .gif，用第一個存在的
 ② 裁留白：trimToContent() 逐點掃描，找出「非空白像素」的外框，用 getSubimage 切出來
 ③ 等比例縮放：長邊縮到 contentSize（預設 40px），短邊依比例
 ④ 補白底置中：畫到一張 40×40、白底的 BufferedImage 正中央
```

```java
// ③ 等比例縮放：取寬、高各自要縮的倍數中較小的那個
double scale = Math.min((double) target / w, (double) target / h);

// getScaledInstance 是「非同步」產生像素的，再包一層 ImageIcon 借用它「會等圖片載入完成」的特性
Image scaled = new ImageIcon(trimmed.getScaledInstance(scaledW, scaledH, Image.SCALE_SMOOTH)).getImage();
```

### 為什麼呼叫端只給「檔名主體」？

```java
addModeButton(toolPanel, "Oval", "circle", ...);   // 不寫 circle.png 或 circle.jpg
```

開發過程中圖檔換過好幾次格式（`circle.png` → `circle.jpg`、`composition.jpg` → `composition.png`），每換一次，寫死的檔名就失效，按鈕退回文字。改成由 `loadIcon` 自動嘗試副檔名後，**換圖不必改程式**。

### 「空白」怎麼判斷？

```java
private static boolean isBlank(int argb) {
    int alpha = (argb >>> 24) & 0xFF;
    int red   = (argb >> 16) & 0xFF;
    int green = (argb >> 8) & 0xFF;
    int blue  = argb & 0xFF;
    return alpha < 32 || (red > 220 && green > 220 && blue > 220);
}
```

- `getRGB()` 回傳的 `int` 把 A、R、G、B 四個 0~255 的值塞在一起，用位移加 `& 0xFF` 取出各通道。
- **門檻是 220 而不是 255**：JPG 是有損壓縮，白底常出現 250、245 之類的雜點，門檻太嚴會把雜點誤判成圖案，裁切範圍就會被撐大。

### 讀不到圖時怎麼辦？

`ImageIcon` 讀取失敗**不會丟例外**，而是回傳寬高為 -1 的空殼，所以要自己檢查：

```java
if (original.getImageLoadStatus() != MediaTracker.COMPLETE) { ...; return null; }
```

`loadIcon` 回傳 `null` 時，按鈕維持顯示文字，程式照常運作，並在終端機印出警告。

> ⚠️ `icon/` 是**相對於目前工作目錄**的路徑，所以要在專案根目錄執行 `java -cp bin Main`。從其他目錄執行時，按鈕會退回文字。

### 個別調整圖案大小：多載（Overload）

Select 的游標是「實心塊狀」，撐滿 40px 會比其他細線條的圖示顯得又大又重：

```java
private void addModeButton(JPanel panel, String name, String iconName, Mode mode) {
    addModeButton(panel, name, iconName, ICON_SIZE, mode);      // 大部分按鈕用預設尺寸
}
private void addModeButton(JPanel panel, String name, String iconName, int iconSize, Mode mode) { ... }

addModeButton(toolPanel, "Select", "select", SELECT_ICON_SIZE, selectMode);   // 22px
```

**圖案**縮小到 22px，但**畫布**仍是 40×40，所以每顆按鈕的大小依然一致。

### 線條太細：從圖檔下手

裁留白後，Association 的箭頭雖然撐滿了寬度，但原圖線條只有 1px，縮放時線寬也同比例縮小，依然很淡。嘗試把整體尺寸放大到 56px，箭頭只粗了一點，小圖（Select）反而被放大到出現鋸齒。

結論：**縮放救不了太細的原圖**。最後用程式（`BasicStroke` 粗線 + 反鋸齒）重新產生 Association、Dependency、Composition、Oval 的圖示，讓線寬在 40px 的按鈕上約 2~3px。

---

## 2. 選取樣式：從「整顆塗黑」改成「換邊框」

原本選取中的按鈕是「黑底白字」。改用圖示後，黑色背景會把白底的圖示包成一塊「黑框白方塊」，視覺上很突兀。改成：

```java
// 未選取：1px 淺灰細框 + 7px 留白 = 8px
BTN_INACTIVE_BORDER = BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1),
    BorderFactory.createEmptyBorder(7, 7, 7, 7));
// 選取中：3px 黑色粗框 + 5px 留白 = 8px
BTN_ACTIVE_BORDER = BorderFactory.createCompoundBorder(
    BorderFactory.createLineBorder(Color.BLACK, 3),
    BorderFactory.createEmptyBorder(5, 5, 5, 5));
```

| 設計點 | 說明 |
|---|---|
| **總厚度相同** | 兩種邊框都是 8px，切換選取時按鈕大小與圖示位置不會跳動 |
| **要自己補留白** | `setBorder()` 會把 Swing 預設邊框「連同內建留白」一起換掉，不補的話圖示會緊貼邊框、按鈕變得很窄 |
| **`CompoundBorder`** | 把兩個 Border 疊在一起（外框在外、內框在內）；`EmptyBorder` 只佔空間、不畫東西 |
| **事件鏈不用動** | 高亮樣式集中在 `highlightActiveButton()` 一處，換樣式只改這個方法，觸發路徑（Observer）完全不變 |

---

## 3. 新功能：Class 物件

### 外觀

UML 類別圖的 Class：一個矩形，用兩條水平線平分成三格。

```
┌──────────┐
│  名稱     │  ← Label 置中在這格
├──────────┤
│  屬性     │
├──────────┤
│  方法     │
└──────────┘
```

### 做法：Factory Method 的擴充

```java
public class ClassFactory extends ShapeFactory {
    protected BasicObject createShape(int x, int y) {
        return new BasicObject(x, y, ObjectType.CLASS);
    }
    public void drawPreview(Graphics g, Rectangle box) { /* 拖曳時的預覽框也畫成三格 */ }
}
```

`CreateObjectMode`、`ShapeFactory.orderShape()` **一行都沒改**。新增一種圖形只要「新增」一個具體工廠——這就是 Factory Method 帶來的**開放封閉原則（Open-Closed Principle）**：對擴充開放、對修改封閉。

### `BasicObject` 裡的改動

| 位置 | 改動 |
|---|---|
| Port 數量 | 原本 `RECT ? 8 : 4` → 改成 `OVAL ? 4 : 8`，Class 跟 Rect 一樣有 8 個 Port |
| `updatePorts()` | 原本 `if (RECT)` → 改成 `if (type != OVAL)`，Rect 與 Class 共用矩形的 8 點配置 |
| `draw()` | `if / else` 改成 `switch`，新增 `case CLASS` |
| 標籤位置 | 新增 `labelAreaHeight`：Rect / Oval 是整個高度，Class 只有 1/3（名稱格） |

> **為什麼條件要寫成「`OVAL` 例外」而不是「`RECT` 才有 8 個」？** 新增的 Class 外形也是矩形。把「特例」寫在條件裡（只有 Oval 是 4 個點），之後再加其他矩形類圖形時就不必回來改。

### 為什麼 `BasicObject` 裡用 `switch(type)` 不算違反「避免 instanceof」？

專案慣例針對的是 **`Canvas` / `UMLEditor` 這些呼叫端**不該去判斷具體型別。`BasicObject` 判斷**自己的** `ObjectType` 是物件內部的實作細節，呼叫端仍然只呼叫 `draw()`，完全不知道裡面有幾種類型。

---

## 4. 新功能：Dependency 連線

### 外觀

虛線線身 + 開放式 V 型箭頭（跟 Association 同款箭頭）。

### 做法一：虛線筆觸

```java
private static final float[] DASH_PATTERN = { 6f, 4f };   // 畫 6px、空 4px，不斷重複
private static final Stroke DASHED_STROKE = new BasicStroke(
    1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, DASH_PATTERN, 0f);
```

| 參數 | 意思 |
|---|---|
| `1f` | 線寬（選取時用 2.5f 的 `SELECTED_DASHED_STROKE`） |
| `CAP_BUTT` | 端點平切，每段虛線長度才會精準等於設定值 |
| `JOIN_MITER`、`10f` | 轉角樣式與斜接上限，直線用不到，填預設值 |
| `DASH_PATTERN` | 虛線樣式陣列 |
| `0f` | 從虛線樣式的第 0px 開始畫 |

### 做法二：只有線身是虛線

```java
// ConnectionLine.draw()（第一輪，線身）
applyLineStyle(g2d);
if (linkType == LinkType.DEPENDENCY) {
    g2d.setStroke(selected ? SELECTED_DASHED_STROKE : DASHED_STROKE);
}
g2d.drawLine(...);
```

箭頭在 `drawOverlay()`（第二輪）畫，沿用 `applyLineStyle()` 的**實線**筆觸。箭頭的翅膀只有 15px，若也套用「畫 6 空 4」的虛線，會被切成零碎小段，看起來像箭頭斷掉。

### 做法三：箭頭用 switch 貫穿（fall-through）共用

```java
switch (linkType) {
    case ASSOCIATION:
    case DEPENDENCY:        // 沒有 break → 跟 ASSOCIATION 執行同一段
        g.drawLine(x2, y2, xRight, yRight);
        g.drawLine(x2, y2, xLeft, yLeft);
        break;
    ...
}
```

### 為什麼存讀檔不用改？

- `LinkType`、`ObjectType` 是 enum，Java 序列化**以名稱**記錄 enum 值，新增值不影響舊檔的讀取。
- 兩者都在 `shape` 套件裡，已經在 `ObjectInputFilter` 白名單（`shape.*`）內。
- `ClassFactory` 不會被序列化（圖形本身仍是 `BasicObject`）。

---

## 5. 小改動：GridLayout(0, 1)

```java
toolPanel.setLayout(new GridLayout(0, 1, 5, 5));   // 原本是 GridLayout(6, 1, 5, 5)
```

`GridLayout` 的列數填 `0` 代表「**不限，依加入的元件數量自動決定**」。原本寫死 6，這次新增兩顆按鈕就得記得回來改；填 0 之後，加按鈕只要多呼叫一次 `addModeButton`。

---

## 6. 已知問題

- **Generalization 圖示的箭頭方向相反**：`icon/generalization.jpg` 的像素其實是「往左指」，檔案裡的 EXIF 資訊寫著「顯示時旋轉 180°」（orientation = lower-right）。看圖軟體會依 EXIF 轉正，所以在檔案總管裡看起來往右；但 Java 的 `ImageIcon` **不讀 EXIF**，直接顯示原始像素，在工具列上就變成往左。修法是把圖檔本身轉正，或用程式重新產生一張。
- **Class 圖示是灰底**：`icon/class.jpg` 原圖就是灰色填色，跟其他白底黑線的圖示風格不同。

---

## 7. 這次用到的 Java 機制速查

| 機制 | 用在哪 | 一句話 |
|---|---|---|
| `ImageIcon` + `getImageLoadStatus()` | `loadIcon` | 讀圖失敗不丟例外，要自己檢查是否為 `MediaTracker.COMPLETE` |
| `Image.getScaledInstance(..., SCALE_SMOOTH)` | `loadIcon` | 面積平均演算法縮圖，大幅縮小時鋸齒較少；回傳的影像是非同步產生的 |
| `BufferedImage.getRGB` / `getSubimage` | `trimToContent` | 逐點讀像素；切出指定矩形範圍的子圖 |
| 位元運算 `>>>`、`>>`、`& 0xFF` | `isBlank` | 從 ARGB 打包的 int 取出各通道 |
| `TYPE_INT_ARGB` vs `TYPE_INT_RGB` | `trimToContent` / `loadIcon` | 前者保留透明通道（判斷透明底用），後者每個像素一定有顏色（白底畫布用） |
| `JButton.setIcon` / `setToolTipText` | `addModeButton` | 按鈕顯示圖示；滑鼠停留時浮出文字提示 |
| `BorderFactory.createCompoundBorder` / `createEmptyBorder` | 按鈕邊框 | 疊兩層邊框；只佔空間不畫東西的留白 |
| 方法多載（Overload） | `addModeButton` | 同名方法不同參數，讓預設情況呼叫起來更簡潔 |
| `BasicStroke` 虛線建構子 | `ConnectionLine` | 用 dash 陣列定義「畫多長、空多長」 |
| switch 貫穿（fall-through） | `ConnectionLine.drawArrow` | 兩個 case 共用同一段程式碼 |
| `GridLayout(0, n)` | 工具列 | 列數依元件數量自動決定 |

---

## 8. 怎麼驗證的

1. **編譯**：`javac -Xlint:all` 沒有新增警告（既有的 serial、this-escape 警告與本次改動無關）。
2. **畫面截圖**：寫一支小程式建立 `UMLEditor`、用 `doClick()` 切換模式，再把視窗內容畫到 `BufferedImage` 存成 PNG，逐一檢查圖示大小、選取邊框、讀不到圖時的文字退回。
3. **模擬操作**：對 Canvas 送出 `MouseEvent`（按下 → 拖曳 → 放開），依序畫出 Class、Rect、Oval，再拉 Dependency 與 Association，截圖確認三格外觀、虛線與箭頭都正確。
4. **存讀檔往返**：建立 Class 與 Dependency 後呼叫 `DiagramFileIO.save` / `load`，確認圖形數量一致且通過白名單。

---

## 9. 口試速答

- **工具列圖示大小不一，怎麼統一？**
  > `loadIcon` 先裁掉四周的空白，再依長寬中較小的倍數等比例縮放到 40px，最後畫到一張 40×40 的白底畫布正中央。這樣不論原圖多大、什麼比例、透明還是白底，按鈕上的效果都一致。

- **為什麼程式裡只寫檔名、不寫副檔名？**
  > `loadIcon` 會依序嘗試 png、jpg、jpeg、gif，用第一個存在的檔案。圖檔換格式時不必改程式碼。

- **選取中的按鈕為什麼不再塗黑？**
  > 按鈕上是黑線條的圖示，背景塗黑會把白底圖示包成一塊黑框白方塊，很突兀。改成只換邊框：未選取淺灰細框、選取黑色粗框，兩者總厚度相同，所以按鈕不會跳動。高亮邏輯集中在 highlightActiveButton，換樣式只改這一處。

- **新增 Class 改了哪些地方？為什麼 CreateObjectMode 不用改？**
  > 新增 ObjectType.CLASS 和 ClassFactory，BasicObject 加上三格的畫法。CreateObjectMode 只持有抽象的 ShapeFactory，工廠方法模式把「new 哪種圖形」延遲給子類別，所以新增圖形只要新增一個工廠類別，既有程式碼不用修改，符合開放封閉原則。

- **Dependency 的虛線怎麼畫？為什麼箭頭不是虛線？**
  > 用 BasicStroke 帶 dash 陣列的建構子，設定「畫 6px、空 4px」。只有線身在 draw 時換成虛線筆觸，箭頭在 drawOverlay 用實線，因為 15px 的短翅膀套虛線會被切碎。箭頭本身跟 Association 一樣，用 switch 貫穿共用同一段程式。

- **新增 enum 值會不會讓舊的 .uml 檔讀不回來？**
  > 不會。Java 序列化以名稱記錄 enum 值，新增值不影響既有的值；而且 shape 套件本來就在讀檔白名單內。

---

*本筆記依現行程式碼撰寫；若日後修改了相關方法，請同步更新。*
