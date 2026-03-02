import cn.edu.tsinghua.iginx.session.Session;

Session session = new Session("127.0.0.1", 6888, "root", "root");
session.openSession();

String[] sqls = new String[] {
  "REMOVE STORAGEENGINE (\"127.0.0.1\", 6667, \"\", \"\");",
  "REMOVE STORAGEENGINE (\"127.0.0.1\", 6667, \"\", \"demo\");",
  "REMOVE STORAGEENGINE (\"127.0.0.1\", 6667, \"demo\", \"demo\");",
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

String addSql = "ADD STORAGEENGINE (\"127.0.0.1\", 6667, \"iotdb12\", \"has_data=true, is_read_only=false, username=root, password=root, database=root, data_prefix=demo\");";
try {
  session.executeSql(addSql);
  System.out.println("OK: " + addSql);
} catch (Exception e) {
  System.out.println("FAIL: " + addSql + " -> " + e.getMessage());
}

session.closeSession();

/exit
