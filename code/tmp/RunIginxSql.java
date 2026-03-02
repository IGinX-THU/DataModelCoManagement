import cn.edu.tsinghua.iginx.session.Session;

public class RunIginxSql {
    public static void main(String[] args) throws Exception {
        Session session = new Session("127.0.0.1", 6888, "root", "root");
        session.openSession();
        String[] sqls = new String[] {
            "REMOVE STORAGEENGINE (\"127.0.0.1\", 6667, \"\", \"root.demo\");",
            "REMOVE STORAGEENGINE (\"127.0.0.1\", 6667, \"root.demo\", \"root.demo\");"
        };
        for (String sql : sqls) {
            try {
                session.executeSql(sql);
                System.out.println("OK: " + sql);
            } catch (Exception e) {
                System.out.println("FAIL: " + sql + " -> " + e.getMessage());
            }
        }
        session.closeSession();
    }
}
