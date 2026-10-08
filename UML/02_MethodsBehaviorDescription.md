# 第二題：UML編輯器的每個方法行為概略說明

## UI 層 (ui 套件)

### UMLEditor 類別

#### Constructor: UMLEditor()

**行為說明：**

- 初始化主視窗，設定視窗大小為 800x600，設定標題為「UML Editor」
- 創建畫布(Canvas)物件並將其放置在視窗中央
- (方案B)向畫布註冊模式變更監聽器 setModeChangeListener(this::highlightActiveButton)
- 建立左側工具欄，包含6個按鈕(Select、Association、Generalization、Composition、Rect、Oval)，並把「模式→按鈕」記入對照表
- 建立上方選單列，包含File和Edit菜單，Edit菜單含有Group、Ungroup、Label三個選項
- (方案B)呼叫canvas.setDefaultMode(selectMode)指定預設模式;此舉觸發事件,使Select按鈕初始即高亮

#### Method: createBaseButton(String name)

**行為說明：**

- 建立基礎按鈕物件
- 設定按鈕外觀：關閉系統預設效果、設定為不透明、繪製邊框、隱藏焦點線
- 初始化為非激活狀態：白色背景、黑色文字
- 回傳配置完成的按鈕物件

#### Method: addModeButton(JPanel panel, String name, Mode mode)

**行為說明：**

- 創建一個基礎按鈕，並把「mode → 按鈕」放進modeButtons對照表
- 為按鈕安裝ActionListener：被點擊時只呼叫canvas.setCurrentMode(mode)
- 按鈕變色「不在這裡做」：切換模式會觸發事件，由highlightActiveButton()統一處理(使用者點按鈕、畫完自動回Select共用同一段邏輯)

#### Method: highlightActiveButton(Mode activeMode)

**行為說明：**

- (方案B)由Canvas的模式變更事件回呼進來
- 先把所有按鈕恢復未選取樣式(白底黑字)
- 再從modeButtons對照表找出activeMode對應的按鈕,高亮成選取樣式(黑底白字)

#### Method: customizeLabel()

**行為說明：**

- (由「Label」選單呼叫；對話框邏輯從 Canvas 搬來，屬 UI 層職責)
- 遍歷canvas.getShapes()，找出被選取且canCustomizeLabel()為true的圖形(用多型，不用instanceof)
- 若找不到符合條件的圖形則結束
- 彈出對話框：文字輸入框(新標籤) + 顏色按鈕(開啟JColorChooser)
- 使用者按OK後，更新該圖形的label與color，並請canvas.repaint()

> 註：舊的 resetToSelectMode()/resetButtons() 已移除。
> 「回到Select」改由 canvas.returnToDefaultMode() 觸發，按鈕高亮統一交給 highlightActiveButton()。

---

### Canvas 類別

#### Constructor: Canvas()

**行為說明：**

- 初始化畫布為白色背景
- 創建MouseAdapter來統一處理滑鼠事件
- 為滑鼠事件(mousePressed、mouseDragged、mouseReleased、mouseMoved)安裝監聽器
- 將滑鼠事件轉交給當前的Mode物件進行處理

#### Method: setCurrentMode(Mode mode)

**行為說明：**

- 更新畫布的當前模式，後續所有滑鼠事件都由這個新Mode處理
- (方案B)切換後發出模式變更事件 modeListener.onModeChanged(mode)，通知UMLEditor更新按鈕高亮
- 所有模式切換都經過這裡，是按鈕高亮的單一進入點

#### Method: setModeChangeListener(ModeChangeListener listener)

**行為說明：**

- 註冊模式變更的監聽者(UMLEditor啟動時掛入)
- Canvas只認得這個介面,不認得UMLEditor,維持單向依賴

#### Method: setDefaultMode(Mode mode)

**行為說明：**

- 設定預設模式(=SelectMode)並立即切換到它(順帶觸發事件,讓Select按鈕初始高亮)

#### Method: returnToDefaultMode()

**行為說明：**

- 一次性動作(畫圖/連線)完成後呼叫,自動切回預設模式
- 取代舊的 resetToSelectMode_Notify()，語意更直覺

#### Method: addShape(Shape s)

**行為說明：**

- 將圖形物件添加到畫布的圖形清單中
- 該圖形將在下次重畫時顯示

#### Method: clearSelection()

**行為說明：**

- 遍歷所有圖形
- 將每個圖形的selected狀態設置為false
- 確保畫面上沒有任何物件處於被選取的狀態

#### Method: getShapes()

**行為說明：**

- 回傳畫布上所有圖形清單的「唯讀檢視」(unmodifiableList)
- 避免外部直接 add/remove 破壞 z-order 封裝；z-order 調整一律走 bringToFront/floatToFront/restoreFloating

#### Method: getTopShapeAt(Point p)

**行為說明：**

- z-order 即清單順序：從清單尾端往前走訪(最上層優先)
- 檢查座標p是否落在圖形範圍內(使用contains()方法)
- 回傳第一個命中的圖形(即最上層)，若無則回傳null

#### Method: bringToFront(Shape s)

**行為說明：**

- 永久把指定圖形移到清單末端 → 視覺最上層(可供未來「Bring to Front」選單使用)

#### Method: floatToFront(Shape s)

**行為說明：**

- 暫時把某物件浮到最上層，並記住它原本的位置以便還原
- 內部先呼叫restoreFloating()，確保同時只有一個物件處於浮起狀態

#### Method: restoreFloating()

**行為說明：**

- 把先前浮起的物件還原回它原本的層數
- 若該物件已被移出清單(例如被群組收編)，則只清掉浮起狀態，不重新加回

#### Method: paintComponent(Graphics g)

**行為說明：**

- 呼叫父類方法清空畫布並填入背景色
- z-order 即清單順序：直接按清單先後繪製(前面先畫=底層，後面後畫=上層)，不需每幀排序
- 遍歷並繪製每個圖形
- 讓當前Mode在最上層繪製暫時性UI元素(如選取框、預覽框)

> 註：舊的 setResetModeCallback(Runnable)/resetToSelectMode_Notify() 已移除，
> 改用 setModeChangeListener + returnToDefaultMode (方案B，語意更直覺)。

#### Method: groupSelected()

**行為說明：**

- 遍歷所有圖形，找出所有被選取(selected=true)的圖形
- 檢查選取的圖形數量是否≥2
- 如果數量足夠：
  - 創建新的CompositeObject
  - 將每個被選取的圖形加入到群組中
  - 從畫布清單中移除這些圖形
  - 將群組設置為被選取狀態並添加到畫布
  - 重畫畫布

#### Method: ungroupSelected()

**行為說明：**

- 遍歷所有圖形，找出所有被選取的圖形
- 檢查是否恰好有1個被選取的圖形，且它是CompositeObject
- 如果條件滿足：
  - 從畫布清單中移除該群組
  - 取得群組內的所有子圖形
  - 將每個子圖形重新加入到畫布清單
  - 重畫畫布

> 註：原本的 customizeLabelStyle() 對話框已搬到 UMLEditor.customizeLabel()，
> 讓 Canvas 專心負責圖形管理與繪圖 (符合單一職責原則)。

---

## 模式層 (mode 套件)

### Mode 抽象類別

#### Constructor: Mode(Canvas c)

**行為說明：**

- 接受畫布引用並儲存
- 使此Mode與特定的畫布綁定
- 後續的所有操作都在這個畫布上進行

#### Method: mousePressed(MouseEvent e)

**行為說明：**

- 抽象方法，由子類別實作
- 在滑鼠按下時被呼叫
- 各子類別根據不同的模式有不同的實作

#### Method: mouseDragged(MouseEvent e)

**行為說明：**

- 抽象方法，由子類別實作
- 在滑鼠拖曳時被呼叫
- 各子類別根據不同的模式有不同的實作

#### Method: mouseReleased(MouseEvent e)

**行為說明：**

- 抽象方法，由子類別實作
- 在滑鼠放開時被呼叫
- 各子類別根據不同的模式有不同的實作

#### Method: mouseMoved(MouseEvent e)

**行為說明：**

- 抽象方法，由子類別實作
- 在滑鼠移動時被呼叫
- 各子類別根據不同的模式有不同的實作

#### Method: draw(Graphics g)

**行為說明：**

- 抽象方法，由子類別實作
- 允許Mode在畫布最上層繪製暫時性UI元素
- 各子類別根據不同的模式有不同的實作

---

### SelectMode 類別

#### Constructor: SelectMode(Canvas c)

**行為說明：**

- 呼叫父類別Constructor，綁定畫布
- 初始化所有屬性為null或false狀態
- 此模式用於選取、移動和縮放圖形

#### Method: mouseMoved(MouseEvent e)

**行為說明：**

- 獲取滑鼠目前位置最上層的圖形
- 若與之前滑鼠所在圖形不同：
  - 取消之前圖形的hovered狀態
  - 設置新圖形的hovered狀態為true
  - 更新currentHovered指標
  - 重畫畫布以顯示懸停效果

#### Method: mousePressed(MouseEvent e)

**行為說明：**

- 記錄滑鼠按下的起始座標
- **優先檢查Port**：檢查是否點擊到任何被選取或懸停的圖形的Port
  - 若點中Port：記錄draggedPort和draggedShape，進入「縮放狀態」，結束
- **次檢查圖形**：尋找被點擊到的最上層圖形
  - 若點中「尚未被選取」的圖形：清除其他選取狀態，設為被選取，並呼叫canvas.floatToFront()請Canvas把它暫時浮到最上層
  - 若點中「已被選取」的圖形(單選或多選)：保留所有選取，準備整批移動
  - 若點在空白：呼叫canvas.restoreFloating()還原浮起物件，初始化selectionBox，進入「框選狀態」
- 重畫畫布

#### Method: mouseDragged(MouseEvent e)

**行為說明：**

- **若在縮放狀態**：呼叫draggedShape的resize()方法，按照draggedPort和當前滑鼠位置進行縮放
- **若在移動狀態**：計算滑鼠移動的偏移量，呼叫draggedShape的move()方法移動圖形
- **若在框選狀態**：根據起始座標和當前座標更新selectionBox的邊界
- 重畫畫布以即時顯示變化

#### Method: mouseReleased(MouseEvent e)

**行為說明：**

- **若在框選狀態**：根據selectionBox範圍檢查並選取所有被包含的圖形
- 清除縮放/移動/框選狀態
- 重畫畫布，顯示最終結果

#### Method: draw(Graphics g)

**行為說明：**

- 若存在selectionBox(框選狀態)：
  - 填充半透明藍色區域(RGBA)
  - 繪製藍色邊框
- 用於視覺回饋框選過程

---

### CreateObjectMode 類別

#### Constructor: CreateObjectMode(Canvas c, ShapeFactory factory)

**行為說明：**

- 呼叫父類別Constructor，綁定畫布
- 記錄具體建立者(RectFactory或OvalFactory)，由它決定要建立的圖形
- 初始化預覽框為null

#### Method: mousePressed(MouseEvent e)

**行為說明：**

- 記錄起始座標
- 創建初始的預覽框(大小為0)

#### Method: mouseDragged(MouseEvent e)

**行為說明：**

- 計算支持反向拖曳的座標(min/max計算)
- 更新預覽框的位置和大小以跟隨滑鼠
- 重畫畫布，顯示灰色預覽框

#### Method: mouseReleased(MouseEvent e)

**行為說明：**

- 根據最終預覽框計算圖形的寬度和高度
- 若寬度或高度小於10像素，設置預設大小80x80
- 呼叫factory.orderShape(x, y, width, height)建立圖形(工廠方法模式核心流程，內部負責createShape與setBounds)
- 將圖形添加到畫布
- 清除預覽框和起始座標
- 重畫畫布
- 呼叫returnToDefaultMode()自動切回預設(Select)模式

#### Method: draw(Graphics g)

**行為說明：**

- 若存在預覽框：
  - 設置畫筆顏色為深灰色
  - 呼叫factory.drawPreview(g, previewBox)，由具體工廠決定畫矩形或橢圓輪廓(不再有型別判斷)
  - 用於視覺回饋，讓使用者看到將要創建的圖形

---

### CreateLinkMode 類別

#### Constructor: CreateLinkMode(Canvas c, LinkType type)

**行為說明：**

- 呼叫父類別Constructor，綁定畫布
- 記錄連線類型("Association"、"Generalization"或"Composition")
- 初始化所有屬性為null或0

#### Method: mousePressed(MouseEvent e)

**行為說明：**

- 清除之前的startPort
- 遍歷所有圖形，詢問它們是否有Port在點擊座標處
- 若找到Port：
  - 記錄startPort
  - 記錄sourceShape(起點圖形)
  - 記錄起始座標(startX, startY)和當前座標(currentX, currentY)
  - 結束迴圈
- 若無法找到有效的起點Port，此次操作無效

#### Method: mouseDragged(MouseEvent e)

**行為說明：**

- 更新當前座標以跟隨滑鼠位置
- 強制清除所有圖形的hovered狀態(防止持久亮起)
- 詢問畫布是否在當前座標有圖形
- 若有圖形且不是起點圖形(sourceShape)：
  - 設置該圖形的hovered狀態為true(亮起Port)
- 重畫畫布，顯示從startPort到當前滑鼠位置的預覽線

#### Method: mouseReleased(MouseEvent e)

**行為說明：**

- 若無有效的起點Port則結束
- 遍歷所有圖形，詢問是否有Port在放開座標處
- 若找到終點Port(endPort)，檢查：
  - 起點Port和終點Port不能屬於同一個圖形
  - 若檢查通過：
    - 使用ShapeFactory創建ConnectionLine
    - 將連線添加到畫布
- 清除所有狀態
- 重畫畫布
- 呼叫returnToDefaultMode()自動切回預設(Select)模式

#### Method: draw(Graphics g)

**行為說明：**

- 若存在startPort(正在拉線)：
  - 設置畫筆顏色為黑色
  - 從startPort繪製直線到currentX, currentY
- 若需要繪製箭頭(根據linkType繪製不同樣式)

---

## 圖形層 (shape 套件)

### Shape 抽象類別

#### Constructor: Shape()

**行為說明：**

- 初始化圖形屬性
- 預設位置為(0, 0)，大小為100x100
- 預設深度為50
- 預設為未選取、未懸停狀態
- 預設標籤為空字串
- 預設顏色為淺灰色

#### Method: move(int dx, int dy)

**行為說明：**

- 根據偏移量(dx, dy)更新圖形位置
- x += dx, y += dy
- 此方法由SelectMode在拖曳移動時呼叫
- 子類別可覆寫以實現額外行為(如CompositeObject需移動所有子圖形)

#### Method: setSelected(boolean b)

**行為說明：**

- 設置圖形的selected狀態
- 當下次draw()時，若selected=true則顯示控制點

#### Method: isSelected()

**行為說明：**

- 回傳圖形是否被選取

#### Method: isHovered()

**行為說明：**

- 回傳圖形是否被懸停

#### Method: setHovered(boolean b)

**行為說明：**

- 設置圖形的hovered狀態
- 當下次draw()時，若hovered=true可能顯示特殊視覺效果

#### Method: isBackgroundLayer()

**行為說明：**

- z-order 分層提示，用多型取代 instanceof
- 預設回傳false(一般物件，加入畫布時放到清單尾端=最上層)
- ConnectionLine覆寫為true(背景層，加入時放到清單最前面=最底層)

#### Method: getBounds()

**行為說明：**

- 創建並回傳代表圖形邊界框的Rectangle物件
- 用於碰撞檢測和框選

#### Method: setBounds(int x, int y, int width, int height)

**行為說明：**

- 一次性設置圖形的位置和大小
- 子類別通常會在此基礎上執行額外動作(如更新Port位置)

#### Method: getPortAt(Point p)

**行為說明：**

- 預設實作回傳null(大多圖形無Port)
- BasicObject覆寫此方法以檢查Port是否在座標p處

#### Method: resize(Port p, Point pt)

**行為說明：**

- 預設實作為空(大多圖形不支援縮放)
- BasicObject覆寫此方法以實現實際縮放

#### Method: draw(Graphics g)

**行為說明：**

- 抽象方法，由子類別實作
- 負責將圖形繪製到畫布

#### Method: contains(Point p)

**行為說明：**

- 抽象方法，由子類別實作
- 檢查座標p是否落在圖形範圍內

#### Method: getLabel(), setLabel(String label), getColor(), setColor(Color color)

**行為說明：**

- Getter/Setter方法
- 管理圖形的標籤文本和填充顏色 (label欄位為protected，外部一律走此處的getter/setter)
- 由UMLEditor.customizeLabel()呼叫

---

### BasicObject 類別

#### Constructor: BasicObject(int x, int y, ObjectType type)

**行為說明：**

- 設置圖形位置和類型("Rect"或"Oval")
- 根據類型決定Port數量：Rect=8個, Oval=4個
- 為每個Port創建對象並加入清單
- 呼叫updatePorts()初始化Port位置

#### Method: setBounds(int x, int y, int width, int height)

**行為說明：**

- 覆寫父類方法
- 設置圖形的位置和大小
- 呼叫updatePorts()重新計算所有Port的座標

#### Method: move(int dx, int dy)

**行為說明：**

- 覆寫父類方法
- 呼叫父類move()移動圖形位置
- 呼叫updatePorts()同步更新Port位置

#### Method: updatePorts()

**行為說明：**

- 計算圖形邊界和中心點座標
- 根據圖形類型設置Port位置：
  - **Rect**：8個Port分別位於上中、下中、左中、右中、四個角落
  - **Oval**：4個Port分別位於上中、下中、左中、右中
- 確保Port始終跟隨圖形邊界

#### Method: getPortAt(Point p)

**行為說明：**

- 遍歷所有Port
- 若Port的contains(p)回傳true則回傳該Port
- 否則回傳null
- 用於檢測滑鼠是否點擊到控制點

#### Method: resize(Port p, Point pt)

**行為說明：**

- 根據被拖曳的Port(p)和當前滑鼠位置(pt)計算新的邊界
- 根據Port位置(上、下、左、右、角落)決定縮放方向
- 更新圖形邊界
- 呼叫updatePorts()重新計算Port位置

#### Method: draw(Graphics g)

**行為說明：**

- 設置畫筆顏色為圖形顏色
- 根據type繪製對應形狀：
  - **Rect**：drawRect()和fillRect()
  - **Oval**：drawOval()和fillOval()
- 若selected或hovered=true，繪製8個黑色的Port方形
- 繪製標籤文本

#### Method: contains(Point p)

**行為說明：**

- 檢查座標p是否落在矩形邊界框內
- 用於判斷是否點擊到此圖形

---

### CompositeObject 類別

#### Constructor: CompositeObject()

**行為說明：**

- 初始化空的components清單
- 設置深度為50(預設值)

#### Method: addComponent(Shape s)

**行為說明：**

- 將圖形添加到components清單
- 呼叫updateBounds()重新計算群組的邊界框

#### Method: getComponents()

**行為說明：**

- 回傳包含所有子圖形的清單

#### Method: updateBounds()

**行為說明：**

- 若components為空則結束
- 遍歷所有子圖形，找出最小包圍盒：
  - 計算最左上角座標(minX, minY)
  - 計算最右下角座標(maxX, maxY)
- 更新群組自身的x、y、width、height

#### Method: move(int dx, int dy)

**行為說明：**

- 遍歷所有子圖形，呼叫它們的move(dx, dy)
- 呼叫updateBounds()更新群組邊界

#### Method: draw(Graphics g)

**行為說明：**

- 遍歷所有子圖形，呼叫它們的draw()
- 若群組被選取或懸停：
  - 創建Graphics2D以支援虛線
  - 設定虛線樣式(5像素實線+5像素空白)
  - 用深藍色虛線繪製群組的邊界框(外擴5像素)
  - 還原原始畫筆設定

#### Method: contains(Point p)

**行為說明：**

- 檢查座標p是否落在群組的邊界框內
- 只要p在邊界框範圍內就回傳true

#### Method: resize(Port p, Point pt)

**行為說明：**

- 空實作(群組通常不支援直接縮放)
- 但方法存在以滿足Liskov替換原則

---

### ConnectionLine 類別

#### Constructor: ConnectionLine(Port start, Port end, LinkType type)

**行為說明：**

- 儲存起點和終點Port引用
- 記錄連線類型
- 設置深度為99(最底層，不會遮擋圖形)

#### Method: draw(Graphics g)

**行為說明：**

- 取得startPort和endPort的目前座標
- 設置畫筆顏色為黑色
- 繪製從startPort到endPort的直線
- 根據linkType繪製不同樣式的箭頭：
  - **Association**：V型箭頭(兩條短線)
  - **Generalization**：空心三角形
  - **Composition**：實心菱形

#### Method: drawArrow(Graphics g, int x1, int y1, int x2, int y2)

**行為說明：**

- 使用三角函數計算主線條的傾斜角
- 根據傾斜角和箭頭長度(15像素)計算箭頭尖端座標
- 根據linkType繪製對應的箭頭形狀

#### Method: contains(Point p)

**行為說明：**

- 預設回傳false(連線通常不支援點擊選取)

#### Method: resize(Port p, Point pt)

**行為說明：**

- 空實作(連線不支援縮放)

---

### Port 類別

#### Constructor: Port(Shape parent)

**行為說明：**

- 記錄Port所屬的Shape
- 初始座標為0, 0(將在updatePorts()時重新設置)

#### Method: getParent()

**行為說明：**

- 回傳此Port所屬的Shape

#### Method: getX(), getY()

**行為說明：**

- 回傳Port的座標

#### Method: setPosition(int x, int y)

**行為說明：**

- 更新Port的座標

#### Method: contains(Point p)

**行為說明：**

- 以Port座標為中心建立10x10像素的隱形正方形
- 檢查點p是否在此正方形範圍內
- 用於滑鼠點擊檢測

#### Method: draw(Graphics g)

**行為說明：**

- 設置畫筆顏色為黑色
- 在Port座標處繪製10x10像素的黑色方形
- 位置以Port座標為中心進行了偏移調整

---

### ShapeFactory 類別 (抽象建立者 / 工廠方法模式 Creator)

> 對應披薩範例的 PizzaStore。本身為抽象類別，不能直接 new，必須由子類別實作工廠方法。

#### Method: orderShape(int x, int y, int width, int height)

**行為說明：**

- 工廠方法模式的核心流程(對應orderPizza)，流程固定不變
- 呼叫工廠方法createShape(x, y)建立圖形(此時不知道是Rect還是Oval)
- 對建立出的圖形呼叫setBounds設定邊界
- 回傳完成的BasicObject

#### Method: createShape(int x, int y) — abstract

**行為說明：**

- 抽象工廠方法(對應createPizza)，交由子類別決定要new哪種圖形

#### Method: drawPreview(Graphics g, Rectangle box) — abstract

**行為說明：**

- 抽象工廠方法，交由子類別決定預覽框要畫成矩形或橢圓

---

### RectFactory 類別 (具體建立者，對應NYPizzaStore)

#### Method: createShape(int x, int y)

**行為說明：**

- 回傳 new BasicObject(x, y, ObjectType.RECT)

#### Method: drawPreview(Graphics g, Rectangle box)

**行為說明：**

- 以drawRect繪製矩形預覽輪廓

---

### OvalFactory 類別 (具體建立者，對應ChicagoPizzaStore)

#### Method: createShape(int x, int y)

**行為說明：**

- 回傳 new BasicObject(x, y, ObjectType.OVAL)

#### Method: drawPreview(Graphics g, Rectangle box)

**行為說明：**

- 以drawOval繪製橢圓預覽輪廓
