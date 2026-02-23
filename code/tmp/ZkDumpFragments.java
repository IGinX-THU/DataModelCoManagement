import org.apache.zookeeper.ZooKeeper;
import java.util.List;

public class ZkDumpFragments {
    public static void main(String[] args) throws Exception {
        ZooKeeper zk = new ZooKeeper("127.0.0.1:2181", 3000, event -> {});
        List<String> columns = zk.getChildren("/fragment", false);
        for (String col : columns) {
            String colPath = "/fragment/" + col;
            List<String> keys = zk.getChildren(colPath, false);
            for (String key : keys) {
                String fullPath = colPath + "/" + key;
                byte[] data = zk.getData(fullPath, false, null);
                String json = data == null ? "" : new String(data, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println(fullPath + " => " + json);
            }
        }
        zk.close();
    }
}
