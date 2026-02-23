import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.session.SessionQueryDataSet;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class TestIginxQuery {
    private static long toMillis(String time) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime dt = LocalDateTime.parse(time, formatter);
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static void main(String[] args) throws Exception {
        Session session = new Session("127.0.0.1", 6888, "root", "root");
        session.openSession();
        List<String> paths = Arrays.asList(
            "root.demo.t1.temperature",
            "root.demo.t1.pressure",
            "root.demo.t1.flow"
        );
        long startMs = toMillis("2026-02-18 19:50:00");
        long endMs = toMillis("2026-02-18 20:10:00");

        long startNs = startMs * 1_000_000L;
        long endNs = endMs * 1_000_000L;

        SessionQueryDataSet dataSetNs = session.queryData(paths, startNs, endNs);
        long[] keysNs = dataSetNs.getKeys();
        System.out.println("ns keys size=" + keysNs.length);

        SessionQueryDataSet dataSetMs = session.queryData(paths, startMs, endMs, null,
            cn.edu.tsinghua.iginx.thrift.TimePrecision.MS);
        long[] keysMs = dataSetMs.getKeys();
        System.out.println("ms keys size=" + keysMs.length);
        session.closeSession();
    }
}
