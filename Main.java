import javax.swing.SwingUtilities;

import ui.UMLEditor;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UMLEditor().setVisible(true));
        // new UMLEditor()：在記憶體中把你的主視窗（包含畫布、選單、工具列）全部建構出來。
        // .setVisible(true)：把這個建構好的視窗，實實在在地推到使用者的螢幕上顯示出來。
    }
}
/*
所有的 UI 建立、畫面更新、滑鼠點擊，都必須在同一個專屬的執行緒上進行，這個執行緒叫做 EDT (Event Dispatch Thread，事件分派執行緒)。
SwingUtilities.invokeLater(...): SwingUtilities.invokeLater 告訴系統：
「哈囉，主執行緒這裡有一包任務想要執行。但我知道不能直接衝進執行緒裡，所以麻煩你幫我把這包任務拿給 EDT，請他『等一下有空的時候』幫我執行。」
這是為了確保程式執行極度穩定。
*/
/*
() ->：Lambda 表達式
這個箭頭語法，就是用來「打包任務」的包裝紙。
也就是()->把後面的 new UMLEditor().setVisible(true) 包裝成一個可以被傳遞的指令包裹，
然後交給 invokeLater 這個服務生帶進廚房。
*/
