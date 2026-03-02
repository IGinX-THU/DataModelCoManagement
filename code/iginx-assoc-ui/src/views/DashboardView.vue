<script setup>
import { onMounted, ref, computed } from 'vue'
import * as echarts from 'echarts'
import { fetchDashboardSummary } from '../api/dashboard'

const chartRef = ref(null)
const summary = ref({ taskTrend: [], recentTasks: [] })
const loading = ref(false)
let chartInstance = null

const kpiCards = computed(() => {
  const data = summary.value || {}
  const successRate = data.taskCount
    ? `${((data.successTaskCount || 0) / data.taskCount * 100).toFixed(1)}%`
    : '-'
  return [
    { label: '模型档案', value: data.modelCount ?? 0, unit: '', change: `规则 ${data.ruleCount ?? 0}`, color: 'text-blue-600', icon: 'ri-box-3-line', bg: 'bg-blue-50', iconColor: 'text-blue-600' },
    { label: '数据源数量', value: data.dataSourceCount ?? 0, unit: '', change: `已接入 ${data.dataSourceCount ?? 0}`, color: 'text-green-600', icon: 'ri-database-2-line', bg: 'bg-green-50', iconColor: 'text-green-600' },
    { label: '任务总数', value: data.taskCount ?? 0, unit: '', change: `成功率 ${successRate}`, color: 'text-purple-600', icon: 'ri-rocket-line', bg: 'bg-purple-50', iconColor: 'text-purple-600' },
    { label: '运行中任务', value: data.runningTaskCount ?? 0, unit: '', change: `失败 ${data.failedTaskCount ?? 0}`, color: 'text-orange-600', icon: 'ri-timer-flash-line', bg: 'bg-orange-50', iconColor: 'text-orange-600' },
  ]
})

const recentTasks = computed(() => summary.value?.recentTasks || [])

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

const formatDuration = (value) => {
  if (value === null || value === undefined) return '-'
  const seconds = Number(value)
  if (Number.isNaN(seconds)) return '-'
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `${minutes}m ${remain}s`
}

const statusMeta = (status) => {
  switch (status) {
    case 'SUCCESS':
      return { text: '成功', className: 'text-green-600', icon: 'ri-checkbox-circle-fill' }
    case 'RUNNING':
      return { text: '运行中', className: 'text-blue-600', icon: 'ri-loader-4-line animate-spin' }
    case 'FAILED':
      return { text: '失败', className: 'text-red-500', icon: 'ri-close-circle-fill' }
    case 'ABORTED':
      return { text: '已终止', className: 'text-gray-500', icon: 'ri-stop-circle-fill' }
    default:
      return { text: '等待中', className: 'text-gray-500', icon: 'ri-time-line' }
  }
}

const buildChartOption = () => {
  const trend = summary.value?.taskTrend || []
  const labels = trend.map(item => item.date ? item.date.slice(5) : '')
  const taskCounts = trend.map(item => item.taskCount ?? 0)
  const avgDurations = trend.map(item => item.avgDurationSec ?? 0)
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        if (!params || !params.length) return ''
        const title = params[0].axisValue || ''
        const lines = params.map(p => {
          if (p.seriesName === '平均耗时') {
            return `${p.marker}${p.seriesName}: ${formatDuration(p.value)}`
          }
          return `${p.marker}${p.seriesName}: ${p.value}`
        })
        return [title, ...lines].join('<br/>')
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    legend: { textStyle: { color: '#666' }, right: 10 },
    xAxis: [
      {
        type: 'category',
        data: labels,
        axisLine: { lineStyle: { color: '#ccc' } },
        axisLabel: { color: '#666' }
      }
    ],
    yAxis: [
      { type: 'value', splitLine: { lineStyle: { color: '#eee' } }, axisLabel: { color: '#666' } },
      { type: 'value', splitLine: { show: false }, axisLabel: { color: '#666' } }
    ],
    series: [
      { name: '任务数量', type: 'bar', barWidth: '30%', data: taskCounts, itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] } },
      { name: '平均耗时', type: 'line', yAxisIndex: 1, data: avgDurations, smooth: true, itemStyle: { color: '#10b981' } }
    ]
  }
}

const renderChart = () => {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  chartInstance.setOption(buildChartOption())
}

const loadSummary = async () => {
  loading.value = true
  try {
    summary.value = await fetchDashboardSummary()
  } catch (err) {
    console.error('加载系统总览失败', err)
  } finally {
    loading.value = false
    renderChart()
  }
}

onMounted(() => {
  renderChart()
  loadSummary()
  window.addEventListener('resize', () => chartInstance && chartInstance.resize())
})
</script>

<template>
  <div class="p-6 space-y-6 h-full overflow-y-auto custom-scrollbar bg-white">
    <!-- 指标概览 -->
    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <div v-for="card in kpiCards" :key="card.label" class="bg-white p-4 rounded border border-gray-200 shadow-sm hover:border-blue-200 transition-colors cursor-default group">
        <div class="flex justify-between items-start">
          <div>
            <div class="text-gray-500 text-xs mb-1 font-medium uppercase tracking-wide">{{ card.label }}</div>
            <div class="text-2xl font-bold text-gray-800 tracking-tight">{{ card.value }} <span class="text-xs font-normal text-gray-400">{{ card.unit }}</span></div>
            <div class="text-xs mt-1 font-mono font-medium" :class="card.color">{{ card.change }}</div>
          </div>
          <div class="p-2 rounded" :class="card.bg">
            <i :class="[card.icon, card.iconColor]"></i>
          </div>
        </div>
      </div>
    </div>

    <!-- 任务趋势 -->
    <div class="bg-white p-4 rounded border border-gray-200 shadow-sm">
      <h3 class="text-sm font-bold text-gray-700 mb-4 flex items-center select-none border-b border-gray-100 pb-2">
        <i class="ri-bar-chart-fill mr-2 text-blue-500"></i> 任务调度趋势
      </h3>
      <div ref="chartRef" class="w-full h-64"></div>
      <div v-if="loading" class="text-xs text-gray-400 mt-2">正在加载数据...</div>
    </div>

    <!-- 最新执行记录 -->
    <div class="bg-white rounded border border-gray-200 shadow-sm overflow-hidden">
      <div class="px-4 py-3 border-b border-gray-200 font-bold text-gray-700 text-sm flex justify-between items-center select-none bg-gray-50">
        <span>最新执行记录</span>
        <button class="text-blue-600 text-xs hover:underline">查看全部</button>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-xs text-left whitespace-nowrap">
          <thead class="bg-gray-50 text-gray-500">
            <tr>
              <th class="px-4 py-2 font-medium">任务 ID</th>
              <th class="px-4 py-2 font-medium">关联规则名称</th>
              <th class="px-4 py-2 font-medium">模型类型</th>
              <th class="px-4 py-2 font-medium">状态</th>
              <th class="px-4 py-2 font-medium">开始时间</th>
              <th class="px-4 py-2 font-medium">耗时</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 text-gray-600">
            <tr v-for="task in recentTasks" :key="task.id" class="hover:bg-gray-50 transition-colors group">
              <td class="px-4 py-3 font-mono text-gray-500 group-hover:text-blue-600">{{ task.id }}</td>
              <td class="px-4 py-3 font-medium text-gray-800">{{ task.ruleName }}</td>
              <td class="px-4 py-3">
                <span class="bg-gray-100 px-2 py-0.5 rounded text-[10px] text-gray-500 border border-gray-200">{{ task.modelType }}</span>
              </td>
              <td class="px-4 py-3">
                <span class="flex items-center" :class="statusMeta(task.status).className">
                  <i :class="[statusMeta(task.status).icon, 'mr-1']"></i> {{ statusMeta(task.status).text }}
                </span>
              </td>
              <td class="px-4 py-3 text-gray-400">{{ formatTime(task.startTime || task.createTime) }}</td>
              <td class="px-4 py-3 text-gray-400">{{ formatDuration(task.durationSec) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
