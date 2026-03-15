<script setup>
import { ref, onMounted, watch, nextTick, reactive, computed } from 'vue'
import { useDataStore } from '../stores/data'
import * as echarts from 'echarts'

const dataStore = useDataStore()
const chartRef = ref(null)
let chartInstance = null
const treeChartRef = ref(null)
let treeChartInstance = null

const tsData = ref([])
const tableData = ref([])
const tableColumns = ref([])
const tableColumnMeta = ref([])
const loadingData = ref(false)
const tsQueryError = ref('')
const tsInvalidValueCount = ref(0)
const INTERNAL_KEY = '_iginx_key'

const CHART_MAX_POINTS = 2000
const TABLE_MAX_ROWS = 500

const tsQueryForm = reactive({
    startTime: '',
    endTime: '',
    aggregator: 'RAW',
    precisionMs: 10000
})

const pagination = reactive({
    pageNum: 1,
    pageSize: 500,
    total: 0
})

const showQueryBuilder = ref(false)
const queryConditions = ref([
    { logic: 'AND', field: '', op: '=', value: '' }
])

const addCondition = () => {
    queryConditions.value.push({ logic: 'AND', field: '', op: '=', value: '' })
}

const removeCondition = (index) => {
    if (queryConditions.value.length > 1) {
        queryConditions.value.splice(index, 1)
    } else {
        queryConditions.value[0] = { logic: 'AND', field: '', op: '=', value: '' }
    }
}

const showEditModal = ref(false)
const isEditMode = ref(false)
const editingRow = reactive({})
const selectedRowIndex = ref(null)

const selectedRow = computed(() => {
    if (selectedRowIndex.value === null) return null
    return tableData.value[selectedRowIndex.value] || null
})

const visibleTableColumns = computed(() => {
    const columns = tableColumns.value.length
        ? tableColumns.value
        : (tableData.value[0] ? Object.keys(tableData.value[0]) : [])
    return columns.filter((key) => key !== INTERNAL_KEY)
})

const visibleTableData = computed(() => {
    const columns = visibleTableColumns.value
    if (columns.length === 0) return tableData.value.map(() => ({}))
    return tableData.value.map((row) => {
        const filtered = {}
        columns.forEach((key) => {
            filtered[key] = row?.[key]
        })
        return filtered
    })
})

const visibleEditKeys = computed(() => Object.keys(editingRow).filter((key) => key !== INTERNAL_KEY))

const sampleTimeSeries = (data, maxPoints) => {
    if (!Array.isArray(data) || data.length === 0) return []
    if (data.length <= maxPoints) return data
    const step = Math.ceil(data.length / maxPoints)
    const sampled = []
    for (let i = 0; i < data.length; i += step) {
        sampled.push(data[i])
    }
    if (sampled[sampled.length - 1] !== data[data.length - 1]) {
        sampled.push(data[data.length - 1])
    }
    return sampled
}

const isValidPointValue = (value) => {
    if (value === null || value === undefined) return false
    if (typeof value === 'number' && Number.isNaN(value)) return false
    return true
}

const validTsData = computed(() => tsData.value.filter(item => isValidPointValue(item.value)))
const sampledTsData = computed(() => sampleTimeSeries(validTsData.value, CHART_MAX_POINTS))
const isChartSampled = computed(() => validTsData.value.length > CHART_MAX_POINTS)
const chartTimeRangeMs = computed(() => {
    if (validTsData.value.length < 2) return 0
    let min = validTsData.value[0].time
    let max = validTsData.value[0].time
    for (const item of validTsData.value) {
        if (item.time < min) min = item.time
        if (item.time > max) max = item.time
    }
    return Math.max(0, max - min)
})
const visibleTsTableData = computed(() => validTsData.value.slice(0, TABLE_MAX_ROWS))
const isTsTableTruncated = computed(() => validTsData.value.length > TABLE_MAX_ROWS)

const formatDateTime = (value) => {
    const date = value instanceof Date ? value : new Date(value)
    const pad = (num) => String(num).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const formatAxisTime = (value, includeDate) => {
    const date = value instanceof Date ? value : new Date(value)
    const pad = (num) => String(num).padStart(2, '0')
    if (includeDate) {
        return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
    return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const toLocalInput = (date) => {
    const pad = (num) => String(num).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const normalizeInputTime = (value) => {
    if (!value) return ''
    const text = value.replace('T', ' ')
    return text.length === 16 ? `${text}:00` : text
}

const normalizeTimestamp = (value) => {
    if (value === null || value === undefined) return null
    const num = Number(value)
    if (Number.isFinite(num)) {
        return num < 1e12 ? num * 1000 : num
    }
    const parsed = Date.parse(String(value).replace(' ', 'T'))
    return Number.isNaN(parsed) ? null : parsed
}

const parseInputToMillis = (value) => {
    const normalized = normalizeInputTime(value)
    if (!normalized) return null
    const iso = normalized.replace(' ', 'T')
    const time = Date.parse(iso)
    return Number.isNaN(time) ? null : time
}

const normalizeValue = (value) => {
    if (value === null || value === undefined || value === '') return null
    const num = Number(value)
    if (Number.isFinite(num)) return num
    return value
}

const tsQueryRangeText = computed(() => {
    const start = normalizeInputTime(tsQueryForm.startTime)
    const end = normalizeInputTime(tsQueryForm.endTime)
    if (!start || !end) return ''
    return `${start} ~ ${end}`
})

const tsDataRangeText = computed(() => {
    if (!tsData.value.length) return ''
    let min = tsData.value[0].time
    let max = tsData.value[0].time
    for (const item of tsData.value) {
        if (item.time < min) min = item.time
        if (item.time > max) max = item.time
    }
    return `${formatDateTime(min)} ~ ${formatDateTime(max)}`
})
const validDataRangeText = computed(() => {
    if (!validTsData.value.length) return ''
    let min = validTsData.value[0].time
    let max = validTsData.value[0].time
    for (const item of validTsData.value) {
        if (item.time < min) min = item.time
        if (item.time > max) max = item.time
    }
    return `${formatDateTime(min)} ~ ${formatDateTime(max)}`
})
const sampledRangeText = computed(() => {
    if (!sampledTsData.value.length) return ''
    const first = sampledTsData.value[0].time
    const last = sampledTsData.value[sampledTsData.value.length - 1].time
    return `${formatDateTime(first)} ~ ${formatDateTime(last)}`
})

const setDefaultTimeRange = () => {
    const end = new Date()
    const start = new Date(end.getTime() - 60 * 60 * 1000)
    tsQueryForm.startTime = toLocalInput(start)
    tsQueryForm.endTime = toLocalInput(end)
}

const loadTableColumns = async () => {
    const node = dataStore.currentNode
    if (!node.sourceId || !node.schema || !node.table) {
        tableColumnMeta.value = []
        return
    }
    try {
        tableColumnMeta.value = await dataStore.fetchTableColumns(node.sourceId, node.schema, node.table)
    } catch (e) {
        tableColumnMeta.value = []
    }
}

const loadTimeSeriesData = async () => {
    const node = dataStore.currentNode
    if (!node.sourceId || !node.path) return
    if (!tsQueryForm.startTime || !tsQueryForm.endTime) {
        setDefaultTimeRange()
    }
    const startMs = parseInputToMillis(tsQueryForm.startTime)
    const endMs = parseInputToMillis(tsQueryForm.endTime)
    if (startMs !== null && endMs !== null && startMs > endMs) {
        tsQueryError.value = '开始时间不能晚于结束时间'
        tsData.value = []
        nextTick(initChart)
        return
    }
    loadingData.value = true
    tsQueryError.value = ''
    let payload = null
    try {
        payload = {
            sourceId: node.sourceId,
            paths: [node.path],
            timeRange: {
                start: normalizeInputTime(tsQueryForm.startTime),
                end: normalizeInputTime(tsQueryForm.endTime)
            }
        }
        if (tsQueryForm.aggregator && tsQueryForm.aggregator !== 'RAW') {
            payload.downsample = true
            payload.aggregator = tsQueryForm.aggregator
            payload.precisionMs = tsQueryForm.precisionMs
        }
        const result = await dataStore.queryTimeSeriesData(payload)
        const timestamps = Array.isArray(result.timestamps) ? result.timestamps : []
        if (result.series && result.series.length > 0) {
            const series = result.series[0]
            const values = Array.isArray(series.values) ? series.values : []
            const length = Math.min(timestamps.length, values.length)
            const rows = []
            let invalidCount = 0
            for (let i = 0; i < length; i += 1) {
                const ts = normalizeTimestamp(timestamps[i])
                if (ts === null) continue
                const value = normalizeValue(values[i])
                if (value === null || (typeof value === 'number' && Number.isNaN(value))) {
                    invalidCount += 1
                }
                rows.push({
                    time: ts,
                    timeText: formatDateTime(ts),
                    value
                })
            }
            rows.sort((a, b) => a.time - b.time)
            tsData.value = rows
            tsInvalidValueCount.value = invalidCount
        } else {
            tsData.value = []
            tsInvalidValueCount.value = 0
        }
        nextTick(initChart)
    } catch (e) {
        console.error('查询时序数据失败', { error: e, payload })
        tsQueryError.value = e?.message || '时序数据查询失败'
        tsData.value = []
        tsInvalidValueCount.value = 0
        nextTick(initChart)
    } finally {
        loadingData.value = false
    }
}
const loadStructuredData = async () => {
    const node = dataStore.currentNode
    if (!node.sourceId || !node.schema || !node.table) return
    loadingData.value = true
    try {
        const conditions = queryConditions.value
            .filter(cond => cond.field && cond.op)
            .map(cond => ({
                logic: cond.logic || 'AND',
                field: cond.field,
                op: cond.op,
                value: cond.value
            }))
        const result = await dataStore.queryStructuredData({
            sourceId: node.sourceId,
            schema: node.schema,
            table: node.table,
            conditions: conditions.length > 0 ? conditions : undefined,
            pageNum: pagination.pageNum,
            pageSize: pagination.pageSize
        })
        tableColumns.value = (result.columns || []).filter((key) => key !== INTERNAL_KEY)
        tableData.value = result.page?.records || []
        pagination.total = result.page?.total || 0
        selectedRowIndex.value = null
        await loadTableColumns()
    } catch (e) {
        console.error('结构化查询失败', e)
        tableData.value = []
    } finally {
        loadingData.value = false
    }
}

const applyQuery = () => {
    showQueryBuilder.value = false
    loadStructuredData()
}

const openNewModal = () => {
    isEditMode.value = false
    Object.keys(editingRow).forEach(key => delete editingRow[key])
    const columns = tableColumnMeta.value.length
        ? tableColumnMeta.value.map(col => col.name)
        : (tableData.value[0] ? Object.keys(tableData.value[0]) : [])
    const visibleColumns = columns.filter((key) => key !== INTERNAL_KEY)
    columns.forEach(key => {
        if (visibleColumns.includes(key)) {
            editingRow[key] = ''
        }
    })
    showEditModal.value = true
}

const openEditModal = () => {
    if (!selectedRow.value) {
        alert('请先选择一条记录')
        return
    }
    isEditMode.value = true
    Object.keys(editingRow).forEach(key => delete editingRow[key])
    Object.assign(editingRow, JSON.parse(JSON.stringify(selectedRow.value)))
    showEditModal.value = true
}

const saveRow = async () => {
    const node = dataStore.currentNode
    try {
        if (isEditMode.value) {
            await dataStore.updateRow({
                sourceId: node.sourceId,
                schema: node.schema,
                table: node.table,
                data: { ...editingRow }
            })
        } else {
            await dataStore.createRow({
                sourceId: node.sourceId,
                schema: node.schema,
                table: node.table,
                data: { ...editingRow }
            })
        }
        await loadStructuredData()
        showEditModal.value = false
    } catch (e) {
        alert(e.message || '删除失败')
    }
}

const selectRow = (index) => {
    selectedRowIndex.value = index
}

const handleDeleteRow = async () => {
    if (!selectedRow.value) {
        alert('请先选择一条记录')
        return
    }
    if (confirm('确认删除该行数据吗？')) {
        const node = dataStore.currentNode
        const primaryKeys = tableColumnMeta.value.filter(col => col.primaryKey).map(col => col.name)
        const keys = {}
        if (primaryKeys.length > 0) {
            primaryKeys.forEach(key => {
                keys[key] = selectedRow.value[key]
            })
        } else {
            Object.assign(keys, selectedRow.value)
        }
        try {
            await dataStore.deleteRow({
                sourceId: node.sourceId,
                schema: node.schema,
                table: node.table,
                keys
            })
            await loadStructuredData()
        } catch (e) {
            alert(e.message || '删除失败')
        }
    }
}

const handleRemoveCurrentNode = () => {
    if (!dataStore.currentNode.id) return
    if (['ts', 'rt', 'models'].includes(dataStore.currentNode.type)) {
        alert('根节点不支持直接删除')
        return
    }
    dataStore.showDeletePathModal = true
}

const initChart = () => {
    if (!chartRef.value) return
    if (chartInstance) chartInstance.dispose()
    chartInstance = echarts.init(chartRef.value)
    const chartPoints = sampledTsData.value.map(item => [item.time, item.value])
    const includeDate = chartTimeRangeMs.value >= 24 * 60 * 60 * 1000
    const gridBottom = chartPoints.length > 200 ? 40 : 20
    chartInstance.setOption({
        animation: chartPoints.length <= 1000,
        backgroundColor: 'transparent',
        grid: { top: 30, right: 20, bottom: gridBottom, left: 40 },
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'line' },
            formatter: (params) => {
                if (!params || !params.length) return ''
                const data = params[0].data || []
                const timeText = formatDateTime(data[0])
                const valueText = data[1] === null || data[1] === undefined ? '-' : data[1]
                return `${timeText}<br/>值：${valueText}`
            }
        },
        xAxis: {
            type: 'time',
            axisLine: { lineStyle: { color: '#ccc' } },
            axisLabel: {
                color: '#666',
                formatter: (value) => formatAxisTime(value, includeDate)
            }
        },
        yAxis: { 
            type: 'value', 
            splitLine: { lineStyle: { color: '#eee' } },
            axisLabel: { color: '#666' }
        },
        dataZoom: chartPoints.length > 200
            ? [
                { type: 'inside', throttle: 50 },
                { type: 'slider', height: 16, bottom: 4 }
            ]
            : [],
        series: [{ 
            data: chartPoints,
            type: 'line', 
            smooth: false,
            showSymbol: chartPoints.length <= 200,
            areaStyle: chartPoints.length > 2000 ? undefined : { opacity: 0.2, color: '#3b82f6' },
            lineStyle: { color: '#3b82f6' },
            itemStyle: { color: '#3b82f6' },
            emphasis: { disabled: chartPoints.length > 2000 }
        }]
    })
}

const initTreeChart = () => {
    if (!treeChartRef.value) return
    if (treeChartInstance) treeChartInstance.dispose()
    treeChartInstance = echarts.init(treeChartRef.value)

    const findNode = (nodes, id) => {
        for (const node of nodes) {
            if (node.id === id) return node
            if (node.children) {
                const found = findNode(node.children, id)
                if (found) return found
            }
        }
        return null
    }

    const sourceNode = findNode(dataStore.resourceTree, dataStore.currentNode.id)
    if (!sourceNode) return

    const transformData = (node) => {
        let symbolColor = '#3b82f6'
        let borderColor = '#2563eb'
        
        if (node.type === 'rt') {
            symbolColor = '#6366f1'
            borderColor = '#4f46e5'
        } else if (node.type === 'models') {
            symbolColor = '#f97316'
            borderColor = '#ea580c'
        }
        
        if (node.type === 'group' || node.type === 'schema') {
            symbolColor = '#f59e0b'
            borderColor = '#d97706'
        } else if (['point', 'table', 'file'].includes(node.type)) {
            symbolColor = '#22c55e'
            borderColor = '#16a34a'
        }

        return {
            name: node.name,
            value: node.type,
            children: node.children ? node.children.map(transformData) : [],
            symbol: 'circle',
            symbolSize: 18,
            itemStyle: {
                color: symbolColor,
                borderColor: '#fff',
                borderWidth: 2,
                shadowBlur: 5,
                shadowColor: 'rgba(0,0,0,0.1)'
            },
            label: {
                position: 'top',
                verticalAlign: 'middle',
                align: 'center',
                distance: 8,
                fontSize: 12,
                fontFamily: 'sans-serif',
                color: '#4b5563',
                backgroundColor: '#fff',
                padding: [2, 6],
                borderRadius: 4
            },
            lineStyle: {
                color: '#cbd5e1',
                width: 1.5,
                type: 'solid',
                curveness: 0.5
            },
            collapsed: false 
        }
    }

    const option = {
        tooltip: {
            trigger: 'item',
            triggerOn: 'mousemove',
            backgroundColor: 'rgba(255, 255, 255, 0.95)',
            borderColor: '#e5e7eb',
            textStyle: { color: '#374151' },
            formatter: (params) => {
                 const n = params.data
                 const typeMap = { ts: '时序数据', rt: '结构化数据', models: '模型文件', group: '存储组', point: '测点', schema: 'Schema', table: '表', file: '文件' }
                 return `<div class="font-bold text-gray-800">${n.name}</div>
                         <div class="text-xs text-gray-500">${typeMap[n.value] || n.value}</div>`
            }
        },
        series: [
            {
                type: 'tree',
                data: [transformData(sourceNode)],
                top: '10%',
                left: '10%',
                bottom: '10%',
                right: '20%',
                symbolSize: 18,
                layout: 'orthogonal',
                orient: 'LR', 
                expandAndCollapse: true,
                initialTreeDepth: 1,
                roam: true,
                edgeShape: 'curve', 
                label: {
                    position: 'top',
                    verticalAlign: 'middle',
                    align: 'center',
                    fontSize: 12
                },
                leaves: {
                    label: {
                        position: 'right',
                        verticalAlign: 'middle',
                        align: 'left',
                        distance: 8
                    }
                },
                animationDuration: 550,
                animationDurationUpdate: 750
            }
        ]
    }
    treeChartInstance.setOption(option)
}

const updateTableDataForSelection = () => {
    if (['group', 'schema', 'ts', 'rt', 'models', 'file'].includes(dataStore.currentNode.type)) {
        const findNode = (nodes, id) => {
            for (const node of nodes) {
                if (node.id === id) return node
                if (node.children) {
                    const found = findNode(node.children, id)
                    if (found) return found
                }
            }
            return null
        }
        const resolveTypeLabel = (child, rootType) => {
            if (child.type === 'group') {
                if (rootType === 'models') return '目录'
                if (rootType === 'rt') return '路径'
                return '存储组'
            }
            if (child.type === 'schema') return 'Schema'
            if (child.type === 'table') return '表'
            if (child.type === 'point') return '测点'
            if (child.type === 'file') return '文件'
            return child.type || '-'
        }
        const node = findNode(dataStore.resourceTree, dataStore.currentNode.id)
        if (node && node.children) {
            tableData.value = node.children.map(child => {
                const rootType = child.rootType || dataStore.currentNode.rootType || dataStore.currentNode.type
                return {
                    名称: child.name,
                    类型: resolveTypeLabel(child, rootType),
                    子项数: child.children ? child.children.length : '-'
                }
            })
        } else {
            tableData.value = []
        }
    }
}

watch(() => [dataStore.currentNode.id, dataStore.currentNode.viewMode], async ([newId, newMode]) => {
    if (!newId) return
    if (newMode === 'topology' && ['group', 'schema', 'ts', 'rt', 'models'].includes(dataStore.currentNode.type)) {
        nextTick(initTreeChart)
        return
    }
    if (dataStore.currentNode.type === 'point') {
        await loadTimeSeriesData()
    } else if (dataStore.currentNode.type === 'table') {
        await loadStructuredData()
    } else {
        updateTableDataForSelection()
    }
})

onMounted(() => {
    dataStore.loadResourceTree().catch(err => {
        console.error('加载数据资源树失败', err)
    })
    dataStore.loadDataSources().catch(err => {
        console.error('加载数据源失败', err)
    })

    if (dataStore.currentNode.id) {
        if (dataStore.currentNode.viewMode === 'topology' && ['group', 'schema', 'ts', 'rt', 'models'].includes(dataStore.currentNode.type)) {
            initTreeChart()
        } else if (dataStore.currentNode.type === 'point') {
            loadTimeSeriesData()
        }
    }
    window.addEventListener('resize', () => {
        chartInstance && chartInstance.resize()
        treeChartInstance && treeChartInstance.resize()
    })
})
</script>

<template>
  <div class="h-full flex flex-col relative">
     <!-- Empty State (No Selection) -->
     <div v-if="!dataStore.currentNode.id" class="flex-1 flex flex-col items-center justify-center m-10 bg-white">
         <div class="w-32 h-32 bg-blue-50 rounded-full flex items-center justify-center mb-6">
             <i class="ri-database-2-line text-6xl text-blue-200"></i>
         </div>
         <h2 class="text-xl font-bold text-gray-800 mb-2">欢迎使用 IGinX 数据管理器</h2>
         <p class="text-gray-500 text-sm max-w-md text-center mb-8">
             请从左侧数据资源库选择一个数据源以查看详情，或使用上方工具栏添加新的数据源。
         </p>
         <div class="flex space-x-4">
             <button @click="dataStore.showAddSourceModal = true" class="px-6 py-2 bg-blue-600 rounded-full shadow-lg shadow-blue-200 text-white text-sm hover:bg-blue-700 transition-all transform hover:-translate-y-1">
                 <i class="ri-add-line mr-1"></i> 新增数据源</button>
             <button @click="dataStore.openImportWizard('ts')" class="px-6 py-2 bg-white border border-gray-200 rounded-full shadow-lg shadow-gray-100 text-gray-600 text-sm hover:bg-gray-50 transition-all transform hover:-translate-y-1">
                 <i class="ri-upload-cloud-line mr-1"></i> 导入数据</button>
         </div>
     </div>

     <!-- Content Viewer -->
     <div v-else class="flex-1 flex flex-col h-full min-h-0">
         <!-- Group/Tree View in Workspace -->
         <div v-show="dataStore.currentNode.viewMode === 'topology' && ['group', 'schema', 'ts', 'rt', 'models'].includes(dataStore.currentNode.type)" 
              class="flex-1 bg-white rounded border border-gray-100 shadow-sm p-4 flex flex-col relative overflow-hidden">
             <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center border-b border-gray-100 pb-2 z-10 relative bg-white justify-between">
                <div class="flex items-center">
                    <i class="ri-node-tree text-blue-500 mr-2"></i>
                    资源拓扑预览：{{ dataStore.currentNode.id }}
                </div>
                <div class="flex items-center space-x-2">
                    <button @click="dataStore.selectNode(dataStore.currentNode)" class="text-gray-400 hover:text-gray-800 transition-colors p-1 rounded hover:bg-gray-100">
                        <i class="ri-close-line text-xl"></i>
                    </button>
                </div>
             </h3>
             <div class="absolute inset-0 top-14">
                 <div ref="treeChartRef" class="w-full h-full"></div>
             </div>
             <div class="absolute bottom-4 right-4 z-10 bg-white/80 backdrop-blur px-3 py-1 rounded text-[10px] text-gray-500 border border-gray-200 shadow-sm">
                 <i class="ri-mouse-line mr-1"></i> 滚轮缩放 / 拖拽平移</div>
         </div>

         <!-- Leaf Node View / Default Table View -->
         <div v-show="!(dataStore.currentNode.viewMode === 'topology' && ['group', 'schema', 'ts', 'rt', 'models'].includes(dataStore.currentNode.type))" 
              class="flex-1 relative bg-white rounded border border-gray-100 shadow-sm p-4 flex flex-col min-h-0">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold text-gray-800 flex items-center">
                    <i :class="dataStore.currentNode.type === 'point'
                        ? 'ri-pulse-line text-blue-500'
                        : dataStore.currentNode.type === 'table'
                            ? 'ri-table-line text-green-500'
                            : dataStore.currentNode.type === 'file'
                                ? 'ri-file-2-line text-amber-500'
                                : 'ri-folder-3-line text-yellow-500'" class="mr-2"></i>
                    {{ dataStore.currentNode.id }}
                </h3>
                <!-- Query / Aggregation Controls for TS -->
                <div v-if="dataStore.currentNode.type === 'point'" class="flex space-x-2 items-center">
                    <div class="flex items-center space-x-1 bg-gray-50 border border-gray-200 rounded px-2 py-1">
                        <i class="ri-calendar-line text-gray-400 text-xs"></i>
                        <input v-model="tsQueryForm.startTime" type="datetime-local" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-32">
                        <span class="text-gray-400">-</span>
                        <input v-model="tsQueryForm.endTime" type="datetime-local" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-32">
                    </div>
                    <select v-model="tsQueryForm.aggregator" class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8">
                        <option value="RAW">原始数据</option>
                        <option value="AVG">均值</option>
                        <option value="MAX">最大值</option>
                        <option value="MIN">最小值</option>
                        <option value="SUM">求和</option>
                        <option value="COUNT">计数</option>
                    </select>
                    <input v-if="tsQueryForm.aggregator !== 'RAW'" v-model.number="tsQueryForm.precisionMs" type="number" min="1000" class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8 w-24" placeholder="步长(ms)">
                    <button @click="loadTimeSeriesData" class="px-3 py-1 bg-blue-50 text-blue-600 rounded text-xs hover:bg-blue-100 border border-blue-200 h-8">查询</button>
                    <button @click="dataStore.showMaintenanceModal = true" class="px-3 py-1 bg-red-50 text-red-600 rounded text-xs hover:bg-red-100 border border-red-200 h-8" title="数据维护">
                        <i class="ri-edit-line"></i>
                    </button>
                    <button @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="删除测点">
                        <i class="ri-delete-bin-line"></i>
                    </button>
                </div>

                <!-- Group / Schema Specific Controls -->
                <div v-if="['group', 'schema'].includes(dataStore.currentNode.type)" class="flex space-x-2 items-center">
                    <button @click="dataStore.showTopology(dataStore.currentNode.type, dataStore.currentNode.id)" class="px-3 py-1 bg-indigo-50 text-indigo-600 rounded text-xs hover:bg-indigo-100 border border-indigo-200 h-8 flex items-center"><i class="ri-node-tree mr-1"></i> 查看拓扑结构</button>
                     <div class="h-4 border-l border-gray-300 mx-1"></div>
                     <button @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="删除节点">
                        <i class="ri-delete-bin-line"></i>
                    </button>
                </div>

                <!-- Query / Filter Controls for Structured -->
                <div v-if="dataStore.currentNode.type === 'table'" class="relative flex items-center space-x-2">
                    <button @click="showQueryBuilder = !showQueryBuilder" 
                            :class="showQueryBuilder ? 'bg-blue-100 text-blue-600 border-blue-200' : 'bg-white text-gray-600 border-gray-300'"
                            class="flex items-center space-x-2 border rounded px-3 py-1 h-8 text-xs hover:bg-gray-50 transition-colors">
                        <i class="ri-filter-3-line"></i>
                        <span>高级筛选</span>
                        <span v-if="queryConditions.length > 0 && queryConditions[0].field" class="bg-blue-600 text-white text-[10px] px-1.5 rounded-full">{{ queryConditions.length }}</span>
                    </button>
                    
                    <!-- Query Builder Dropdown Panel -->
                        <div v-if="showQueryBuilder" class="absolute top-full right-0 mt-2 w-[550px] bg-white border border-gray-200 shadow-xl rounded-lg z-50 p-4">
                        <div class="flex justify-between items-center mb-3 pb-2 border-b border-gray-100">
                            <span class="font-bold text-gray-700 text-xs">筛选条件</span>
                            <button @click="showQueryBuilder = false" class="text-gray-400 hover:text-gray-600"><i class="ri-close-line"></i></button>
                        </div>
                        
                        <div class="space-y-2 max-h-60 overflow-y-auto mb-4">
                            <div v-for="(cond, index) in queryConditions" :key="index" class="flex items-center space-x-2">
                                <!-- Logic Operator (AND/OR) - Skip for first item -->
                                <select v-if="index > 0" v-model="cond.logic" class="w-16 text-xs border border-gray-300 rounded px-1 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="AND">AND</option>
                                    <option value="OR">OR</option>
                                </select>
                                <div v-else class="w-16 text-xs text-gray-400 text-center font-mono py-1">WHERE</div>
                                
                                <!-- Field -->
                                <input type="text" v-model="cond.field" placeholder="字段名" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                
                                <!-- Operator -->
                                <select v-model="cond.op" class="w-20 text-xs border border-gray-300 rounded px-1 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="=">=</option>
                                    <option value=">">></option>
                                    <option value="<"><</option>
                                    <option value=">=">>=</option>
                                    <option value="<="><=</option>
                                    <option value="LIKE">LIKE</option>
                                    <option value="IN">IN</option>
                                </select>
                                
                                <!-- Value -->
                                <input type="text" v-model="cond.value" placeholder="值" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                
                                <!-- Remove -->
                                <button @click="removeCondition(index)" class="text-red-400 hover:text-red-600 p-1 rounded hover:bg-red-50"><i class="ri-delete-bin-line"></i></button>
                            </div>
                            
                            <button @click="addCondition" class="text-xs text-blue-600 hover:text-blue-700 flex items-center mt-2 px-2 py-1 rounded hover:bg-blue-50"><i class="ri-add-circle-line mr-1"></i> 添加条件</button>
                        </div>
                        
                                                <div class="flex justify-end space-x-2 pt-2 border-t border-gray-100">
                            <button @click="queryConditions = [{ logic: 'AND', field: '', op: '=', value: '' }]" class="px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 rounded">清空</button>
                            <button @click="applyQuery" class="px-3 py-1.5 text-xs bg-blue-600 text-white hover:bg-blue-700 rounded shadow-sm">应用筛选</button>
                        </div>
                    </div>

                    <div class="h-4 border-l border-gray-300 mx-1"></div>
                    <button @click="openNewModal" class="px-3 py-1 bg-green-50 text-green-600 rounded text-xs hover:bg-green-100 border border-green-200 h-8">
                        <i class="ri-add-line mr-1"></i> 新增
                    </button>
                    <button @click="openEditModal" class="px-3 py-1 bg-gray-50 text-gray-600 rounded text-xs hover:bg-gray-100 border border-gray-200 h-8">
                        <i class="ri-edit-box-line mr-1"></i> 编辑
                    </button>
                    <button @click="handleDeleteRow" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="删除行">
                        <i class="ri-delete-row"></i>
                    </button>
                    <button v-if="dataStore.currentNode.type === 'table'" @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="删除表">
                        <i class="ri-delete-bin-2-line"></i>
                    </button>
                </div>
            </div>

            <div v-if="dataStore.currentNode.type === 'point'" class="h-64 w-full mb-2 border border-gray-100 rounded shrink-0">
                <div ref="chartRef" class="w-full h-full"></div>
            </div>
            <div v-if="dataStore.currentNode.type === 'point'" class="flex items-center justify-between text-[10px] text-gray-400 mb-2">
                <span>有效数据点：{{ validTsData.length }} / 原始 {{ tsData.length }}</span>
                <span v-if="isChartSampled">图表已采样显示（{{ sampledTsData.length }} / {{ validTsData.length }}）</span>
            </div>
            <div v-if="dataStore.currentNode.type === 'point' && (tsQueryRangeText || tsDataRangeText || validDataRangeText || sampledRangeText)" class="text-[10px] text-gray-400 mb-2">
                <span v-if="tsQueryRangeText">查询范围：{{ tsQueryRangeText }}</span>
                <span v-if="tsDataRangeText" class="ml-2">返回范围：{{ tsDataRangeText }}</span>
                <span v-if="validDataRangeText" class="ml-2">有效范围：{{ validDataRangeText }}</span>
                <span v-if="sampledRangeText" class="ml-2">采样范围：{{ sampledRangeText }}</span>
                <span v-if="tsInvalidValueCount > 0" class="ml-2 text-orange-500">无效值：{{ tsInvalidValueCount }}</span>
            </div>
            <div v-if="dataStore.currentNode.type === 'point' && tsQueryError" class="text-[10px] text-red-500 mb-2">
                {{ tsQueryError }}
            </div>
            <div v-if="dataStore.currentNode.type === 'point' && isTsTableTruncated" class="text-[10px] text-gray-400 mb-2">
                表格仅展示前 {{ TABLE_MAX_ROWS }} 条记录，完整数据请缩小时间范围或导出查看。
            </div>
            <div class="flex-1 overflow-auto border border-gray-200 rounded min-h-0">
                 <table class="w-full text-xs text-left">
                       <thead class="bg-gray-50 text-gray-500 sticky top-0">
                           <tr v-if="dataStore.currentNode.type === 'point'">
                               <th class="px-4 py-2 border-b">时间戳</th><th class="px-4 py-2 border-b">数值</th>
                           </tr>
                           <tr v-else>
                               <th v-for="key in visibleTableColumns" :key="key" class="px-4 py-2 border-b capitalize">{{ key }}</th>
                           </tr>
                       </thead>
                       <tbody class="divide-y divide-gray-100">
                           <template v-if="dataStore.currentNode.type === 'point'">
                               <tr v-if="visibleTsTableData.length === 0" class="text-gray-400 text-center italic p-4">
                                   <td colspan="2" class="py-8">暂无数据</td>
                               </tr>
                               <tr v-else v-for="(row, index) in visibleTsTableData" :key="`${row.time}-${index}`" class="hover:bg-gray-50">
                                   <td class="px-4 py-2 font-mono text-blue-600">{{ row.timeText }}</td>
                                   <td class="px-4 py-2">{{ row.value }}</td>
                               </tr>
                           </template>
                           <template v-else>
                               <tr v-if="tableData.length === 0" class="text-gray-400 text-center italic p-4">
                                   <td colspan="100%" class="py-8">暂无数据或节点为空</td>
                               </tr>
                               <tr v-else v-for="(row, index) in visibleTableData" :key="index" 
                                   @click="selectRow(index)"
                                   @dblclick="openEditModal"
                                   :class="selectedRowIndex === index ? 'bg-blue-50 border-l-4 border-blue-500' : 'hover:bg-gray-50 border-l-4 border-transparent'"
                                   class="cursor-pointer transition-colors">
                                   <td v-for="key in visibleTableColumns" :key="key" class="px-4 py-2">{{ row[key] }}</td>
                               </tr>
                           </template>
                       </tbody>
                   </table>
            </div>
         </div>
     </div>
     
     <!-- Edit Modal -->
     <div v-if="showEditModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4">{{ isEditMode ? '编辑记录' : '新增记录' }}</h3>
            <div class="space-y-3">
                <div v-for="key in visibleEditKeys" :key="key">
                    <label class="block text-xs font-medium text-gray-700 mb-1 capitalize">{{ key }}</label>
                    <input v-model="editingRow[key]" type="text" class="w-full text-sm border border-gray-300 rounded px-2 py-1.5 focus:ring-1 focus:ring-blue-500 outline-none">
                </div>
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="showEditModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                <button @click="saveRow" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">保存</button>
            </div>
        </div>
     </div>
  </div>
</template>

