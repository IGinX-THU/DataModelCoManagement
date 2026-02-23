import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.thrift.DataType;
import java.util.Arrays;
import java.util.List;

public class TestIginxInsert {
    public static void main(String[] args) throws Exception {
        Session session = new Session("127.0.0.1", 6888, "root", "root");
        session.openSession();
        List<String> paths = Arrays.asList(
            "root.demo.t1.temperature",
            "root.demo.t1.pressure",
            "root.demo.t1.flow"
        );
        long[] keys = new long[] {
            1771416000000000000L,
            1771416060000000000L
        };
        Object[] valuesList = new Object[] {
            new Object[] {22.1f, 22.4f},
            new Object[] {1.02d, 1.04d},
            new Object[] {0.80d, 0.82d}
        };
        List<DataType> types = Arrays.asList(DataType.FLOAT, DataType.DOUBLE, DataType.DOUBLE);
        session.insertColumnRecords(paths, keys, valuesList, types);
        session.closeSession();
        System.out.println("iginx insert done");
    }
}
