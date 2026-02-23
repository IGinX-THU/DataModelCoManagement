import org.apache.zookeeper.ZooKeeper;
import java.util.List;

public class ZkDeleteFragmentsAndUnits {
    public static void main(String[] args) throws Exception {
        ZooKeeper zk = new ZooKeeper("127.0.0.1:2181", 3000, event -> {});
        deleteRecursive(zk, "/fragment");
        deleteRecursive(zk, "/unit");
        zk.close();
    }

    private static void deleteRecursive(ZooKeeper zk, String path) throws Exception {
        if (zk.exists(path, false) == null) {
            return;
        }
        List<String> children = zk.getChildren(path, false);
        for (String child : children) {
            deleteRecursive(zk, path + "/" + child);
        }
        zk.delete(path, -1);
        System.out.println("deleted: " + path);
    }
}
