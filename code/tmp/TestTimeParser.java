import com.xmu.iginx.assoc.modules.data.util.TimeParser;

public class TestTimeParser {
    public static void main(String[] args) {
        String v = "2026-02-18 20:00:00";
        long millis = TimeParser.parseToMillis(v, null);
        long nanos = TimeParser.toNano(millis);
        System.out.println("millis=" + millis);
        System.out.println("nanos=" + nanos);
    }
}
