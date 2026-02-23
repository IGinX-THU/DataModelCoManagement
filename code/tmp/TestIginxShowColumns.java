import cn.edu.tsinghua.iginx.session.Session;
import java.util.List;

public class TestIginxShowColumns {
    public static void main(String[] args) throws Exception {
        Session session = new Session("127.0.0.1", 6888, "root", "root");
        session.openSession();
        List columns = session.showColumns();
        System.out.println("columns size=" + columns.size());
        for (Object c : columns) {
            System.out.println(c);
        }
        session.closeSession();
    }
}
