<script setup>
import { useAssociationStore } from '../stores/association'
import { computed, ref } from 'vue'

const associationStore = useAssociationStore()
const filterStatus = ref('All')

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

const handleStop = (task) => {
    if (confirm(`Stop task ${task.id}?`)) {
        associationStore.stopTask(task.id)
    }
}

const handleRerun = (task) => {
    // Rerun logic would typically create a new task with same config
    alert(`Rerunning task ${task.id}...`)
    // Mock rerun
    associationStore.createTask(task.ruleId, {
        startTime: task.startTime,
        endTime: task.endTime
    })
}
</script>

<template>
    <div class="flex flex-col h-full bg-gray-50 p-6">
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
                                {{ task.startTime }} <i class="ri-arrow-right-line mx-1 text-gray-300"></i> {{ task.endTime }}
                            </td>
                            <td class="px-6 py-3 text-gray-500 text-xs">{{ task.createdAt }}</td>
                            <td class="px-6 py-3">
                                <span :class="getStatusColor(task.status)" class="px-2 py-0.5 rounded-full text-[10px] font-bold border border-current opacity-80">
                                    {{ task.status }}
                                </span>
                            </td>
                            <td class="px-6 py-3">
                                <div class="w-24 bg-gray-100 rounded-full h-1.5 overflow-hidden">
                                    <div class="h-full bg-blue-500 transition-all duration-500" :style="{ width: task.progress + '%' }"></div>
                                </div>
                                <div class="text-[10px] text-gray-400 mt-0.5 text-right w-24">{{ task.progress }}%</div>
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
                                <button class="p-1 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded" title="View Logs">
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