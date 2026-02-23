import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestCsvParse {
    public static void main(String[] args) throws Exception {
        String path = "E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/examples/model/predict_power_demo.csv";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            Map<String, Integer> columnIndex = new HashMap<>();
            long count = 0;
            long firstTs = -1;
            long lastTs = -1;
            while ((line = reader.readLine()) != null) {
                List<String> values = CsvUtils.parseLine(line);
                if (header) {
                    for (int i = 0; i < values.size(); i++) {
                        columnIndex.put(values.get(i).trim(), i);
                    }
                    header = false;
                    System.out.println("header=" + values);
                    continue;
                }
                Integer idx = columnIndex.get("timestamp");
                String value = values.get(idx);
                long millis = TimeParser.parseToMillis(value, null);
                long nanos = TimeParser.toNano(millis);
                if (count == 0) {
                    firstTs = nanos;
                }
                lastTs = nanos;
                count++;
            }
            System.out.println("count=" + count);
            System.out.println("firstTs=" + firstTs);
            System.out.println("lastTs=" + lastTs);
        }
    }
}
