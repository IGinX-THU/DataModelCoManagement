<script setup>
import { useDataStore } from '../stores/data'
import { reactive, ref, computed, watch } from 'vue'
import ResourceTreeSelectorNode from './ResourceTreeSelectorNode.vue'

const dataStore = useDataStore()

// --- Local State for Forms ---
const addSourceForm = reactive({
    type: 'iotdb',
    name: '',
    host: '127.0.0.1',
    port: '6667',
    username: '',
    password: '',
    database: 'root',
    schema: 'public',
    hasData: true,
    readOnly: false,
    schemaPrefix: 'ts',
    dataPrefix: ''
})

const portDefaults = {
    influx: '8086',
    iotdb: '6667',
    postgres: '5432'
}

const databaseDefaults = {
    influx: 'default',
    iotdb: 'root',
    postgres: 'postgres'
}

const schemaPrefixDefaults = {
    influx: 'ts',
    iotdb: 'ts',
    postgres: 'rt'
}

const resolveSourceTypeLabel = (source) => {
    if (!source) return '-'
    const sourceType = String(source.sourceType || '').toUpperCase()
    const sourceTypeLabelMap = {
        INFLUXDB: 'InfluxDB',
        IOTDB: 'IoTDB',
        POSTGRESQL: 'PostgreSQL'
    }
    const label = sourceTypeLabelMap[sourceType] || sourceType || '未知'
    return source.type === 'ts' ? `时序（${label}）` : `结构化（${label}）`
}

const maintenanceForm = reactive({
    type: 'delete',
    startTime: '',
    endTime: ''
})

const exportForm = reactive({
    startTime: '',
    endTime: '',
    format: 'csv',
    layout: 'wide',
    sql: ''
})

const isConnecting = ref(false)
const isImporting = ref(false)
const isExporting = ref(false)

watch(() => addSourceForm.type, (val, oldVal) => {
    const nextPort = portDefaults[val] || ''
    const prevPort = portDefaults[oldVal] || ''
    if (!addSourceForm.port || addSourceForm.port === prevPort) {
        addSourceForm.port = nextPort
    }

    const nextDatabase = databaseDefaults[val] || ''
    const prevDatabase = databaseDefaults[oldVal] || ''
    if (!addSourceForm.database || addSourceForm.database === prevDatabase) {
        addSourceForm.database = nextDatabase
    }

    const nextSchemaPrefix = schemaPrefixDefaults[val] || 'ts'
    const prevSchemaPrefix = schemaPrefixDefaults[oldVal] || ''
    if (!addSourceForm.schemaPrefix || addSourceForm.schemaPrefix === prevSchemaPrefix) {
        addSourceForm.schemaPrefix = nextSchemaPrefix
    }
})

watch(() => addSourceForm.hasData, (val) => {
    if (!val) {
        addSourceForm.readOnly = false
    }
})

const normalizeImportPath = (value) => String(value || '').trim()

const syncImportPath = (value) => {
    const normalized = normalizeImportPath(value)
    if (normalized !== dataStore.importForm.path) {
        dataStore.importForm.path = normalized
    }
}

const detectedImportType = computed(() => dataStore.detectImportTypeByPath(dataStore.importForm.path))
const isTimeSeriesExportTarget = computed(() => {
    const node = dataStore.currentNode || {}
    if (node.type === 'ts') return true
    return node.rootType === 'ts' && node.isLeaf
})
const isStructuredExportTarget = computed(() => {
    const node = dataStore.currentNode || {}
    return node.rootType === 'rt' && node.schema && node.table
})

const resolveSelectedPath = (node) => {
    if (!node) return ''
    return node.path || node.id || node.name || ''
}

const importHeaders = ref([])
const importHeaderError = ref('')
const isParsingImportHeader = ref(false)

const normalizeCsvHeader = (value) => String(value || '').replace(/^\uFEFF/, '').trim()

const parseCsvHeaderLine = (lineText) => {
    const values = []
    let current = ''
    let inQuotes = false
    for (let i = 0; i < lineText.length; i += 1) {
        const ch = lineText[i]
        if (ch === '"') {
            if (inQuotes && lineText[i + 1] === '"') {
                current += '"'
                i += 1
            } else {
                inQuotes = !inQuotes
            }
            continue
        }
        if (ch === ',' && !inQuotes) {
            values.push(normalizeCsvHeader(current))
            current = ''
            continue
        }
        current += ch
    }
    values.push(normalizeCsvHeader(current))
    return values.filter(Boolean)
}

const extractFirstCsvLine = (content) => {
    let inQuotes = false
    for (let i = 0; i < content.length; i += 1) {
        const ch = content[i]
        if (ch === '"') {
            if (inQuotes && content[i + 1] === '"') {
                i += 1
            } else {
                inQuotes = !inQuotes
            }
            continue
        }
        if ((ch === '\n' || ch === '\r') && !inQuotes) {
            return content.slice(0, i)
        }
    }
    return content
}

const updateTsKeyColumnByHeaders = () => {
    if (detectedImportType.value !== 'ts') return
    const current = normalizeCsvHeader(dataStore.importForm.keyColumn)
    if (importHeaders.value.length === 0) {
        dataStore.importForm.keyColumn = ''
        return
    }
    if (!current || !importHeaders.value.includes(current)) {
        dataStore.importForm.keyColumn = importHeaders.value[0]
    }
}

const syncImportKeyPolicy = () => {
    if (detectedImportType.value === 'ts') {
        dataStore.importForm.keyMode = 'COLUMN'
        updateTsKeyColumnByHeaders()
        return
    }
    dataStore.importForm.keyMode = 'AUTO_GENERATED'
    dataStore.importForm.keyColumn = ''
}

const parseCsvHeaders = async (file) => {
    importHeaders.value = []
    importHeaderError.value = ''
    if (!file) return
    isParsingImportHeader.value = true
    try {
        const content = await file.slice(0, 256 * 1024).text()
        const firstLine = extractFirstCsvLine(content)
        const headers = parseCsvHeaderLine(firstLine)
        if (headers.length === 0) {
            throw new Error('CSV 表头为空，无法选择时间列')
        }
        importHeaders.value = headers
        updateTsKeyColumnByHeaders()
    } catch (e) {
        importHeaders.value = []
        importHeaderError.value = e?.message || '读取 CSV 表头失败'
        dataStore.importForm.keyColumn = ''
    } finally {
        isParsingImportHeader.value = false
    }
}

watch(() => dataStore.importForm.path, (val) => {
    syncImportPath(val)
    syncImportKeyPolicy()
})

watch(() => dataStore.showImportModal, (visible) => {
    if (visible) {
        importHeaders.value = []
        importHeaderError.value = ''
        isParsingImportHeader.value = false
        syncImportKeyPolicy()
        return
    }
    importHeaders.value = []
    importHeaderError.value = ''
    isParsingImportHeader.value = false
})

const handleFile = async (e) => {
    const file = e.target.files?.[0]
    dataStore.importForm.file = file
    importHeaders.value = []
    importHeaderError.value = ''
    if (!file) {
        dataStore.importForm.keyColumn = ''
        syncImportKeyPolicy()
        return
    }
    // 导入协议当前仅支持 CSV，避免前后端能力不一致。
    const lowerName = String(file.name || '').toLowerCase()
    if (!lowerName.endsWith('.csv')) {
        dataStore.importForm.file = null
        dataStore.importForm.keyColumn = ''
        alert('当前仅支持 CSV 文件')
        syncImportKeyPolicy()
        return
    }
    await parseCsvHeaders(file)
    syncImportKeyPolicy()
}

// --- Action Handlers ---

const handleTestConnection = async () => {
    isConnecting.value = true
    try {
        await dataStore.testConnection({ ...addSourceForm })
        alert('连接成功')
    } catch (e) {
        alert(`连接失败: ${e.message || e}`)
    } finally {
        isConnecting.value = false
    }
}

const handleAddSource = async () => {
    if (!addSourceForm.name) {
        alert('请输入数据源名称')
        return
    }
    try {
        await dataStore.addSource({ ...addSourceForm })
    } catch (e) {
        alert(e.message || '新增数据源失败')
        return
    }
    
    // Reset Form & Close
    addSourceForm.name = ''
    addSourceForm.username = ''
    addSourceForm.password = ''
    addSourceForm.hasData = true
    addSourceForm.readOnly = false
    addSourceForm.schemaPrefix = schemaPrefixDefaults[addSourceForm.type] || 'ts'
    addSourceForm.dataPrefix = ''
    dataStore.showAddSourceModal = false
}

const selectedDetailSourceId = ref('')
const detailLoading = ref(false)
const detailError = ref('')
const isUninstallingSource = ref(false)

const selectedDetailSource = computed(() => {
    return dataStore.dataSourceTree.find(s => s.id === selectedDetailSourceId.value)
})

const selectedDetailData = computed(() => {
    if (!selectedDetailSourceId.value) return null
    return dataStore.detailMap[String(selectedDetailSourceId.value)]
})

const selectedDetailMeta = computed(() => selectedDetailData.value?.meta || selectedDetailSource.value)

watch(() => selectedDetailSourceId.value, async (val) => {
    detailError.value = ''
    if (!val) {
        return
    }
    detailLoading.value = true
    try {
        await dataStore.loadDataSourceDetail(val)
    } catch (e) {
        detailError.value = e?.message || '加载详情失败'
    } finally {
        detailLoading.value = false
    }
})

const toLocalInput = (date) => {
    const pad = (num) => String(num).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const setDefaultRange = (target) => {
    const end = new Date()
    const start = new Date(end.getTime() - 60 * 60 * 1000)
    target.startTime = toLocalInput(start)
    target.endTime = toLocalInput(end)
}

const normalizeTime = (value) => {
    if (!value) return ''
    const text = value.replace('T', ' ')
    return text.length === 16 ? `${text}:00` : text
}

const splitPathSegments = (value) => String(value || '')
    .split('.')
    .map(item => item.trim())
    .filter(Boolean)

const quoteIdentifier = (identifier) => {
    const text = String(identifier || '').trim()
    if (!text) return ''
    if (/^[A-Za-z0-9_]+$/.test(text)) {
        return text
    }
    return `\`${text.replace(/\\/g, '\\\\').replace(/`/g, '\\`')}\``
}

const resolveStructuredSchemaPath = (node) => {
    const schemaText = String(node?.schema || '').trim()
    if (!schemaText) {
        return node?.rootType === 'rt' ? 'rt' : ''
    }
    const lower = schemaText.toLowerCase()
    if (lower === 'rt' || lower.startsWith('rt.')) {
        return schemaText
    }
    if (node?.rootType === 'rt') {
        return `rt.${schemaText}`
    }
    return schemaText
}

watch(() => dataStore.showExportModal, async (val) => {
    if (!val) {
        return
    }
    const node = dataStore.currentNode
    exportForm.sql = ''
    if (isTimeSeriesExportTarget.value) {
        if (!exportForm.startTime || !exportForm.endTime) {
            setDefaultRange(exportForm)
        }
    }
})

const handleUninstallSource = async () => {
    if (!selectedDetailSourceId.value) {
        alert('请先选择要卸载的数据源')
        return
    }
    const source = selectedDetailMeta.value
    if (!source) {
        alert('未获取到数据源信息')
        return
    }
    const connection = source.connectionConfig || {}
    const schemaPrefix = String(connection.schemaPrefix || '').trim()
    const dataPrefix = String(connection.dataPrefix || '').trim()
    const host = String(connection.host || '').trim()
    const port = connection.port ?? ''
    const sqlPreview = `REMOVE STORAGEENGINE (\"${host}\", ${port}, \"${schemaPrefix}\", \"${dataPrefix}\");`
    const confirmText = [
        `确认卸载数据源「${source.name || selectedDetailSourceId.value}」吗？`,
        '',
        '将执行以下 IGinX 语句：',
        sqlPreview,
        '',
        '卸载后将移除该数据源配置，操作不可恢复。'
    ].join('\n')
    if (!confirm(confirmText)) {
        return
    }
    isUninstallingSource.value = true
    try {
        await dataStore.uninstallSource(selectedDetailSourceId.value)
        alert('数据源卸载成功')
        selectedDetailSourceId.value = ''
        detailError.value = ''
    } catch (e) {
        alert(e?.message || '卸载数据源失败')
    } finally {
        isUninstallingSource.value = false
    }
}

watch(() => dataStore.showMaintenanceModal, (val) => {
    if (val) {
        if (!maintenanceForm.startTime || !maintenanceForm.endTime) {
            setDefaultRange(maintenanceForm)
        }
    }
})

watch(() => dataStore.showDeletePathModal, (val) => {
    if (val) {
        dataStore.loadResourceTree().catch(err => {
            console.error('加载数据资源树失败', err)
        })
        deleteIncludeChildren.value = false
        const node = dataStore.currentNode || {}
        if (node.id && !['ts', 'rt', 'models'].includes(node.type)) {
            deleteTargetNode.value = node
            deleteTargetPath.value = resolveSelectedPath(node)
        }
        return
    }
    deleteTargetPath.value = ''
    deleteTargetNode.value = null
    deleteIncludeChildren.value = false
})

const executeImport = async () => {
    if (!dataStore.importForm.path) {
        alert('请输入导入目标路径')
        return
    }
    isImporting.value = true
    try {
        const result = await dataStore.importDataByPath()
        const message = `导入完成：成功 ${result.success} 行，失败 ${result.failed} 行`
        if (result.errorFileUrl) {
            const url = dataStore.buildDownloadUrl(result.errorFileUrl)
            if (confirm(`${message}\n存在错误日志，是否下载？`)) {
                window.open(url, '_blank')
            }
        } else {
            alert(message)
        }
        dataStore.showImportModal = false
    } catch (e) {
        alert(e.message || '导入失败')
    } finally {
        isImporting.value = false
    }
}

const handleExport = async () => {
    const node = dataStore.currentNode
    if (!node.id) {
        alert('请先选择导出目标')
        return
    }
    isExporting.value = true
    try {
        const payload = {
            sourceId: node.sourceId,
            format: exportForm.format.toUpperCase()
        }
        if (isTimeSeriesExportTarget.value) {
            payload.type = 'TS'
            payload.paths = [node.path || node.id]
            payload.timeRange = {
                start: normalizeTime(exportForm.startTime),
                end: normalizeTime(exportForm.endTime)
            }
            payload.layout = exportForm.layout
        } else if (isStructuredExportTarget.value) {
            payload.type = 'STRUCT'
            payload.schema = node.schema
            payload.table = node.table
            const sqlText = (exportForm.sql || '').trim()
            if (sqlText) {
                payload.sql = sqlText
            }
        } else {
            alert('请选择具体测点或数据表再导出')
            return
        }
        let result = await dataStore.exportDataFile(payload)
        if (result.taskId && result.status !== 'SUCCESS') {
            result = await dataStore.pollExportTask(result.taskId)
        }
        if (result.status === 'FAILED') {
            alert('导出失败，请稍后重试')
            return
        }
        const downloadUrl = dataStore.buildDownloadUrl(result.downloadUrl)
        window.open(downloadUrl, '_blank')
        dataStore.showExportModal = false
    } catch (e) {
        alert(e.message || '导出失败')
    } finally {
        isExporting.value = false
    }
}

const handleMaintenance = async () => {
    if (!dataStore.currentNode.id) return
    if (confirm('删除操作不可恢复，确认继续吗？')) {
        try {
            await dataStore.deleteTimeSeriesRange({
                sourceId: dataStore.currentNode.sourceId,
                paths: [dataStore.currentNode.path || dataStore.currentNode.id],
                timeRange: {
                    start: normalizeTime(maintenanceForm.startTime),
                    end: normalizeTime(maintenanceForm.endTime)
                },
                operation: maintenanceForm.type
            })
            alert('删除成功')
            dataStore.showMaintenanceModal = false
        } catch (e) {
            alert(e.message || '删除失败')
        }
    }
}

const deleteTargetPath = ref('')
const deleteTargetNode = ref(null)
const deleteIncludeChildren = ref(false)
const deleteTreeRoots = computed(() => dataStore.resourceTree || [])

const handleDeletePathSelect = (node) => {
    deleteTargetNode.value = node
    deleteTargetPath.value = resolveSelectedPath(node)
}

const isDeletePathDisabled = computed(() => {
    const path = normalizeImportPath(deleteTargetPath.value)
    if (!path) return true
    const type = deleteTargetNode.value?.type || ''
    return ['ts', 'rt', 'models'].includes(type)
})

const handleDeletePath = async () => {
    const path = normalizeImportPath(deleteTargetPath.value)
    if (!path) {
        alert('请先选择要删除的路径')
        return
    }
    if (['ts', 'rt', 'models'].includes(deleteTargetNode.value?.type)) {
        alert('根节点不支持直接删除')
        return
    }
    const confirmText = deleteIncludeChildren.value
        ? `确认删除 ${path} 及其子路径下的全部数据吗？`
        : `确认删除 ${path} 本路径的数据吗？`
    if (!confirm(confirmText)) {
        return
    }
    try {
        const res = await dataStore.deletePath(path, deleteIncludeChildren.value)
        if (res && res.success === false) {
            alert(res.msg || '删除失败')
            return
        }
        dataStore.showDeletePathModal = false
        deleteTargetPath.value = ''
        deleteTargetNode.value = null
        deleteIncludeChildren.value = false
    } catch (e) {
        alert(e.message || '删除失败')
    }
}
</script>

<template>
  <div class="relative z-[100]">
	     <!-- Import Modal -->
	     <div v-if="dataStore.showImportModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
	         <div class="bg-white rounded-lg shadow-xl w-[560px] flex flex-col">
	            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
	                <div class="flex items-center space-x-2">
	                    <h3 class="font-bold text-gray-800">统一 CSV 数据导入</h3>
	                    <span
	                      v-if="detectedImportType === 'ts'"
	                      class="text-[10px] px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 border border-blue-200"
	                    >当前路径语义：ts</span>
                    <span
                      v-else-if="detectedImportType === 'rt'"
                      class="text-[10px] px-2 py-0.5 rounded-full bg-green-100 text-green-700 border border-green-200"
                    >当前路径语义：rt</span>
                    <span
                      v-else
                      class="text-[10px] px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700 border border-yellow-200"
                    >待识别路径前缀</span>
	                </div>
	                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showImportModal = false"></i>
	            </div>

	            <div class="p-6 space-y-4">
	                <div>
	                    <label class="block text-xs font-bold text-gray-700 mb-1">1. 文件（CSV）</label>
	                    <div class="border-2 border-dashed border-gray-300 rounded-lg h-28 flex flex-col items-center justify-center bg-gray-50">
		                        <input type="file" accept=".csv,text/csv" @change="handleFile" class="hidden" id="fileUpload">
	                        <label for="fileUpload" class="cursor-pointer flex flex-col items-center">
	                            <i class="ri-upload-cloud-2-line text-3xl text-gray-400"></i>
	                            <span class="text-xs text-gray-500 mt-2">{{ dataStore.importForm.file ? dataStore.importForm.file.name : '点击上传 CSV 文件' }}</span>
	                        </label>
	                    </div>
	                </div>

	                <div>
	                   <label class="block text-xs font-bold text-gray-700 mb-1">2. 目标路径</label>
	                   <input v-model="dataStore.importForm.path" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="ts.demo.predict_power 或 rt.biz.order">
	                   <p class="text-[10px] text-gray-500 mt-1">此字段会映射到 SQL 的 INTO &lt;prefixPath&gt;。</p>
	                </div>

	                <div v-if="!detectedImportType" class="text-[11px] text-red-700 bg-red-50 border border-red-100 rounded px-3 py-2">
	                    路径前缀未识别，请使用 ts.xxx 或 rt.xxx。
	                </div>

	                <div v-if="detectedImportType === 'ts'">
	                    <label class="block text-xs font-bold text-gray-700 mb-1">3. 时间列（KEY）</label>
	                    <div v-if="!dataStore.importForm.file" class="text-[11px] text-gray-500 bg-gray-50 border border-gray-200 rounded px-3 py-2">
	                        请先上传 CSV 文件，再从表头选择时间列作为 KEY。
	                    </div>
	                    <div v-else-if="isParsingImportHeader" class="text-[11px] text-blue-700 bg-blue-50 border border-blue-100 rounded px-3 py-2">
	                        正在读取 CSV 表头，请稍候...
	                    </div>
	                    <div v-else-if="importHeaderError" class="text-[11px] text-red-700 bg-red-50 border border-red-100 rounded px-3 py-2">
	                        {{ importHeaderError }}
	                    </div>
	                    <div v-else-if="!importHeaders.length" class="text-[11px] text-red-700 bg-red-50 border border-red-100 rounded px-3 py-2">
	                        未解析到 CSV 表头，请检查文件格式是否正确。
	                    </div>
	                    <div v-else>
	                        <select v-model="dataStore.importForm.keyColumn" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
	                            <option v-for="header in importHeaders" :key="header" :value="header">{{ header }}</option>
	                        </select>
	                        <p class="text-[10px] text-gray-500 mt-1">后端会生成 `SET KEY "列名"`，用于时序写入。</p>
	                    </div>
	                </div>

	                <div v-else-if="detectedImportType === 'rt'">
	                    <label class="block text-xs font-bold text-gray-700 mb-1">3. KEY 方式</label>
	                    <div class="text-[11px] text-gray-600 bg-gray-50 border border-gray-200 rounded px-3 py-2">
	                        已默认使用“自动生成 KEY”，无需额外设置。
	                    </div>
	                </div>
	            </div>

	            <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
	                <button @click="dataStore.showImportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
	                <button @click="executeImport" :disabled="isImporting" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700 disabled:opacity-60">开始导入</button>
	            </div>
	         </div>
	     </div>

     <!-- Add Source Modal -->
     <div v-if="dataStore.showAddSourceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">新增数据源</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showAddSourceModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">数据源类型</label>
                     <select v-model="addSourceForm.type" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="iotdb">IoTDB（时序）</option>
                         <option value="influx">InfluxDB（时序）</option>
                         <option value="postgres">PostgreSQL（结构化）</option>
                     </select>
                 </div>
                 
                 <!-- Dynamic Fields based on Type -->
                <div v-if="['iotdb', 'influx'].includes(addSourceForm.type)">
                    <div class="mb-4">
                        <label class="block text-xs font-bold text-gray-700 mb-1">数据库 / Bucket</label>
                        <input v-model="addSourceForm.database" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" :placeholder="addSourceForm.type === 'iotdb' ? 'root' : 'default'">
                    </div>
                </div>
                 <div v-if="['postgres'].includes(addSourceForm.type)">
                     <div class="grid grid-cols-2 gap-4 mb-4">
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">数据库名</label>
                             <input v-model="addSourceForm.database" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">Schema</label>
                             <input v-model="addSourceForm.schema" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                     </div>
                 </div>

                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">别名</label>
                     <input v-model="addSourceForm.name" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="e.g. factory_db_01">
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">主机 / IP</label>
                         <input v-model="addSourceForm.host" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="127.0.0.1">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">端口</label>
                         <input v-model="addSourceForm.port" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" :placeholder="portDefaults[addSourceForm.type] || '5432'">
                     </div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">用户名</label>
                         <input v-model="addSourceForm.username" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">密码</label>
                         <input v-model="addSourceForm.password" type="password" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">是否已有数据 (has_data)</label>
                         <select v-model="addSourceForm.hasData" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                             <option :value="true">是</option>
                             <option :value="false">否</option>
                         </select>
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">是否只读 (is_read_only)</label>
                         <select v-model="addSourceForm.readOnly" :disabled="!addSourceForm.hasData" class="w-full border border-gray-300 rounded px-3 py-2 text-sm disabled:opacity-60">
                             <option :value="true">是</option>
                             <option :value="false">否</option>
                         </select>
                         <p v-if="!addSourceForm.hasData" class="text-[10px] text-gray-400 mt-1">无数据不可只读</p>
                     </div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">schemaPrefix</label>
                         <select v-model="addSourceForm.schemaPrefix" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                             <option value="ts">ts</option>
                             <option value="rt">rt</option>
                         </select>
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">dataPrefix</label>
                         <input v-model="addSourceForm.dataPrefix" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="可选，如 ts_root">
                     </div>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-between space-x-2">
                 <button @click="handleTestConnection" :disabled="isConnecting" class="px-4 py-2 border border-blue-200 bg-blue-50 text-blue-600 rounded text-sm hover:bg-blue-100">
                     <i v-if="isConnecting" class="ri-loader-4-line animate-spin mr-1"></i>
                     测试连接
                 </button>
                 <div class="flex space-x-2">
                    <button @click="dataStore.showAddSourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                    <button @click="handleAddSource" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">保存并注册</button>
                 </div>
             </div>
         </div>
     </div>

     <!-- Source Details Modal -->
     <div v-if="dataStore.showSourceDetailsModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">数据源详情</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showSourceDetailsModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div class="mb-4">
                     <label class="block text-xs font-bold text-gray-700 mb-1">选择数据源</label>
                     <select v-model="selectedDetailSourceId" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="" disabled>-- 请选择 --</option>
                         <option v-for="source in dataStore.dataSourceTree.filter(n => ['ts', 'rel'].includes(n.type))" :key="source.id" :value="source.id">
                             {{ source.name }}
                         </option>
                     </select>
                 </div>
                 
                 <div v-if="detailLoading" class="text-xs text-gray-400">正在加载详情...</div>
                 <div v-if="detailError" class="text-xs text-red-600 bg-red-50 border border-red-100 rounded px-3 py-2">
                     {{ detailError }}
                 </div>

                 <div v-if="selectedDetailMeta" class="space-y-4">
                     <div class="flex items-center space-x-4 mb-6">
                         <div class="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center text-blue-600">
                             <i class="ri-database-2-line text-2xl"></i>
                         </div>
                         <div>
                             <h4 class="font-bold text-lg text-gray-800">{{ selectedDetailMeta.name }}</h4>
                             <span class="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full border border-green-200">已连接</span>
                         </div>
                     </div>
                     <div class="grid grid-cols-2 gap-y-4 text-sm">
                         <div class="text-gray-500">类型</div>
                         <div class="font-mono text-gray-800">{{ resolveSourceTypeLabel(selectedDetailMeta) }}</div>
                         <div class="text-gray-500">节点 ID</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.id }}</div>
                         <div class="text-gray-500">主机</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.host || '-' }}</div>
                         <div class="text-gray-500">端口</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.port || '-' }}</div>
                         <div class="text-gray-500">数据库</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.database || '-' }}</div>
                         <div class="text-gray-500">用户名</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.username || '-' }}</div>
                         <div class="text-gray-500">是否有数据</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.hasData === false ? '否' : '是' }}</div>
                         <div class="text-gray-500">只读</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.readOnly ? '是' : '否' }}</div>
                         <div class="text-gray-500">schemaPrefix</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.schemaPrefix || '-' }}</div>
                         <div class="text-gray-500">dataPrefix</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.connectionConfig?.dataPrefix || '-' }}</div>
                         <div class="text-gray-500">创建时间</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailMeta.createTime || '-' }}</div>
                     </div>
                 </div>
                 <div v-else class="text-center text-gray-400 py-8">
                     <i class="ri-search-line text-4xl mb-2"></i>
                     <p>请选择数据源查看详情</p>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-between">
                 <button @click="handleUninstallSource" :disabled="!selectedDetailSourceId || isUninstallingSource" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed">
                     <i v-if="isUninstallingSource" class="ri-loader-4-line animate-spin mr-1"></i>
                     卸载数据源
                 </button>
                 <button @click="dataStore.showSourceDetailsModal = false" class="px-4 py-2 bg-gray-100 text-gray-700 rounded text-sm hover:bg-gray-200">关闭</button>
             </div>
         </div>
     </div>

     <!-- Export Modal -->
     <div v-if="dataStore.showExportModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">数据导出</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showExportModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">导出目标</label>
                     <div class="w-full border border-gray-300 rounded px-3 py-2 text-sm bg-gray-50 text-gray-500">
                         {{ dataStore.currentNode.id || '未选择' }}
                     </div>
                 </div>
                 <div v-if="isTimeSeriesExportTarget" class="space-y-3">
                     <div class="grid grid-cols-2 gap-4">
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">开始时间</label>
                            <input v-model="exportForm.startTime" type="datetime-local" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">结束时间</label>
                            <input v-model="exportForm.endTime" type="datetime-local" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">导出格式</label>
                         <div class="flex space-x-4 mt-1">
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="csv" class="mr-2"> CSV</label>
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="json" class="mr-2"> JSON</label>
                         </div>
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">布局</label>
                         <div class="flex space-x-4 mt-1">
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.layout" value="wide" class="mr-2"> 宽表</label>
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.layout" value="long" class="mr-2"> 长表</label>
                         </div>
                     </div>
                 </div>
                 <div v-else-if="isStructuredExportTarget" class="space-y-3">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">导出格式</label>
                         <div class="flex space-x-4 mt-1">
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="csv" class="mr-2"> CSV</label>
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="excel" class="mr-2"> Excel</label>
                             <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="json" class="mr-2"> JSON</label>
                         </div>
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">自定义 SQL（可选）</label>
                        <textarea v-model="exportForm.sql" rows="3" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="SELECT * FROM ..."></textarea>
                     </div>
                 </div>
                 <div v-else class="text-xs text-gray-400">
                     当前节点不支持导出，请选择具体测点或数据表。
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showExportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                 <button @click="handleExport" :disabled="isExporting" class="px-4 py-2 bg-purple-600 text-white rounded text-sm hover:bg-purple-700 disabled:opacity-60">开始导出</button>
             </div>
         </div>
     </div>

     <!-- Delete Path Modal -->
     <div v-if="dataStore.showDeletePathModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[420px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800 text-red-600">删除数据</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showDeletePathModal = false"></i>
             </div>
              <div class="p-6 space-y-4 text-sm text-gray-600">
                  <div>
                      <label class="block text-xs font-bold text-gray-700 mb-1">选择路径</label>
                      <div class="border border-gray-200 rounded px-2 py-2 max-h-56 overflow-y-auto bg-gray-50">
                          <div v-if="!deleteTreeRoots.length" class="text-xs text-gray-400 text-center py-4">暂无可选路径</div>
                          <ResourceTreeSelectorNode
                            v-else
                            :nodes="deleteTreeRoots"
                            :allow-group-select="true"
                            :on-select="handleDeletePathSelect"
                          />
                      </div>
                  </div>
                  <div>
                      <label class="block text-xs font-bold text-gray-700 mb-1">已选路径</label>
                      <div class="font-mono text-gray-800 bg-gray-50 border border-gray-200 rounded px-3 py-2">
                          {{ deleteTargetPath || '-' }}
                      </div>
                  </div>
                  <div>
                      <label class="block text-xs font-bold text-gray-700 mb-1">删除范围</label>
                      <div class="flex items-center space-x-4">
                          <label class="flex items-center text-xs text-gray-700">
                              <input type="radio" v-model="deleteIncludeChildren" :value="false" class="mr-2">
                              仅删除当前路径
                          </label>
                          <label class="flex items-center text-xs text-gray-700">
                              <input type="radio" v-model="deleteIncludeChildren" :value="true" class="mr-2">
                              删除当前路径及子路径
                          </label>
                      </div>
                  </div>
                  <div class="p-3 bg-red-50 rounded border border-red-100 text-red-700 text-xs">
                      <i class="ri-alert-line mr-1"></i>
                      <span v-if="deleteIncludeChildren">将删除所选路径及其子路径下的全部数据，请谨慎操作。</span>
                      <span v-else>将仅删除所选路径本身的数据，不包含子路径。</span>
                  </div>
                  <div v-if="isDeletePathDisabled" class="text-xs text-red-600">
                      根节点不支持删除，请选择具体路径或表。
                  </div>
              </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showDeletePathModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                 <button @click="handleDeletePath" :disabled="isDeletePathDisabled" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50">确认删除</button>
             </div>
         </div>
     </div>
     
     <!-- Maintenance Modal -->
     <div v-if="dataStore.showMaintenanceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800 text-red-600">时序数据维护</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showMaintenanceModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div class="bg-yellow-50 p-3 rounded text-yellow-800 text-xs border border-yellow-200">
                     <i class="ri-alert-line"></i> 删除操作不可恢复，请谨慎执行。
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">目标节点</label>
                     <div class="font-mono text-sm text-gray-800">{{ dataStore.currentNode.id }}</div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">开始时间</label>
                        <input v-model="maintenanceForm.startTime" type="datetime-local" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">结束时间</label>
                        <input v-model="maintenanceForm.endTime" type="datetime-local" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">操作</label>
                     <select v-model="maintenanceForm.type" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="delete">删除区间数据</option>
                         <option value="fill">插值填补（未实现）</option>
                     </select>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showMaintenanceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                 <button @click="handleMaintenance" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700">执行</button>
             </div>
         </div>
     </div>
  </div>
</template>
