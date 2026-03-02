<script setup>
import { ref, onMounted, watch, nextTick, reactive, computed } from 'vue'
import * as echarts from 'echarts'
import { useAssociationStore } from '../stores/association'
import TaskMonitorView from './TaskMonitorView.vue'
import { fetchTaskSeries, compareTaskSeries, exportTaskPackage, exportTaskReport } from '../api/analysis'
import { BASE_URL } from '../api/request'

const associationStore = useAssociationStore()
const currentView = ref('chart') // 'chart' or 'monitor'

const taskList = computed(() => {
    return (associationStore.tasks || []).map(task => ({
        id: task.id,
        name: task.id,
        time: task.createTime ? task.createTime.replace('T', ' ') : '',
        data: []
    }))
})

const selectedTasks = ref([])
const seriesData = ref([])
const loadingSeries = ref(false)
const chartRef = ref(null)
let chartInstance = null
const useRelativeTime = ref(false)
const packageConfig = reactive({ includeModel: true, includeData: true, includeResult: true })
const reportConfig = reactive({ includeStats: true, includeCharts: true })

const toggleTaskSelection = (task) => {
    const index = selectedTasks.value.findIndex(t => t.id === task.id)
    if (index > -1) {
        selectedTasks.value.splice(index, 1)
    } else {
        selectedTasks.value.push(task)
    }
}

const loadSeries = async () => {
    if (!selectedTasks.value.length) {
        seriesData.value = []
        nextTick(initChart)
        return
    }
    loadingSeries.value = true
    try {
        const ids = selectedTasks.value.map(item => item.id)
        if (ids.length === 1) {
            seriesData.value = await fetchTaskSeries(ids[0], useRelativeTime.value)
        } else {
            seriesData.value = await compareTaskSeries(ids, useRelativeTime.value)
        }
    } catch (err) {
        console.error('加载任务曲线失败', err)
        alert(err.message || '加载任务曲线失败')
        seriesData.value = []
    } finally {
        loadingSeries.value = false
        nextTick(initChart)
    }
}

const initChart = () => {
    if (!chartRef.value) return
    if (chartInstance) chartInstance.dispose()
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
            text: selectedTasks.value.length ? (relativeMode ? '相对时间对比' : '绝对时间对比') : '请选择任务进行分析',
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
            includeCharts: reportConfig.includeCharts
        })
        const url = downloadPath.startsWith('http') ? downloadPath : `${BASE_URL}${downloadPath}`
        window.open(url, '_blank')
        associationStore.showExportReportModal = false
    } catch (err) {
        alert(err.message || '报告生成失败')
    }
}

watch([selectedTasks, useRelativeTime], () => {
    loadSeries()
}, { deep: true })

onMounted(() => {
    window.addEventListener('resize', () => chartInstance && chartInstance.resize())
    associationStore.loadTasks()
    loadSeries()
})
</script>

<template>
  <div class="h-full flex flex-col bg-white rounded-lg overflow-hidden border border-gray-200 relative">
    
    <!-- Analysis Tabs -->
    <div class="h-10 border-b border-gray-200 flex items-center px-4 bg-gray-50 space-x-4">
         <div @click="currentView = 'monitor'" 
              :class="currentView === 'monitor' ? 'text-blue-600 border-b-2 border-blue-600 font-bold' : 'text-gray-600 hover:text-gray-800'"
              class="cursor-pointer h-full flex items-center px-2 text-sm transition-colors">
             <i class="ri-task-line mr-2"></i> Task Monitor
         </div>
         <div @click="currentView = 'chart'" 
              :class="currentView === 'chart' ? 'text-blue-600 border-b-2 border-blue-600 font-bold' : 'text-gray-600 hover:text-gray-800'"
              class="cursor-pointer h-full flex items-center px-2 text-sm transition-colors">
             <i class="ri-line-chart-line mr-2"></i> Result Analysis
         </div>
    </div>

    <!-- Monitor View -->
    <div v-if="currentView === 'monitor'" class="flex-1 overflow-hidden">
        <TaskMonitorView />
    </div>

    <!-- Chart View -->
    <div v-else class="flex-1 flex overflow-hidden">
        <!-- Export Resource Modal -->
        <div v-if="associationStore.showExportResourceModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4">Export Resource Package</h3>
            <div class="space-y-3">
                <label class="flex items-center text-sm text-gray-700">
                    <input type="checkbox" v-model="packageConfig.includeModel" class="mr-2"> Include Algorithm Model
                </label>
                <label class="flex items-center text-sm text-gray-700">
                    <input type="checkbox" v-model="packageConfig.includeData" class="mr-2"> Include Input Data Source
                </label>
                <label class="flex items-center text-sm text-gray-700">
                    <input type="checkbox" v-model="packageConfig.includeResult" class="mr-2"> Include Output Results
                </label>
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="associationStore.showExportResourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="exportPackage" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Export ZIP</button>
            </div>
        </div>
    </div>

    <!-- Export Report Modal -->
    <div v-if="associationStore.showExportReportModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4">Generate Experiment Report</h3>
            <div class="space-y-3">
                <label class="flex items-center text-sm text-gray-700">
                    <input type="checkbox" v-model="reportConfig.includeStats" class="mr-2"> Include Statistics Table
                </label>
                <label class="flex items-center text-sm text-gray-700">
                    <input type="checkbox" v-model="reportConfig.includeCharts" class="mr-2"> Include Chart Snapshots
                </label>
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="associationStore.showExportReportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="exportReport" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700">Generate PDF</button>
            </div>
        </div>
    </div>

    <!-- Task List -->
    <div class="w-56 border-r border-gray-200 bg-gray-50 flex flex-col">
        <div class="p-3 border-b border-gray-200 font-bold text-xs text-gray-600 uppercase">
            Analysis Tasks
        </div>
        <div class="flex-1 overflow-y-auto">
             <div v-for="task in taskList" :key="task.id"
                  @click="toggleTaskSelection(task)"
                  :class="selectedTasks.find(t => t.id === task.id) ? 'bg-blue-50 border-l-4 border-blue-500' : 'border-l-4 border-transparent hover:bg-gray-100'"
                  class="p-3 border-b border-gray-100 cursor-pointer transition-all">
                 <div class="flex items-center mb-1">
                     <input type="checkbox" :checked="!!selectedTasks.find(t => t.id === task.id)" class="mr-2 pointer-events-none text-blue-600">
                     <div class="font-bold text-xs text-gray-700 truncate">{{ task.name }}</div>
                 </div>
                 <div class="flex justify-between items-center text-[10px] text-gray-400 pl-5">
                     <span>{{ task.time.split(' ')[1] }}</span>
                 </div>
             </div>
        </div>
    </div>

    <!-- Chart -->
    <div class="flex-1 flex flex-col relative bg-white">
        <div class="h-10 border-b border-gray-200 flex items-center justify-between px-4 bg-gray-50/50">
            <span class="text-xs font-bold text-gray-600 flex items-center">
                <i class="ri-line-chart-line mr-2"></i> Performance Metrics
            </span>
            <div class="flex space-x-2">
                <button class="text-gray-400 hover:text-gray-800 p-1"><i class="ri-camera-line"></i></button>
            </div>
        </div>
        <div class="flex-1 relative">
            <div ref="chartRef" class="w-full h-full"></div>
            <div v-if="selectedTasks.length === 0" class="absolute inset-0 flex items-center justify-center text-gray-400 pointer-events-none">
                Select tasks from the left panel
            </div>
            <div v-if="loadingSeries" class="absolute inset-0 flex items-center justify-center text-gray-500 bg-white/70">
                正在加载任务曲线...
            </div>
        </div>
    </div>

    <!-- Settings -->
    <div class="w-48 border-l border-gray-200 bg-gray-50 flex flex-col">
        <div class="p-3 border-b border-gray-200 font-bold text-xs text-gray-600 uppercase">
            Settings
        </div>
        <div class="p-4 space-y-4">
            <div>
                <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">Axes</label>
                <div class="space-y-1">
                    <label class="flex items-center text-xs text-gray-600">
                        <input type="checkbox" checked class="mr-2"> X-Axis Grid
                    </label>
                    <label class="flex items-center text-xs text-gray-600">
                        <input type="checkbox" checked class="mr-2"> Y-Axis Grid
                    </label>
                </div>
            </div>
            <div>
                <label class="block text-[10px] font-bold text-gray-500 uppercase mb-2">Comparison Mode</label>
                <div class="space-y-1">
                    <label class="flex items-center text-xs text-gray-600">
                        <input type="checkbox" v-model="useRelativeTime" class="mr-2"> Time Alignment (Relative)
                    </label>
                </div>
            </div>
        </div>
    </div>
    </div>
  </div>
</template>
