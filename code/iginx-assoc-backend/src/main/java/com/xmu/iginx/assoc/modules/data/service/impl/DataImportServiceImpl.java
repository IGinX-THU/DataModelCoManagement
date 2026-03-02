package com.xmu.iginx.assoc.modules.data.service.impl;

import cn.edu.tsinghua.iginx.session.Column;
import cn.edu.tsinghua.iginx.session.ClusterInfo;
import cn.edu.tsinghua.iginx.session.QueryDataSet;
import cn.edu.tsinghua.iginx.session.Session;
import cn.edu.tsinghua.iginx.thrift.DataType;
import cn.edu.tsinghua.iginx.thrift.StorageEngineInfo;
import com.alibaba.excel.EasyExcel;
import com.xmu.iginx.assoc.common.exception.BizException;
import com.xmu.iginx.assoc.framework.iginx.IginxStorageWrapper;
import com.xmu.iginx.assoc.modules.data.dto.DataSourceConnectionConfig;
import com.xmu.iginx.assoc.modules.data.dto.StructuredImportRequest;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesColumnMappingDTO;
import com.xmu.iginx.assoc.modules.data.dto.TimeSeriesImportRequest;
import com.xmu.iginx.assoc.modules.data.enums.DataSourceType;
import com.xmu.iginx.assoc.modules.data.model.DataSourceDetail;
import com.xmu.iginx.assoc.modules.data.service.DataImportService;
import com.xmu.iginx.assoc.modules.data.service.DataSourceAccessor;
import com.xmu.iginx.assoc.modules.data.util.CsvUtils;
import com.xmu.iginx.assoc.modules.data.util.DataFileStorageService;
import com.xmu.iginx.assoc.modules.data.util.ExcelRowListener;
import com.xmu.iginx.assoc.modules.data.util.IginxDataTypeConverter;
import com.xmu.iginx.assoc.modules.data.util.IginxStorageEngineHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredQueryHelper;
import com.xmu.iginx.assoc.modules.data.util.IginxStructuredUtils;
import com.xmu.iginx.assoc.modules.data.util.StructuredKeyGenerator;
import com.xmu.iginx.assoc.modules.data.util.TimeParser;
import com.xmu.iginx.assoc.modules.data.util.TimeSeriesPathUtils;
import com.xmu.iginx.assoc.modules.data.vo.DataImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataImportServiceImpl implements DataImportService {

    private static final int TS_BATCH_SIZE = 10000;
    private static final int STRUCT_BATCH_SIZE = 2000;
    private static final String BOM = "\uFEFF";

    private final DataSourceAccessor dataSourceAccessor;
    private final IginxStorageWrapper iginxStorageWrapper;
    private final DataFileStorageService fileStorageService;
    private final IginxStructuredQueryHelper structuredQueryHelper;
    private final IginxStorageEngineHelper storageEngineHelper;

    @Override
    public DataImportResultVO importTimeSeries(TimeSeriesImportRequest request, MultipartFile file) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.INFLUXDB, DataSourceType.IOTDB);
        String mountPath = detail.entity().getMountPath();
        if (detail.type() == DataSourceType.IOTDB) {
            mountPath = TimeSeriesPathUtils.normalizeIotdbMountPath(mountPath);
        }
        String storageGroup = TimeSeriesPathUtils.resolvePathUnderMount(request.getStorageGroup(), mountPath, true);
        ensureStorageGroupExists(storageGroup);
        String extension = getExtension(file);
        // 闂傚倸鍊搁崐宄懊归崶褏鏆﹂柣銏㈩焾缁愭鏌熼幍顔碱暭闁稿绻濆鍫曞醇濮橆厽鐝旂紓浣界堪閸婃洝鐏冮梺鎸庣箓閹冲酣寮抽悙鐑樼厱濠电姴娲﹀☉褔妫佹径鎰厽婵☆垳鍎ら埢鏇㈡煕鎼达紕绠插ǎ鍥э躬椤㈡洟鏁愯箛姘ｅ亾閸ф鐓涢悘鐐插⒔濞叉潙鈹戦敍鍕幋妞ゃ垺鐩幃娆撳箹椤撴稑浜剧€广儱鎷嬪〒濠氭煏閸繃顥滃┑顔艰嫰閳规垿顢欓崫鍕ㄥ亾濠靛违闁告劦鍠栧敮闂佸啿鎼崐鎼佸焵椤掑倸鍘撮柟顔款潐閵堬箓骞愭惔顔诲摋闂備礁鎲￠敃鈺呭磻婵犲洤钃熼柨婵嗘閸庣喖鏌ㄥ☉妯侯仹缂侇喚鏁哥槐鎾寸瑹閸パ勭亶闂佸湱鎳撳ú顓㈠箖娴兼惌鏁婄痪鏉垮船閹垶绻濋姀锝嗙【妞ゆ垵鎳忕粋鎺楊敇閵忊檧鎷洪梺鍛婄箓鐎氬嘲危瑜版帗鍊电紒妤佺☉閸熺娀寮稿澶嬬厪闊洤顑呴埀顒佺墵閸?IGinX
        TimeSeriesImportContext context = new TimeSeriesImportContext(storageGroup, request);
        if ("csv".equals(extension)) {
            readCsv(file, context::handleRow);
        } else if ("xlsx".equals(extension) || "xls".equals(extension)) {
            readExcel(file, 0, context::handleRow);
        } else {
            throw BizException.badRequest("\u4ec5\u652f\u6301 CSV \u6216 Excel \u6587\u4ef6");
        }
        context.flush();
        return context.buildResult();
    }

    @Override
    public DataImportResultVO importStructured(StructuredImportRequest request, MultipartFile file) {
        DataSourceDetail detail = dataSourceAccessor.getDetail(request.getSourceId(), DataSourceType.POSTGRESQL);
        ensureRelationalStorageEngine(detail);
        String extension = resolveFileType(request.getFileType(), file);
        if ("sql".equals(extension)) {
            return importStructuredSql(file);
        }
        if (!List.of("csv", "xlsx", "xls").contains(extension)) {
            throw BizException.badRequest("仅支持 CSV、Excel 或 SQL 文件");
        }
        try {
            StructuredImportContext context = new StructuredImportContext(request, detail.entity().getMountPath());
            if ("csv".equals(extension)) {
                readCsv(file, context::handleRow);
            } else {
                readExcel(file, Optional.ofNullable(request.getSheetIndex()).orElse(0), context::handleRow);
            }
            context.flush();
            return context.buildResult();
        } catch (BizException e) {
            throw e;
        } catch (Exception ex) {
            throw BizException.internal("结构化导入失败: " + ex.getMessage());
        }
    }

    private DataImportResultVO importStructuredSql(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder buffer = new StringBuilder();
            String line;
            long total = 0;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    String sql = buffer.toString().trim();
                    if (!sql.isBlank()) {
                        structuredQueryHelper.executeSql(sql);
                        total++;
                    }
                    buffer.setLength(0);
                }
            }
            if (!buffer.isEmpty()) {
                String sql = buffer.toString().trim();
                if (!sql.isBlank()) {
                    structuredQueryHelper.executeSql(sql);
                    total++;
                }
            }
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(total);
            result.setFailed(0);
            return result;
        } catch (Exception ex) {
            throw BizException.internal("SQL 导入失败: " + ex.getMessage());
        }
    }
    private void readCsv(MultipartFile file, RowConsumer consumer) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                List<String> values = CsvUtils.parseLine(line);
                consumer.accept(values, isHeader);
                isHeader = false;
            }
        } catch (Exception ex) {
            throw BizException.internal("闂傚倸鍊搁崐宄懊归崶褏鏆﹂柛顭戝亝閸欏繘鏌℃径瀣婵炲樊浜滈悡娑㈡煕濠娾偓閻掞箓寮查鍫熷仭婵犲﹤瀚悘鏉戔攽?CSV 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晝閳ь剛澹曢崷顓犵＜閻庯綆鍋撶槐鈺傜箾瀹割喕绨奸柡鍛叀閺屾稑鈽夐崣妯煎嚬闂佽楠搁…宄邦潖濞差亝顥堟繛鎴炴皑閻ｉ箖姊洪柅鐐茶嫰婢ь喚鐥紒銏犲箻缂侇噯缍侀幃娆撴倻濡厧骞愬┑鐘灱濞夋盯鏁冮敂鑺ユ珷闂侇剙绉甸悡鏇㈡倵閿濆簼鎲炬俊鎻掑悑閵? " + ex.getMessage());
        }
    }

    private void readExcel(MultipartFile file, Integer sheetIndex, RowConsumer consumer) {
        try {
            int index = Optional.ofNullable(sheetIndex).orElse(0);
            EasyExcel.read(file.getInputStream(), new ExcelRowListener((row, header) -> consumer.accept(row, header)))
                .sheet(index)
                .doRead();
        } catch (Exception ex) {
            throw BizException.internal("闂傚倸鍊搁崐宄懊归崶褏鏆﹂柛顭戝亝閸欏繘鏌℃径瀣婵炲樊浜滈悡娑㈡煕濠娾偓閻掞箓寮查鍫熷仭婵犲﹤瀚悘鏉戔攽?Excel 闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晝閳ь剛澹曢崷顓犵＜閻庯綆鍋撶槐鈺傜箾瀹割喕绨奸柡鍛叀閺屾稑鈽夐崣妯煎嚬闂佽楠搁…宄邦潖濞差亝顥堟繛鎴炴皑閻ｉ箖姊洪柅鐐茶嫰婢ь喚鐥紒銏犲箻缂侇噯缍侀幃娆撴倻濡厧骞愬┑鐘灱濞夋盯鏁冮敂鑺ユ珷闂侇剙绉甸悡鏇㈡倵閿濆簼鎲炬俊鎻掑悑閵? " + ex.getMessage());
        }
    }

    private String getExtension(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveFileType(String fileType, MultipartFile file) {
        if (fileType != null && !fileType.isBlank()) {
            return fileType.trim().toLowerCase(Locale.ROOT);
        }
        return getExtension(file);
    }

    private void ensureStorageGroupExists(String storageGroup) {
        if (storageGroup == null || storageGroup.isBlank()) {
            return;
        }
        String initPath = TimeSeriesPathUtils.joinPath(storageGroup, "__init__");
        iginxStorageWrapper.executeWithSession(session -> {
            if (columnExists(session, initPath)) {
                return null;
            }
            long timestamp = System.currentTimeMillis() * 1_000_000;
            session.insertRowRecords(List.of(initPath), new long[]{timestamp},
                new Object[]{new Object[]{0L}}, List.of(DataType.LONG), null);
            return null;
        });
    }

    private boolean columnExists(Session session, String path) throws Exception {
        List<Column> columns = session.showColumns();
        if (columns == null || columns.isEmpty()) {
            return false;
        }
        for (Column column : columns) {
            if (column == null || column.getPath() == null) {
                continue;
            }
            if (path.equals(column.getPath())) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(List<String> row, boolean header);
    }

    private class TimeSeriesImportContext {
        private final String storageGroup;
        private final TimeSeriesImportRequest request;
        private final List<String> paths = new ArrayList<>();
        private final List<String> mappingColumns = new ArrayList<>();
        private final List<DataType> dataTypes = new ArrayList<>();
        private final List<Boolean> explicitTypes = new ArrayList<>();
        private final List<Long> keys = new ArrayList<>();
        private final List<List<Object>> values = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final Map<String, Integer> columnIndex = new HashMap<>();
        private boolean headerReady = false;
        private long total = 0;
        private long success = 0;
        private long failed = 0;

        private TimeSeriesImportContext(String storageGroup, TimeSeriesImportRequest request) {
            this.storageGroup = storageGroup;
            this.request = request;
        }

        private void handleRow(List<String> row, boolean header) {
            if (header) {
                buildHeader(row);
                return;
            }
            if (!headerReady) {
                throw BizException.badRequest("\u65f6\u95f4\u5e8f\u5217\u5bfc\u5165\u7f3a\u5c11\u8868\u5934");
            }
            total++;
            try {
                long timestamp = parseTimestamp(row);
                List<Object> rowValues = new ArrayList<>(mappingColumns.size());
                for (int i = 0; i < mappingColumns.size(); i++) {
                    String column = mappingColumns.get(i);
                    Integer idx = columnIndex.get(column);
                    String rawValue = idx == null || idx >= row.size() ? null : row.get(idx);
                    Object value = IginxDataTypeConverter.parseValue(rawValue, dataTypes.get(i));
                    rowValues.add(value);
                }
                keys.add(timestamp);
                for (int i = 0; i < rowValues.size(); i++) {
                    values.get(i).add(rowValues.get(i));
                }
                success++;
                if (keys.size() >= TS_BATCH_SIZE) {
                    flush();
                }
            } catch (Exception ex) {
                failed++;
                errors.add(ex.getMessage());
            }
        }

        private void buildHeader(List<String> header) {
            columnIndex.clear();
            for (int i = 0; i < header.size(); i++) {
                String name = header.get(i);
                if (i == 0 && name != null && name.startsWith(BOM)) {
                    name = name.replace(BOM, "");
                }
                columnIndex.put(name.trim(), i);
            }
            String timestampColumn = request.getTimestampColumn();
            if (timestampColumn == null || !columnIndex.containsKey(timestampColumn)) {
                throw BizException.badRequest("闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晝閳ь剟鎮块濮愪簻闁规澘鐖煎顕€鏌涚€ｎ亶妯€闁哄矉缍侀獮姗€宕樺顔兼暔婵犵數鍋為崹顖炲垂瑜版帗鐓ラ柕鍫濇缁诲棝鏌曢崼婵嗏偓鍛婄閸撗呯＝濞达絽鎼鎾剁磽瀹ュ嫮绐旂€殿喛顕ч埥澶愬閻樼數鏉告俊鐐€栭幐楣冨磻閻旇櫣鐭堥柣鎴ｅГ閳锋垿鎮归崶顏勭毢缂佺姵鐓￠弻锝夋偄閺夋垵濮﹂梺绯曟杺閸庢彃顕ラ崟顖氱疀妞ゆ帒鍋嗛崯瀣煟鎼淬値娼愭繛鍙夌墪閳绘棃鏁冮崒姘兼綂闂侀潧鐗嗗Λ娑㈠储娴犲鐓欓柣鎴灻埢鏇犫偓瑙勬礃缁诲牓鐛€ｎ亖鏀介柛顐ゅ枑椤?");
            }
            List<TimeSeriesColumnMappingDTO> mappings = request.getMappings();
            if (mappings == null || mappings.isEmpty()) {
                mappings = header.stream()
                    .filter(col -> !col.equals(timestampColumn))
                    .map(col -> {
                        TimeSeriesColumnMappingDTO dto = new TimeSeriesColumnMappingDTO();
                        dto.setColumn(col);
                        dto.setTarget(storageGroup + "." + col);
                        return dto;
                    })
                    .collect(Collectors.toList());
            }
            for (TimeSeriesColumnMappingDTO mapping : mappings) {
                if (mapping.getColumn() == null || mapping.getColumn().isBlank()) {
                    continue;
                }
                if (!columnIndex.containsKey(mapping.getColumn())) {
                    throw BizException.badRequest("\u5217\u4e0d\u5b58\u5728: " + mapping.getColumn());
                }
                String target = mapping.getTarget();
                if (target == null || target.isBlank()) {
                    throw BizException.badRequest("\u76ee\u6807\u6d4b\u70b9\u4e0d\u80fd\u4e3a\u7a7a");
                }
                // 闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ磵閳ь剨绠撳畷濂稿閳ュ啿绨ラ梻浣烘嚀椤曨厽鎱ㄩ幘顔煎瀭婵犻潧鐗冮崑鎾诲礂婢跺﹣澹曢梻渚€鈧偛鑻晶鎾煙瀹曞洤浠辩€规洩绻濋幃娆撳垂椤愶綆鍟庨梻鍌欑劍閹爼宕曢鐐茬閻忕偛澧介々鐑芥煙閻戞﹩娈曢柍閿嬪灴濮婂宕奸悢鍓佺箒濠碘剝褰冮悥濂稿蓟濞戞瑦鍎熼柍銉ョ枃閸╃偛顪冮妶鍡樼┛缂傚秳绶氶悰顕€骞囬鐔峰妳闂佹寧绻傚ù鍌炲疮鎼淬劍鈷掑ù锝呮啞閹牊銇勯敂鍨祮鐎规洘妞藉畷濂稿Ψ閵壯嶇吹闂備胶顢婇幓顏堟⒔閸曨垱鍋傞柡鍥ュ灪閸婄敻鏌ㄥ┑鍡樺櫣妞わ絾濞婇弻鐔碱敊缁楀搫浠梺鍝勭灱閸犳牠骞冮悜钘夌骇婵炲棗褰炵槐鏃€淇婇悙顏勨偓鎴﹀垂閾忓湱鐭欓柟杈捐缂嶆牗绻濋棃娑欏缂傚秴娲弻宥夊传閸曨偅娈紓浣风筏缁犳挸顫忔繝姘＜婵炲棙鍨垫俊浠嬫煟鎼达絿鎳楅柛娑卞灣閻撴捇姊虹涵鍛涧闂傚嫬瀚板畷鏇炍旈崨顔惧幈闁诲繒鍋涙晶浠嬪煡婢跺浜滈柡鍥舵線閹查箖鏌＄仦鐣屝х€规洦鍋婂畷鐔碱敆閳ь剙鈻嶉敐鍥╃＝濞达絾褰冩禍楣冩⒑缁嬫寧婀板瑙勬礋瀹曟垿骞橀懡銈呯ウ闂佸壊鐓堥崰鏍ㄦ叏鎼淬劍鈷戦柣鐔告緲閺嗗崬螖閻樿櫕鍊愰柛鈹垮劜瀵板嫭鎯旈鍙晠姊绘担鍝ユ瀮妞ゎ偄顦靛畷鍦崉娓氼垰娈梺鍛婃处閸婄娀鎮㈤崨濠傤潯闂佽顔栭崰姘枖閸ф鈷掗柛灞剧懅椤︼箑顭块悷甯含闁诡噯绻濆畷濂告偄娓氼垱閿ゆ俊鐐€曠换鎰偓姘煎櫍瀵娊鏁冮崒娑氬帾婵犮垼娉涢悧鍡涘礉閹绢喗鐓曟慨姗嗗墻閸庢棃鏌＄仦鍓р槈妞ゎ厹鍔戦崺鈧い鎺戝€婚惌鍡椕归敐鍥┿€婇柡瀣煥閳规垿宕掑顓炴殘闂?IGinX 闂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柛顐ｆ磸閳ь兛鐒︾换婵嬪礋椤撶媭妲卞┑鐐存綑閸氬顭囧▎鎴濐棜闁秆勵殕閻撶喖鏌曡箛瀣労闁绘帡绠栭弻?
                String normalizedTarget = TimeSeriesPathUtils.resolvePathUnderMount(target.trim(), storageGroup, false);
                paths.add(normalizedTarget);
                mappingColumns.add(mapping.getColumn());
                String rawType = mapping.getDataType();
                boolean hasExplicitType = rawType != null && !rawType.isBlank();
                dataTypes.add(IginxDataTypeConverter.parseType(rawType));
                explicitTypes.add(hasExplicitType);
                values.add(new ArrayList<>());
            }
            if (paths.isEmpty()) {
                throw BizException.badRequest("闂傚倸鍊搁崐鎼佸磹妞嬪海鐭嗗〒姘ｅ亾妤犵偞鐗犻、鏇㈡晜閽樺缃曟繝鐢靛Т閿曘倗鈧凹鍣ｉ妴鍛村矗婢跺牅绨婚棅顐㈡处閹哥偓鏅堕弴銏＄厱閻忕偠顕ф慨鍌炴煛鐏炵偓绀嬬€规洜鍘ч埞鎴﹀炊瑜忛悰鈺冪磽娴ｇ懓鍔ゅ褌绮欏畷鎴﹀箻鐠囧弬锕傛煕閺囥劌鐏犵紒顐㈢Ч閺屾稓浠﹂幑鎰棟婵炲濮甸敃銏ゅ蓟閿濆鍋愰弶鍫氭櫅婵′粙姊虹紒妯绘儓缂佺粯绻堥悰顕€宕橀妸搴㈡瀹曘劑顢欓梻瀵哥处闂傚倸鍊搁崐鎼佹偋婵犲嫮鐭欓柟瀵稿仧椤╅攱鎱ㄥ璇蹭壕濠殿喖锕ュ钘夌暦閵婏妇绡€闁告洦鍓欏▍鎴炵節绾版ɑ顫婇柛瀣噽閹广垽宕奸妷褍绁﹂梺绯曞墲缁嬫垹绮堥崘鈹夸簻闁哄啫娲ゆ禍瑙勩亜閿旇姤绶叉い顏勫暣婵″爼宕卞Δ鍐噯闂備胶顭堥敃銈夋偉閻撳海鏆﹂柟杈鹃檮閸婄兘鏌ｉ幋鐐冩岸骞忛搹鍦＝闁稿本鐟ч崝宥夋煥濮橆兘鏀芥い鏂垮悑濠€浼存煏閸パ冾伃妞ゃ垺娲熸慨鈧柍鍝勫€愰敃鍌涚厱婵°倕瀚崥顐︽煙閸欏鎽冪紒鐘崇洴瀵噣宕掗妶鍡涙暅?");
            }
            alignDataTypesWithExisting();
            headerReady = true;
        }

        private void alignDataTypesWithExisting() {
            List<Column> columns = iginxStorageWrapper.executeWithSession(session -> session.showColumns());
            if (columns == null || columns.isEmpty()) {
                return;
            }
            Map<String, DataType> existingTypes = new HashMap<>();
            for (Column column : columns) {
                if (column == null || column.getPath() == null) {
                    continue;
                }
                existingTypes.put(column.getPath(), column.getDataType());
            }
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                DataType existingType = existingTypes.get(path);
                if (existingType == null) {
                    continue;
                }
                DataType requestedType = dataTypes.get(i);
                if (explicitTypes.get(i)) {
                    if (!existingType.equals(requestedType)) {
                        throw BizException.badRequest("婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾惧鏌ｉ幇顒佲枙闁绘帟濮ょ换娑㈠幢濡粯鍎庨梺杞扮鐎氫即寮诲☉銏犖ㄦい鏃傚帶椤晝绱掗悙顒€鍔ゆい顓犲厴瀵濡搁妷銏℃杸闂佺硶鍓濋敋缂佹劖鐩弻锝堢疀閹惧墎顔婇梺杞扮椤兘鐛崘顓滀汗闁圭儤鍨归崐鐐差渻閵堝懐绠伴悗姘煎枛琚欓柕蹇嬪€栭埛鎴︽⒑椤愩倕浠滈柤娲诲灡閺呭爼骞橀鐣屽幐闂侀€炲苯澧繛鐓庣箻閸╋繝宕橀鍡楀箚婵犵數濮伴崹鐓庘枖濞戔懇鈧箓顢楅崟顐バ曢柣搴秵閸犳鎮￠弴銏＄厸闁搞儯鍎辨俊鑲┾偓娈垮枟閸庢娊婀侀梺缁樻尭鐎涒晠宕板Ο缁樺弿? " + path
                            + " 闂傚倸鍊峰ù鍥敋瑜嶉湁闁绘垼妫勭粻鐘绘煙閹规劦鍤欑紒鐘靛枛濮婁粙宕堕鈧闂佸湱澧楀妯肩矆閸愨斂浜滈柡鍐ㄦ处椤ュ霉閻樿鎲炬慨濠冩そ濡啫霉閵夈儳澧︾€殿喗褰冮埥澶愬閻樻鍞?" + existingType + "闂傚倸鍊搁崐鎼佸磹閻戣姤鍊块柨鏃堟暜閸嬫挾绮☉妯诲櫧闁活厽鐟╅弻鐔告綇妤ｅ啯顎嶉梺鎼炲€栭崝鏍Φ閸曨垰鍐€妞ゆ劑鍨规竟澶愭⒑鏉炴壆顦︽い鎴濐槸椤繐煤椤忓秵鏅㈤梺閫炲苯澧扮紒顔芥⒒閳ь剟娼ч幗婊堟儗閸℃稒鐓曠€光偓閳ь剟宕戦悙鐑樺亗闊洦鎼╅悢鍡涙偣閸ワ絺鍋撳畷鍥﹀摋婵犵妲呴崑鎺楀储婵傜硶鈧箓宕堕鈧柋鍥煛閸モ晛鏆遍柟閿嬫そ濮婅櫣娑甸崨顓濇睏闂佺顑嗙粙鎺撶┍婵犲洤绀傜紒妤勬〃缁ㄥ姊洪悷鎵憼缂佽鍊块、鏃堫敃閿旂晫鍘?" + requestedType);
                    }
                } else {
                    dataTypes.set(i, existingType);
                }
            }
        }

        private long parseTimestamp(List<String> row) {
            Integer idx = columnIndex.get(request.getTimestampColumn());
            if (idx == null || idx >= row.size()) {
            throw new IllegalArgumentException("\u65f6\u95f4\u6233\u5217\u7f3a\u5931");
            }
            String value = row.get(idx);
            return TimeParser.toNano(TimeParser.parseToMillis(value, request.getTimestampFormat()));
        }

        private void flush() {
            if (keys.isEmpty()) {
                return;
            }
            // \u6279\u91cf\u5199\u5165\u65f6\u5e8f\u6570\u636e\uff0c\u6309\u5217\u5199\u5165\u63d0\u9ad8\u6548\u7387
            long[] keyArray = keys.stream().mapToLong(Long::longValue).toArray();
            Object[] valuesArray = new Object[values.size()];
            for (int i = 0; i < values.size(); i++) {
                valuesArray[i] = values.get(i).toArray();
            }
            iginxStorageWrapper.executeWithSession(session -> {
                session.insertColumnRecords(paths, keyArray, valuesArray, dataTypes);
                return null;
            });
            keys.clear();
            values.forEach(List::clear);
        }

        private DataImportResultVO buildResult() {
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(success);
            result.setFailed(failed);
            return result;
        }
    }

    private static class StructuredBatchRow {
        private final long key;
        private final Object[] values;
        private final List<String> rawRow;

        private StructuredBatchRow(long key, Object[] values, List<String> rawRow) {
            this.key = key;
            this.values = values;
            this.rawRow = rawRow;
        }
    }

    private class StructuredImportContext {
        private static final int KEY_QUERY_BATCH = 500;

        private final StructuredImportRequest request;
        private final String schemaWithMount;
        private final List<String> columns = new ArrayList<>();
        private final Map<String, Integer> columnIndex = new HashMap<>();
        private final List<StructuredBatchRow> batchRows = new ArrayList<>();
        private final List<String> errorMessages = new ArrayList<>();
        private final List<List<String>> errorRows = new ArrayList<>();
        private final Map<String, DataType> columnTypes = new LinkedHashMap<>();
        private List<String> primaryKeys = new ArrayList<>();
        private Integer internalKeyIndex = null;
        private boolean headerReady = false;
        private long total = 0;
        private long success = 0;
        private long failed = 0;
        private final String conflictStrategy;

        private StructuredImportContext(StructuredImportRequest request, String mountPath) {
            this.request = request;
            this.schemaWithMount = IginxStructuredUtils.mergeMountPath(mountPath, request.getSchema());
            this.conflictStrategy = Optional.ofNullable(request.getConflictStrategy())
                .orElse("update")
                .trim()
                .toLowerCase(Locale.ROOT);
        }

        private void handleRow(List<String> row, boolean header) {
            if (header) {
                buildHeader(row);
                return;
            }
            if (!headerReady) {
                throw BizException.badRequest("结构化导入缺少表头");
            }
            total++;
            try {
                StructuredBatchRow batchRow = buildBatchRow(row);
                batchRows.add(batchRow);
                if (batchRows.size() >= STRUCT_BATCH_SIZE) {
                    flush();
                }
            } catch (Exception ex) {
                failed++;
                errorMessages.add(ex.getMessage());
                errorRows.add(normalizeErrorRow(row));
            }
        }

        private void buildHeader(List<String> header) {
            columns.clear();
            columnIndex.clear();
            internalKeyIndex = null;
            for (int i = 0; i < header.size(); i++) {
                String name = header.get(i);
                if (i == 0 && name != null && name.startsWith(BOM)) {
                    name = name.replace(BOM, "");
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                String trimmed = name.trim();
                columnIndex.put(trimmed, i);
                if (IginxStructuredUtils.isInternalKey(trimmed)) {
                    internalKeyIndex = i;
                    continue;
                }
                columns.add(trimmed);
            }
            if (columns.isEmpty()) {
                throw BizException.badRequest("CSV/Excel 表头为空");
            }
            if (request.getPrimaryKeys() != null && !request.getPrimaryKeys().isEmpty()) {
                primaryKeys = request.getPrimaryKeys();
                for (String key : primaryKeys) {
                    if (!columnIndex.containsKey(key)) {
                        throw BizException.badRequest("主键字段不存在: " + key);
                    }
                }
            } else {
                primaryKeys = List.of();
            }
            prepareColumnTypes();
            headerReady = true;
        }

        private void prepareColumnTypes() {
            Map<String, DataType> existing = structuredQueryHelper.loadColumnTypes(schemaWithMount, request.getTable());
            boolean tableExists = existing != null && !existing.isEmpty();
            if (!tableExists && !request.isAutoCreateTable()) {
                throw BizException.badRequest("目标表不存在，请先建表或开启自动建表");
            }
            for (String column : columns) {
                DataType type = existing == null ? null : existing.get(column);
                if (type == null) {
                    type = DataType.BINARY;
                }
                columnTypes.put(column, type);
            }
            if (!tableExists && request.isAutoCreateTable()) {
                createDummyRow();
            }
        }

        private void createDummyRow() {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = defaultValue(columnTypes.get(columns.get(i)));
            }
            StructuredBatchRow dummy = new StructuredBatchRow(IginxStructuredUtils.DUMMY_KEY, values, List.of());
            String sql = buildInsertSql(List.of(dummy));
            structuredQueryHelper.executeSql(sql);
        }

        private StructuredBatchRow buildBatchRow(List<String> row) {
            long key = resolveRowKey(row);
            Object[] values = convertRow(row);
            return new StructuredBatchRow(key, values, new ArrayList<>(row));
        }

        private long resolveRowKey(List<String> row) {
            Long internal = extractInternalKey(row);
            if (internal != null) {
                if (IginxStructuredUtils.isReservedKey(internal)) {
                    throw BizException.badRequest("内部键 _iginx_key 不合法");
                }
                if (internal < 0) {
                    throw BizException.badRequest("内部键 _iginx_key 不能为负数");
                }
                return internal;
            }
            if (!primaryKeys.isEmpty()) {
                Map<String, Object> keyFields = new LinkedHashMap<>();
                for (String key : primaryKeys) {
                    Integer idx = columnIndex.get(key);
                    String raw = idx == null || idx >= row.size() ? null : row.get(idx);
                    String normalized = normalizeCell(raw, idx);
                    if (normalized == null || normalized.isBlank()) {
                        throw BizException.badRequest("主键字段不能为空: " + key);
                    }
                    keyFields.put(key, normalized);
                }
                return StructuredKeyGenerator.hashKey(keyFields);
            }
            return StructuredKeyGenerator.randomKey();
        }

        private Long extractInternalKey(List<String> row) {
            if (internalKeyIndex == null || internalKeyIndex < 0) {
                return null;
            }
            if (internalKeyIndex >= row.size()) {
                return null;
            }
            String raw = normalizeCell(row.get(internalKeyIndex), internalKeyIndex);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                long value = Long.parseLong(raw.trim());
                if (value < 0) {
                    throw BizException.badRequest("内部键 _iginx_key 不能为负数");
                }
                if (IginxStructuredUtils.isReservedKey(value)) {
                    throw BizException.badRequest("内部键 _iginx_key 不合法");
                }
                return value;
            } catch (Exception ex) {
                throw BizException.badRequest("内部键 _iginx_key 不合法");
            }
        }

        private Object[] convertRow(List<String> row) {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                String column = columns.get(i);
                Integer idx = columnIndex.get(column);
                String raw = idx == null || idx >= row.size() ? null : row.get(idx);
                raw = normalizeCell(raw, idx);
                DataType type = columnTypes.getOrDefault(column, DataType.BINARY);
                values[i] = convertValue(raw, type);
            }
            return values;
        }

        private String normalizeCell(String raw, Integer index) {
            if (raw == null) {
                return null;
            }
            String value = raw;
            if (index != null && index == 0 && value.startsWith(BOM)) {
                value = value.replace(BOM, "");
            }
            return value;
        }

        private Object convertValue(String raw, DataType type) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            if (type == DataType.BINARY) {
                return raw.getBytes(StandardCharsets.UTF_8);
            }
            return IginxDataTypeConverter.parseValue(raw, type);
        }

        private Object defaultValue(DataType type) {
            if (type == null) {
                return new byte[0];
            }
            return switch (type) {
                case BOOLEAN -> false;
                case INTEGER -> 0;
                case LONG -> 0L;
                case FLOAT -> 0.0f;
                case DOUBLE -> 0.0d;
                case BINARY -> new byte[0];
            };
        }

        private void flush() {
            if (batchRows.isEmpty()) {
                return;
            }
            List<StructuredBatchRow> rows = new ArrayList<>(batchRows);
            batchRows.clear();
            Set<Long> existingKeys = queryExistingKeysIfNeeded(rows);
            List<StructuredBatchRow> rowsToInsert = filterRowsForInsert(rows, existingKeys);
            List<StructuredBatchRow> deduplicated = deduplicateRows(rowsToInsert);
            try {
                if (!deduplicated.isEmpty()) {
                    executeBatchInsert(deduplicated);
                }
                success += rows.size();
            } catch (Exception ex) {
                handleBatchFailure(rows, existingKeys);
            }
        }

        private Set<Long> queryExistingKeysIfNeeded(List<StructuredBatchRow> rows) {
            if (!"ignore".equals(conflictStrategy)) {
                return Set.of();
            }
            Set<Long> keys = new HashSet<>();
            for (StructuredBatchRow row : rows) {
                if (row.key != IginxStructuredUtils.DUMMY_KEY) {
                    keys.add(row.key);
                }
            }
            if (keys.isEmpty()) {
                return Set.of();
            }
            return queryExistingKeys(new ArrayList<>(keys));
        }

        private Set<Long> queryExistingKeys(List<Long> keys) {
            Set<Long> existing = new HashSet<>();
            String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
            int index = 0;
            while (index < keys.size()) {
                int end = Math.min(index + KEY_QUERY_BATCH, keys.size());
                List<Long> batch = keys.subList(index, end);
                String inClause = batch.stream().map(String::valueOf).collect(Collectors.joining(", "));
                String sql = "SELECT KEY FROM " + tablePath + " WHERE KEY IN (" + inClause + ")";
                QueryDataSet dataSet = structuredQueryHelper.executeQuery(sql, batch.size());
                try {
                    Object[] row;
                    while ((row = nextRowQuietly(dataSet)) != null) {
                        Long value = parseLong(row[0]);
                        if (value != null) {
                            existing.add(value);
                        }
                    }
                } finally {
                    closeQuietly(dataSet);
                }
                index = end;
            }
            return existing;
        }

        private Long parseLong(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (Exception ex) {
                return null;
            }
        }

        private List<StructuredBatchRow> filterRowsForInsert(List<StructuredBatchRow> rows, Set<Long> existingKeys) {
            if (!"ignore".equals(conflictStrategy) || existingKeys.isEmpty()) {
                return rows;
            }
            List<StructuredBatchRow> filtered = new ArrayList<>();
            for (StructuredBatchRow row : rows) {
                if (!existingKeys.contains(row.key)) {
                    filtered.add(row);
                }
            }
            return filtered;
        }

        private List<StructuredBatchRow> deduplicateRows(List<StructuredBatchRow> rows) {
            if (rows.size() <= 1) {
                return rows;
            }
            Map<Long, StructuredBatchRow> unique = new LinkedHashMap<>();
            for (StructuredBatchRow row : rows) {
                if ("ignore".equals(conflictStrategy)) {
                    unique.putIfAbsent(row.key, row);
                } else {
                    unique.put(row.key, row);
                }
            }
            return new ArrayList<>(unique.values());
        }

        private void executeBatchInsert(List<StructuredBatchRow> rows) {
            String sql = buildInsertSql(rows);
            structuredQueryHelper.executeSql(sql);
        }

        private String buildInsertSql(List<StructuredBatchRow> rows) {
            String tablePath = IginxStructuredUtils.buildTablePath(schemaWithMount, request.getTable());
            StringBuilder builder = new StringBuilder();
            builder.append("INSERT INTO ").append(tablePath).append(" (KEY");
            for (String column : columns) {
                builder.append(", ").append(IginxStructuredUtils.buildInsertColumn(column));
            }
            builder.append(") VALUES ");
            for (int i = 0; i < rows.size(); i++) {
                StructuredBatchRow row = rows.get(i);
                builder.append("(").append(row.key);
                for (Object value : row.values) {
                    builder.append(", ").append(IginxStructuredUtils.toSqlLiteral(value));
                }
                builder.append(")");
                if (i < rows.size() - 1) {
                    builder.append(", ");
                }
            }
            return builder.toString();
        }

        private void handleBatchFailure(List<StructuredBatchRow> rows, Set<Long> existingKeys) {
            for (StructuredBatchRow row : rows) {
                if ("ignore".equals(conflictStrategy) && existingKeys.contains(row.key)) {
                    success++;
                    continue;
                }
                try {
                    executeBatchInsert(List.of(row));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    errorMessages.add(ex.getMessage());
                    errorRows.add(normalizeErrorRow(row.rawRow));
                }
            }
        }

        private List<String> normalizeErrorRow(List<String> row) {
            if (row == null) {
                return List.of();
            }
            if (internalKeyIndex == null || internalKeyIndex < 0 || internalKeyIndex >= row.size()) {
                return new ArrayList<>(row);
            }
            List<String> normalized = new ArrayList<>(row);
            normalized.remove((int) internalKeyIndex);
            return normalized;
        }

        private void closeQuietly(QueryDataSet dataSet) {
            if (dataSet == null) {
                return;
            }
            try {
                dataSet.close();
            } catch (Exception ignored) {
            }
        }

        private Object[] nextRowQuietly(QueryDataSet dataSet) {
            if (dataSet == null) {
                return null;
            }
            try {
                return dataSet.nextRow();
            } catch (Exception ex) {
                return null;
            }
        }

        private DataImportResultVO buildResult() {
            DataImportResultVO result = new DataImportResultVO();
            result.setTotal(total);
            result.setSuccess(success);
            result.setFailed(failed);
            if (!errorRows.isEmpty()) {
                DataFileStorageService.StoredFile file = fileStorageService.createFile("import_error", ".csv");
                try (BufferedWriter writer = Files.newBufferedWriter(file.path(), StandardCharsets.UTF_8)) {
                    List<String> header = new ArrayList<>();
                    header.add("error_message");
                    header.addAll(columns);
                    writer.write(header.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                    writer.newLine();
                    for (int i = 0; i < errorRows.size(); i++) {
                        List<String> row = new ArrayList<>();
                        row.add(errorMessages.get(i));
                        row.addAll(errorRows.get(i));
                        writer.write(row.stream().map(CsvUtils::toCsvValue).collect(Collectors.joining(",")));
                        writer.newLine();
                    }
                    result.setErrorFile(file.fileName());
                    result.setErrorFileUrl("/api/v1/data/files/" + file.fileName());
                } catch (Exception ex) {
                    throw BizException.internal("结构化导入错误文件生成失败: " + ex.getMessage());
                }
            }
            return result;
        }
    }

    private void ensureRelationalStorageEngine(DataSourceDetail detail) {
        if (detail == null || detail.type() != DataSourceType.POSTGRESQL) {
            return;
        }
        DataSourceConnectionConfig config = detail.config();
        String mountPath = detail.entity() == null ? "" : detail.entity().getMountPath();
        if (storageEngineExists(DataSourceType.POSTGRESQL, config, mountPath)) {
            return;
        }
        String addSql = storageEngineHelper.buildAddStorageEngineSql(DataSourceType.POSTGRESQL, config, mountPath);
        iginxStorageWrapper.executeSql(addSql);
    }

    private boolean storageEngineExists(DataSourceType sourceType,
                                        DataSourceConnectionConfig config,
                                        String mountPath) {
        String resolvedHost = storageEngineHelper.resolveStorageHost(config.getHost());
        String engineType = storageEngineHelper.resolveEngineType(sourceType);
        int port = config.getPort() == null ? -1 : config.getPort();
        String expectedPrefix = normalizePrefix(mountPath);
        try {
            return iginxStorageWrapper.executeWithSession(session -> {
                ClusterInfo clusterInfo = session.getClusterInfo();
                List<StorageEngineInfo> infos = clusterInfo == null ? null : clusterInfo.getStorageEngineInfos();
                if (infos == null || infos.isEmpty()) {
                    return false;
                }
                for (StorageEngineInfo info : infos) {
                    String infoType = info.getType() == null ? "" : info.getType().toString();
                    if (!engineType.equalsIgnoreCase(infoType)) {
                        continue;
                    }
                    if (resolvedHost == null || info.getIp() == null) {
                        continue;
                    }
                    if (!resolvedHost.equalsIgnoreCase(info.getIp())
                        && !isHostAliasMatch(config.getHost(), info.getIp())) {
                        continue;
                    }
                    if (info.getPort() != port) {
                        continue;
                    }
                    String schemaPrefix = normalizePrefix(info.getSchemaPrefix());
                    String dataPrefix = normalizePrefix(info.getDataPrefix());
                    boolean schemaMatch = expectedPrefix.equals(schemaPrefix) || schemaPrefix.isEmpty();
                    if (expectedPrefix.equals(dataPrefix) && schemaMatch) {
                        return true;
                    }
                }
                return false;
            });
        } catch (BizException ex) {
            if (isClusterInfoIncompatible(ex)) {
                // 兼容旧版 IGinX：无法读取集群信息时默认按未注册处理
                return false;
            }
            throw ex;
        }
    }

    private boolean isClusterInfoIncompatible(BizException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("connectable") && lower.contains("iginxinfo");
    }

    private boolean isHostAliasMatch(String rawHost, String actualHost) {
        if (rawHost == null || actualHost == null) {
            return false;
        }
        String raw = rawHost.trim().toLowerCase(Locale.ROOT);
        String actual = actualHost.trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (("127.0.0.1".equals(raw) || "localhost".equals(raw))
            && ("host.docker.internal".equals(actual) || "192.168.65.254".equals(actual))) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && "host.docker.internal".equals(actual)) {
            return true;
        }
        if ("host.docker.internal".equals(raw) && actual.equals("192.168.65.254")) {
            return true;
        }
        return false;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String normalized = prefix.trim();
        return normalized.isEmpty() ? "" : normalized;
    }
}
