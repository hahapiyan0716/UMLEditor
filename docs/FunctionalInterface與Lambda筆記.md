# FunctionalInterface 與 Lambda 筆記

> 以本專案的 `ModeChangeListener` 為例，分兩個層面整理：
>
> - **Part 1 語法機制**：`@FunctionalInterface`、Lambda、方法參考是什麼、怎麼用。
> - **Part 2 設計層面**：為什麼要「多設一個介面」（觀察者模式 / 解耦）。
> - 最後附**口試速答**。

---

## 目錄

- **Part 1 — 語法機制**
  - [1. 背景：本專案的相關程式碼](#1-背景本專案的相關程式碼)
  - [2. @FunctionalInterface 是什麼](#2-functionalinterface-是什麼)
  - [3. onModeChanged 是誰的方法（釐清誤會）](#3-onmodechanged-是誰的方法釐清誤會)
  - [4. Lambda 與方法參考](#4-lambda-與方法參考)
  - [5. 三種寫法等價（匿名類別 / Lambda / 方法參考）](#5-三種寫法等價匿名類別--lambda--方法參考)
- **Part 2 — 設計層面**
  - [6. 為什麼要「多設一個介面」？（解耦）](#6-為什麼要多設一個介面解耦)
- **附錄**
  - [7. 口試速答](#7-口試速答)

---

# Part 1 — 語法機制

## 1. 背景：本專案的相關程式碼

整份筆記都圍繞這三段程式碼。三個角色（觀察者模式）：

| 角色                           | 是誰                   | 做什麼               |
| ------------------------------ | ---------------------- | -------------------- |
| Subject（被觀察者）            | `Canvas`             | 切換模式時對外發通知 |
| Observer（抽象觀察者 = 介面）  | `ModeChangeListener` | 通知契約             |
| ConcreteObserver（具體觀察者） | `UMLEditor`          | 收到通知後高亮按鈕   |

**介面**（`ui/ModeChangeListener.java`）：

```java
@FunctionalInterface
public interface ModeChangeListener {
    void onModeChanged(Mode newMode);   // 唯一的抽象方法
}
```

**Canvas（Subject）**：持有一個監聽器「插槽」，切換模式時呼叫它：

```java
private ModeChangeListener modeListener;   // 插槽：裝一個實作了介面的物件

// 提供方法讓觀察者來「註冊」
public void setModeChangeListener(ModeChangeListener listener) {
    this.modeListener = listener;
}

public void setCurrentMode(Mode mode) {
    this.currentMode = mode;
    if (modeListener != null) {
        modeListener.onModeChanged(mode);  // 對「插槽裡的物件」呼叫 onModeChanged
    }
}
```

**UMLEditor（ConcreteObserver）**：把實作掛進去（用方法參考）：

```java
canvas.setModeChangeListener(this::highlightActiveButton);
```
* 註冊方法 setModeChangeListener 定義在 Subject（Canvas）（因為名單要存在它那裡），但呼叫它的是 Observer（UMLEditor）。canvas. 只是「方法的擁有者」，**真正執行註冊動作的是 UMLEditor**。
---

## 2. @FunctionalInterface 是什麼

一個**標註（annotation）**，標記「這個介面是**函式介面**」＝**只有一個抽象方法**的介面（SAM：Single Abstract Method）。

### 它的兩個作用

1. **編譯器上鎖**：若不小心多寫第二個抽象方法，編譯直接報錯。
   ```java
   @FunctionalInterface
   public interface ModeChangeListener {
       void onModeChanged(Mode newMode);
       void onSomethingElse();   // ❌ 編譯錯誤：Multiple non-overriding abstract methods
   }
   ```
2. **能用 Lambda / 方法參考實作**：只有一個抽象方法，Java 才能用一行 Lambda 提供實作（見第 4、5 節）。

### 標註是「選用」的

就算不加 `@FunctionalInterface`，只要介面只有一個抽象方法，它**本來就是**函式介面，一樣能用 Lambda。加標註只是多了「編譯器保護 + 表達意圖」。

### 計算「一個抽象方法」的規則

| 方法種類                                        | 算進「唯一抽象方法」嗎？ |
| ----------------------------------------------- | ------------------------ |
| 抽象方法（沒實作）                              | ✅ 算                    |
| `default` 方法（有實作）                      | ❌ 不算                  |
| `static` 方法                                 | ❌ 不算                  |
| 繼承自 `Object`（`toString`、`equals`…） | ❌ 不算                  |

### Java 內建的常見函式介面

| 介面                             | 唯一方法                | 用在哪                                    |
| -------------------------------- | ----------------------- | ----------------------------------------- |
| `Runnable`                     | `run()`               | `SwingUtilities.invokeLater(() -> ...)` |
| `ActionListener`               | `actionPerformed(e)`  | `btn.addActionListener(e -> ...)`       |
| `Comparator<T>`                | `compare(a, b)`       | 排序                                      |
| **`ModeChangeListener`** | `onModeChanged(mode)` | Canvas 通知 UMLEditor                     |

---

## 3. onModeChanged 是誰的方法（釐清誤會）

**常見誤會**：「Canvas 沒有 `onModeChanged`，為什麼可以呼叫？匿名類別為什麼要 `@Override`？」

**關鍵**：`onModeChanged` 是 **`ModeChangeListener` 介面**定義的方法，**不是 Canvas 的**。

- Canvas 只是「**持有一個 ModeChangeListener 物件，呼叫它的 `onModeChanged`**」。
- Canvas 完全不知道 `onModeChanged` 裡面做什麼——那由「提供物件的人」（UMLEditor）決定。

### 責任分工

```
1. 介面 ModeChangeListener  →  規定「要有一個 onModeChanged(Mode) 方法」（只有形狀，沒內容）
2. Canvas                   →  持有這種物件，並呼叫它的 onModeChanged
3. UMLEditor                →  提供物件，決定內容 = highlightActiveButton（高亮按鈕）
```

### `@Override` 是在覆寫「介面」的方法

匿名類別裡寫 `@Override`，表示「我正在**實作介面宣告的抽象方法**」，跟 Canvas 一點關係都沒有。實作任何介面方法時加 `@Override` 都合法且建議。

---

## 4. Lambda 與方法參考

兩者都是「**用簡短語法，提供函式介面那唯一一個方法的實作**」。

### Lambda 表達式（lambda expression）

一段「匿名函式」的簡寫，語法 `(參數) -> { 內容 }`，等於「把抽象方法的實作直接寫出來」。

**先看一個最簡單的例子**（跟本專案無關）：

```java
// 一個函式介面：只規定「給兩個 int，回傳一個 int」
interface MathOp {
    int apply(int a, int b);
}

// 用 Lambda 提供「加法」的實作 ——「(a, b) -> a + b」就是 apply 的內容
MathOp add = (a, b) -> a + b;
System.out.println(add.apply(3, 5));   // 印出 8

// 想換成「乘法」？只要換 Lambda 內容，介面完全不用改
MathOp mul = (a, b) -> a * b;
System.out.println(mul.apply(3, 5));   // 印出 15
```

重點：`(a, b) -> a + b` 就是 `apply` 這個方法的「身體」。換一段 Lambda，就換一種實作。

**對照本專案**：

```java
(Mode newMode) -> { highlightActiveButton(newMode); }   // 完整
newMode -> highlightActiveButton(newMode)               // 簡化（型別可推斷、單行省大括號）
```

讀法：「給我一個 newMode，我就執行 highlightActiveButton(newMode)」——這段 Lambda 就是 `onModeChanged` 的內容。

### 方法參考（method reference）

當 Lambda 的內容 **只是「原封不動呼叫另一個現成方法」** 時，用 `::` 直接指過去，連 Lambda 都省了。

```java
(Mode newMode) -> { highlightActiveButton(newMode); }   // Lambda
this::highlightActiveButton                             // 方法參考（完全等價）
```

> 「Lambda」常泛指 Lambda 表達式；方法參考是 Lambda 的更精簡特例。

### 方法參考的四種形式

| 形式                       | 語法               | 例子                                                |
| -------------------------- | ------------------ | --------------------------------------------------- |
| 靜態方法                   | `類別::靜態方法` | `Integer::parseInt`                               |
| **某物件的實例方法** | `物件::方法`     | **`this::highlightActiveButton`**（本專案） |
| 某類別的實例方法           | `類別::方法`     | `String::length`                                  |
| 建構子                     | `類別::new`      | `ArrayList::new`                                  |

本專案用的是第二種：`this`（目前的 UMLEditor 物件）的 `highlightActiveButton`。

---

## 5. 三種寫法等價（匿名類別 / Lambda / 方法參考）

`setModeChangeListener(...)` 需要一個「實作了 ModeChangeListener 的物件」。下面三種寫法**都在製造同一種物件**，只是繁簡不同。

**寫法 A：匿名類別（最囉嗦）**

```java
canvas.setModeChangeListener(new ModeChangeListener() {
    @Override
    public void onModeChanged(Mode newMode) {   // 實作介面的方法
        highlightActiveButton(newMode);
    }
});
```

**寫法 B：Lambda（中等）**

```java
canvas.setModeChangeListener( newMode -> highlightActiveButton(newMode) );
```

**寫法 C：方法參考（最短）**

```java
canvas.setModeChangeListener(this::highlightActiveButton);
```

Java 看到 `highlightActiveButton(Mode)` 的參數與回傳跟 `onModeChanged(Mode)` **形狀完全吻合**，就自動把它包成等價於寫法 A 的物件。

```
匿名類別(最囉嗦)        Lambda(中等)                     方法參考(最短)
new X(){               newMode ->                       this::highlightActiveButton
  onModeChanged(m){       highlightActiveButton(m)
   →highlight...(m) }
}
       ──────────── 三者產生「同一種物件」，行為相同，可互換 ────────────
```

> 因為 `onModeChanged` 的內容「剛好只是去呼叫一個現成方法」，所以能一路簡化到方法參考。

---

# Part 2 — 設計層面

## 6. 為什麼要「多設一個介面」？（解耦）

前面講「介面怎麼用」；這一節回答更根本的問題：**為什麼不讓 Canvas 直接呼叫 UMLEditor，要多繞一個 `ModeChangeListener` 介面？**

> 一句話：**為了不讓 Canvas「認識」UMLEditor，避免雙向依賴。** 這個介面就是觀察者模式的 **Observer（抽象觀察者）** 角色——Subject 與具體觀察者之間的解耦層。

### 介面具體做的四件事

1. **定義通知契約**：規定「想被通知的人，身上必須有 `onModeChanged(Mode)` 方法」——只規定形狀，不規定內容。
2. **當解耦牆（最核心）**：Canvas 只依賴這個抽象，**不認識 UMLEditor**，對它零相依。
3. **提供多型接點**：Canvas 對「介面型別」呼叫 `onModeChanged`，執行期才分派到 UMLEditor 的實作。
4. **當註冊的型別把關**：`setModeChangeListener(ModeChangeListener)` 只收符合契約的物件。

### 反證：如果「不設介面」直接呼叫

```java
// Canvas（壞做法）
import ui.UMLEditor;          // ← Canvas 開始認識 UMLEditor
private UMLEditor editor;

public void setCurrentMode(Mode mode) {
    this.currentMode = mode;
    editor.highlightActiveButton(mode);   // 直接叫 UMLEditor
}
```

這樣功能也會動，但依賴變成**雙向**：

原本（乾淨）：  UMLEditor $\rightarrow$ Canvas        （單向：上層認識下層）
直接呼叫後：    UMLEditor ⇄ Canvas          （雙向：互相認識）


### 雙向依賴的壞處

| 壞處                      | 說明                                                       |
| ------------------------- | ---------------------------------------------------------- |
| **Canvas 不再通用** | 被綁死 UMLEditor，無法搬去別的視窗/程式重用。              |
| **無法單獨測試**    | 想測 Canvas，得先生一個完整 UMLEditor（含按鈕、選單…）。  |
| **概念錯位**        | 「畫布」是單純繪圖元件，不該知道「主視窗上有按鈕要變色」。 |
| **牽一髮動全身**    | 兩類別綁在一起，改一個常要動另一個。                       |

### 介面怎麼解決

讓 Canvas **只認抽象契約**，不認具體是誰（程式碼見第 1 節）。依賴維持單向：

```
UMLEditor ──→ Canvas ──→ ModeChangeListener（介面）
    │                          ↑
    └────── 我來當聽眾 ─────────┘
```

Canvas 從頭到尾**沒有 import UMLEditor**。這就是 **Observer Pattern ＋ 依賴反轉原則（DIP）**：高層與低層都依賴抽象，而不是低層依賴高層。

### 「可是它們都在 ui 套件，有差嗎？」

有。重點不是套件邊界，而是 **Canvas 依賴的是「抽象」還是「具體類別」**：

- 依賴具體 `UMLEditor` → Canvas 永遠只能配 UMLEditor。
- 依賴抽象 `ModeChangeListener` → **任何**實作它的東西都能當聽眾（UMLEditor 高亮按鈕、未來的 Logger、測試用假監聽器），Canvas 完全不用改。

### 兩個比喻

**插槽**（介面 = 契約的形狀）：

```
Canvas 身上有插槽：ModeChangeListener modeListener;
                   ▲ 介面 = 插槽的形狀，規定什麼物件才插得進來
UMLEditor 提供符合形狀的東西（this::highlightActiveButton）插進去
Canvas 切換模式時對著「插槽裡的東西」喊 onModeChanged，不在乎是誰
```

**YouTuber**（Subject 廣播、不認識訂閱者）：

- 沒介面：YouTuber 得拿到「王小明的電話」親自打；換人訂閱要改程式。
- 有介面：YouTuber 只管「對訂閱清單發通知」，`ModeChangeListener` 就是那顆「訂閱按鈕」的契約——任何人訂閱都收得到，不必認識每個人。

---

# 附錄

## 7. 口試速答

- **@FunctionalInterface？**

  > 標記「只有一個抽象方法」的介面。好處：編譯器守住規則、能用一行 Lambda / 方法參考實作。是選用標註。
  >
- **onModeChanged 是誰的、為什麼 Canvas 能呼叫？**

  > 它是 `ModeChangeListener` 介面的方法。Canvas 只持有該介面的物件並呼叫它的 onModeChanged；內容由 UMLEditor 提供。`@Override` 是在實作介面方法，與 Canvas 無關。
  >
- **`this::highlightActiveButton` 為什麼可行？**

  > 因為 `highlightActiveButton(Mode)` 的參數與回傳跟 `onModeChanged(Mode)` 完全吻合，Java 自動把它包成一個監聽器物件，等價於匿名類別但更簡潔。
  >
- **Lambda / 方法參考？**

  > Lambda 把函式介面那唯一方法的實作寫成 `(參數)->內容`；當內容只是呼叫現成方法時，可再簡化為 `物件::方法` 的方法參考。
  >
- **為什麼要多設一個 `ModeChangeListener` 介面？**

  > 它是觀察者模式的 Observer 角色（通知契約）。Canvas 當 Subject，只依賴這個介面、用多型呼叫 onModeChanged，所以不認識具體的 UMLEditor——避免雙向依賴，Canvas 因此可重用、可測試。這就是 Observer 模式 ＋ 依賴反轉原則（DIP）。
  >
