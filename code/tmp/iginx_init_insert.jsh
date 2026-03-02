import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.thrift.DataType;
import java.util.*;

Session session = new Session("127.0.0.1", 6888, "root", "root");
session.openSession();

List<String> paths = new ArrayList<>();
paths.add("demo.__init__");
long[] keys = new long[]{1L};
Object[] values = new Object[]{ new Object[]{ 1L } };
List<DataType> types = new ArrayList<>();
types.add(DataType.LONG);

try {
  session.insertRowRecords(paths, keys, values, types, null);
  System.out.println("OK: insertRowRecords");
} catch (Exception e) {
  System.out.println("FAIL: insertRowRecords -> " + e.getMessage());
}

session.closeSession();
/exit
