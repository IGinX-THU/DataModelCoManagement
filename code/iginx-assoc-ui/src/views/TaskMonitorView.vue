<script setup>
import { useAssociationStore } from '../stores/association'
import { useDataStore } from '../stores/data'
import { useRouter } from 'vue-router'
import { computed, ref, onMounted, watch } from 'vue'

const associationStore = useAssociationStore()
const dataStore = useDataStore()
const router = useRouter()
const TASK_PAGE_SIZE_OPTIONS = [10, 20, 50]
const filterStatus = ref('All')
const showLogModal = ref(false)
const logTask = ref(null)
const taskPageNum = ref(1)
const taskPageSize = ref(TASK_PAGE_SIZE_OPTIONS[0])
const taskJumpPage = ref('1')

const filteredTasks = computed(() => {
    if (filterStatus.value === 'All') return associationStore.tasks
    return associationStore.tasks.filter(t => t.status === filterStatus.value.toUpperCase())
})

const taskTotalPages = computed(() => {
    const total = filteredTasks.value.length
    return Math.max(1, Math.ceil(total / taskPageSize.value))
})

const paginatedTasks = computed(() => {
    const start = (taskPageNum.value - 1) * taskPageSize.value
    const end = start + taskPageSize.value
    return filteredTasks.value.slice(start, end)
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

const formatScheduleTime = (value, emptyText) => {
    if (!value) return emptyText
    return formatTime(value)
}

const resolveTaskDisplayName = (task) => {
    return String(task?.taskName || '').trim() || task?.id || '未命名任务'
}

const normalizePath = (value) => String(value || '').trim().replace(/\.+$/, '')

const appendPreviewCandidate = (candidates, path) => {
    const normalized = normalizePath(path)
    if (!normalized || candidates.includes(normalized)) {
        return
    }
    candidates.push(normalized)
}

const resolveParentPath = (path) => {
    const normalized = normalizePath(path)
    if (!normalized) return ''
    const segments = normalized.split('.').map(item => item.trim()).filter(Boolean)
    if (segments.length <= 1) return ''
    return segments.slice(0, -1).join('.')
}

const resolveCommonPath = (paths) => {
    const normalizedPaths = (paths || []).map(normalizePath).filter(Boolean)
    if (!normalizedPaths.length) return ''
    let prefix = normalizedPaths[0].split('.').map(item => item.trim()).filter(Boolean)
    for (let index = 1; index < normalizedPaths.length; index += 1) {
        const current = normalizedPaths[index].split('.').map(item => item.trim()).filter(Boolean)
        let matched = 0
        const maxLength = Math.min(prefix.length, current.length)
        while (matched < maxLength && prefix[matched] === current[matched]) {
            matched += 1
        }
        prefix = prefix.slice(0, matched)
        if (!prefix.length) {
            return ''
        }
    }
    return prefix.join('.')
}

const getResolvedOutputPaths = (task) => Object.values(task?.outputPaths || {})
    .map(normalizePath)
    .filter(Boolean)

/**
 * 根据任务模式推导最适合打开的数据预览路径。
 * 规则：
 * 1. 时序任务优先打开具体输出测点；
 * 2. 结构化任务优先打开结果表路径，而不是某一列；
 * 3. 若任务配置了多输出，则保留多个候选，按资源树实际存在节点匹配。
 */
const buildTaskPreviewCandidates = (task) => {
    const candidates = []
    const resultLink = normalizePath(task?.resultLink)
    const outputPaths = getResolvedOutputPaths(task)

    if (task?.analysisMode === 'STRUCTURED') {
        appendPreviewCandidate(candidates, resultLink)
        const parentPaths = outputPaths.map(resolveParentPath).filter(Boolean)
        appendPreviewCandidate(candidates, resolveCommonPath(parentPaths))
        parentPaths.forEach(path => appendPreviewCandidate(candidates, path))
        outputPaths.forEach(path => appendPreviewCandidate(candidates, path))
        return candidates
    }

    outputPaths.forEach(path => appendPreviewCandidate(candidates, path))
    appendPreviewCandidate(candidates, resultLink)
    return candidates
}

const canOpenTaskResult = (task) => {
    return task?.status === 'SUCCESS' && buildTaskPreviewCandidates(task).length > 0
}

const syncTaskJumpPage = () => {
    taskJumpPage.value = String(taskPageNum.value)
}

const resetTaskPagination = () => {
    taskPageNum.value = 1
    syncTaskJumpPage()
}

const goToTaskPage = (targetPage) => {
    const numericPage = Number(targetPage)
    const resolvedPage = Number.isFinite(numericPage)
        ? Math.min(Math.max(1, Math.trunc(numericPage)), taskTotalPages.value)
        : taskPageNum.value
    taskPageNum.value = resolvedPage
    syncTaskJumpPage()
}

const jumpToTaskPage = () => {
    goToTaskPage(taskJumpPage.value)
}

const calcProgress = (task) => {
    if (!task) return 0
    if (task.status === 'SUCCESS') return 100
    if (task.status === 'FAILED' || task.status === 'ABORTED') return 100
    if (task.status === 'RUNNING') return 60
    if (task.status === 'PENDING') return 15
    return 0
}

const handleStop = async (task) => {
    if (confirm(`确认终止任务“${resolveTaskDisplayName(task)}”吗？`)) {
        await associationStore.stopTask(task.id)
    }
}

const handleRerun = async (task) => {
    alert(`正在重新提交任务：${resolveTaskDisplayName(task)}`)
    const taskOptions = {}
    if (task.rangeStart && task.rangeEnd) {
        taskOptions.timeRange = {
            startTime: task.rangeStart,
            endTime: task.rangeEnd
        }
    }
    await associationStore.createTask(task.ruleId, taskOptions)
    await associationStore.loadTasks()
}

const openLog = (task) => {
    logTask.value = task
    showLogModal.value = true
}

const getOutputPathEntries = (task) => {
    if (!task || !task.outputPaths) return []
    return Object.entries(task.outputPaths)
}

const openTaskResultPreview = async (task) => {
    if (!canOpenTaskResult(task)) {
        alert('当前任务尚未生成可预览的结果')
        return
    }
    await dataStore.loadResourceTree()
    const candidates = buildTaskPreviewCandidates(task)
    const targetNode = candidates
        .map(path => dataStore.findNodeByPath(path))
        .find(Boolean)
    if (!targetNode) {
        alert('未在数据资源库中定位到该任务结果，请稍后刷新后再试')
        return
    }
    dataStore.selectNode(targetNode)
    await router.push('/data')
}

const closeLog = () => {
    showLogModal.value = false
    logTask.value = null
}

watch(filterStatus, () => {
    resetTaskPagination()
})

watch(taskPageSize, () => {
    resetTaskPagination()
})

watch(filteredTasks, () => {
    if (taskPageNum.value > taskTotalPages.value) {
        taskPageNum.value = taskTotalPages.value
    }
    syncTaskJumpPage()
})

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
                            <div class="text-gray-400">任务名称</div>
                            <div class="text-gray-800">{{ resolveTaskDisplayName(logTask) }}</div>
                        </div>
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
                        <div>
                            <div class="text-gray-400">计划开始</div>
                            <div class="font-mono text-gray-800">{{ formatScheduleTime(logTask?.scheduledStartTime, '立即执行') }}</div>
                        </div>
                        <div>
                            <div class="text-gray-400">计划终止</div>
                            <div class="font-mono text-gray-800">{{ formatScheduleTime(logTask?.scheduledEndTime, '不限制') }}</div>
                        </div>
                        <div class="col-span-2">
                            <div class="text-gray-400">时间范围</div>
                            <div class="font-mono text-gray-800">{{ formatTime(logTask?.rangeStart) }} ~ {{ formatTime(logTask?.rangeEnd) }}</div>
                        </div>
                        <div class="col-span-2">
                            <div class="text-gray-400">结果路径</div>
                            <div v-if="getOutputPathEntries(logTask).length" class="space-y-1">
                                <div v-for="([name, path]) in getOutputPathEntries(logTask)" :key="name" class="font-mono text-gray-800 break-all">
                                    <span class="text-gray-500">{{ name }}</span> -> {{ path }}
                                </div>
                            </div>
                            <div v-else class="font-mono text-gray-800 break-all">{{ logTask?.resultLink || '-' }}</div>
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
                <button v-for="status in ['All', 'Pending', 'Running', 'Success', 'Failed', 'Aborted']" :key="status"
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
                            <th class="px-6 py-3">任务名称</th>
                            <th class="px-6 py-3">Rule Name</th>
                            <th class="px-6 py-3">Time Range</th>
                            <th class="px-6 py-3">Schedule</th>
                            <th class="px-6 py-3">Created At</th>
                            <th class="px-6 py-3">Status</th>
                            <th class="px-6 py-3">Progress</th>
                            <th class="px-6 py-3 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-100">
                        <tr v-if="filteredTasks.length === 0">
                            <td colspan="8" class="px-6 py-12 text-center text-gray-400">
                                <i class="ri-inbox-line text-4xl mb-2"></i>
                                <p>No tasks found.</p>
                            </td>
                        </tr>
                        <tr v-for="task in paginatedTasks" :key="task.id" class="hover:bg-gray-50 transition-colors group">
                            <td class="px-6 py-3">
                                <button
                                    type="button"
                                    @click="openTaskResultPreview(task)"
                                    :disabled="!canOpenTaskResult(task)"
                                    class="font-medium text-left transition-colors"
                                    :class="canOpenTaskResult(task)
                                        ? 'text-blue-600 hover:text-blue-700 hover:underline cursor-pointer'
                                        : 'text-gray-800 cursor-not-allowed'"
                                >
                                    {{ resolveTaskDisplayName(task) }}
                                </button>
                                <div class="font-mono text-[10px] text-gray-400">{{ task.id }}</div>
                            </td>
                            <td class="px-6 py-3 font-medium text-gray-800">{{ associationStore.rules.find(r => r.id === task.ruleId)?.name || 'Unknown Rule' }}</td>
                            <td class="px-6 py-3 text-gray-500 text-xs">
                                {{ formatTime(task.rangeStart) }} <i class="ri-arrow-right-line mx-1 text-gray-300"></i> {{ formatTime(task.rangeEnd) }}
                            </td>
                            <td class="px-6 py-3 text-gray-500 text-xs leading-5">
                                <div>开始: {{ formatScheduleTime(task.scheduledStartTime, '立即执行') }}</div>
                                <div>终止: {{ formatScheduleTime(task.scheduledEndTime, '不限制') }}</div>
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
            <div class="border-t border-gray-200 bg-gray-50 px-6 py-3 text-xs text-gray-500">
                <div class="flex items-center justify-between gap-4">
                    <div class="flex items-center gap-3">
                        <span>共 {{ filteredTasks.length }} 条，当前第 {{ taskPageNum }} / {{ taskTotalPages }} 页</span>
                        <label class="flex items-center gap-2">
                            <span>每页</span>
                            <select v-model.number="taskPageSize" class="rounded border border-gray-200 bg-white px-2 py-1 text-xs text-gray-700 focus:border-blue-400 focus:outline-none">
                                <option v-for="size in TASK_PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
                            </select>
                            <span>条</span>
                        </label>
                    </div>
                    <div class="flex items-center gap-2">
                        <button
                            @click="goToTaskPage(taskPageNum - 1)"
                            :disabled="taskPageNum <= 1"
                            class="rounded border border-gray-200 px-2.5 py-1 text-gray-600 transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            上一页
                        </button>
                        <button
                            @click="goToTaskPage(taskPageNum + 1)"
                            :disabled="taskPageNum >= taskTotalPages"
                            class="rounded border border-gray-200 px-2.5 py-1 text-gray-600 transition hover:bg-white disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            下一页
                        </button>
                        <span>跳至</span>
                        <input
                            v-model="taskJumpPage"
                            type="number"
                            min="1"
                            :max="taskTotalPages"
                            class="w-20 rounded border border-gray-200 px-2 py-1 text-gray-700 focus:border-blue-400 focus:outline-none"
                            @keyup.enter="jumpToTaskPage"
                        >
                        <button
                            @click="jumpToTaskPage"
                            class="rounded bg-blue-600 px-2.5 py-1 text-white transition hover:bg-blue-700"
                        >
                            跳转
                        </button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
