# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 溝通偏好

- 一律使用**繁體中文**回答。
- 程式碼註解請寫得**詳盡**:本專案的既有程式碼帶有大量教學性質的中文註解(解釋設計模式、Swing/AWT 機制、為什麼這樣寫),新增或修改程式碼時請延續這個風格。

## 編譯與執行

純 Java + Swing 專案,**不使用 Maven / Gradle**,直接以 `javac` 編譯。

```bash
# 編譯(輸出到 bin/)
javac -d bin Main.java mode/*.java shape/*.java ui/*.java

# 執行
java -cp bin Main
```

- 沒有測試框架、沒有 lint 設定、沒有依賴管理檔。驗證方式是手動編譯後執行 GUI。
- 編譯產物(`*.class`、`bin/`)與 `*.jar` 已被 `.gitignore` 排除。
- `plantuml.jar` **未納入版控**,僅用於把 `UML/` 下的 `.puml` / `.plantuml` 圖檔轉成 PNG,**與應用程式本身的編譯/執行無關**;需要時請自行下載放到根目錄(見 README「產生 UML 圖」)。

## 架構總覽

這是一個 Swing 桌面 UML 編輯器。整體刻意以三個經典設計模式組織,理解這三者就掌握了大局:

### 1. State Pattern — 滑鼠行為的切換(`mode/`)

`Canvas` 本身**不解讀滑鼠事件**。它在建構子裡掛一個 `MouseAdapter`,把所有 `mousePressed/Dragged/Released/Moved` 原封不動轉交給 `currentMode`(見 [ui/Canvas.java](ui/Canvas.java))。

- `Mode`(抽象基底)定義四個滑鼠方法 + 一個 `draw(Graphics)`,預設皆空實作,子類別只覆寫需要的。
- 具體模式:`SelectMode`(選取/移動/縮放/懸停)、`CreateObjectMode`(畫方形/橢圓/Class)、`CreateLinkMode`(拉連線)。
- 「一次性動作」(畫完一個圖形或一條線)結束後,模式會呼叫 `canvas.returnToDefaultMode()` **自動切回 `SelectMode`**。

### 2. 模式切換的廣播 — Observer(`ui/ModeChangeListener`)

模式切換**一律經過** `Canvas.setCurrentMode()`,它切換後會 `onModeChanged()` 廣播給註冊的監聽者。`UMLEditor` 在啟動時用 `canvas.setModeChangeListener(this::highlightActiveButton)` 註冊。

- 關鍵效果:**按鈕高亮邏輯只有一份**。不論是「使用者點工具列按鈕」還是「畫完自動回 Select」,都走同一條 `highlightActiveButton()` 路徑。
- `UMLEditor` 維護一張 `Map<Mode, JButton>`(`LinkedHashMap`,保留加入順序),廣播回來時用它找到該高亮哪顆按鈕。
- 因此,**新增工具按鈕**時只要在 `UMLEditor` 建構子裡 `addModeButton(...)`,高亮會自動運作,不需另寫高亮程式碼。

### 3. Composite + Factory — 圖形物件(`shape/`)

`Shape`(抽象基底)是所有圖形的共同型別,`Canvas` 只認得 `Shape`。兩條繼承線:

- `BasicObject` — 單一圖形(方形 / 橢圓 / UML Class),持有一組 `Port`(縮放控制點;Rect 與 Class 8 個、Oval 4 個)。
- `CompositeObject` — 群組,內部持有 `List<Shape>`,`move()` 會帶著所有子圖形一起移動,`draw()` 會畫出虛線外框。群組可巢狀。

**圖形的建立**走 Factory Method:`ShapeFactory.orderShape()` 是固定流程(建立 → 設邊界),把 `new` 哪種圖形延遲給子類別 `RectFactory` / `OvalFactory` / `ClassFactory` 決定;`CreateObjectMode` 只持有一個抽象的 `ShapeFactory`,完全不認得具體型別。

### 兩個容易踩雷的約定

**避免 `instanceof` — 用多型查詢取代。** `Shape` 基底刻意提供一組可被覆寫的查詢方法,讓 `Canvas` / `UMLEditor` 不需知道具體型別:
- `isGroup()`、`canCustomizeLabel()`、`isBackgroundLayer()`、`getComponents()`、`getPortAt()`、`resize()`。
- 新增圖形類型或行為時,**請延續這個模式**(在基底加預設方法、子類別覆寫),不要在呼叫端寫 `instanceof`。

**z-order 即清單順序。** `Canvas.shapes` 這個 `List` 的順序就是圖層順序:index 越小越早畫(越底層),越大越晚畫(越上層)。
- **沒有** depth 欄位;所有疊放都由清單位置決定。
- `addShape()` 依 `isBackgroundLayer()` 把連線放到清單最前(最底層)、一般物件放尾端(最上層)。
- 外部**不可**直接 add/remove 清單(`getShapes()` 回傳 unmodifiable view)。調整圖層一律走 `bringToFront()` / `floatToFront()` / `restoreFloating()`。
- `floatToFront()`(選取時暫時浮到最上層)會記住原位置,之後 `restoreFloating()` 還原;同時只允許一個物件處於浮起狀態。

### 刪除與檔案 IO

- `Canvas.deleteSelected()` 刪除選取圖形時,會遞迴收集群組子孫,再用多型查詢 `Shape.isAttachedToAny()` 清掉依附在被刪物件上的連線。
- 連線(`isBackgroundLayer()` 為 true)可被選取,但**不會浮起**(`floatToFront` 跳過)、**不會被收進群組**(`groupSelected` 跳過)。
- 存檔 / 讀檔 / 匯出 PNG 位於 [ui/DiagramFileIO.java](ui/DiagramFileIO.java):Java 序列化,整個清單一次 `writeObject` 以保留「連線 → Port → 物件」的共用參照;讀檔掛 `ObjectInputFilter` 白名單。`Shape` / `Port` 實作 `Serializable`,**新增可序列化欄位時,其型別必須在白名單內**(`shape.*`、`java.util.*`、`java.lang.*`、`java.awt.Color`),否則讀檔會被拒絕。

### 繪圖流程

`Canvas.paintComponent()` 透過 `drawAllShapes()` 分**兩輪**畫圖形:第一輪依清單順序呼叫每個 `Shape.draw()`(本體;連線線身在最底層),第二輪呼叫每個 `Shape.drawOverlay()`(前景層;目前只有 `ConnectionLine` 用它畫箭頭,避免箭頭被物件遮住)。**最後**才呼叫 `currentMode.draw(g)`,讓當前模式的暫時性 UI(建立圖形的預覽虛線、框選的半透明選取框)畫在最上層。匯出 PNG 的 `renderToImage()` 共用同一個 `drawAllShapes()`。改變資料後要重畫一律呼叫 `repaint()`(Canvas 內部)或 `canvas.repaint()`(外部)。

需要浮在所有物件上方的視覺元素,請覆寫 `drawOverlay()`,不要改動 z-order 規則。

## 其他目錄

- `UML/` — 專案的 UML 設計圖(類別圖、Use Case 序列圖)的 `.puml`/`.plantuml` 原始檔與產出的 PNG。
- `docs/` — 中文學習筆記(事件監聽機制、繪圖機制、Lambda、執行流程等),反映本專案的教學取向。
