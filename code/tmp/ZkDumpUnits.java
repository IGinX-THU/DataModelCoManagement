import org.apache.zookeeper.ZooKeeper;
import java.util.List;

public class ZkDumpUnits {
    public static void main(String[] args) throws Exception {
        ZooKeeper zk = new ZooKeeper("127.0.0.1:2181", 3000, event -> {});
        dump(zk, "/unit");
        dump(zk, "/storage");
        zk.close();
    }

    private static void dump(ZooKeeper zk, String path) throws Exception {
        List<String> children = zk.getChildren(path, false);
        for (String child : children) {
            String fullPath = path + "/" + child;
            byte[] data = zk.getData(fullPath, false, null);
            String json = data == null ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println(fullPath + " => " + json);
        }
    }
}
