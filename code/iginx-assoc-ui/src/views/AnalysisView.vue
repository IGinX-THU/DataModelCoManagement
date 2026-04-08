<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, reactive, computed } from 'vue'
import * as echarts from 'echarts'
import { useAssociationStore } from '../stores/association'
import TaskMonitorView from './TaskMonitorView.vue'
import { fetchTaskSeries, compareTaskSeries, exportTaskPackage, exportTaskReport } from '../api/analysis'
import { BASE_URL } from '../api/request'
import { TIME_PRECISION_UNIT_OPTIONS, formatPrecisionMs, parsePrecisionValueToMs } from '../utils/timePrecision'

const associationStore = useAssociationStore()
const DEFAULT_ANALYSIS_MAX_POINTS = 1200
const DEFAULT_STRUCTURED_PAGE_SIZE = 50

const createEmptyStructuredResult = () => ({
  columns: [],
  page: {
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: DEFAULT_STRUCTURED_PAGE_SIZE
  }
})

const currentView = ref('chart')
const selectedTasks = ref([])
const analysisMode = ref('TIME_SERIES')
const seriesData = ref([])
const structuredResult = ref(createEmptyStructuredResult())
const structuredChartRows = ref([])
const structuredDisplayMode = ref('chart')
const structuredChartType = ref('line')
const structuredXAxis = ref('')
const structuredYAxis = ref('')
const structuredAxisRange = reactive({
  xMin: '',
  xMax: '',
  yMin: '',
  yMax: ''
})
const loadingSeries = ref(false)
const structuredChartLoading = ref(false)
const structuredChartLoaded = ref(false)
const structuredChartError = ref('')
const structuredChartTaskId = ref('')
const chartRef = ref(null)
const structuredChartRef = ref(null)
const useRelativeTime = ref(false)
const packageConfig = reactive({ includeModel: true, includeData: true, includeResult: true })
const reportPreviewPresets = [10, 20, 50, 100]
const reportConfig = reactive({
  includeStats: true,
  includeCharts: true,
  previewStrategy: 'HEAD',
  previewRows: 20
})
const analysisQuery = reactive({
  downsample: true,
  aggregator: 'AVG',
  precisionValue: '',
  precisionUnit: 'ms'
})
const structuredPagination = reactive({
  pageNum: 1,
  pageSize: DEFAULT_STRUCTURED_PAGE_SIZE,
  total: 0
})
const structuredJumpPage = ref('1')

let chartInstance = null
let structuredChartInstance = null
let resizeHandler = null
let structuredChartRequestToken = 0

const resolveTaskDisplayName = (task) => {
  return String(task?.taskName || task?.name || '').trim() || task?.id || '未命名任务'
}

const taskList = computed(() => {
  return (associationStore.tasks || []).map(task => ({
    id: task.id,
    name: resolveTaskDisplayName(task),
    taskName: task.taskName || '',
    time: task.createTime ? task.createTime.replace('T', ' ') : '',
    analysisMode: task.analysisMode || 'TIME_SERIES',
    rangeStart: task.rangeStart,
    rangeEnd: task.rangeEnd
  }))
})

const selectedMode = computed(() => {
  if (selectedTasks.value.some(task => task.analysisMode === 'STRUCTURED')) {
    return 'STRUCTURED'
  }
  return 'TIME_SERIES'
})

const isStructuredTaskSelected = computed(() =>
  selectedTasks.value.length === 1 && selectedTasks.value[0]?.analysisMode === 'STRUCTURED'
)

const isStructuredResultView = computed(() => selectedTasks.value.length > 0 && analysisMode.value === 'STRUCTURED')
const structuredColumns = computed(() => structuredResult.value?.columns || [])
const structuredResultRows = computed(() => structuredResult.value?.page?.records || [])
const structuredChartDataRows = computed(() => structuredChartRows.value || [])
const isStructuredChartView = computed(() =>
  isStructuredResultView.value && structuredDisplayMode.value === 'chart'
)
const structuredTotalPages = computed(() => {
  const total = Number(structuredPagination.total) || 0
  const pageSize = Number(structuredPagination.pageSize) || DEFAULT_STRUCTURED_PAGE_SIZE
  return Math.max(1, Math.ceil(total / pageSize))
})
const structuredNumericColumns = computed(() =>
  structuredColumns.value.filter(column =>
    structuredChartDataRows.value.some(row => toFiniteNumber(row?.[column]) !== null)
  )
)
const structuredXAxisOptions = computed(() => {
  if (structuredChartType.value === 'scatter' || structuredChartType.value === 'histogram') {
    return structuredNumericColumns.value
  }
  return structuredColumns.value
})
const structuredYAxisOptions = computed(() => {
  if (structuredChartType.value === 'histogram') {
    return []
  }
  return structuredNumericColumns.value
})
const structuredChartDescription = computed(() => {
  const baseText = structuredChartLoading.value
    ? '正在加载完整结果表用于图表分析。'
    : structuredChartDataRows.value.length
      ? `图表基于完整结果表 ${structuredChartDataRows.value.length} 条数据绘制。`
      : '图表将基于完整结果表绘制。'
  if (structuredChartType.value === 'histogram') {
    if (!structuredXAxis.value) {
      return `${baseText} 请选择一个数值列生成分布直方图。`
    }
    return `${baseText} X 轴使用 ${structuredXAxis.value} 自动分箱，Y 轴为频次。`
  }
  if (!structuredXAxis.value || !structuredYAxis.value) {
    return `${baseText} 请选择 X 轴列和 Y 轴列。`
  }
  return `${baseText} 当前使用 ${structuredXAxis.value} 作为 X 轴，${structuredYAxis.value} 作为 Y 轴。`
})
const resolvedAnalysisPrecisionMs = computed(() => {
  if (!analysisQuery.downsample) {
    return null
  }
  const manualPrecision = parsePrecisionValueToMs(
    analysisQuery.precisionValue,
    analysisQuery.precisionUnit
  )
  if (manualPrecision) {
    return manualPrecision
  }
  const durations = selectedTasks.value
    .map(task => {
      const start = parseDateTime(task?.rangeStart)
      const end = parseDateTime(task?.rangeEnd)
      if (start === null || end === null || end <= start) {
        return 0
      }
      return end - start
    })
    .filter(duration => duration > 0)
  const durationMs = durations.length ? Math.max(...durations) : 60 * 60 * 1000
  return Math.max(1, Math.ceil(durationMs / DEFAULT_ANALYSIS_MAX_POINTS))
})
const analysisPrecisionText = computed(() => {
  if (!analysisQuery.downsample) {
    return '未启用'
  }
  return formatPrecisionMs(resolvedAnalysisPrecisionMs.value || 1) || '1 毫秒'
})

const resetAnalysisState = () => {
  analysisMode.value = 'TIME_SERIES'
  seriesData.value = []
  structuredResult.value = createEmptyStructuredResult()
  resetStructuredPagination()
  resetStructuredChartState()
  clearStructuredChartData()
}

const disposeChart = () => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

const disposeStructuredChart = () => {
  if (structuredChartInstance) {
    structuredChartInstance.dispose()
    structuredChartInstance = null
  }
}

const ensureSelectable = (task) => {
  const selectingStructured = task.analysisMode === 'STRUCTURED'
  const hasStructuredSelected = selectedTasks.value.some(item => item.analysisMode === 'STRUCTURED')
  if (selectingStructured && selectedTasks.value.length > 0) {
    alert('结构化输入任务只能单独查看，请先取消其他任务的选择。')
    return false
  }
  if (!selectingStructured && hasStructuredSelected) {
    alert('当前已选择结构化输入任务，请先取消后再选择时序任务。')
    return false
  }
  return true
}

const toggleTaskSelection = (task) => {
  const index = selectedTasks.value.findIndex(item => item.id === task.id)
  if (index > -1) {
    selectedTasks.value.splice(index, 1)
    return
  }
  if (!ensureSelectable(task)) {
    return
  }
  if (task.analysisMode === 'STRUCTURED') {
    selectedTasks.value = [task]
    useRelativeTime.value = false
    return
  }
  selectedTasks.value.push(task)
}

const parseDateTime = (value) => {
  if (!value) return null
  const text = String(value).replace(' ', 'T')
  const timestamp = Date.parse(text)
  return Number.isNaN(timestamp) ? null : timestamp
}

const toFiniteNumber = (value) => {
  if (value === null || value === undefined || value === '') {
    return null
  }
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? numericValue : null
}

const formatStructuredCell = (value) => {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

const formatChartNumber = (value) => {
  if (!Number.isFinite(value)) {
    return '-'
  }
  return new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 6
  }).format(value)
}

const formatAxisInputValue = (value) => {
  if (!Number.isFinite(value)) {
    return ''
  }
  if (Math.abs(value) >= 1000000 || Number.isInteger(value)) {
    return String(value)
  }
  return value.toFixed(6).replace(/\.?0+$/, '')
}

const parseOptionalAxisNumber = (value) => {
  if (value === null || value === undefined) {
    return null
  }
  const text = String(value).trim()
  if (!text) {
    return null
  }
  const numericValue = Number(text)
  return Number.isFinite(numericValue) ? numericValue : Number.NaN
}

const sortNumericValues = (values) =>
  values
    .filter(value => Number.isFinite(value))
    .slice()
    .sort((left, right) => left - right)

const calculateQuantile = (sortedValues, ratio) => {
  if (!sortedValues.length) {
    return null
  }
  if (sortedValues.length === 1) {
    return sortedValues[0]
  }
  const index = (sortedValues.length - 1) * ratio
  const lowerIndex = Math.floor(index)
  const upperIndex = Math.ceil(index)
  if (lowerIndex === upperIndex) {
    return sortedValues[lowerIndex]
  }
  const weight = index - lowerIndex
  return sortedValues[lowerIndex] * (1 - weight) + sortedValues[upperIndex] * weight
}

const expandAxisExtent = (minValue, maxValue, paddingRatio = 0.08) => {
  if (!Number.isFinite(minValue) || !Number.isFinite(maxValue)) {
    return null
  }
  if (minValue === maxValue) {
    const padding = Math.max(Math.abs(minValue) * paddingRatio, 1)
    return {
      min: minValue - padding,
      max: maxValue + padding
    }
  }
  const span = maxValue - minValue
  const padding = span * paddingRatio
  return {
    min: minValue - padding,
    max: maxValue + padding
  }
}

const calculateValueAxisWindow = (values, { focus = false, paddingRatio = 0.08 } = {}) => {
  const sortedValues = sortNumericValues(values)
  if (!sortedValues.length) {
    return null
  }

  const rawMin = sortedValues[0]
  const rawMax = sortedValues[sortedValues.length - 1]
  const fullExtent = expandAxisExtent(rawMin, rawMax, paddingRatio)
  let autoExtent = fullExtent
  let focusApplied = false

  if (focus && sortedValues.length >= 6) {
    const q1 = calculateQuantile(sortedValues, 0.25)
    const q3 = calculateQuantile(sortedValues, 0.75)
    const interQuartileRange = (q3 ?? 0) - (q1 ?? 0)
    if (Number.isFinite(interQuartileRange) && interQuartileRange > 0) {
      const lowerFence = q1 - interQuartileRange * 1.5
      const upperFence = q3 + interQuartileRange * 1.5
      const focusedValues = sortedValues.filter(value => value >= lowerFence && value <= upperFence)
      if (focusedValues.length >= Math.max(4, Math.ceil(sortedValues.length * 0.6))) {
        autoExtent = expandAxisExtent(
          focusedValues[0],
          focusedValues[focusedValues.length - 1],
          Math.max(paddingRatio, 0.08)
        )
        focusApplied = autoExtent.min > fullExtent.min || autoExtent.max < fullExtent.max
      }
    }
  }

  return {
    rawMin,
    rawMax,
    autoMin: autoExtent.min,
    autoMax: autoExtent.max,
    focusApplied
  }
}

const resolveVisibleAxisRange = (axisLabel, axisWindow, minInput, maxInput) => {
  if (!axisWindow) {
    return {
      range: null,
      issue: '',
      usingManual: false
    }
  }

  const parsedMin = parseOptionalAxisNumber(minInput)
  const parsedMax = parseOptionalAxisNumber(maxInput)
  if (Number.isNaN(parsedMin) || Number.isNaN(parsedMax)) {
    return {
      range: null,
      issue: `${axisLabel} 范围必须填写为有效数字`,
      usingManual: false
    }
  }

  const minValue = parsedMin ?? axisWindow.autoMin
  const maxValue = parsedMax ?? axisWindow.autoMax
  if (!Number.isFinite(minValue) || !Number.isFinite(maxValue) || minValue >= maxValue) {
    return {
      range: null,
      issue: `${axisLabel} 最小值必须小于最大值`,
      usingManual: parsedMin !== null || parsedMax !== null
    }
  }

  return {
    range: {
      min: minValue,
      max: maxValue
    },
    issue: '',
    usingManual: parsedMin !== null || parsedMax !== null
  }
}

const createAxisMeta = ({
  axisLabel,
  supportsManual = false,
  autoMin = null,
  autoMax = null,
  rawMin = null,
  rawMax = null,
  focusApplied = false,
  helperText = ''
}) => {
  return {
    supportsManual,
    minPlaceholder: supportsManual ? formatAxisInputValue(autoMin) : '',
    maxPlaceholder: supportsManual ? formatAxisInputValue(autoMax) : '',
    helperText: helperText || (
      supportsManual
        ? focusApplied
          ? `${axisLabel} 已自动聚焦主数据区间，自动范围 ${formatChartNumber(autoMin)} ~ ${formatChartNumber(autoMax)}，完整数据范围 ${formatChartNumber(rawMin)} ~ ${formatChartNumber(rawMax)}。`
          : `${axisLabel} 自动范围 ${formatChartNumber(autoMin)} ~ ${formatChartNumber(autoMax)}。`
        : `${axisLabel} 当前不支持直接输入数值范围。`
    )
  }
}

const createEmptyStructuredAxisMeta = () => ({
  x: createAxisMeta({ axisLabel: 'X 轴' }),
  y: createAxisMeta({ axisLabel: 'Y 轴' })
})

const createChartBaseOption = () => ({
  backgroundColor: 'transparent',
  animationDuration: 300,
  grid: { top: 48, right: 24, bottom: 48, left: 56 },
  tooltip: { trigger: 'axis' }
})

const resetStructuredAxisControls = () => {
  structuredAxisRange.xMin = ''
  structuredAxisRange.xMax = ''
  structuredAxisRange.yMin = ''
  structuredAxisRange.yMax = ''
}

const resetStructuredChartState = () => {
  structuredDisplayMode.value = 'chart'
  structuredChartType.value = 'line'
  structuredXAxis.value = ''
  structuredYAxis.value = ''
  resetStructuredAxisControls()
  disposeStructuredChart()
}

const clearStructuredChartData = () => {
  structuredChartRequestToken += 1
  structuredChartRows.value = []
  structuredChartLoading.value = false
  structuredChartLoaded.value = false
  structuredChartError.value = ''
  structuredChartTaskId.value = ''
  resetStructuredAxisControls()
  disposeStructuredChart()
}

const resetStructuredPagination = () => {
  structuredPagination.pageNum = 1
  structuredPagination.pageSize = DEFAULT_STRUCTURED_PAGE_SIZE
  structuredPagination.total = 0
  structuredJumpPage.value = '1'
}

const syncStructuredPagination = (page) => {
  structuredPagination.pageNum = Number(page?.pageNum) > 0 ? Number(page.pageNum) : 1
  structuredPagination.pageSize = Number(page?.pageSize) > 0 ? Number(page.pageSize) : DEFAULT_STRUCTURED_PAGE_SIZE
  structuredPagination.total = Math.max(0, Number(page?.total) || 0)
  structuredJumpPage.value = String(structuredPagination.pageNum)
}

const buildAnalysisOptions = () => ({
  relative: useRelativeTime.value,
  downsample: analysisQuery.downsample,
  aggregator: analysisQuery.aggregator,
  precisionMs: resolvedAnalysisPrecisionMs.value,
  pageNum: structuredPagination.pageNum,
  pageSize: structuredPagination.pageSize
})

const loadStructuredChartData = async (taskId, force = false) => {
  if (!taskId) {
    clearStructuredChartData()
    return
  }
  if (!force && structuredChartLoaded.value && structuredChartTaskId.value === taskId) {
    return
  }
  if (!force && structuredChartLoading.value && structuredChartTaskId.value === taskId) {
    return
  }

  const requestToken = ++structuredChartRequestToken
  structuredChartTaskId.value = taskId
  structuredChartLoading.value = true
  structuredChartLoaded.value = false
  structuredChartError.value = ''
  structuredChartRows.value = []

  try {
    const payload = await fetchTaskSeries(taskId, {
      includeChartData: true,
      includePageData: false
    })
    if (requestToken !== structuredChartRequestToken) {
      return
    }
    const chartResult = payload?.structuredResult || {}
    if (Array.isArray(chartResult.columns) && chartResult.columns.length) {
      structuredResult.value = {
        ...structuredResult.value,
        columns: chartResult.columns
      }
    }
    structuredChartRows.value = Array.isArray(chartResult.chartRows) ? chartResult.chartRows : []
    structuredChartLoaded.value = true
  } catch (err) {
    if (requestToken !== structuredChartRequestToken) {
      return
    }
    console.error('加载结构化完整图表数据失败', err)
    structuredChartError.value = err.message || '加载完整图表数据失败'
  } finally {
    if (requestToken === structuredChartRequestToken) {
      structuredChartLoading.value = false
    }
  }
}

const loadAnalysis = async () => {
  if (!selectedTasks.value.length) {
    resetAnalysisState()
    nextTick(disposeChart)
    return
  }
  loadingSeries.value = true
  try {
    const ids = selectedTasks.value.map(item => item.id)
    const options = buildAnalysisOptions()
    const payload = ids.length === 1
      ? await fetchTaskSeries(ids[0], options)
      : await compareTaskSeries(ids, options)
    analysisMode.value = payload?.analysisMode || 'TIME_SERIES'
    seriesData.value = payload?.series || []
    structuredResult.value = payload?.structuredResult || createEmptyStructuredResult()
    syncStructuredPagination(structuredResult.value?.page)
    if (analysisMode.value === 'STRUCTURED') {
      useRelativeTime.value = false
      void loadStructuredChartData(ids[0], structuredChartTaskId.value !== ids[0] || !structuredChartLoaded.value)
    } else {
      clearStructuredChartData()
    }
  } catch (err) {
    console.error('加载任务分析结果失败', err)
    alert(err.message || '加载任务分析结果失败')
    resetAnalysisState()
  } finally {
    loadingSeries.value = false
    nextTick(() => {
      if (analysisMode.value === 'TIME_SERIES') {
        initChart()
      } else {
        disposeChart()
      }
    })
  }
}

const goToStructuredPage = async (targetPage) => {
  if (!isStructuredTaskSelected.value || loadingSeries.value) {
    return
  }
  const numericPage = Number(targetPage)
  const resolvedPage = Number.isFinite(numericPage)
    ? Math.min(Math.max(1, Math.trunc(numericPage)), structuredTotalPages.value)
    : structuredPagination.pageNum
  if (resolvedPage === structuredPagination.pageNum) {
    structuredJumpPage.value = String(structuredPagination.pageNum)
    return
  }
  structuredPagination.pageNum = resolvedPage
  structuredJumpPage.value = String(resolvedPage)
  await loadAnalysis()
}

const jumpToStructuredPage = async () => {
  await goToStructuredPage(structuredJumpPage.value)
}

const initChart = () => {
  if (analysisMode.value !== 'TIME_SERIES' || !chartRef.value) {
    disposeChart()
    return
  }
  disposeChart()
  chartInstance = echarts.init(chartRef.value)

  const relativeMode = seriesData.value.some(item => item.relative) || useRelativeTime.value
  const series = (seriesData.value || []).map(item => ({
    name: item.label || item.taskId || 'Task',
    type: 'line',
    data: (item.points || []).map(point => [point.timestamp, point.value]),
    smooth: true,
    showSymbol: false
  }))

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: selectedTasks.value.length ? (relativeMode ? '相对时间结果对比' : '绝对时间结果对比') : '请选择任务进行分析',
      left: 'center',
      textStyle: { color: '#666' }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        if (!params || !params.length) return ''
        const x = params[0].value?.[0]
        const title = relativeMode ? `${x}s` : new Date(x).toLocaleString()
        const lines = params.map(p => `${p.marker}${p.seriesName}: ${p.value?.[1] ?? '-'}`)
        return [title, ...lines].join('<br/>')
      }
    },
    legend: { bottom: 0, textStyle: { color: '#666' } },
    grid: { top: 60, right: 30, bottom: 60, left: 50 },
    xAxis: relativeMode ? {
      type: 'value',
      axisLine: { lineStyle: { color: '#ccc' } },
      axisLabel: { color: '#666', formatter: value => `${value}s` }
    } : {
      type: 'time',
      axisLine: { lineStyle: { color: '#ccc' } },
      axisLabel: { color: '#666' }
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#eee' } },
      axisLabel: { color: '#666' }
    },
    series: series.length ? series : [{ type: 'line', data: [] }]
  }
  chartInstance.setOption(option)
}

const buildStructuredLineOption = () => {
  if (!structuredXAxis.value) {
    return { option: null, issue: '请选择 X 轴列', axisMeta: createEmptyStructuredAxisMeta() }
  }
  if (!structuredYAxis.value) {
    return { option: null, issue: '请选择 Y 轴列', axisMeta: createEmptyStructuredAxisMeta() }
  }

  const rawPoints = structuredChartDataRows.value
    .map((row, index) => {
      const yValue = toFiniteNumber(row?.[structuredYAxis.value])
      if (yValue === null) {
        return null
      }
      return {
        rowLabel: `第 ${index + 1} 行`,
        rawX: row?.[structuredXAxis.value],
        xLabel: formatStructuredCell(row?.[structuredXAxis.value] ?? `第 ${index + 1} 行`),
        yValue
      }
    })
    .filter(Boolean)

  if (!rawPoints.length) {
    return {
      option: null,
      issue: '所选列在完整结果表中没有可绘制的数值结果',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }

  const yAxisWindow = calculateValueAxisWindow(
    rawPoints.map(point => point.yValue),
    { focus: false, paddingRatio: 0.08 }
  )
  const yAxisRange = resolveVisibleAxisRange('Y 轴', yAxisWindow, structuredAxisRange.yMin, structuredAxisRange.yMax)
  if (yAxisRange.issue) {
    return {
      option: null,
      issue: yAxisRange.issue,
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          helperText: '当前 X 轴范围将根据所选列自动计算。'
        }),
        y: createAxisMeta({
          axisLabel: 'Y 轴',
          supportsManual: true,
          autoMin: yAxisWindow?.autoMin,
          autoMax: yAxisWindow?.autoMax,
          rawMin: yAxisWindow?.rawMin,
          rawMax: yAxisWindow?.rawMax
        })
      }
    }
  }

  const numericPoints = rawPoints
    .map(point => ({
      ...point,
      xValue: toFiniteNumber(point.rawX)
    }))
    .filter(point => point.xValue !== null)

  const canUseNumericXAxis = numericPoints.length === rawPoints.length

  if (canUseNumericXAxis) {
    const points = numericPoints.sort((left, right) => left.xValue - right.xValue)
    const xAxisWindow = calculateValueAxisWindow(
      points.map(point => point.xValue),
      { focus: false, paddingRatio: 0.06 }
    )
    const xAxisRange = resolveVisibleAxisRange('X 轴', xAxisWindow, structuredAxisRange.xMin, structuredAxisRange.xMax)
    if (xAxisRange.issue) {
      return {
        option: null,
        issue: xAxisRange.issue,
        axisMeta: {
          x: createAxisMeta({
            axisLabel: 'X 轴',
            supportsManual: true,
            autoMin: xAxisWindow?.autoMin,
            autoMax: xAxisWindow?.autoMax,
            rawMin: xAxisWindow?.rawMin,
            rawMax: xAxisWindow?.rawMax
          }),
          y: createAxisMeta({
            axisLabel: 'Y 轴',
            supportsManual: true,
            autoMin: yAxisWindow?.autoMin,
            autoMax: yAxisWindow?.autoMax,
            rawMin: yAxisWindow?.rawMin,
            rawMax: yAxisWindow?.rawMax
          })
        }
      }
    }

    const option = {
      ...createChartBaseOption(),
      tooltip: {
        trigger: 'axis',
        formatter: (params) => {
          const [xValue, yValue] = params?.[0]?.value || []
          return [
            `${structuredXAxis.value}: ${formatChartNumber(Number(xValue))}`,
            `${structuredYAxis.value}: ${formatChartNumber(Number(yValue))}`
          ].join('<br/>')
        }
      },
      xAxis: {
        type: 'value',
        name: structuredXAxis.value,
        min: xAxisRange.range.min,
        max: xAxisRange.range.max,
        boundaryGap: false,
        splitLine: { lineStyle: { color: '#e2e8f0' } },
        axisLabel: {
          color: '#64748b',
          formatter: value => formatChartNumber(value)
        }
      },
      yAxis: {
        type: 'value',
        name: structuredYAxis.value,
        min: yAxisRange.range.min,
        max: yAxisRange.range.max,
        splitLine: { lineStyle: { color: '#e2e8f0' } },
        axisLabel: {
          color: '#64748b',
          formatter: value => formatChartNumber(value)
        }
      },
      series: [
        {
          name: structuredYAxis.value,
          type: 'line',
          smooth: true,
          showSymbol: points.length <= 80,
          symbolSize: 7,
          data: points.map(point => [point.xValue, point.yValue]),
          lineStyle: { width: 3, color: '#2563eb' },
          itemStyle: { color: '#2563eb' },
          areaStyle: { color: 'rgba(37, 99, 235, 0.12)' }
        }
      ]
    }

    return {
      option,
      issue: '',
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow.autoMin,
          autoMax: xAxisWindow.autoMax,
          rawMin: xAxisWindow.rawMin,
          rawMax: xAxisWindow.rawMax
        }),
        y: createAxisMeta({
          axisLabel: 'Y 轴',
          supportsManual: true,
          autoMin: yAxisWindow.autoMin,
          autoMax: yAxisWindow.autoMax,
          rawMin: yAxisWindow.rawMin,
          rawMax: yAxisWindow.rawMax
        })
      }
    }
  }

  const option = {
    ...createChartBaseOption(),
    grid: { top: 48, right: 24, bottom: 92, left: 56 },
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const point = rawPoints[params?.[0]?.dataIndex ?? 0]
        if (!point) {
          return ''
        }
        return [
          `${structuredXAxis.value}: ${point.xLabel}`,
          `${structuredYAxis.value}: ${formatChartNumber(point.yValue)}`
        ].join('<br/>')
      }
    },
    xAxis: {
      type: 'category',
      data: rawPoints.map(point => point.xLabel),
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' }
    },
    yAxis: {
      type: 'value',
      name: structuredYAxis.value,
      min: yAxisRange.range.min,
      max: yAxisRange.range.max,
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: {
        color: '#64748b',
        formatter: value => formatChartNumber(value)
      }
    },
    dataZoom: rawPoints.length > 20 ? [
      {
        type: 'inside',
        xAxisIndex: 0,
        filterMode: 'none'
      },
      {
        type: 'slider',
        xAxisIndex: 0,
        filterMode: 'none',
        height: 18,
        bottom: 16
      }
    ] : [],
    series: [
      {
        name: structuredYAxis.value,
        type: 'line',
        smooth: true,
        showSymbol: rawPoints.length <= 80,
        symbolSize: 7,
        data: rawPoints.map(point => point.yValue),
        lineStyle: { width: 3, color: '#2563eb' },
        itemStyle: { color: '#2563eb' },
        areaStyle: { color: 'rgba(37, 99, 235, 0.12)' }
      }
    ]
  }

  return {
    option,
    issue: '',
    axisMeta: {
      x: createAxisMeta({
        axisLabel: 'X 轴',
        helperText: rawPoints.length > 20
          ? '当前 X 轴为类目轴，可通过图内底部缩放条调整显示窗口。'
          : '当前 X 轴为类目轴，可直接切换列查看不同趋势。'
      }),
      y: createAxisMeta({
        axisLabel: 'Y 轴',
        supportsManual: true,
        autoMin: yAxisWindow.autoMin,
        autoMax: yAxisWindow.autoMax,
        rawMin: yAxisWindow.rawMin,
        rawMax: yAxisWindow.rawMax
      })
    }
  }
}

const buildStructuredScatterOption = () => {
  if (!structuredXAxis.value) {
    return { option: null, issue: '请选择数值型 X 轴列', axisMeta: createEmptyStructuredAxisMeta() }
  }
  if (!structuredYAxis.value) {
    return { option: null, issue: '请选择数值型 Y 轴列', axisMeta: createEmptyStructuredAxisMeta() }
  }
  const points = structuredChartDataRows.value
    .map((row, index) => {
      const xValue = toFiniteNumber(row?.[structuredXAxis.value])
      const yValue = toFiniteNumber(row?.[structuredYAxis.value])
      if (xValue === null || yValue === null) {
        return null
      }
      return {
        value: [xValue, yValue],
        rowLabel: `第 ${index + 1} 行`
      }
    })
    .filter(Boolean)

  if (!points.length) {
    return {
      option: null,
      issue: '所选列在完整结果表中没有可绘制的散点数据',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }

  const xAxisWindow = calculateValueAxisWindow(
    points.map(point => point.value[0]),
    { focus: true, paddingRatio: 0.08 }
  )
  const yAxisWindow = calculateValueAxisWindow(
    points.map(point => point.value[1]),
    { focus: true, paddingRatio: 0.08 }
  )
  const xAxisRange = resolveVisibleAxisRange('X 轴', xAxisWindow, structuredAxisRange.xMin, structuredAxisRange.xMax)
  if (xAxisRange.issue) {
    return {
      option: null,
      issue: xAxisRange.issue,
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow?.autoMin,
          autoMax: xAxisWindow?.autoMax,
          rawMin: xAxisWindow?.rawMin,
          rawMax: xAxisWindow?.rawMax,
          focusApplied: xAxisWindow?.focusApplied
        }),
        y: createAxisMeta({
          axisLabel: 'Y 轴',
          supportsManual: true,
          autoMin: yAxisWindow?.autoMin,
          autoMax: yAxisWindow?.autoMax,
          rawMin: yAxisWindow?.rawMin,
          rawMax: yAxisWindow?.rawMax,
          focusApplied: yAxisWindow?.focusApplied
        })
      }
    }
  }
  const yAxisRange = resolveVisibleAxisRange('Y 轴', yAxisWindow, structuredAxisRange.yMin, structuredAxisRange.yMax)
  if (yAxisRange.issue) {
    return {
      option: null,
      issue: yAxisRange.issue,
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow.autoMin,
          autoMax: xAxisWindow.autoMax,
          rawMin: xAxisWindow.rawMin,
          rawMax: xAxisWindow.rawMax,
          focusApplied: xAxisWindow.focusApplied
        }),
        y: createAxisMeta({
          axisLabel: 'Y 轴',
          supportsManual: true,
          autoMin: yAxisWindow?.autoMin,
          autoMax: yAxisWindow?.autoMax,
          rawMin: yAxisWindow?.rawMin,
          rawMax: yAxisWindow?.rawMax,
          focusApplied: yAxisWindow?.focusApplied
        })
      }
    }
  }

  const scatterSymbolSize = points.length > 500
    ? 6
    : points.length > 220
      ? 8
      : 10

  const option = {
    ...createChartBaseOption(),
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        const [xValue, yValue] = params?.value || []
        return [
          params?.data?.rowLabel || '数据点',
          `${structuredXAxis.value}: ${formatChartNumber(Number(xValue))}`,
          `${structuredYAxis.value}: ${formatChartNumber(Number(yValue))}`
        ].join('<br/>')
      }
    },
    xAxis: {
      type: 'value',
      name: structuredXAxis.value,
      min: xAxisRange.range.min,
      max: xAxisRange.range.max,
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: {
        color: '#64748b',
        formatter: value => formatChartNumber(value)
      }
    },
    yAxis: {
      type: 'value',
      name: structuredYAxis.value,
      min: yAxisRange.range.min,
      max: yAxisRange.range.max,
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: {
        color: '#64748b',
        formatter: value => formatChartNumber(value)
      }
    },
    series: [
      {
        name: `${structuredXAxis.value} / ${structuredYAxis.value}`,
        type: 'scatter',
        data: points,
        symbolSize: scatterSymbolSize,
        itemStyle: {
          color: '#0f766e',
          opacity: points.length > 300 ? 0.65 : 0.8
        }
      }
    ]
  }

  return {
    option,
    issue: '',
    axisMeta: {
      x: createAxisMeta({
        axisLabel: 'X 轴',
        supportsManual: true,
        autoMin: xAxisWindow.autoMin,
        autoMax: xAxisWindow.autoMax,
        rawMin: xAxisWindow.rawMin,
        rawMax: xAxisWindow.rawMax,
        focusApplied: xAxisWindow.focusApplied
      }),
      y: createAxisMeta({
        axisLabel: 'Y 轴',
        supportsManual: true,
        autoMin: yAxisWindow.autoMin,
        autoMax: yAxisWindow.autoMax,
        rawMin: yAxisWindow.rawMin,
        rawMax: yAxisWindow.rawMax,
        focusApplied: yAxisWindow.focusApplied
      })
    }
  }
}

const buildStructuredHistogramOption = () => {
  if (!structuredXAxis.value) {
    return { option: null, issue: '请选择一个数值列生成直方图', axisMeta: createEmptyStructuredAxisMeta() }
  }

  const values = structuredChartDataRows.value
    .map(row => toFiniteNumber(row?.[structuredXAxis.value]))
    .filter(value => value !== null)

  if (!values.length) {
    return {
      option: null,
      issue: '所选列在完整结果表中没有可统计的数值数据',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }

  const xAxisWindow = calculateValueAxisWindow(values, { focus: false, paddingRatio: 0.06 })
  const xAxisRange = resolveVisibleAxisRange('X 轴', xAxisWindow, structuredAxisRange.xMin, structuredAxisRange.xMax)
  if (xAxisRange.issue) {
    return {
      option: null,
      issue: xAxisRange.issue,
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow?.autoMin,
          autoMax: xAxisWindow?.autoMax,
          rawMin: xAxisWindow?.rawMin,
          rawMax: xAxisWindow?.rawMax,
          helperText: xAxisWindow
            ? `X 轴范围会据此重新分箱统计。自动范围 ${formatChartNumber(xAxisWindow.autoMin)} ~ ${formatChartNumber(xAxisWindow.autoMax)}，完整数据范围 ${formatChartNumber(xAxisWindow.rawMin)} ~ ${formatChartNumber(xAxisWindow.rawMax)}。`
            : 'X 轴范围会据此重新分箱统计。'
        }),
        y: createAxisMeta({ axisLabel: 'Y 轴' })
      }
    }
  }

  const visibleValues = values.filter(value =>
    value >= xAxisRange.range.min && value <= xAxisRange.range.max
  )
  if (!visibleValues.length) {
    return {
      option: null,
      issue: '当前 X 轴范围内没有可统计的数值数据',
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow.autoMin,
          autoMax: xAxisWindow.autoMax,
          rawMin: xAxisWindow.rawMin,
          rawMax: xAxisWindow.rawMax,
          helperText: `X 轴范围会据此重新分箱统计。自动范围 ${formatChartNumber(xAxisWindow.autoMin)} ~ ${formatChartNumber(xAxisWindow.autoMax)}，完整数据范围 ${formatChartNumber(xAxisWindow.rawMin)} ~ ${formatChartNumber(xAxisWindow.rawMax)}。`
        }),
        y: createAxisMeta({ axisLabel: 'Y 轴' })
      }
    }
  }

  const minValue = Math.min(...visibleValues)
  const maxValue = Math.max(...visibleValues)
  let categories = []
  let counts = []

  if (minValue === maxValue) {
    categories = [formatChartNumber(minValue)]
    counts = [visibleValues.length]
  } else {
    const binCount = Math.min(20, Math.max(5, Math.round(Math.sqrt(visibleValues.length))))
    const binWidth = (maxValue - minValue) / binCount
    counts = Array.from({ length: binCount }, () => 0)
    categories = Array.from({ length: binCount }, (_, index) => {
      const start = minValue + index * binWidth
      const end = index === binCount - 1 ? maxValue : start + binWidth
      return `${formatChartNumber(start)} ~ ${formatChartNumber(end)}`
    })
    visibleValues.forEach(value => {
      const index = value === maxValue
        ? binCount - 1
        : Math.min(binCount - 1, Math.max(0, Math.floor((value - minValue) / binWidth)))
      counts[index] += 1
    })
  }

  const yAxisWindow = calculateValueAxisWindow(counts.map(value => Number(value)), {
    focus: false,
    paddingRatio: 0.12
  })
  const yAxisRange = resolveVisibleAxisRange('Y 轴', yAxisWindow, structuredAxisRange.yMin, structuredAxisRange.yMax)
  if (yAxisRange.issue) {
    return {
      option: null,
      issue: yAxisRange.issue,
      axisMeta: {
        x: createAxisMeta({
          axisLabel: 'X 轴',
          supportsManual: true,
          autoMin: xAxisWindow.autoMin,
          autoMax: xAxisWindow.autoMax,
          rawMin: xAxisWindow.rawMin,
          rawMax: xAxisWindow.rawMax,
          helperText: `X 轴范围会据此重新分箱统计。自动范围 ${formatChartNumber(xAxisWindow.autoMin)} ~ ${formatChartNumber(xAxisWindow.autoMax)}，完整数据范围 ${formatChartNumber(xAxisWindow.rawMin)} ~ ${formatChartNumber(xAxisWindow.rawMax)}。`
        }),
        y: createAxisMeta({
          axisLabel: 'Y 轴',
          supportsManual: true,
          autoMin: yAxisWindow?.autoMin,
          autoMax: yAxisWindow?.autoMax,
          rawMin: 0,
          rawMax: yAxisWindow?.rawMax,
          helperText: yAxisWindow
            ? `Y 轴默认按频次自动计算，可手动收紧或放大频次显示区间。`
            : 'Y 轴默认按频次自动计算。'
        })
      }
    }
  }

  const option = {
    ...createChartBaseOption(),
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        const item = params?.[0]
        if (!item) {
          return ''
        }
        return [
          `${structuredXAxis.value}: ${item.axisValue}`,
          `频次: ${item.value}`
        ].join('<br/>')
      }
    },
    xAxis: {
      type: 'category',
      name: structuredXAxis.value,
      data: categories,
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b', interval: 0, rotate: categories.length > 8 ? 20 : 0 }
    },
    yAxis: {
      type: 'value',
      name: '频次',
      min: Math.min(0, yAxisRange.range.min),
      max: yAxisRange.range.max,
      splitLine: { lineStyle: { color: '#e2e8f0' } },
      axisLabel: { color: '#64748b' }
    },
    series: [
      {
        name: '频次',
        type: 'bar',
        data: counts,
        barMaxWidth: 42,
        itemStyle: { color: '#ea580c', borderRadius: [4, 4, 0, 0] }
      }
    ]
  }

  return {
    option,
    issue: '',
    axisMeta: {
      x: createAxisMeta({
        axisLabel: 'X 轴',
        supportsManual: true,
        autoMin: xAxisWindow.autoMin,
        autoMax: xAxisWindow.autoMax,
        rawMin: xAxisWindow.rawMin,
        rawMax: xAxisWindow.rawMax,
        helperText: `X 轴范围会据此重新分箱统计。自动范围 ${formatChartNumber(xAxisWindow.autoMin)} ~ ${formatChartNumber(xAxisWindow.autoMax)}，完整数据范围 ${formatChartNumber(xAxisWindow.rawMin)} ~ ${formatChartNumber(xAxisWindow.rawMax)}。`
      }),
      y: createAxisMeta({
        axisLabel: 'Y 轴',
        supportsManual: true,
        autoMin: Math.min(0, yAxisWindow.autoMin),
        autoMax: yAxisWindow.autoMax,
        rawMin: 0,
        rawMax: yAxisWindow.rawMax,
        helperText: 'Y 轴默认按频次自动计算，可手动调节频次显示区间。'
      })
    }
  }
}

const structuredChartState = computed(() => {
  if (structuredChartError.value) {
    return {
      option: null,
      issue: structuredChartError.value,
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }
  if (structuredChartLoading.value && !structuredChartDataRows.value.length) {
    return {
      option: null,
      issue: '',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }
  if (!structuredChartDataRows.value.length) {
    return {
      option: null,
      issue: '完整结果表暂无可用于绘图的数据',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }
  if (!structuredColumns.value.length) {
    return {
      option: null,
      issue: '当前结果表没有可用列',
      axisMeta: createEmptyStructuredAxisMeta()
    }
  }
  if (structuredChartType.value === 'scatter') {
    return buildStructuredScatterOption()
  }
  if (structuredChartType.value === 'histogram') {
    return buildStructuredHistogramOption()
  }
  return buildStructuredLineOption()
})

const structuredAxisMeta = computed(() => structuredChartState.value?.axisMeta || createEmptyStructuredAxisMeta())

const syncStructuredChartSelections = () => {
  const xOptions = structuredXAxisOptions.value
  if (!xOptions.includes(structuredXAxis.value)) {
    structuredXAxis.value = xOptions[0] || ''
  }
  if (structuredChartType.value === 'histogram') {
    structuredYAxis.value = ''
    return
  }
  const yOptions = structuredYAxisOptions.value
  if (!yOptions.includes(structuredYAxis.value)) {
    structuredYAxis.value = yOptions.find(column => column !== structuredXAxis.value) || yOptions[0] || ''
  }
}

const initStructuredChart = () => {
  if (!isStructuredChartView.value || !structuredChartRef.value) {
    disposeStructuredChart()
    return
  }
  const option = structuredChartState.value?.option
  if (!option) {
    disposeStructuredChart()
    return
  }
  if (!structuredChartInstance) {
    structuredChartInstance = echarts.init(structuredChartRef.value)
  }
  structuredChartInstance.setOption(option, true)
}

const exportPackage = async () => {
  if (selectedTasks.value.length !== 1) {
    alert('请先选择一个任务进行导出')
    return
  }
  const task = selectedTasks.value[0]
  try {
    const downloadPath = await exportTaskPackage(task.id, {
      includeModel: packageConfig.includeModel,
      includeInput: packageConfig.includeData,
      includeOutput: packageConfig.includeResult,
      format: 'CSV'
    })
    const url = downloadPath.startsWith('http') ? downloadPath : `${BASE_URL}${downloadPath}`
    window.open(url, '_blank')
    associationStore.showExportResourceModal = false
  } catch (err) {
    alert(err.message || '资源包导出失败')
  }
}

const exportReport = async () => {
  if (selectedTasks.value.length !== 1) {
    alert('请先选择一个任务生成报告')
    return
  }
  normalizeReportPreviewRows()
  const task = selectedTasks.value[0]
  try {
    const downloadPath = await exportTaskReport(task.id, {
      includeStats: reportConfig.includeStats,
      includeCharts: reportConfig.includeCharts,
      previewStrategy: reportConfig.previewStrategy,
      previewRows: Number(reportConfig.previewRows)
    })
    const url = downloadPath.startsWith('http') ? downloadPath : `${BASE_URL}${downloadPath}`
    window.open(url, '_blank')
    associationStore.showExportReportModal = false
  } catch (err) {
    alert(err.message || '报告生成失败')
  }
}

watch(selectedTasks, () => {
  resetStructuredPagination()
  loadAnalysis()
}, { deep: true })

watch(useRelativeTime, () => {
  loadAnalysis()
})

watch([
  () => analysisQuery.downsample,
  () => analysisQuery.aggregator,
  () => analysisQuery.precisionValue,
  () => analysisQuery.precisionUnit
], () => {
  loadAnalysis()
})

const normalizeReportPreviewRows = () => {
  const value = Number(reportConfig.previewRows)
  if (!Number.isFinite(value) || value < 1) {
    reportConfig.previewRows = 20
    return
  }
  reportConfig.previewRows = Math.min(200, Math.floor(value))
}

watch(
  [
    () => structuredChartType.value,
    () => structuredXAxis.value,
    () => structuredYAxis.value,
    () => structuredChartTaskId.value
  ],
  () => {
    resetStructuredAxisControls()
  }
)

watch(
  [
    () => structuredChartType.value,
    structuredColumns,
    structuredChartDataRows
  ],
  () => {
    syncStructuredChartSelections()
  },
  { deep: true, immediate: true }
)

watch(
  [
    () => analysisMode.value,
    () => structuredDisplayMode.value,
    () => structuredChartType.value,
    () => structuredXAxis.value,
    () => structuredYAxis.value,
    () => structuredAxisRange.xMin,
    () => structuredAxisRange.xMax,
    () => structuredAxisRange.yMin,
    () => structuredAxisRange.yMax,
    structuredChartDataRows,
    structuredColumns,
    () => structuredChartLoading.value,
    () => structuredChartError.value
  ],
  () => {
    nextTick(() => {
      if (analysisMode.value === 'STRUCTURED' && structuredDisplayMode.value === 'chart') {
        initStructuredChart()
      } else {
        disposeStructuredChart()
      }
    })
  },
  { deep: true }
)

onMounted(() => {
  resizeHandler = () => {
    if (chartInstance) {
      chartInstance.resize()
    }
    if (structuredChartInstance) {
      structuredChartInstance.resize()
    }
  }
  window.addEventListener('resize', resizeHandler)
  associationStore.loadTasks()
  loadAnalysis()
})

onBeforeUnmount(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
  disposeChart()
  disposeStructuredChart()
})
</script>

<template>
  <div class="h-full flex flex-col bg-white rounded-lg overflow-hidden border border-gray-200 relative">
    <div class="h-10 border-b border-gray-200 flex items-center px-4 bg-gray-50 space-x-4">
      <div
        @click="currentView = 'monitor'"
        :class="currentView === 'monitor' ? 'text-blue-600 border-b-2 border-blue-600 font-bold' : 'text-gray-600 hover:text-gray-800'"
        class="cursor-pointer h-full flex items-center px-2 text-sm transition-colors"
      >
        <i class="ri-task-line mr-2"></i> Task Monitor
      </div>
      <div
        @click="currentView = 'chart'"
        :class="currentView === 'chart' ? 'text-blue-600 border-b-2 border-blue-600 font-bold' : 'text-gray-600 hover:text-gray-800'"
        class="cursor-pointer h-full flex items-center px-2 text-sm transition-colors"
      >
        <i class="ri-line-chart-line mr-2"></i> Result Analysis
      </div>
    </div>

    <div v-if="currentView === 'monitor'" class="flex-1 overflow-hidden">
      <TaskMonitorView />
    </div>

    <div v-else class="flex-1 flex overflow-hidden">
      <div v-if="associationStore.showExportResourceModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
          <h3 class="font-bold text-gray-800 mb-4">导出资源包</h3>
          <div class="space-y-3">
            <div v-if="isStructuredTaskSelected" class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-700">
              结构化输入任务会导出按执行顺序对齐的输入表和结果表，文件中的 KEY 为从 0 开始的行序号。
            </div>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="packageConfig.includeModel" class="mr-2"> 包含算法模型
            </label>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="packageConfig.includeData" class="mr-2"> {{ isStructuredTaskSelected ? '包含结构化输入表' : '包含输入数据' }}
            </label>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="packageConfig.includeResult" class="mr-2"> {{ isStructuredTaskSelected ? '包含结果表' : '包含输出结果' }}
            </label>
          </div>
          <div class="flex justify-end space-x-2 mt-6">
            <button @click="associationStore.showExportResourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
            <button @click="exportPackage" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">导出 ZIP</button>
          </div>
        </div>
      </div>

      <div v-if="associationStore.showExportReportModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[560px] max-w-[92vw] p-6">
          <h3 class="font-bold text-gray-800 mb-4">生成实验报告</h3>
          <div class="space-y-3">
            <div v-if="isStructuredTaskSelected" class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-700">
              结构化输入任务的报告会导出任务概览、输入输出预览，并在启用图表时自动附带数值列的直方图、曲线图和散点图。
            </div>
            <div v-else class="rounded-lg border border-blue-200 bg-blue-50 p-3 text-xs leading-5 text-blue-700">
              时序任务的报告会附带输出结果折线图，并按照所选预览策略展示输入/输出数据样例。
            </div>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="reportConfig.includeStats" class="mr-2"> 包含统计表
            </label>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="reportConfig.includeCharts" class="mr-2"> 包含图表快照
            </label>
            <div class="rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div class="text-sm font-semibold text-slate-700 mb-3">数据预览打印设置</div>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
                <label class="text-sm text-gray-700">
                  <span class="block mb-1">预览方式</span>
                  <select v-model="reportConfig.previewStrategy" class="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none">
                    <option value="HEAD">前 K 条</option>
                    <option value="UNIFORM">均匀采样</option>
                  </select>
                </label>
                <label class="text-sm text-gray-700">
                  <span class="block mb-1">预览条数</span>
                  <input
                    v-model.number="reportConfig.previewRows"
                    type="number"
                    min="1"
                    max="200"
                    class="w-full rounded border border-slate-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                    @blur="normalizeReportPreviewRows"
                  >
                </label>
              </div>
              <div class="flex flex-wrap gap-2 mt-3">
                <button
                  v-for="preset in reportPreviewPresets"
                  :key="preset"
                  type="button"
                  @click="reportConfig.previewRows = preset"
                  :class="Number(reportConfig.previewRows) === preset ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-slate-600 border-slate-300 hover:border-blue-400 hover:text-blue-600'"
                  class="rounded-full border px-3 py-1 text-xs font-medium transition"
                >
                  {{ preset }} 条
                </button>
              </div>
              <p class="mt-3 text-xs leading-5 text-slate-500">
                预览表会按所选策略展示数据；图表部分会自动做均匀采样或下采样，避免 PDF 过大。
              </p>
            </div>
          </div>
          <div class="flex justify-end space-x-2 mt-6">
            <button @click="associationStore.showExportReportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
            <button @click="exportReport" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700">生成 PDF</button>
          </div>
        </div>
      </div>

      <div class="w-56 border-r border-gray-200 bg-gray-50 flex flex-col">
        <div class="p-3 border-b border-gray-200 font-bold text-xs text-gray-600 uppercase">
          Analysis Tasks
        </div>
        <div class="flex-1 overflow-y-auto">
          <div
            v-for="task in taskList"
            :key="task.id"
            @click="toggleTaskSelection(task)"
            :class="selectedTasks.find(t => t.id === task.id) ? 'bg-blue-50 border-l-4 border-blue-500' : 'border-l-4 border-transparent hover:bg-gray-100'"
            class="p-3 border-b border-gray-100 cursor-pointer transition-all"
          >
            <div class="flex items-center justify-between mb-1">
              <div class="flex items-center min-w-0">
                <input type="checkbox" :checked="!!selectedTasks.find(t => t.id === task.id)" class="mr-2 pointer-events-none text-blue-600">
                <div class="min-w-0">
                  <div class="font-bold text-xs text-gray-700 truncate">{{ task.name }}</div>
                  <div class="font-mono text-[10px] text-gray-400 truncate">{{ task.id }}</div>
                </div>
              </div>
              <span
                :class="task.analysisMode === 'STRUCTURED' ? 'bg-amber-100 text-amber-700' : 'bg-blue-100 text-blue-700'"
                class="shrink-0 px-2 py-0.5 rounded-full text-[10px] font-semibold"
              >
                {{ task.analysisMode === 'STRUCTURED' ? '结构化' : '时序' }}
              </span>
            </div>
            <div class="flex justify-between items-center text-[10px] text-gray-400 pl-5">
              <span>{{ task.time.split(' ')[1] }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="flex-1 flex flex-col relative bg-white">
        <div class="h-10 border-b border-gray-200 flex items-center justify-between px-4 bg-gray-50/50">
          <div class="flex items-center gap-3">
            <span class="text-xs font-bold text-gray-600 flex items-center">
              <i :class="analysisMode === 'STRUCTURED' && structuredDisplayMode === 'chart' ? 'ri-bar-chart-box-line mr-2' : analysisMode === 'STRUCTURED' ? 'ri-table-line mr-2' : 'ri-line-chart-line mr-2'"></i>
              {{ analysisMode === 'STRUCTURED' ? (structuredDisplayMode === 'chart' ? '结构化结果图表' : '结构化结果表') : '结果曲线' }}
            </span>
            <div
              v-if="analysisMode === 'STRUCTURED' && selectedTasks.length > 0"
              class="inline-flex rounded-lg border border-slate-200 bg-white p-0.5 shadow-sm"
            >
              <button
                @click="structuredDisplayMode = 'chart'"
                :class="structuredDisplayMode === 'chart' ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'"
                class="rounded-md px-3 py-1 text-xs font-medium transition"
              >
                图表分析
              </button>
              <button
                @click="structuredDisplayMode = 'table'"
                :class="structuredDisplayMode === 'table' ? 'bg-blue-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'"
                class="rounded-md px-3 py-1 text-xs font-medium transition"
              >
                结果表
              </button>
            </div>
            <span
              v-if="analysisMode === 'TIME_SERIES' && selectedTasks.length > 0 && analysisQuery.downsample"
              class="rounded-full bg-blue-50 px-2 py-0.5 text-[10px] text-blue-600 border border-blue-100"
            >
              降采样 {{ analysisQuery.aggregator }} / {{ analysisPrecisionText }}
            </span>
            <span
              v-else-if="analysisMode === 'STRUCTURED' && selectedTasks.length > 0"
              class="rounded-full bg-amber-50 px-2 py-0.5 text-[10px] text-amber-700 border border-amber-100"
            >
              第 {{ structuredPagination.pageNum }} / {{ structuredTotalPages }} 页，共 {{ structuredPagination.total }} 条
            </span>
          </div>
          <div class="flex space-x-2">
            <button class="text-gray-400 hover:text-gray-800 p-1"><i class="ri-camera-line"></i></button>
          </div>
        </div>

        <div v-if="isStructuredResultView" class="flex-1 relative flex flex-col overflow-hidden bg-slate-50/60">
          <div class="flex-1 overflow-auto p-4">
            <div v-if="structuredDisplayMode === 'chart'" class="h-full min-h-[420px] flex flex-col gap-4">
              <div class="rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-xs leading-5 text-sky-700">
                {{ structuredChartDescription }}
              </div>
              <div class="relative min-h-[360px] flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
                <div ref="structuredChartRef" class="h-full w-full"></div>
                <div
                  v-if="structuredChartLoading"
                  class="absolute inset-0 flex items-center justify-center px-6 text-center text-sm text-slate-500 bg-white/80"
                >
                  正在加载完整结果表并生成图表...
                </div>
                <div
                  v-else-if="structuredChartState.issue"
                  class="absolute inset-0 flex items-center justify-center px-6 text-center text-sm text-slate-500 bg-white/80"
                >
                  {{ structuredChartState.issue }}
                </div>
              </div>
            </div>
            <div v-else-if="structuredResultRows.length" class="overflow-auto rounded-lg border border-slate-200 bg-white shadow-sm">
              <table class="min-w-full text-sm text-left">
                <thead class="bg-slate-100 text-slate-600">
                  <tr>
                    <th v-for="column in structuredColumns" :key="column" class="px-4 py-3 font-semibold whitespace-nowrap border-b border-slate-200">
                      {{ column }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in structuredResultRows" :key="index" class="odd:bg-white even:bg-slate-50">
                    <td v-for="column in structuredColumns" :key="`${index}-${column}`" class="px-4 py-3 text-slate-700 whitespace-nowrap border-b border-slate-100">
                      {{ formatStructuredCell(row[column]) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="h-full flex items-center justify-center text-sm text-slate-500">
              当前结构化任务暂无结果可展示
            </div>
          </div>
          <div class="border-t border-slate-200 bg-white px-4 py-3 text-xs text-slate-500">
            <div class="flex items-center justify-between gap-4">
              <span>共 {{ structuredPagination.total }} 条，当前第 {{ structuredPagination.pageNum }} / {{ structuredTotalPages }} 页，每页 {{ structuredPagination.pageSize }} 条</span>
              <div class="flex items-center gap-2">
                <button
                  @click="goToStructuredPage(structuredPagination.pageNum - 1)"
                  :disabled="structuredPagination.pageNum <= 1 || loadingSeries"
                  class="rounded border border-slate-200 px-2.5 py-1 text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  上一页
                </button>
                <button
                  @click="goToStructuredPage(structuredPagination.pageNum + 1)"
                  :disabled="structuredPagination.pageNum >= structuredTotalPages || loadingSeries"
                  class="rounded border border-slate-200 px-2.5 py-1 text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  下一页
                </button>
                <span>跳至</span>
                <input
                  v-model="structuredJumpPage"
                  type="number"
                  min="1"
                  :max="structuredTotalPages"
                  class="w-20 rounded border border-slate-200 px-2 py-1 text-slate-700 focus:border-blue-400 focus:outline-none"
                  @keyup.enter="jumpToStructuredPage"
                >
                <button
                  @click="jumpToStructuredPage"
                  :disabled="loadingSeries"
                  class="rounded bg-blue-600 px-2.5 py-1 text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  跳转
                </button>
              </div>
            </div>
          </div>
          <div v-if="loadingSeries" class="absolute inset-0 flex items-center justify-center text-gray-500 bg-white/70">
            正在加载结构化结果...
          </div>
        </div>

        <div v-else class="flex-1 relative">
          <div ref="chartRef" class="w-full h-full"></div>
          <div v-if="selectedTasks.length === 0" class="absolute inset-0 flex items-center justify-center text-gray-400 pointer-events-none">
            请先从左侧选择要分析的任务
          </div>
          <div v-if="loadingSeries" class="absolute inset-0 flex items-center justify-center text-gray-500 bg-white/70">
            正在加载任务曲线...
          </div>
        </div>
      </div>

      <div class="w-56 border-l border-gray-200 bg-gray-50 flex flex-col">
        <div class="p-3 border-b border-gray-200 font-bold text-xs text-gray-600 uppercase">
          Settings
        </div>
        <div class="p-4 space-y-4">
          <template v-if="selectedMode === 'TIME_SERIES'">
            <div>
              <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">对比模式</label>
              <div class="space-y-1">
                <label class="flex items-center text-xs text-gray-600">
                  <input type="checkbox" v-model="useRelativeTime" class="mr-2"> 相对时间对齐
                </label>
              </div>
            </div>
            <div>
              <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">降采样</label>
              <div class="space-y-2">
                <label class="flex items-center text-xs text-gray-600">
                  <input type="checkbox" v-model="analysisQuery.downsample" class="mr-2"> 启用降采样
                </label>
                <select
                  v-model="analysisQuery.aggregator"
                  :disabled="!analysisQuery.downsample"
                  class="w-full rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
                >
                  <option value="AVG">均值</option>
                  <option value="MAX">最大值</option>
                  <option value="MIN">最小值</option>
                  <option value="SUM">求和</option>
                  <option value="COUNT">计数</option>
                </select>
                <div class="flex gap-2">
                  <input
                    v-model="analysisQuery.precisionValue"
                    type="number"
                    min="0"
                    step="any"
                    :disabled="!analysisQuery.downsample"
                    class="min-w-0 flex-1 rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
                    placeholder="步长值，留空自动计算"
                  >
                  <select
                    v-model="analysisQuery.precisionUnit"
                    :disabled="!analysisQuery.downsample"
                    class="w-20 rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
                  >
                    <option
                      v-for="unit in TIME_PRECISION_UNIT_OPTIONS"
                      :key="unit.value"
                      :value="unit.value"
                    >
                      {{ unit.label }}
                    </option>
                  </select>
                </div>
                <div class="text-[10px] leading-5 text-gray-400">
                  当前步长：{{ analysisPrecisionText }}。支持毫秒、秒、分、小时、天，多任务对比会按同一组降采样参数返回结果。
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-700">
              结构化输入任务仅支持单独查看。图表分析会读取完整结果表，表格分页仅影响下方预览，不再影响图表。
            </div>
            <div>
              <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">图表类型</label>
              <select
                v-model="structuredChartType"
                class="w-full rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700"
              >
                <option value="line">曲线图</option>
                <option value="histogram">直方图</option>
                <option value="scatter">散点图</option>
              </select>
            </div>
            <div>
              <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">X 轴列</label>
              <select
                v-model="structuredXAxis"
                :disabled="!structuredXAxisOptions.length"
                class="w-full rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
              >
                <option value="" disabled>请选择列</option>
                <option
                  v-for="column in structuredXAxisOptions"
                  :key="`x-${column}`"
                  :value="column"
                >
                  {{ column }}
                </option>
              </select>
            </div>
            <div>
              <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">Y 轴列</label>
              <select
                v-model="structuredYAxis"
                :disabled="structuredChartType === 'histogram' || !structuredYAxisOptions.length"
                class="w-full rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
              >
                <option value="" disabled>
                  {{ structuredChartType === 'histogram' ? '直方图自动统计频次' : '请选择列' }}
                </option>
                <option
                  v-for="column in structuredYAxisOptions"
                  :key="`y-${column}`"
                  :value="column"
                >
                  {{ column }}
                </option>
              </select>
            </div>
            <div class="rounded-lg border border-slate-200 bg-white p-3 space-y-3">
              <div class="flex items-center justify-between gap-2">
                <label class="block text-[10px] font-bold text-gray-500 uppercase">坐标范围</label>
                <button
                  type="button"
                  @click="resetStructuredAxisControls"
                  class="rounded border border-slate-200 px-2 py-1 text-[10px] text-slate-500 transition hover:bg-slate-50"
                >
                  恢复自动
                </button>
              </div>
              <div class="grid grid-cols-2 gap-2">
                <label class="text-[11px] text-slate-600">
                  <span class="mb-1 block">X 最小</span>
                  <input
                    v-model="structuredAxisRange.xMin"
                    type="number"
                    step="any"
                    :disabled="!structuredAxisMeta.x.supportsManual"
                    :placeholder="structuredAxisMeta.x.minPlaceholder || '自动'"
                    class="w-full rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 disabled:cursor-not-allowed disabled:bg-slate-100"
                  >
                </label>
                <label class="text-[11px] text-slate-600">
                  <span class="mb-1 block">X 最大</span>
                  <input
                    v-model="structuredAxisRange.xMax"
                    type="number"
                    step="any"
                    :disabled="!structuredAxisMeta.x.supportsManual"
                    :placeholder="structuredAxisMeta.x.maxPlaceholder || '自动'"
                    class="w-full rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 disabled:cursor-not-allowed disabled:bg-slate-100"
                  >
                </label>
                <label class="text-[11px] text-slate-600">
                  <span class="mb-1 block">Y 最小</span>
                  <input
                    v-model="structuredAxisRange.yMin"
                    type="number"
                    step="any"
                    :disabled="!structuredAxisMeta.y.supportsManual"
                    :placeholder="structuredAxisMeta.y.minPlaceholder || '自动'"
                    class="w-full rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 disabled:cursor-not-allowed disabled:bg-slate-100"
                  >
                </label>
                <label class="text-[11px] text-slate-600">
                  <span class="mb-1 block">Y 最大</span>
                  <input
                    v-model="structuredAxisRange.yMax"
                    type="number"
                    step="any"
                    :disabled="!structuredAxisMeta.y.supportsManual"
                    :placeholder="structuredAxisMeta.y.maxPlaceholder || '自动'"
                    class="w-full rounded border border-slate-200 bg-white px-2 py-1.5 text-xs text-slate-700 disabled:cursor-not-allowed disabled:bg-slate-100"
                  >
                </label>
              </div>
              <div class="space-y-1 text-[10px] leading-5 text-slate-500">
                <div>{{ structuredAxisMeta.x.helperText }}</div>
                <div>{{ structuredAxisMeta.y.helperText }}</div>
              </div>
            </div>
            <div class="rounded-lg border border-slate-200 bg-white p-3 text-[11px] leading-5 text-slate-500">
              {{ structuredChartDescription }}
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
