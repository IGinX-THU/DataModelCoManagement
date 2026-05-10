<script setup>
import { ref, onMounted, watch, nextTick, reactive, computed } from 'vue'
import { useDataStore } from '../stores/data'
import * as echarts from 'echarts'
import { TIME_PRECISION_UNIT_OPTIONS, formatPrecisionMs, parsePrecisionValueToMs } from '../utils/timePrecision'
import { normalizeTimestampToMillis } from '../utils/timeNormalize'

const dataStore = useDataStore()
const chartRef = ref(null)
let chartInstance = null
const treeChartRef = ref(null)
let treeChartInstance = null

const tsData = ref([])
const tableData = ref([])
const tableColumns = ref([])
const loadingData = ref(false)
const tsQueryError = ref('')
const tsInvalidValueCount = ref(0)
const INTERNAL_KEY = '_iginx_key'
const structuredSchemaLoading = ref(false)
const structuredSchemaError = ref('')
const structuredDataError = ref('')
const structuredDataQueried = ref(false)
const currentStructuredTablePath = ref('')
const currentTsPreviewPath = ref('')
const structuredJumpPage = ref('1')
const lastTsQueryAggregator = ref('AVG')
const lastTsPrecisionMs = ref(null)

const CHART_MAX_POINTS = 2000
const TABLE_MAX_ROWS = 500
const DEFAULT_TS_QUERY_MAX_POINTS = 1200
const TS_AUTO_DISCOVERY_WINDOWS = [
    60 * 60 * 1000,
    6 * 60 * 60 * 1000,
    24 * 60 * 60 * 1000,
    7 * 24 * 60 * 60 * 1000,
    30 * 24 * 60 * 60 * 1000,
    90 * 24 * 60 * 60 * 1000,
    180 * 24 * 60 * 60 * 1000,
    365 * 24 * 60 * 60 * 1000,
    3 * 365 * 24 * 60 * 60 * 1000,
    5 * 365 * 24 * 60 * 60 * 1000,
    10 * 365 * 24 * 60 * 60 * 1000,
    30 * 365 * 24 * 60 * 60 * 1000,
    50 * 365 * 24 * 60 * 60 * 1000
]

const tsQueryForm = reactive({
    startTime: '',
    endTime: '',
    aggregator: 'AVG',
    precisionValue: '',
    precisionUnit: 'ms'
})

const pagination = reactive({
    pageNum: 1,
    pageSize: 50,
    total: 0
})

const showQueryBuilder = ref(false)
const queryConditions = ref([
    { logic: 'AND', field: '', op: '=', value: '' }
])

/**
 * 重置结构化筛选条件。
 * 说明：切换到新表时清空旧条件，避免把上一个表的字段名带到当前表导致查询失败。
 */
const resetStructuredConditions = () => {
    queryConditions.value = [{ logic: 'AND', field: '', op: '=', value: '' }]
}

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
const isTimeSeriesNode = computed(() =>
    dataStore.currentNode.previewMode === 'TIME_SERIES' && dataStore.currentNode.isLeaf
)
const isStructuredTableNode = computed(() =>
    dataStore.currentNode.previewMode === 'STRUCTURED' && dataStore.currentNode.isStructuredTable
)
const isStructuredColumnNode = computed(() =>
    dataStore.currentNode.previewMode === 'STRUCTURED' && dataStore.currentNode.isStructuredColumn
)
const isReadOnlyStructuredTable = computed(() => isStructuredTableNode.value && dataStore.currentNode.readOnly)
const structuredFilterFields = computed(() => {
    const fields = []
    let hasKeyField = false
    for (const rawField of visibleTableColumns.value) {
        const field = String(rawField || '').trim()
        if (!field) continue
        if (field.toUpperCase() === 'KEY') {
            if (!hasKeyField) {
                fields.push('KEY')
                hasKeyField = true
            }
            continue
        }
        fields.push(field)
    }
    if (!hasKeyField) {
        fields.unshift('KEY')
    }
    return fields
})
const structuredTotalPages = computed(() => {
    const total = Number(pagination.total) || 0
    const pageSize = Number(pagination.pageSize) || 50
    return Math.max(1, Math.ceil(total / pageSize))
})
const tsDownsampleSummary = computed(() => {
    if (lastTsQueryAggregator.value === 'RAW' || !lastTsPrecisionMs.value) {
        return ''
    }
    return `${lastTsQueryAggregator.value} / ${formatPrecisionMs(lastTsPrecisionMs.value)}`
})

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
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const normalizeInputTime = (value) => {
    if (!value) return ''
    const text = value.replace('T', ' ')
    return text.length === 16 ? `${text}:00` : text
}

const normalizePreviewPath = (value) => String(value || '').trim().replace(/\.+$/, '')

const normalizeTimestamp = (value) => {
    return normalizeTimestampToMillis(value)
}

const parseInputToMillis = (value) => {
    const normalized = normalizeInputTime(value)
    if (!normalized) return null
    const iso = normalized.replace(' ', 'T')
    const time = Date.parse(iso)
    return Number.isNaN(time) ? null : time
}

const buildDateTimeText = (value) => {
    const timestamp = normalizeTimestamp(value)
    return timestamp === null ? '' : formatDateTime(timestamp)
}

const applyTimeRangeToForm = (startValue, endValue) => {
    const startMs = normalizeTimestamp(startValue)
    const endMs = normalizeTimestamp(endValue)
    if (startMs === null || endMs === null) {
        return false
    }
    tsQueryForm.startTime = toLocalInput(new Date(startMs))
    tsQueryForm.endTime = toLocalInput(new Date(endMs))
    return true
}

const extractTimeRangeFromRows = (rows) => {
    if (!Array.isArray(rows) || rows.length === 0) {
        return null
    }
    let min = Number.POSITIVE_INFINITY
    let max = Number.NEGATIVE_INFINITY
    rows.forEach((row) => {
        const timestamp = normalizeTimestamp(row?.time)
        if (timestamp === null) {
            return
        }
        min = Math.min(min, timestamp)
        max = Math.max(max, timestamp)
    })
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        return null
    }
    return { startMs: min, endMs: max }
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

const resolveTsPrecisionMs = () => {
    const manualPrecision = parsePrecisionValueToMs(
        tsQueryForm.precisionValue,
        tsQueryForm.precisionUnit
    )
    if (manualPrecision) {
        return manualPrecision
    }
    const startMs = parseInputToMillis(tsQueryForm.startTime)
    const endMs = parseInputToMillis(tsQueryForm.endTime)
    if (startMs === null || endMs === null || endMs <= startMs) {
        return 1000
    }
    return Math.max(1, Math.ceil((endMs - startMs) / DEFAULT_TS_QUERY_MAX_POINTS))
}

const buildDiscoveryPayload = (path, startMs, endMs) => {
    const precisionMs = Math.max(1, Math.ceil(Math.max(1, endMs - startMs) / DEFAULT_TS_QUERY_MAX_POINTS))
    return {
        paths: [path],
        timeRange: {
            start: buildDateTimeText(startMs),
            end: buildDateTimeText(endMs)
        },
        downsample: true,
        aggregator: 'COUNT',
        precisionMs
    }
}

const buildRawRangePayload = (path, startMs, endMs) => ({
    paths: [path],
    timeRange: {
        start: buildDateTimeText(startMs),
        end: buildDateTimeText(endMs)
    }
})

const resolveResultTimestamps = (result) => (Array.isArray(result?.timestamps) ? result.timestamps : [])
    .map(item => normalizeTimestamp(item))
    .filter(item => item !== null)

const isIginxProjectTaskError = (error) => String(error?.message || error || '')
    .toLowerCase()
    .includes('execute project task')

const buildDiscoveryCandidateRanges = (now, windowSize) => {
    const safeNow = Number.isFinite(now) ? now : Date.now()
    const safeWindow = Math.max(1, Number(windowSize) || 1)
    return [
        {
            startMs: Math.max(0, safeNow - safeWindow),
            endMs: safeNow + safeWindow
        }
    ]
}

/**
 * 在已定位到的时间桶内再做一次原始查询，尽量把首尾时间收敛到真实数据点。
 */
const refineTimeSeriesRangeInWindow = async (path, startMs, endMs) => {
    const normalizedPath = normalizePreviewPath(path)
    if (!normalizedPath || !Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs < startMs) {
        return null
    }
    const payload = buildRawRangePayload(normalizedPath, startMs, endMs)
    try {
        const result = await dataStore.queryTimeSeriesData(payload)
        const timestamps = resolveResultTimestamps(result)
        if (!timestamps.length) {
            return null
        }
        return {
            startMs: Math.min(...timestamps),
            endMs: Math.max(...timestamps)
        }
    } catch (error) {
        console.error('精确收敛时序数据范围失败', { path: normalizedPath, error, payload })
        return null
    }
}

/**
 * 自动探测当前测点最近一段“确实有数据”的时间窗口。
 * 说明：按多个时间窗逐步扩大查询，避免首次打开时默认时间段为空。
 */
const discoverTimeSeriesRange = async (path) => {
    const normalizedPath = normalizePreviewPath(path)
    if (!normalizedPath) {
        return null
    }
    const now = Date.now()
    for (const windowSize of TS_AUTO_DISCOVERY_WINDOWS) {
        const candidateRanges = buildDiscoveryCandidateRanges(now, windowSize)
        for (const candidateRange of candidateRanges) {
            const payload = buildDiscoveryPayload(
                normalizedPath,
                candidateRange.startMs,
                candidateRange.endMs
            )
            try {
                const result = await dataStore.queryTimeSeriesData(payload)
                const timestamps = resolveResultTimestamps(result)
                if (!timestamps.length) {
                    continue
                }
                const precisionMs = Number(payload.precisionMs) || 1
                const firstBucketStart = Math.min(...timestamps)
                const lastBucketStart = Math.max(...timestamps)
                const firstWindowRange = await refineTimeSeriesRangeInWindow(
                    normalizedPath,
                    firstBucketStart,
                    Math.min(candidateRange.endMs, firstBucketStart + precisionMs)
                )
                const lastWindowRange = lastBucketStart === firstBucketStart
                    ? firstWindowRange
                    : await refineTimeSeriesRangeInWindow(
                        normalizedPath,
                        lastBucketStart,
                        Math.min(candidateRange.endMs, lastBucketStart + precisionMs)
                    )
                return {
                    startMs: firstWindowRange?.startMs ?? firstBucketStart,
                    endMs: lastWindowRange?.endMs ?? lastBucketStart
                }
            } catch (error) {
                console.error('自动探测时序数据范围失败', { path: normalizedPath, error, payload })
                if (isIginxProjectTaskError(error)) {
                    return null
                }
            }
        }
    }
    return null
}

const DEFAULT_RANGE_SOURCE = 'default'
const EMPTY_DISCOVERY_RANGE_SOURCE = 'empty-discovery'

/**
 * 初始化时序预览时间范围。
 * 优先级：任务跳转指定范围 > 本地记忆范围 > 自动探测范围 > 最近 1 小时默认范围。
 */
const initializeTimeSeriesRange = async (path) => {
    const normalizedPath = normalizePreviewPath(path)
    if (!normalizedPath) {
        setDefaultTimeRange()
        return 'default'
    }

    const preferredRange = dataStore.consumePreferredPreviewTimeRange(normalizedPath)
    if (preferredRange && applyTimeRangeToForm(preferredRange.startTime, preferredRange.endTime)) {
        return 'preferred'
    }

    const rememberedRange = dataStore.getRememberedTimeSeriesRange(normalizedPath)
    if (rememberedRange && applyTimeRangeToForm(rememberedRange.startTime, rememberedRange.endTime)) {
        return 'cache'
    }

    const discoveredRange = await discoverTimeSeriesRange(normalizedPath)
    if (discoveredRange && applyTimeRangeToForm(discoveredRange.startMs, discoveredRange.endMs)) {
        return 'discovered'
    }

    setDefaultTimeRange()
    return EMPTY_DISCOVERY_RANGE_SOURCE
}

const resetStructuredPagination = () => {
    pagination.pageNum = 1
    pagination.pageSize = 50
    pagination.total = 0
    structuredJumpPage.value = '1'
}

const syncStructuredPagination = (page) => {
    pagination.pageNum = Number(page?.pageNum) > 0 ? Number(page.pageNum) : 1
    pagination.pageSize = Number(page?.pageSize) > 0 ? Number(page.pageSize) : pagination.pageSize
    pagination.total = Math.max(0, Number(page?.total) || 0)
    structuredJumpPage.value = String(pagination.pageNum)
}

const changeStructuredPage = async (targetPage) => {
    if (!structuredDataQueried.value || loadingData.value) {
        return
    }
    const numericPage = Number(targetPage)
    const resolvedPage = Number.isFinite(numericPage)
        ? Math.min(Math.max(1, Math.trunc(numericPage)), structuredTotalPages.value)
        : pagination.pageNum
    if (resolvedPage === pagination.pageNum) {
        structuredJumpPage.value = String(pagination.pageNum)
        return
    }
    pagination.pageNum = resolvedPage
    structuredJumpPage.value = String(resolvedPage)
    await loadStructuredData()
}

const jumpToStructuredPage = async () => {
    await changeStructuredPage(structuredJumpPage.value)
}

/**
 * 查询时序数据，并在需要时把表单时间范围回填为实际返回的数据首尾时间。
 */
const loadTimeSeriesData = async ({ syncRangeToData = false } = {}) => {
    const node = dataStore.currentNode
    // 资源树节点可能直接来自 IGinX 当前列集合，此时未必能关联到系统内的数据源记录；
    // 时序查询接口实际只依赖路径和时间范围，因此这里不能再把 sourceId 作为查询前置条件。
    if (!node.path) {
        tsQueryError.value = '未获取到有效的时序路径'
        tsData.value = []
        nextTick(initChart)
        return { hasData: false }
    }
    if (!tsQueryForm.startTime || !tsQueryForm.endTime) {
        setDefaultTimeRange()
    }
    const startMs = parseInputToMillis(tsQueryForm.startTime)
    const endMs = parseInputToMillis(tsQueryForm.endTime)
    if (startMs !== null && endMs !== null && startMs > endMs) {
        tsQueryError.value = '开始时间不能晚于结束时间'
        tsData.value = []
        nextTick(initChart)
        return { hasData: false }
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
            const resolvedPrecisionMs = resolveTsPrecisionMs()
            payload.downsample = true
            payload.aggregator = tsQueryForm.aggregator
            payload.precisionMs = resolvedPrecisionMs
            lastTsQueryAggregator.value = tsQueryForm.aggregator
            lastTsPrecisionMs.value = resolvedPrecisionMs
        } else {
            lastTsQueryAggregator.value = 'RAW'
            lastTsPrecisionMs.value = null
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
            const actualRange = extractTimeRangeFromRows(rows)
            if (actualRange) {
                dataStore.rememberTimeSeriesRange(
                    node.path,
                    buildDateTimeText(actualRange.startMs),
                    buildDateTimeText(actualRange.endMs)
                )
                if (syncRangeToData) {
                    applyTimeRangeToForm(actualRange.startMs, actualRange.endMs)
                }
            }
        } else {
            tsData.value = []
            tsInvalidValueCount.value = 0
        }
        nextTick(initChart)
        return { hasData: tsData.value.length > 0 }
    } catch (e) {
        console.error('查询时序数据失败', { error: e, payload })
        tsQueryError.value = e?.message || '时序数据查询失败'
        tsData.value = []
        tsInvalidValueCount.value = 0
        nextTick(initChart)
        return { hasData: false }
    } finally {
        loadingData.value = false
    }
}

/**
 * 打开时序测点预览时，自动切到更合适的有数据区间。
 */
const openTimeSeriesPreview = async () => {
    const previewPath = normalizePreviewPath(dataStore.currentNode.path)
    const pathChanged = currentTsPreviewPath.value !== previewPath
    currentTsPreviewPath.value = previewPath

    let rangeSource = 'manual'
    if (pathChanged) {
        rangeSource = await initializeTimeSeriesRange(previewPath)
    }

    let result = await loadTimeSeriesData({
        syncRangeToData: pathChanged && (rangeSource === 'preferred' || rangeSource === 'default')
    })

    if (!result?.hasData && pathChanged && rangeSource !== EMPTY_DISCOVERY_RANGE_SOURCE) {
        const discoveredRange = await discoverTimeSeriesRange(previewPath)
        if (discoveredRange && applyTimeRangeToForm(discoveredRange.startMs, discoveredRange.endMs)) {
            result = await loadTimeSeriesData({ syncRangeToData: true })
        }
    }
}

/**
 * 查询结构化表结构（列名与类型），并可选地自动查询表数据。
 */
const loadStructuredSchema = async (autoLoadData = false) => {
    const node = dataStore.currentNode
    const tablePath = resolveStructuredDeletePath(node)
    if (!tablePath) {
        currentStructuredTablePath.value = ''
        resetStructuredPagination()
        return
    }
    currentStructuredTablePath.value = tablePath
    showQueryBuilder.value = false
    structuredSchemaLoading.value = true
    loadingData.value = true
    structuredSchemaError.value = ''
    structuredDataError.value = ''
    try {
        const schema = await dataStore.queryStructuredSchemaData(tablePath)
        const columns = Array.isArray(schema?.columns) ? schema.columns : []
        // 后端 schema 已经不补 _iginx_key，这里仍保留一次前端兜底过滤。
        tableColumns.value = columns
            .map((column) => String(column?.name || '').trim())
            .filter((name) => name && name !== INTERNAL_KEY)
        tableData.value = []
        resetStructuredPagination()
        selectedRowIndex.value = null
        structuredDataQueried.value = false
        resetStructuredConditions()
        // 选中表节点后自动查询表数据，满足“点击表直接展示内容”的交互要求。
        if (autoLoadData) {
            structuredDataQueried.value = true
            await loadStructuredData()
        }
    } catch (e) {
        console.error('结构化表结构查询失败', e)
        structuredSchemaError.value = e?.message || '结构化表结构查询失败'
        tableColumns.value = []
        tableData.value = []
        resetStructuredPagination()
        selectedRowIndex.value = null
        structuredDataQueried.value = false
    } finally {
        structuredSchemaLoading.value = false
        loadingData.value = false
    }
}

/**
 * 按当前筛选条件查询结构化表数据。
 */
const loadStructuredData = async () => {
    const tablePath = currentStructuredTablePath.value || resolveStructuredDeletePath(dataStore.currentNode)
    if (!tablePath) return
    loadingData.value = true
    structuredDataError.value = ''
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
            tablePath,
            conditions: conditions.length > 0 ? conditions : undefined,
            pageNum: pagination.pageNum,
            pageSize: pagination.pageSize
        })
        const queriedColumns = Array.isArray(result?.columns) ? result.columns : []
        if (queriedColumns.length > 0) {
            tableColumns.value = queriedColumns.filter((key) => key !== INTERNAL_KEY)
        }
        syncStructuredPagination(result?.page)
        tableData.value = result?.page?.records || []
        selectedRowIndex.value = null
    } catch (e) {
        console.error('结构化数据查询失败', e)
        structuredDataError.value = e?.message || '结构化数据查询失败'
        tableData.value = []
        pagination.total = 0
        selectedRowIndex.value = null
    } finally {
        loadingData.value = false
    }
}

/**
 * 用户主动触发结构化数据查询（符合“先查结构，再查数据”的交互）。
 */
const handleStructuredQuery = async () => {
    structuredDataQueried.value = true
    await loadStructuredData()
}

const applyQuery = async () => {
    showQueryBuilder.value = false
    pagination.pageNum = 1
    structuredJumpPage.value = '1'
    await handleStructuredQuery()
}

const openNewModal = () => {
    if (isReadOnlyStructuredTable.value) {
        return
    }
    isEditMode.value = false
    Object.keys(editingRow).forEach(key => delete editingRow[key])
    const columns = tableColumns.value.length
        ? tableColumns.value
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
    if (isReadOnlyStructuredTable.value) {
        return
    }
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
    if (isReadOnlyStructuredTable.value) {
        return
    }
    const node = dataStore.currentNode
    const path = resolveStructuredDeletePath(node)
    if (!path) {
        alert('未获取到有效的结构化表路径')
        return
    }
    try {
        if (isEditMode.value) {
            await dataStore.updateRow({
                path,
                data: { ...editingRow }
            })
        } else {
            await dataStore.createRow({
                path,
                data: { ...editingRow }
            })
        }
        structuredDataQueried.value = true
        await loadStructuredData()
        showEditModal.value = false
    } catch (e) {
        alert(e.message || '保存失败')
    }
}

const selectRow = (index) => {
    selectedRowIndex.value = index
}

const resolveStructuredDeletePath = (node) => {
    const normalizedPath = String(node?.path || '').trim().replace(/\.+$/, '')
    if (normalizedPath) {
        // 当前节点是列节点时，需要回退到上一级表路径
        if (!node?.isStructuredColumn) return normalizedPath
        const segments = normalizedPath.split('.').map(item => item.trim()).filter(Boolean)
        if (segments.length > 1) {
            return segments.slice(0, -1).join('.')
        }
    }
    const schema = String(node?.schema || '').trim()
    const table = String(node?.table || '').trim()
    if (!table) return ''
    if (!schema) return `rt.${table}`
    const lower = schema.toLowerCase()
    if (lower === 'rt') return `rt.${table}`
    if (lower.startsWith('rt.')) return `${schema}.${table}`
    return `rt.${schema}.${table}`
}

const handleDeleteRow = async () => {
    if (isReadOnlyStructuredTable.value) {
        return
    }
    if (!selectedRow.value) {
        alert('请先选择一条记录')
        return
    }
    if (confirm('确认删除该行数据吗？')) {
        const node = dataStore.currentNode
        const path = resolveStructuredDeletePath(node)
        if (!path) {
            alert('未获取到有效的结构化表路径')
            return
        }
        const keys = { ...selectedRow.value }
        try {
            await dataStore.deleteRow({
                path,
                keys
            })
            structuredDataQueried.value = true
            await loadStructuredData()
        } catch (e) {
            alert(e.message || '删除失败')
        }
    }
}

const handleRemoveCurrentNode = () => {
    if (!dataStore.currentNode.id) return
    if (['ts', 'rt', 'task', 'models'].includes(dataStore.currentNode.type)) {
        alert('根节点不支持直接删除')
        return
    }
    if (dataStore.currentNode.readOnly) {
        alert('任务结果节点暂不支持删除')
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
        } else if (node.type === 'task') {
            symbolColor = '#6366f1'
            borderColor = '#4f46e5'
        } else if (node.type === 'models') {
            symbolColor = '#f97316'
            borderColor = '#ea580c'
        }
        
        if (node.type === 'group') {
            if (String(node?.previewRole || '').trim().toUpperCase() === 'TABLE') {
                symbolColor = '#22c55e'
                borderColor = '#16a34a'
            } else if (String(node?.previewMode || '').trim().toUpperCase() === 'TIME_SERIES') {
                symbolColor = '#3b82f6'
                borderColor = '#2563eb'
            } else if (String(node?.previewMode || '').trim().toUpperCase() === 'STRUCTURED') {
                symbolColor = '#22c55e'
                borderColor = '#16a34a'
            } else {
                symbolColor = '#f59e0b'
                borderColor = '#d97706'
            }
        } else if (['point', 'file'].includes(node.type)) {
            if (String(node?.previewMode || '').trim().toUpperCase() === 'TIME_SERIES') {
                symbolColor = '#3b82f6'
                borderColor = '#2563eb'
            } else {
                symbolColor = '#22c55e'
                borderColor = '#16a34a'
            }
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
                 const typeMap = { ts: '时序数据', rt: '结构化数据', task: '任务结果', models: '模型文件', group: '路径', point: '叶子', file: '文件' }
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
    if (['group', 'ts', 'rt', 'task', 'models', 'file'].includes(dataStore.currentNode.type)) {
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
            const previewMode = String(child?.previewMode || '').trim().toUpperCase()
            const previewRole = String(child?.previewRole || '').trim().toUpperCase()
            if (rootType === 'task' && previewRole === 'TABLE') {
                return '结果表'
            }
            if (child.type === 'group') {
                if (rootType === 'models') return '目录'
                return '路径'
            }
            if (child.type === 'point') {
                if (rootType === 'task' && previewMode === 'STRUCTURED') return '结果列'
                if (rootType === 'task' && previewMode === 'TIME_SERIES') return '结果测点'
                return rootType === 'rt' ? '列' : '测点'
            }
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
    if (newMode === 'topology' && ['group', 'ts', 'rt', 'task', 'models'].includes(dataStore.currentNode.type)) {
        nextTick(initTreeChart)
        return
    }
    if (isTimeSeriesNode.value) {
        await openTimeSeriesPreview()
        return
    }
    if (isStructuredTableNode.value) {
        await loadStructuredSchema(true)
        return
    }
    currentTsPreviewPath.value = ''
    currentStructuredTablePath.value = ''
    structuredSchemaError.value = ''
    structuredDataError.value = ''
    structuredDataQueried.value = false
    resetStructuredPagination()
    updateTableDataForSelection()
})

onMounted(() => {
    dataStore.loadResourceTree().catch(err => {
        console.error('加载数据资源树失败', err)
    })
    dataStore.loadDataSources().catch(err => {
        console.error('加载数据源失败', err)
    })

    if (dataStore.currentNode.id) {
        if (dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rt', 'task', 'models'].includes(dataStore.currentNode.type)) {
            initTreeChart()
        } else if (isTimeSeriesNode.value) {
            openTimeSeriesPreview()
        } else if (isStructuredTableNode.value) {
            loadStructuredSchema(true)
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
            <button @click="dataStore.openImportWizard()" class="px-6 py-2 bg-white border border-gray-200 rounded-full shadow-lg shadow-gray-100 text-gray-600 text-sm hover:bg-gray-50 transition-all transform hover:-translate-y-1">
                 <i class="ri-upload-cloud-line mr-1"></i> 导入数据</button>
         </div>
     </div>

     <!-- Content Viewer -->
     <div v-else class="flex-1 flex flex-col h-full min-h-0">
         <!-- Group/Tree View in Workspace -->
         <div v-show="dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rt', 'task', 'models'].includes(dataStore.currentNode.type)" 
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
         <div v-show="!(dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rt', 'task', 'models'].includes(dataStore.currentNode.type))" 
              class="flex-1 relative bg-white rounded border border-gray-100 shadow-sm p-4 flex flex-col min-h-0">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold text-gray-800 flex items-center">
                    <i :class="isTimeSeriesNode
                        ? 'ri-pulse-line text-blue-500'
                        : (isStructuredTableNode || isStructuredColumnNode)
                            ? 'ri-table-line text-green-500'
                            : dataStore.currentNode.type === 'task'
                                ? 'ri-rocket-line text-indigo-500'
                            : dataStore.currentNode.type === 'file'
                                ? 'ri-file-2-line text-amber-500'
                                : 'ri-folder-3-line text-yellow-500'" class="mr-2"></i>
                    {{ dataStore.currentNode.id }}
                </h3>
                <!-- Query / Aggregation Controls for TS -->
                <div v-if="isTimeSeriesNode" class="flex space-x-2 items-center">
                    <div class="flex items-center space-x-1 bg-gray-50 border border-gray-200 rounded px-2 py-1">
                        <i class="ri-calendar-line text-gray-400 text-xs"></i>
                        <input v-model="tsQueryForm.startTime" type="datetime-local" step="1" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-40">
                        <span class="text-gray-400">-</span>
                        <input v-model="tsQueryForm.endTime" type="datetime-local" step="1" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-40">
                    </div>
                    <select v-model="tsQueryForm.aggregator" class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8">
                        <option value="RAW">原始数据</option>
                        <option value="AVG">均值</option>
                        <option value="MAX">最大值</option>
                        <option value="MIN">最小值</option>
                        <option value="SUM">求和</option>
                        <option value="COUNT">计数</option>
                    </select>
                    <div v-if="tsQueryForm.aggregator !== 'RAW'" class="flex items-center space-x-2">
                        <input
                            v-model="tsQueryForm.precisionValue"
                            type="number"
                            min="0"
                            step="any"
                            class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8 w-28"
                            placeholder="步长值">
                        <select v-model="tsQueryForm.precisionUnit" class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8 w-20">
                            <option v-for="unit in TIME_PRECISION_UNIT_OPTIONS" :key="unit.value" :value="unit.value">
                                {{ unit.label }}
                            </option>
                        </select>
                    </div>
                    <button @click="loadTimeSeriesData" class="px-3 py-1 bg-blue-50 text-blue-600 rounded text-xs hover:bg-blue-100 border border-blue-200 h-8">查询</button>
                </div>

                <!-- Group Controls -->
                <div v-if="['group'].includes(dataStore.currentNode.type) && !isStructuredTableNode" class="flex space-x-2 items-center">
                    <button @click="dataStore.showTopology(dataStore.currentNode.type, dataStore.currentNode.id)" class="px-3 py-1 bg-indigo-50 text-indigo-600 rounded text-xs hover:bg-indigo-100 border border-indigo-200 h-8 flex items-center"><i class="ri-node-tree mr-1"></i> 查看拓扑结构</button>
                     <div class="h-4 border-l border-gray-300 mx-1"></div>
                     <button v-if="!isStructuredTableNode && dataStore.currentNode.rootType !== 'ts' && !dataStore.currentNode.readOnly" @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="删除节点">
                        <i class="ri-delete-bin-line"></i>
                    </button>
                </div>

                <!-- Query / Filter Controls for Structured -->
                <div v-if="isStructuredTableNode" class="relative flex items-center space-x-2">
                    <button
                        @click="handleStructuredQuery"
                        :disabled="structuredSchemaLoading || visibleTableColumns.length === 0 || loadingData"
                        class="px-3 py-1 bg-blue-50 text-blue-600 rounded text-xs hover:bg-blue-100 border border-blue-200 h-8 disabled:opacity-50 disabled:cursor-not-allowed">
                        <i class="ri-search-line mr-1"></i> 查询数据
                    </button>
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
                                <select v-model="cond.field" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="">选择字段</option>
                                    <option v-for="field in structuredFilterFields" :key="field" :value="field">{{ field }}</option>
                                </select>
                                
                                <!-- Operator -->
                                <select v-model="cond.op" class="w-20 text-xs border border-gray-300 rounded px-1 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="=">=</option>
                                    <option value=">">></option>
                                    <option value="<"><</option>
                                    <option value=">=">>=</option>
                                    <option value="<="><=</option>
                                    <option value="LIKE">LIKE</option>
                                    <option value="IN">IN</option>
                                    <option value="NOT IN">NOT IN</option>
                                </select>
                                
                                <!-- Value -->
                                <input type="text" v-model="cond.value" placeholder="值" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                
                                <!-- Remove -->
                                <button @click="removeCondition(index)" class="text-red-400 hover:text-red-600 p-1 rounded hover:bg-red-50"><i class="ri-delete-bin-line"></i></button>
                            </div>
                            
                            <button @click="addCondition" class="text-xs text-blue-600 hover:text-blue-700 flex items-center mt-2 px-2 py-1 rounded hover:bg-blue-50"><i class="ri-add-circle-line mr-1"></i> 添加条件</button>
                        </div>
                        
                        <div class="flex justify-end space-x-2 pt-2 border-t border-gray-100">
                            <button @click="resetStructuredConditions" class="px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 rounded">清空</button>
                            <button @click="applyQuery" class="px-3 py-1.5 text-xs bg-blue-600 text-white hover:bg-blue-700 rounded shadow-sm">应用筛选</button>
                        </div>
                    </div>

                    <template v-if="!isReadOnlyStructuredTable">
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
                    </template>
                </div>
            </div>

            <div v-if="isTimeSeriesNode" class="h-64 w-full mb-2 border border-gray-100 rounded shrink-0">
                <div ref="chartRef" class="w-full h-full"></div>
            </div>
            <div v-if="isTimeSeriesNode" class="flex items-center justify-between text-[10px] text-gray-400 mb-2">
                <span>有效数据点：{{ validTsData.length }} / 原始 {{ tsData.length }}</span>
                <span v-if="isChartSampled">图表已采样显示（{{ sampledTsData.length }} / {{ validTsData.length }}）</span>
            </div>
            <div v-if="isTimeSeriesNode && (tsQueryRangeText || tsDataRangeText || validDataRangeText || sampledRangeText)" class="text-[10px] text-gray-400 mb-2">
                <span v-if="tsQueryRangeText">查询范围：{{ tsQueryRangeText }}</span>
                <span v-if="tsDataRangeText" class="ml-2">返回范围：{{ tsDataRangeText }}</span>
                <span v-if="validDataRangeText" class="ml-2">有效范围：{{ validDataRangeText }}</span>
                <span v-if="sampledRangeText" class="ml-2">采样范围：{{ sampledRangeText }}</span>
                <span v-if="tsDownsampleSummary" class="ml-2">降采样：{{ tsDownsampleSummary }}</span>
                <span v-if="tsInvalidValueCount > 0" class="ml-2 text-orange-500">无效值：{{ tsInvalidValueCount }}</span>
            </div>
            <div v-if="isTimeSeriesNode && tsQueryError" class="text-[10px] text-red-500 mb-2">
                {{ tsQueryError }}
            </div>
            <div v-if="isTimeSeriesNode && isTsTableTruncated" class="text-[10px] text-gray-400 mb-2">
                表格仅展示前 {{ TABLE_MAX_ROWS }} 条记录，完整数据请缩小时间范围或导出查看。
            </div>
            <div v-if="isStructuredTableNode" class="text-[10px] text-gray-400 mb-2">
                表路径：{{ currentStructuredTablePath || dataStore.currentNode.path }}，列数：{{ visibleTableColumns.length }}
                <span v-if="!structuredDataQueried && !structuredSchemaError && !structuredSchemaLoading" class="ml-2 text-blue-500">
                    已完成表结构查询，可点击“查询数据”重新加载。
                </span>
                <span v-if="structuredDataQueried" class="ml-2">
                    共 {{ pagination.total }} 条，第 {{ pagination.pageNum }} / {{ structuredTotalPages }} 页
                </span>
                <span v-if="isReadOnlyStructuredTable" class="ml-2 text-amber-600">
                    任务结果为只读预览，不支持新增、编辑和删除。
                </span>
            </div>
            <div v-if="isStructuredTableNode && structuredSchemaError" class="text-[10px] text-red-500 mb-2">
                {{ structuredSchemaError }}
            </div>
            <div v-if="isStructuredTableNode && structuredDataError" class="text-[10px] text-red-500 mb-2">
                {{ structuredDataError }}
            </div>
            <div class="flex-1 overflow-auto border border-gray-200 rounded min-h-0">
                 <table class="w-full text-xs text-left">
                       <thead class="bg-gray-50 text-gray-500 sticky top-0">
                           <tr v-if="isTimeSeriesNode">
                               <th class="px-4 py-2 border-b">时间戳</th><th class="px-4 py-2 border-b">数值</th>
                           </tr>
                           <tr v-else-if="isStructuredTableNode">
                               <th v-for="key in visibleTableColumns" :key="key" class="px-4 py-2 border-b capitalize">{{ key }}</th>
                           </tr>
                       </thead>
                       <tbody class="divide-y divide-gray-100">
                           <template v-if="isTimeSeriesNode">
                               <tr v-if="visibleTsTableData.length === 0" class="text-gray-400 text-center italic p-4">
                                   <td colspan="2" class="py-8">暂无数据</td>
                               </tr>
                               <tr v-else v-for="(row, index) in visibleTsTableData" :key="`${row.time}-${index}`" class="hover:bg-gray-50">
                                   <td class="px-4 py-2 font-mono text-blue-600">{{ row.timeText }}</td>
                                   <td class="px-4 py-2">{{ row.value }}</td>
                               </tr>
                           </template>
                           <template v-else-if="isStructuredTableNode">
                               <tr v-if="tableData.length === 0" class="text-gray-400 text-center italic p-4">
                                   <td colspan="100%" class="py-8">
                                       {{ structuredDataQueried ? '查询完成，但当前条件下无数据。' : '当前仅展示表结构，请点击上方“查询数据”按钮。' }}
                                   </td>
                               </tr>
                               <tr v-else v-for="(row, index) in visibleTableData" :key="index" 
                                   @click="selectRow(index)"
                                   @dblclick="openEditModal"
                                   :class="selectedRowIndex === index ? 'bg-blue-50 border-l-4 border-blue-500' : 'hover:bg-gray-50 border-l-4 border-transparent'"
                                   :style="isReadOnlyStructuredTable ? 'cursor: default;' : ''"
                                   class="cursor-pointer transition-colors">
                                   <td v-for="key in visibleTableColumns" :key="key" class="px-4 py-2">{{ row[key] }}</td>
                               </tr>
                           </template>
                           <template v-else>
                               <tr class="text-gray-400 text-center italic p-4">
                                   <td colspan="100%" class="py-8">请先选择上级表节点查看数据</td>
                               </tr>
                           </template>
                       </tbody>
                   </table>
            </div>
            <div v-if="isStructuredTableNode && structuredDataQueried" class="mt-3 flex items-center justify-between gap-4 text-xs text-gray-500">
                <span>共 {{ pagination.total }} 条，当前第 {{ pagination.pageNum }} / {{ structuredTotalPages }} 页，每页 {{ pagination.pageSize }} 条</span>
                <div class="flex items-center gap-2">
                    <button
                        @click="changeStructuredPage(pagination.pageNum - 1)"
                        :disabled="pagination.pageNum <= 1 || loadingData"
                        class="rounded border border-gray-200 px-2.5 py-1 text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50">
                        上一页
                    </button>
                    <button
                        @click="changeStructuredPage(pagination.pageNum + 1)"
                        :disabled="pagination.pageNum >= structuredTotalPages || loadingData"
                        class="rounded border border-gray-200 px-2.5 py-1 text-gray-600 transition hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50">
                        下一页
                    </button>
                    <span>跳至</span>
                    <input
                        v-model="structuredJumpPage"
                        type="number"
                        min="1"
                        :max="structuredTotalPages"
                        class="w-20 rounded border border-gray-200 px-2 py-1 text-gray-700 focus:border-blue-400 focus:outline-none"
                        @keyup.enter="jumpToStructuredPage">
                    <button
                        @click="jumpToStructuredPage"
                        :disabled="loadingData"
                        class="rounded bg-blue-600 px-2.5 py-1 text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50">
                        跳转
                    </button>
                </div>
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
