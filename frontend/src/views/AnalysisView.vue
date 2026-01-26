<script setup>
import { ref, onMounted, watch, nextTick, reactive, computed } from 'vue'
import * as echarts from 'echarts'
import { useAssociationStore } from '../stores/association'
import TaskMonitorView from './TaskMonitorView.vue'

const associationStore = useAssociationStore()
const currentView = ref('chart') // 'chart' or 'monitor'

const taskList = ref([
    { id: 'job_001', name: 'PID_Test_Run_1', time: '2025-01-14 10:00', data: [10, 25, 40, 55, 60] },
    { id: 'job_002', name: 'PID_Test_Run_2 (Optimized)', time: '2025-01-14 11:00', data: [10, 30, 55, 60, 60] },
    { id: 'job_003', name: 'PID_Test_Run_3 (High Load)', time: '2025-01-15 09:00', data: [5, 15, 25, 35, 45] },
    { id: 'job_004', name: 'Failed_Run', time: '2025-01-13 14:00', data: [] }
])

const selectedTasks = ref([])
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

const initChart = () => {
    if (!chartRef.value) return
    if (chartInstance) chartInstance.dispose()
    chartInstance = echarts.init(chartRef.value)

    const series = selectedTasks.value.map((task, idx) => ({
        name: task.name,
        type: 'line',
        data: task.data,
        smooth: true,
    }))
    
    const option = {
        backgroundColor: 'transparent',
        title: { 
            text: selectedTasks.value.length ? (useRelativeTime.value ? 'Relative Time Comparison' : 'Absolute Time Comparison') : 'Select tasks to analyze',
            left: 'center',
            textStyle: { color: '#666' }
        },
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0, textStyle: { color: '#666' } },
        grid: { top: 60, right: 30, bottom: 60, left: 50 },
        xAxis: { 
            type: 'category', 
            data: useRelativeTime.value ? ['0s', '10s', '20s', '30s', '40s'] : ['10:00', '10:10', '10:20', '10:30', '10:40'],
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

const exportPackage = () => {
    alert(`Exporting ZIP Package...\nInclude Model: ${packageConfig.includeModel}\nInclude Data: ${packageConfig.includeData}\nInclude Result: ${packageConfig.includeResult}`)
    associationStore.showExportResourceModal = false
}

const exportReport = () => {
    alert(`Generating Experiment Report (PDF)...\nInclude Stats: ${reportConfig.includeStats}\nInclude Charts: ${reportConfig.includeCharts}`)
    associationStore.showExportReportModal = false
}

watch([selectedTasks, useRelativeTime], () => {
    nextTick(initChart)
}, { deep: true })

onMounted(() => {
    window.addEventListener('resize', () => chartInstance && chartInstance.resize())
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
