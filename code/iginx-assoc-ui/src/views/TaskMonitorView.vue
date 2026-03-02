<script setup>
import { useAssociationStore } from '../stores/association'
import { computed, ref, onMounted } from 'vue'

const associationStore = useAssociationStore()
const filterStatus = ref('All')
const showLogModal = ref(false)
const logTask = ref(null)

const filteredTasks = computed(() => {
    if (filterStatus.value === 'All') return associationStore.tasks
    return associationStore.tasks.filter(t => t.status === filterStatus.value.toUpperCase())
})

const getStatusColor = (status) => {
    switch (status) {
        case 'RUNNING': return 'bg-blue-100 text-blue-700'
        case 'SUCCESS': return 'bg-green-100 text-green-700'
        case 'FAILED': return 'bg-red-100 text-red-700'
        case 'PENDING': return 'bg-yellow-100 text-yellow-700'
        case 'ABORTED': return 'bg-gray-100 text-gray-700'
        default: return 'bg-gray-100 text-gray-600'
    }
}

const formatTime = (value) => {
    if (!value) return '-'
    return value.replace('T', ' ')
}

const calcProgress = (task) => {
    if (!task) return 0
    if (task.status === 'SUCCESS') return 100
    if (task.status === 'FAILED' || task.status === 'ABORTED') return 100
    if (task.status === 'RUNNING') return 60
    return 0
}

const handleStop = async (task) => {
    if (confirm(`Stop task ${task.id}?`)) {
        await associationStore.stopTask(task.id)
    }
}

const handleRerun = async (task) => {
    alert(`Rerunning task ${task.id}...`)
    await associationStore.createTask(task.ruleId, {
        startTime: task.rangeStart,
        endTime: task.rangeEnd
    })
    await associationStore.loadTasks()
}

const openLog = (task) => {
    logTask.value = task
    showLogModal.value = true
}

const closeLog = () => {
    showLogModal.value = false
    logTask.value = null
}

onMounted(() => {
    associationStore.loadTasks()
    associationStore.loadRules()
})
</script>

<template>
    <div class="flex flex-col h-full bg-gray-50 p-6">
        <div v-if="showLogModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div class="bg-white rounded-lg shadow-xl w-[620px] max-h-[80vh] flex flex-col">
                <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                    <h3 class="font-bold text-gray-800">任务详情</h3>
                    <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="closeLog"></i>
                </div>
                <div class="p-6 space-y-4 overflow-y-auto text-sm text-gray-600">
                    <div class="grid grid-cols-2 gap-4 text-xs">
                        <div>
                            <div class="text-gray-400">任务 ID</div>
                            <div class="font-mono text-gray-800">{{ logTask?.id || '-' }}</div>
                        </div>
                        <div>
                            <div class="text-gray-400">状态</div>
                            <div class="font-mono text-gray-800">{{ logTask?.status || '-' }}</div>
                        </div>
                        <div>
                            <div class="text-gray-400">开始时间</div>
                            <div class="font-mono text-gray-800">{{ formatTime(logTask?.startTime) }}</div>
                        </div>
                        <div>
                            <div class="text-gray-400">结束时间</div>
                            <div class="font-mono text-gray-800">{{ formatTime(logTask?.endTime) }}</div>
                        </div>
                        <div class="col-span-2">
                            <div class="text-gray-400">时间范围</div>
                            <div class="font-mono text-gray-800">{{ formatTime(logTask?.rangeStart) }} ~ {{ formatTime(logTask?.rangeEnd) }}</div>
                        </div>
                        <div class="col-span-2">
                            <div class="text-gray-400">结果路径</div>
                            <div class="font-mono text-gray-800 break-all">{{ logTask?.resultLink || '-' }}</div>
                        </div>
                    </div>
                    <div>
                        <div class="text-gray-400 mb-2">执行日志</div>
                        <pre class="bg-gray-50 border border-gray-200 rounded p-3 text-xs text-gray-700 whitespace-pre-wrap">{{ logTask?.execLog || '暂无日志' }}</pre>
                    </div>
                </div>
                <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                    <button @click="closeLog" class="px-4 py-2 bg-gray-100 text-gray-700 rounded text-sm hover:bg-gray-200">关闭</button>
                </div>
            </div>
        </div>
        <div class="flex justify-between items-center mb-6">
            <h2 class="text-xl font-bold text-gray-800 flex items-center">
                <i class="ri-task-line mr-2 text-blue-600"></i> Task Monitor
            </h2>
            <div class="flex space-x-2 bg-white p-1 rounded border border-gray-200 shadow-sm">
                <button v-for="status in ['All', 'Running', 'Success', 'Failed']" :key="status"
                        @click="filterStatus = status"
                        :class="filterStatus === status ? 'bg-blue-50 text-blue-600 font-bold shadow-sm' : 'text-gray-600 hover:bg-gray-50'"
                        class="px-3 py-1.5 rounded text-xs transition-all">
                    {{ status }}
                </button>
            </div>
        </div>

        <div class="bg-white rounded-lg shadow-sm border border-gray-200 flex-1 overflow-hidden flex flex-col">
            <div class="overflow-x-auto">
                <table class="w-full text-sm text-left">
                    <thead class="bg-gray-50 text-gray-500 font-medium border-b border-gray-200">
                        <tr>
                            <th class="px-6 py-3">Task ID</th>
                            <th class="px-6 py-3">Rule Name</th>
                            <th class="px-6 py-3">Time Range</th>
                            <th class="px-6 py-3">Created At</th>
                            <th class="px-6 py-3">Status</th>
                            <th class="px-6 py-3">Progress</th>
                            <th class="px-6 py-3 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-100">
                        <tr v-if="filteredTasks.length === 0">
                            <td colspan="7" class="px-6 py-12 text-center text-gray-400">
                                <i class="ri-inbox-line text-4xl mb-2"></i>
                                <p>No tasks found.</p>
                            </td>
                        </tr>
                        <tr v-for="task in filteredTasks" :key="task.id" class="hover:bg-gray-50 transition-colors group">
                            <td class="px-6 py-3 font-mono text-gray-600 text-xs">{{ task.id }}</td>
                            <td class="px-6 py-3 font-medium text-gray-800">{{ associationStore.rules.find(r => r.id === task.ruleId)?.name || 'Unknown Rule' }}</td>
                            <td class="px-6 py-3 text-gray-500 text-xs">
                                {{ formatTime(task.rangeStart) }} <i class="ri-arrow-right-line mx-1 text-gray-300"></i> {{ formatTime(task.rangeEnd) }}
                            </td>
                            <td class="px-6 py-3 text-gray-500 text-xs">{{ formatTime(task.createTime) }}</td>
                            <td class="px-6 py-3">
                                <span :class="getStatusColor(task.status)" class="px-2 py-0.5 rounded-full text-[10px] font-bold border border-current opacity-80">
                                    {{ task.status }}
                                </span>
                            </td>
                            <td class="px-6 py-3">
                                <div class="w-24 bg-gray-100 rounded-full h-1.5 overflow-hidden">
                                    <div class="h-full bg-blue-500 transition-all duration-500" :style="{ width: calcProgress(task) + '%' }"></div>
                                </div>
                                <div class="text-[10px] text-gray-400 mt-0.5 text-right w-24">{{ calcProgress(task) }}%</div>
                            </td>
                            <td class="px-6 py-3 text-right space-x-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button v-if="task.status === 'RUNNING' || task.status === 'PENDING'" 
                                        @click="handleStop(task)"
                                        class="p-1 text-red-600 hover:bg-red-50 rounded" title="Stop Task">
                                    <i class="ri-stop-circle-line text-lg"></i>
                                </button>
                                <button v-else
                                        @click="handleRerun(task)"
                                        class="p-1 text-blue-600 hover:bg-blue-50 rounded" title="Rerun Task">
                                    <i class="ri-refresh-line text-lg"></i>
                                </button>
                                <button class="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded" title="查看日志" @click="openLog(task)">
                                    <i class="ri-file-list-line text-lg"></i>
                                </button>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</template>
