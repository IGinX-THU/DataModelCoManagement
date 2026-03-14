<script setup>
import { useDataStore } from '../stores/data'
import { reactive, ref, computed, watch } from 'vue'

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
    readOnly: false
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
    sql: '',
    columns: []
})

const isConnecting = ref(false)
const isImporting = ref(false)
const isExporting = ref(false)
const exportColumns = ref([])
const isLoadingExportColumns = ref(false)

const pickAllExportColumns = () => {
    exportForm.columns = exportColumns.value.map(column => column.name).filter(Boolean)
}

const clearExportColumns = () => {
    exportForm.columns = []
}

const loadExportColumns = async (node) => {
    if (!node?.sourceId || !node?.schema || !node?.table) {
        exportColumns.value = []
        exportForm.columns = []
        return
    }
    isLoadingExportColumns.value = true
    try {
        const columns = await dataStore.fetchTableColumns(node.sourceId, node.schema, node.table)
        exportColumns.value = Array.isArray(columns) ? columns : []
        exportForm.columns = []
    } catch (e) {
        console.error('Failed to load export columns', e)
        exportColumns.value = []
        exportForm.columns = []
    } finally {
        isLoadingExportColumns.value = false
    }
}

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
})

watch(() => addSourceForm.hasData, (val) => {
    if (!val) {
        addSourceForm.readOnly = false
    }
})

const selectedImportSource = computed(() => {
    return dataStore.dataSourceTree.find(s => s.id === dataStore.importForm.source)
})

watch(() => dataStore.importForm.source, (val) => {
    if (dataStore.importType === 'ts' && val && selectedImportSource.value) {
        if (!dataStore.importForm.storageGroup) {
            dataStore.importForm.storageGroup = selectedImportSource.value.mountPath || selectedImportSource.value.name
        }
    }
})

const parseCsvLine = (line) => {
    const result = []
    let current = ''
    let inQuotes = false
    for (let i = 0; i < line.length; i += 1) {
        const char = line[i]
        if (char === '"' && line[i + 1] === '"') {
            current += '"'
            i += 1
            continue
        }
        if (char === '"') {
            inQuotes = !inQuotes
            continue
        }
        if (char === ',' && !inQuotes) {
            result.push(current.trim())
            current = ''
            continue
        }
        current += char
    }
    if (current.length > 0) result.push(current.trim())
    return result.filter(Boolean)
}

const readFileHead = (file, size) => new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error || new Error('读取文件失败'))
    reader.readAsText(file.slice(0, size), 'utf-8')
})

const handleFile = async (e) => {
    const file = e.target.files[0]
    dataStore.importForm.file = file
    dataStore.importForm.columns = []
    dataStore.importForm.mapping = []
    if (!file) return
    const ext = file.name.split('.').pop().toLowerCase()
    if (ext === 'csv') {
        try {
            // 只读取文件头，避免大文件导致前端卡顿
            let headText = await readFileHead(file, 64 * 1024)
            if (!headText.includes('\n') && file.size > 64 * 1024) {
                headText = await readFileHead(file, Math.min(256 * 1024, file.size))
            }
            const firstLine = String(headText).split(/\r?\n/)[0] || ''
            const columns = parseCsvLine(firstLine)
            dataStore.importForm.columns = columns
            if (!dataStore.importForm.timestampColumn && columns.length > 0) {
                dataStore.importForm.timestampColumn = columns[0]
            }
            dataStore.importForm.mapping = columns.map(col => ({
                column: col,
                target: col,
                dataType: 'DOUBLE'
            }))
        } catch (err) {
            console.error('读取 CSV 头部失败', err)
        }
    }
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
    dataStore.showAddSourceModal = false
}

const handleRemoveSource = async () => {
    if (!selectedSourceId.value) {
        alert('请选择要卸载的数据源')
        return
    }
    if (confirm(`确认卸载 ${selectedSourceId.value} 吗？`)) {
        try {
            await dataStore.removeSource(selectedSourceId.value)
        } catch (e) {
            const message = e?.message || '卸载失败'
            if (String(message).includes('IGinX 服务不可用')) {
                const forceConfirm = confirm('IGinX 卸载失败，是否仅删除系统记录？（不会删除实际数据）')
                if (forceConfirm) {
                    try {
                        await dataStore.removeSource(selectedSourceId.value, true)
                    } catch (forceError) {
                        alert(forceError?.message || '强制卸载失败')
                        return
                    }
                } else {
                    return
                }
            } else {
                alert(message)
                return
            }
            return
        }
        dataStore.showRemoveSourceModal = false
        selectedSourceId.value = ''
    }
}

const selectedSourceId = ref('')
const selectedDetailSourceId = ref('')

const selectedDetailSource = computed(() => {
    return dataStore.dataSourceTree.find(s => s.id === selectedDetailSourceId.value)
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

const startsWithSegments = (segments, prefix) => {
    if (!prefix.length) return true
    if (segments.length < prefix.length) return false
    for (let i = 0; i < prefix.length; i += 1) {
        if (String(segments[i]).toLowerCase() !== String(prefix[i]).toLowerCase()) {
            return false
        }
    }
    return true
}

const quoteIdentifier = (identifier) => {
    const text = String(identifier || '').trim()
    if (!text) return ''
    if (/^[A-Za-z0-9_]+$/.test(text)) {
        return text
    }
    return `\`${text.replace(/\\/g, '\\\\').replace(/`/g, '\\`')}\``
}

const buildStructuredExportSql = (node, selectedColumns) => {
    const mountSegments = splitPathSegments(node.mountPath)
    const schemaSegments = splitPathSegments(node.schema)
    const tableSegments = splitPathSegments(node.table)
    if (!tableSegments.length || !selectedColumns.length) {
        return ''
    }
    const pathSegments = []
    if (schemaSegments.length) {
        if (startsWithSegments(schemaSegments, mountSegments)) {
            pathSegments.push(...schemaSegments)
        } else {
            pathSegments.push(...mountSegments, ...schemaSegments)
        }
    } else {
        pathSegments.push(...mountSegments)
    }
    pathSegments.push(...tableSegments)
    const tablePath = pathSegments.map(quoteIdentifier).filter(Boolean).join('.')
    const selectList = selectedColumns.map(quoteIdentifier).filter(Boolean).join(', ')
    if (!tablePath || !selectList) {
        return ''
    }
    return `SELECT ${selectList} FROM ${tablePath}`
}

watch(() => dataStore.showExportModal, async (val) => {
    if (!val) {
        return
    }
    const node = dataStore.currentNode
    exportForm.sql = ''
    if (['point', 'ts'].includes(node.type)) {
        if (!exportForm.startTime || !exportForm.endTime) {
            setDefaultRange(exportForm)
        }
        exportColumns.value = []
        exportForm.columns = []
        return
    }
    if (node.type === 'table') {
        await loadExportColumns(node)
        return
    }
    exportColumns.value = []
    exportForm.columns = []
})

watch(() => dataStore.showMaintenanceModal, (val) => {
    if (val) {
        if (!maintenanceForm.startTime || !maintenanceForm.endTime) {
            setDefaultRange(maintenanceForm)
        }
    }
})

const executeImport = async () => {
    if (!dataStore.importForm.source) {
        alert('请选择目标数据源')
        return
    }
    isImporting.value = true
    try {
        let result
        if (dataStore.importType === 'ts') {
            result = await dataStore.importTimeSeriesData()
        } else {
            result = await dataStore.importStructuredData()
        }
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
        if (['point', 'ts'].includes(node.type)) {
            payload.type = 'TS'
            payload.paths = [node.path || node.id]
            payload.timeRange = {
                start: normalizeTime(exportForm.startTime),
                end: normalizeTime(exportForm.endTime)
            }
            payload.layout = exportForm.layout
        } else if (node.type === 'table') {
            payload.type = 'STRUCT'
            payload.schema = node.schema
            payload.table = node.table
            const selectedColumns = exportForm.columns.filter(Boolean)
            if (selectedColumns.length === 0) {
                alert('请至少选择一列后再导出')
                return
            }
            payload.columns = selectedColumns
            const sqlText = (exportForm.sql || '').trim()
            if (sqlText) {
                payload.sql = sqlText
            } else {
                const autoSql = buildStructuredExportSql(node, selectedColumns)
                if (!autoSql) {
                    alert('导出 SQL 生成失败，请检查表路径后重试')
                    return
                }
                payload.sql = autoSql
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
</script>

<template>
  <div class="relative z-[100]">
     <!-- Import Modal -->
     <div v-if="dataStore.showImportModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[600px] flex flex-col max-h-[80vh]">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">{{ dataStore.importType === 'ts' ? '时序数据导入向导' : '结构化数据导入向导' }}</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showImportModal = false"></i>
             </div>
             
             <div class="p-6 flex-1 overflow-y-auto space-y-6">
                 <!-- Step 0: Select Import Type -->
                 <div v-if="dataStore.importStep === 1">
                     <h4 class="text-sm font-bold text-gray-700 mb-4">1. 选择导入类型</h4>
                     <div class="grid grid-cols-2 gap-4">
                         <div @click="dataStore.importType = 'ts'" 
                              :class="dataStore.importType === 'ts' ? 'border-blue-500 bg-blue-50 ring-1 ring-blue-500' : 'border-gray-200 hover:border-blue-300 hover:bg-gray-50'"
                              class="cursor-pointer border rounded-lg p-4 flex flex-col items-center justify-center transition-all h-32">
                             <div class="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center text-blue-600 mb-2">
                                 <i class="ri-pulse-line text-xl"></i>
                             </div>
                             <span class="font-bold text-sm text-gray-800">时序数据</span>
                             <span class="text-[10px] text-gray-500 mt-1">InfluxDB / IoTDB</span>
                         </div>
                         <div @click="dataStore.importType = 'struct'"
                              :class="dataStore.importType === 'struct' ? 'border-green-500 bg-green-50 ring-1 ring-green-500' : 'border-gray-200 hover:border-green-300 hover:bg-gray-50'"
                              class="cursor-pointer border rounded-lg p-4 flex flex-col items-center justify-center transition-all h-32">
                             <div class="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center text-green-600 mb-2">
                                 <i class="ri-table-line text-xl"></i>
                             </div>
                             <span class="font-bold text-sm text-gray-800">结构化数据</span>
                             <span class="text-[10px] text-gray-500 mt-1">PostgreSQL</span>
                 </div>
             </div>
                 </div>

                 <!-- Step 1: Select Target Source (Renamed to Step 2 logic but keeping variable consistent or increasing step count) -->
                 <!-- Let's increase step count logic. Need to update next/back buttons too. -->
                 <!-- Actually, simpler to keep Step 1 as Type Selection and push others +1 -->
                 
                 <div v-if="dataStore.importStep === 2">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">2. 选择目标数据源</h4>
                     <select v-model="dataStore.importForm.source" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="">请选择数据源...</option>
                         <!-- Filter sources based on import type -->
                         <option v-for="source in dataStore.dataSourceTree.filter(s => (dataStore.importType === 'ts' ? s.type === 'ts' : s.type === 'rel'))" 
                                 :key="source.id" :value="source.id">
                             {{ source.name }} ({{ source.type === 'ts' ? '时序' : '结构化' }})
                         </option>
                     </select>
                     <div v-if="dataStore.importType === 'ts'" class="mt-3">
                        <label class="block text-xs font-bold text-gray-700 mb-1">存储组路径</label>
                        <input v-model="dataStore.importForm.storageGroup" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="root.device.group01">
                        <p class="text-[10px] text-gray-400 mt-1">支持相对路径，将自动挂载到数据源挂载路径下</p>
                     </div>
                     <div v-if="dataStore.importType === 'struct'" class="mt-3 space-y-3">
                        <div>
                            <label class="block text-xs font-bold text-gray-700 mb-1">Schema</label>
                            <input v-model="dataStore.importForm.schema" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="public">
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-700 mb-1">表名</label>
                            <input v-model="dataStore.importForm.table" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="device_list">
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-700 mb-1">主键字段（可选，逗号分隔）</label>
                            <input v-model="dataStore.importForm.primaryKeys" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="id,code">
                        </div>
                     </div>
                 </div>

                 <!-- Step 3 -->
                 <div v-if="dataStore.importStep === 3">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">3. 上传文件</h4>
                     <div class="border-2 border-dashed border-gray-300 rounded-lg h-32 flex flex-col items-center justify-center bg-gray-50">
                         <input type="file" @change="handleFile" class="hidden" id="fileUpload">
                         <label for="fileUpload" class="cursor-pointer flex flex-col items-center">
                             <i class="ri-upload-cloud-2-line text-3xl text-gray-400"></i>
                             <span class="text-xs text-gray-500 mt-2">{{ dataStore.importForm.file ? dataStore.importForm.file.name : '点击上传' + (dataStore.importType === 'ts' ? ' CSV' : ' Excel/SQL') }}</span>
                         </label>
                     </div>
                 </div>

                 <!-- Step 4 (TS) -->
                 <div v-if="dataStore.importStep === 4 && dataStore.importType === 'ts'">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">4. 字段映射与时间戳</h4>
                     <div class="mb-4">
                         <label class="block text-xs font-bold text-gray-600 mb-1">时间戳列</label>
                         <select v-if="dataStore.importForm.columns.length" v-model="dataStore.importForm.timestampColumn" class="w-full border border-gray-300 rounded px-2 py-1 text-xs">
                             <option v-for="col in dataStore.importForm.columns" :key="col" :value="col">{{ col }}</option>
                         </select>
                         <input v-else v-model="dataStore.importForm.timestampColumn" type="text" class="w-full border border-gray-300 rounded px-2 py-1 text-xs" placeholder="Time">
                         <div class="mt-2">
                             <label class="block text-xs font-bold text-gray-600 mb-1">时间格式</label>
                             <input v-model="dataStore.importForm.timestampFormat" type="text" class="w-full border border-gray-300 rounded px-2 py-1 text-xs" placeholder="yyyy-MM-dd HH:mm:ss">
                         </div>
                     </div>
                     <table class="w-full text-xs text-left border border-gray-200">
                         <thead class="bg-gray-50"><tr><th class="p-2">文件列</th><th class="p-2">测点路径</th><th class="p-2">数据类型</th></tr></thead>
                         <tbody>
                             <tr v-for="(m, i) in dataStore.importForm.mapping" :key="i" class="border-t border-gray-100">
                                 <td class="p-2">{{ m.column }}</td>
                                 <td class="p-2"><input v-model="m.target" class="border border-gray-300 rounded w-full px-1"></td>
                                 <td class="p-2">
                                     <select v-model="m.dataType" class="border border-gray-300 rounded w-full px-1">
                                         <option value="DOUBLE">DOUBLE</option>
                                         <option value="LONG">LONG</option>
                                         <option value="INTEGER">INTEGER</option>
                                         <option value="FLOAT">FLOAT</option>
                                         <option value="BOOLEAN">BOOLEAN</option>
                                         <option value="BINARY">BINARY</option>
                                     </select>
                                 </td>
                             </tr>
                         </tbody>
                     </table>
                 </div>

                 <!-- Step 4 (Struct) -->
                 <div v-if="dataStore.importStep === 4 && dataStore.importType === 'struct'">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">4. 导入策略</h4>
                     <div class="space-y-4">
                        <div>
                            <label class="flex items-center text-sm font-bold text-gray-700 mb-2">
                                <input type="checkbox" v-model="dataStore.importForm.autoCreateTable" class="mr-2"> 
                                目标表不存在时自动建表
                            </label>
                            <p class="text-xs text-gray-500 ml-6">系统将根据首行推断字段类型</p>
                        </div>
                        <div class="border-t border-gray-100 pt-4">
                           <h5 class="text-xs font-bold text-gray-600 mb-2">主键冲突策略</h5>
                           <div class="space-y-2">
                               <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="update" class="mr-2"> 覆盖更新</label>
                               <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="skip" class="mr-2"> 跳过重复</label>
                               <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="error" class="mr-2"> 遇错中止</label>
                           </div>
                        </div>
                        <div class="grid grid-cols-2 gap-4 border-t border-gray-100 pt-4">
                           <div>
                               <label class="block text-xs font-bold text-gray-600 mb-1">Sheet 索引</label>
                               <input v-model.number="dataStore.importForm.sheetIndex" type="number" min="0" class="w-full border border-gray-300 rounded px-2 py-1 text-xs">
                           </div>
                           <div>
                               <label class="block text-xs font-bold text-gray-600 mb-1">文件类型（可选）</label>
                               <input v-model="dataStore.importForm.fileType" type="text" class="w-full border border-gray-300 rounded px-2 py-1 text-xs" placeholder="csv/xlsx/sql">
                           </div>
                        </div>
                     </div>
                 </div>
             </div>

             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button v-if="dataStore.importStep > 1" @click="dataStore.importStep--" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">上一步</button>
                 <button v-if="dataStore.importStep < 4" @click="dataStore.importStep++" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">下一步</button>
                 <button v-if="dataStore.importStep === 4" @click="executeImport" :disabled="isImporting" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700 disabled:opacity-60">开始导入</button>
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

     <!-- Remove Source Modal -->
     <div v-if="dataStore.showRemoveSourceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[400px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800 text-red-600">卸载数据源</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showRemoveSourceModal = false"></i>
             </div>
             <div class="p-6 text-sm text-gray-600 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">选择数据源</label>
                     <select v-model="selectedSourceId" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="" disabled>-- 请选择 --</option>
                         <option v-for="source in dataStore.dataSourceTree.filter(n => ['ts', 'rel'].includes(n.type))" :key="source.id" :value="source.id">
                             {{ source.name }}
                         </option>
                     </select>
                 </div>
                 <div class="p-3 bg-red-50 rounded border border-red-100 text-red-700 text-xs">
                     <i class="ri-alert-line mr-1"></i> 注意：卸载仅断开 IGinX 连接，物理数据不会被删除。
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showRemoveSourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                 <button @click="handleRemoveSource" :disabled="!selectedSourceId" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50">确认卸载</button>
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
                 
                 <div v-if="selectedDetailSource" class="space-y-4">
                     <div class="flex items-center space-x-4 mb-6">
                         <div class="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center text-blue-600">
                             <i class="ri-database-2-line text-2xl"></i>
                         </div>
                         <div>
                             <h4 class="font-bold text-lg text-gray-800">{{ selectedDetailSource.name }}</h4>
                             <span class="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full border border-green-200">已连接</span>
                         </div>
                     </div>
                     <div class="grid grid-cols-2 gap-y-4 text-sm">
                         <div class="text-gray-500">类型</div>
                         <div class="font-mono text-gray-800">{{ resolveSourceTypeLabel(selectedDetailSource) }}</div>
                         <div class="text-gray-500">节点 ID</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.id }}</div>
                         <div class="text-gray-500">存储规模</div>
                         <div class="font-mono text-gray-800">-</div>
                         <div class="text-gray-500">运行时长</div>
                         <div class="font-mono text-gray-800">-</div>
                     </div>
                     <div class="grid grid-cols-2 gap-y-4 text-sm">
                         <div class="text-gray-500">挂载路径</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.mountPath || '-' }}</div>
                         <div class="text-gray-500">主机</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.connectionConfig?.host || '-' }}</div>
                         <div class="text-gray-500">端口</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.connectionConfig?.port || '-' }}</div>
                         <div class="text-gray-500">数据库</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.connectionConfig?.database || '-' }}</div>
                         <div class="text-gray-500">用户名</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.connectionConfig?.username || '-' }}</div>
                         <div class="text-gray-500">创建时间</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.createTime || '-' }}</div>
                     </div>
                 </div>
                 <div v-else class="text-center text-gray-400 py-8">
                     <i class="ri-search-line text-4xl mb-2"></i>
                     <p>请选择数据源查看详情</p>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
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
                 <div v-if="['point', 'ts'].includes(dataStore.currentNode.type)" class="space-y-3">
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
                 <div v-else class="space-y-3">
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
                        <div class="mb-3">
                            <div class="flex items-center justify-between mb-1">
                                <label class="block text-xs font-bold text-gray-700">导出列（可多选）</label>
                                <div class="flex items-center space-x-2">
                                    <button @click="pickAllExportColumns" :disabled="isLoadingExportColumns || !exportColumns.length" class="text-xs text-blue-600 disabled:text-gray-300">全选</button>
                                    <button @click="clearExportColumns" :disabled="isLoadingExportColumns || !exportColumns.length" class="text-xs text-gray-500 disabled:text-gray-300">清空</button>
                                </div>
                            </div>
                            <div class="max-h-36 overflow-y-auto border border-gray-300 rounded px-2 py-1 space-y-1">
                                <div v-if="isLoadingExportColumns" class="text-xs text-gray-400 py-2">正在加载列信息...</div>
                                <div v-else-if="!exportColumns.length" class="text-xs text-gray-400 py-2">暂无可选列</div>
                                <label v-else v-for="column in exportColumns" :key="column.name" class="flex items-center text-xs py-1">
                                    <input type="checkbox" v-model="exportForm.columns" :value="column.name" class="mr-2">
                                    <span class="text-gray-700">{{ column.name }}</span>
                                    <span class="ml-auto text-gray-400">{{ column.type || '-' }}</span>
                                </label>
                            </div>
                        </div>
                        <textarea v-model="exportForm.sql" rows="3" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="SELECT * FROM ..."></textarea>
                     </div>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showExportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                 <button @click="handleExport" :disabled="isExporting" class="px-4 py-2 bg-purple-600 text-white rounded text-sm hover:bg-purple-700 disabled:opacity-60">开始导出</button>
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
