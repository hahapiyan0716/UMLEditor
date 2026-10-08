package ui;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import shape.Shape;

/* =========================================
 * DiagramFileIO：圖檔的存檔 / 讀檔 / 匯出 PNG
 * =========================================
 * 職責分離 (Single Responsibility)：
 *   - Canvas      ：管理圖形清單與繪圖
 *   - UMLEditor   ：管理 UI (選單、檔案對話框、錯誤訊息)
 *   - DiagramFileIO：只負責「資料 ↔ 檔案」的轉換，不碰任何 UI 元件
 * 因此這裡全是 static 方法 (工具類別)，不需要建立實例；遇到錯誤一律丟出例外，由呼叫端 (UMLEditor) 決定怎麼顯示給使用者。
 *
 * 放在 ui 套件而不是另開 io 套件，是為了沿用既有的編譯指令 (javac ... ui/*.java)，不必修改 README。
 */
public final class DiagramFileIO {

    // 存檔的副檔名 (給 UMLEditor 的檔案對話框使用)
    public static final String DIAGRAM_EXTENSION = "uml";
    public static final String IMAGE_EXTENSION = "png";

    // ==========================================
    // 反序列化白名單 (ObjectInputFilter，JDK 9+)
    // ==========================================
    // 為什麼需要？ObjectInputStream.readObject() 會「依照檔案內容」建立任意類別的物件。
    // 如果有人給你一個惡意製作的檔案，裡面可以塞進 classpath 上某些危險類別 (所謂的 Gadget Chain)，
    // 在「讀檔的過程中」就觸發執行任意程式碼——這就是著名的 Java 反序列化漏洞。
    // 防護方式：只允許「我們自己的圖形類別」與「它們會用到的 JDK 基本類別」，其他一律拒絕。
    //
    // 規則語法 (分號隔開，由左到右比對，第一個符合的規則決定結果)：
    //   shape.*           ：允許 shape 套件下的所有類別 (Shape、BasicObject、Port、LinkType...)
    //   java.util.*       ：允許 ArrayList 等集合類別
    //   java.lang.*       ：允許 String、Enum、Number 等基本類別
    //   java.awt.Color    ：允許顏色
    //   !*                ：除此之外全部拒絕 (! 代表拒絕，* 代表任何類別)
    //   maxdepth          ：物件巢狀的最大深度 (群組可以巢狀，所以給寬鬆一點)
    //   maxrefs           ：檔案中物件參照的最大數量，防止用超大檔案耗盡記憶體
    //   maxbytes          ：檔案最大位元組數 (50MB)
    // 另外：float[]、int[] 這類「基本型別陣列」不受類別規則約束，會直接允許 (Color 內部就有用到 float[])。
    private static final ObjectInputFilter LOAD_FILTER = ObjectInputFilter.Config.createFilter(
        "maxdepth=200;maxrefs=1000000;maxbytes=52428800;"
        + "shape.*;java.util.*;java.lang.*;java.awt.Color;!*"
    );

    // private 建構子：工具類別不應該被 new 出來
    private DiagramFileIO() {}

    // ==========================================
    // 存檔：把整個圖形清單寫進檔案
    // ==========================================
    public static void save(File file, List<Shape> shapes) throws IOException {
        // try-with-resources：括號內開啟的資源 (串流) 會在區塊結束時「自動 close」，
        // 即使中途丟出例外也一樣，不必自己寫 finally 關檔，避免檔案被鎖住或資料沒寫完。
        // 串流是一層一層包起來的 (Decorator Pattern)：
        //   FileOutputStream    ：負責真正寫進硬碟的檔案
        //   BufferedOutputStream：加上緩衝區，累積一批資料再寫入，減少硬碟 I/O 次數
        //   ObjectOutputStream  ：負責把 Java 物件轉成位元組
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)))) {
            // 關鍵：整個清單「一次」writeObject 寫出。
            // Java 序列化在同一次寫入中，會記住「哪些物件已經寫過」，遇到同一個物件第二次只寫一個參照編號。
            // 因此 ConnectionLine 參照的 Port，與 BasicObject 持有的 Port，讀回來後仍然是「同一個物件」，
            // 連線依然綁在物件的控制點上，拖曳物件時線會正確跟著走。
            // 複製成新的 ArrayList 再寫：因為 Canvas.getShapes() 回傳的是 unmodifiable view，
            // 它的實作類別是 Collections 的內部類別，讀回來時不方便當成一般清單使用。
            out.writeObject(new ArrayList<>(shapes));
        }
    }

    // ==========================================
    // 讀檔：從檔案還原圖形清單
    // ==========================================
    public static List<Shape> load(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            // 先掛上白名單，才開始 readObject；被拒絕的類別會丟出 InvalidClassException
            in.setObjectInputFilter(LOAD_FILTER);
            Object data = in.readObject();

            // 驗證讀進來的資料結構是否正確。
            // 這裡的 instanceof 和專案「避免 instanceof」的慣例並不衝突：
            // 慣例針對的是「依型別決定行為」(應該用多型)；這裡是「驗證外部輸入」——
            // 檔案內容不可信任，必須在轉型前確認它真的是我們預期的型別，否則會在之後的程式中爆出 ClassCastException。
            if (!(data instanceof List)) {
                throw new InvalidObjectException("檔案內容不是 UML 圖形清單");
            }
            List<Shape> result = new ArrayList<>();
            for (Object item : (List<?>) data) {
                if (!(item instanceof Shape)) {
                    throw new InvalidObjectException("檔案中含有非圖形的資料");
                }
                result.add((Shape) item);
            }
            return result;
        }
    }

    // ==========================================
    // 匯出 PNG：把已經畫好的影像寫成圖片檔
    // ==========================================
    // 影像的繪製交給 Canvas.renderToImage() (它最清楚圖形怎麼畫)，這裡只負責寫檔。
    public static void exportPng(File file, BufferedImage image) throws IOException {
        // ImageIO.write 回傳 false 代表「找不到能寫這種格式的編碼器」，並不會丟例外，所以要自己檢查
        if (!ImageIO.write(image, IMAGE_EXTENSION, file)) {
            throw new IOException("系統不支援輸出 PNG 格式");
        }
    }
}
