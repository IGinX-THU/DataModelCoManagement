import org.apache.zookeeper.ZooKeeper;
import java.util.List;

public class ZkList {
    public static void main(String[] args) throws Exception {
        ZooKeeper zk = new ZooKeeper("127.0.0.1:2181", 3000, event -> {});
        List<String> children = zk.getChildren("/", false);
        System.out.println(children);
        zk.close();
    }
}
