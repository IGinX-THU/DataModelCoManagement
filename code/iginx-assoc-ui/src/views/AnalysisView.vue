<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick, reactive, computed } from 'vue'
import * as echarts from 'echarts'
import { useAssociationStore } from '../stores/association'
import TaskMonitorView from './TaskMonitorView.vue'
import { fetchTaskSeries, compareTaskSeries, exportTaskPackage, exportTaskReport } from '../api/analysis'
import { BASE_URL } from '../api/request'

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
const loadingSeries = ref(false)
const chartRef = ref(null)
const useRelativeTime = ref(false)
const packageConfig = reactive({ includeModel: true, includeData: true, includeResult: true })
const reportConfig = reactive({ includeStats: true, includeCharts: true })
const analysisQuery = reactive({
  downsample: true,
  aggregator: 'AVG',
  precisionMs: ''
})
const structuredPagination = reactive({
  pageNum: 1,
  pageSize: DEFAULT_STRUCTURED_PAGE_SIZE,
  total: 0
})
const structuredJumpPage = ref('1')

let chartInstance = null
let resizeHandler = null

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
const structuredResultRows = computed(() => structuredResult.value?.page?.records || [])
const structuredTotalPages = computed(() => {
  const total = Number(structuredPagination.total) || 0
  const pageSize = Number(structuredPagination.pageSize) || DEFAULT_STRUCTURED_PAGE_SIZE
  return Math.max(1, Math.ceil(total / pageSize))
})
const resolvedAnalysisPrecisionMs = computed(() => {
  if (!analysisQuery.downsample) {
    return null
  }
  const manualPrecision = Number(analysisQuery.precisionMs)
  if (Number.isFinite(manualPrecision) && manualPrecision > 0) {
    return Math.floor(manualPrecision)
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
  return `${resolvedAnalysisPrecisionMs.value || 1}ms`
})

const resetAnalysisState = () => {
  analysisMode.value = 'TIME_SERIES'
  seriesData.value = []
  structuredResult.value = createEmptyStructuredResult()
  resetStructuredPagination()
}

const disposeChart = () => {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
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
  const task = selectedTasks.value[0]
  try {
    const downloadPath = await exportTaskReport(task.id, {
      includeStats: reportConfig.includeStats,
      includeCharts: isStructuredTaskSelected.value ? false : reportConfig.includeCharts
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
  () => analysisQuery.precisionMs
], () => {
  loadAnalysis()
})

watch(isStructuredTaskSelected, (value) => {
  if (value) {
    reportConfig.includeCharts = false
  }
})

onMounted(() => {
  resizeHandler = () => {
    if (chartInstance) {
      chartInstance.resize()
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
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
          <h3 class="font-bold text-gray-800 mb-4">生成实验报告</h3>
          <div class="space-y-3">
            <div v-if="isStructuredTaskSelected" class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-700">
              结构化输入任务的报告会导出任务概览、统计表和结果表预览，不生成时序折线图。
            </div>
            <label class="flex items-center text-sm text-gray-700">
              <input type="checkbox" v-model="reportConfig.includeStats" class="mr-2"> 包含统计表
            </label>
            <label class="flex items-center text-sm text-gray-700" :class="isStructuredTaskSelected ? 'opacity-60' : ''">
              <input type="checkbox" v-model="reportConfig.includeCharts" class="mr-2" :disabled="isStructuredTaskSelected"> 包含图表快照
            </label>
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
              <i :class="analysisMode === 'STRUCTURED' ? 'ri-table-line mr-2' : 'ri-line-chart-line mr-2'"></i>
              {{ analysisMode === 'STRUCTURED' ? '结构化结果表' : '结果曲线' }}
            </span>
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
            <div v-if="structuredResultRows.length" class="overflow-auto rounded-lg border border-slate-200 bg-white shadow-sm">
              <table class="min-w-full text-sm text-left">
                <thead class="bg-slate-100 text-slate-600">
                  <tr>
                    <th v-for="column in structuredResult.columns" :key="column" class="px-4 py-3 font-semibold whitespace-nowrap border-b border-slate-200">
                      {{ column }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in structuredResultRows" :key="index" class="odd:bg-white even:bg-slate-50">
                    <td v-for="column in structuredResult.columns" :key="`${index}-${column}`" class="px-4 py-3 text-slate-700 whitespace-nowrap border-b border-slate-100">
                      {{ row[column] ?? '-' }}
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

      <div class="w-48 border-l border-gray-200 bg-gray-50 flex flex-col">
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
                  <input type="checkbox" v-model="analysisQuery.downsample" class="mr-2"> 启用服务端降采样
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
                <input
                  v-model="analysisQuery.precisionMs"
                  type="number"
                  min="1"
                  :disabled="!analysisQuery.downsample"
                  class="w-full rounded border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700 disabled:cursor-not-allowed disabled:bg-gray-100"
                  placeholder="步长(ms，留空自动计算)"
                >
                <div class="text-[10px] leading-5 text-gray-400">
                  当前步长：{{ analysisPrecisionText }}。多任务对比会按同一组降采样参数返回结果。
                </div>
              </div>
            </div>
          </template>
          <div v-else class="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs leading-5 text-amber-700">
            结构化输入任务仅支持单独查看，结果按 KEY 从 0 开始显示为结果表，不参与多任务折线图对比。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
