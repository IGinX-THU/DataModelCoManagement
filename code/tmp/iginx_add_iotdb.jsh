import cn.edu.tsinghua.iginx.session.Session;

Session session = new Session("127.0.0.1", 6888, "root", "root");
session.openSession();

String addSql = "ADD STORAGEENGINE (\"127.0.0.1\", 6667, \"iotdb12\", \"has_data=true, is_read_only=false, username=root, password=root, database=root, data_prefix=demo\");";
try {
  session.executeSql(addSql);
  System.out.println("OK: " + addSql);
} catch (Exception e) {
  System.out.println("FAIL: " + addSql + " -> " + e.getMessage());
}

session.closeSession();

/exit
