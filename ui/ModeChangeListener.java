package ui;

import mode.Mode;

/* =========================================
 * ModeChangeListener - 模式變更事件 --> Observer Pattern
 * =========================================
 * 作用：Canvas 每次切換 currentMode 時，就透過這個介面「廣播」出去，
 *       讓監聽者 (UMLEditor) 知道「模式換了」，進而去更新工具按鈕的高亮。
 *
 * 為什麼需要這個介面，而不是讓 Canvas 直接呼叫 UMLEditor？
 *   依賴方向必須是單向：UMLEditor → Canvas → Mode。
 *   如果 Canvas 直接 import UMLEditor，就變成雙向依賴 (互相認識)，耦合度爆增。
 *   所以這裡用「事件介面」當中介：Canvas 只認得這個介面，完全不知道 UMLEditor 的存在。
 *   實際上是誰在聽、聽到後做什麼，由 UMLEditor 自己決定。
 *
 * @FunctionalInterface：只有一個抽象方法，所以可以用 Lambda 直接當作監聽器傳入。
 * @FunctionalInterface 是一個標註（annotation），用來標記「這個介面是函式介面」，也就是只有一個抽象方法的介面（SAM：Single Abstract Method）
 * 加了 @FunctionalInterface 後，只要你不小心多寫了第二個抽象方法，編譯就會報錯，提醒你這個介面應該只有一個抽象方法，確保它可以被 Lambda 表達式使用。
 */
@FunctionalInterface
public interface ModeChangeListener {
    // newMode：畫布剛剛切換到的新模式 (例如 SelectMode、CreateObjectMode...)
    void onModeChanged(Mode newMode);
}