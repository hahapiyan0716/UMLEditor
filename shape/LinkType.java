package shape;

/* =========================================
 * LinkType - 連線的型別 (取代魔術字串)
 * =========================================
 * 用 enum 取得型別安全：switch 可窮舉檢查，未來新增線型不會漏改。
 */
public enum LinkType {
    ASSOCIATION,        // 關聯：一般 V 型箭頭
    GENERALIZATION,     // 繼承：空心三角形
    COMPOSITION,        // 組合：實心菱形
    DEPENDENCY          // 依賴：虛線 + 一般 V 型箭頭
}
