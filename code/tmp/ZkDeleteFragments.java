import org.apache.zookeeper.ZooKeeper;
import java.util.List;

public class ZkDeleteFragments {
    public static void main(String[] args) throws Exception {
        String zkAddr = "127.0.0.1:2181";
        String targetNode = "\\N-\\N"; // 实际节点名为 \N-\N
        ZooKeeper zk = new ZooKeeper(zkAddr, 3000, event -> {});
        String fragmentRoot = "/fragment";
        List<String> children = zk.getChildren(fragmentRoot, false);
        if (!children.contains(targetNode)) {
            System.out.println("/fragment 下未找到节点: " + targetNode);
            zk.close();
            return;
        }
        String targetPath = fragmentRoot + "/" + targetNode;
        List<String> keys = zk.getChildren(targetPath, false);
        for (String key : keys) {
            String fullPath = targetPath + "/" + key;
            zk.delete(fullPath, -1);
            System.out.println("已删除: " + fullPath);
        }
        zk.delete(targetPath, -1);
        System.out.println("已删除: " + targetPath);
        zk.close();
    }
}
