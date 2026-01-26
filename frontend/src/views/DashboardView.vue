<script setup>
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'

const chartRef = ref(null)

onMounted(() => {
  const myChart = echarts.init(chartRef.value)
  const option = {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    legend: { textStyle: { color: '#666' }, right: 10 },
    xAxis: [
      { 
        type: 'category', 
        data: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'], 
        axisLine: { lineStyle: { color: '#ccc' } },
        axisLabel: { color: '#666' }
      }
    ],
    yAxis: [
      { type: 'value', splitLine: { lineStyle: { color: '#eee' } }, axisLabel: { color: '#666' } },
      { type: 'value', splitLine: { show: false }, axisLabel: { color: '#666' } }
    ],
    series: [
      { name: 'Tasks', type: 'bar', barWidth: '30%', data: [12, 18, 24, 32, 28, 15, 27], itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] } },
      { name: 'Avg Time', type: 'line', yAxisIndex: 1, data: [12, 14, 11, 15, 13, 9, 14], smooth: true, itemStyle: { color: '#10b981' } }
    ]
  };
  myChart.setOption(option)
  window.addEventListener('resize', () => myChart.resize())
})

const kpiCards = [
  { label: 'Total Models', value: '42', unit: '', change: '+2', color: 'text-blue-600', icon: 'ri-box-3-line', bg: 'bg-blue-50', iconColor: 'text-blue-600' },
  { label: 'Time-Series Points', value: '1.2', unit: 'B', change: '+50w/day', color: 'text-green-600', icon: 'ri-line-chart-line', bg: 'bg-green-50', iconColor: 'text-green-600' },
  { label: 'Weekly Tasks', value: '156', unit: '', change: '98.5% Success', color: 'text-purple-600', icon: 'ri-rocket-line', bg: 'bg-purple-50', iconColor: 'text-purple-600' },
  { label: 'System Health', value: 'OK', unit: '', change: 'CPU 32%', color: 'text-green-600', icon: 'ri-heart-pulse-line', bg: 'bg-green-50', iconColor: 'text-green-600' },
]

const recentTasks = [
  { id: 'JOB_001', name: 'CNN Bearing Fault', type: 'Python', status: 'Success', time: '10:00:00', duration: '12s' },
  { id: 'JOB_002', name: 'PID Control Sim', type: 'MATLAB', status: 'Running', time: '10:05:30', duration: '-' },
  { id: 'JOB_003', name: 'Vehicle Dynamics', type: 'Native', status: 'Failed', time: '09:20:00', duration: '2s' },
  { id: 'JOB_004', name: 'Data Cleaning', type: 'Python', status: 'Success', time: '08:30:00', duration: '45s' },
]
</script>

<template>
  <div class="p-6 space-y-6 h-full overflow-y-auto custom-scrollbar bg-white">
    <!-- KPI Grid -->
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

    <!-- Chart Section -->
    <div class="bg-white p-4 rounded border border-gray-200 shadow-sm">
      <h3 class="text-sm font-bold text-gray-700 mb-4 flex items-center select-none border-b border-gray-100 pb-2">
        <i class="ri-bar-chart-fill mr-2 text-blue-500"></i> 任务调度趋势
      </h3>
      <div ref="chartRef" class="w-full h-64"></div>
    </div>

    <!-- Recent Tasks Table -->
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
              <td class="px-4 py-3 font-medium text-gray-800">{{ task.name }}</td>
              <td class="px-4 py-3">
                <span class="bg-gray-100 px-2 py-0.5 rounded text-[10px] text-gray-500 border border-gray-200">{{ task.type }}</span>
              </td>
              <td class="px-4 py-3">
                <span v-if="task.status === 'Success'" class="text-green-600 flex items-center"><i class="ri-checkbox-circle-fill mr-1"></i> 成功</span>
                <span v-else-if="task.status === 'Running'" class="text-blue-600 flex items-center"><i class="ri-loader-4-line mr-1 animate-spin"></i> 运行中</span>
                <span v-else class="text-red-500 flex items-center"><i class="ri-close-circle-fill mr-1"></i> 失败</span>
              </td>
              <td class="px-4 py-3 text-gray-400">{{ task.time }}</td>
              <td class="px-4 py-3 text-gray-400">{{ task.duration }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
