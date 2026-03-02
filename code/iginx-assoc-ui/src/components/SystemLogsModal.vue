<script setup>
import { useUIStore } from '../stores/ui'
import { ref, computed, nextTick, watch, onBeforeUnmount } from 'vue'
import { fetchSystemLogs } from '../api/system'

const uiStore = useUIStore()
const logs = ref([])
const filterLevel = ref('All')
const logContainer = ref(null)
const autoScroll = ref(true)
const isLoading = ref(false)
const errorMessage = ref('')
const lastUpdated = ref('')

const levels = ['INFO', 'WARN', 'ERROR', 'DEBUG']

let refreshTimer = null

const loadLogs = async () => {
    if (!uiStore.showSystemLogs) return
    isLoading.value = true
    errorMessage.value = ''
    try {
        const level = filterLevel.value === 'All' ? '' : filterLevel.value
        const data = await fetchSystemLogs({ limit: 500, level })
        logs.value = Array.isArray(data) ? data : []
        lastUpdated.value = new Date().toLocaleString()
        if (autoScroll.value) scrollToBottom()
    } catch (err) {
        errorMessage.value = err?.message || '获取系统日志失败'
    } finally {
        isLoading.value = false
    }
}

const startRefresh = () => {
    if (refreshTimer) return
    refreshTimer = setInterval(() => {
        loadLogs()
    }, 2000)
}

const stopRefresh = () => {
    if (refreshTimer) {
        clearInterval(refreshTimer)
        refreshTimer = null
    }
}

watch(() => uiStore.showSystemLogs, (visible) => {
    if (visible) {
        loadLogs()
        startRefresh()
    } else {
        stopRefresh()
    }
})

watch(filterLevel, () => {
    if (uiStore.showSystemLogs) {
        loadLogs()
    }
})

onBeforeUnmount(() => {
    stopRefresh()
})

const filteredLogs = computed(() => {
    if (filterLevel.value === 'All') return logs.value
    return logs.value.filter(l => l.level === filterLevel.value)
})

const scrollToBottom = () => {
    nextTick(() => {
        if (logContainer.value) {
            logContainer.value.scrollTop = logContainer.value.scrollHeight
        }
    })
}

const clearLogs = () => {
    logs.value = []
}

const getLevelColor = (level) => {
    switch(level) {
        case 'INFO': return 'text-green-600'
        case 'WARN': return 'text-yellow-600'
        case 'ERROR': return 'text-red-600'
        case 'DEBUG': return 'text-gray-500'
        default: return 'text-gray-800'
    }
}
</script>

<template>
    <div v-if="uiStore.showSystemLogs" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
            <div class="bg-white rounded-lg shadow-2xl w-[800px] h-[600px] flex flex-col border border-gray-700">
            <!-- Header -->
            <div class="px-4 py-3 bg-gray-800 text-white flex justify-between items-center rounded-t-lg">
                <div class="flex items-center space-x-2">
                    <i class="ri-terminal-box-line"></i>
                    <span class="font-mono font-bold">后端系统日志</span>
                </div>
                <div class="flex items-center space-x-3">
                    <button @click="uiStore.showSystemLogs = false" class="hover:text-red-400 transition-colors">
                        <i class="ri-close-line text-xl"></i>
                    </button>
                </div>
            </div>

            <!-- Toolbar -->
            <div class="bg-gray-100 border-b border-gray-200 p-2 flex justify-between items-center">
                <div class="flex space-x-2">
                    <select v-model="filterLevel" class="text-xs border border-gray-300 rounded px-2 py-1 bg-white focus:outline-none focus:border-blue-500">
                        <option value="All">All Levels</option>
                        <option v-for="l in levels" :key="l" :value="l">{{ l }}</option>
                    </select>
                    <div class="h-6 w-px bg-gray-300 mx-1"></div>
                    <button @click="loadLogs" class="px-2 py-1 bg-white border border-gray-300 rounded text-xs hover:bg-gray-50 text-gray-700 flex items-center">
                        <i class="ri-refresh-line mr-1"></i> 刷新
                    </button>
                    <button @click="clearLogs" class="px-2 py-1 bg-white border border-gray-300 rounded text-xs hover:bg-gray-50 text-gray-700 flex items-center">
                        <i class="ri-delete-bin-line mr-1"></i> 清空视图
                    </button>
                </div>
                <div class="flex items-center space-x-2">
                    <label class="flex items-center text-xs text-gray-600 cursor-pointer select-none">
                        <input type="checkbox" v-model="autoScroll" class="mr-1"> Auto-scroll
                    </label>
                </div>
            </div>

            <!-- Log Area -->
            <div ref="logContainer" class="flex-1 bg-gray-900 overflow-y-auto p-2 font-mono text-xs space-y-1">
                <div v-if="errorMessage" class="text-red-400 text-center py-4">{{ errorMessage }}</div>
                <div v-else-if="isLoading && filteredLogs.length === 0" class="text-gray-500 text-center py-4">正在读取日志...</div>
                <div v-for="log in filteredLogs" :key="log.id || `${log.time}-${log.component}-${log.message}`" class="flex space-x-2 hover:bg-gray-800 p-0.5 rounded">
                    <span class="text-gray-500 shrink-0">[{{ log.time }}]</span>
                    <span :class="getLevelColor(log.level)" class="w-12 shrink-0 font-bold">{{ log.level }}</span>
                    <span class="text-blue-400 w-32 shrink-0 truncate" :title="log.component">{{ log.component }}</span>
                    <span class="text-gray-300 break-all">{{ log.message }}</span>
                </div>
                <div v-if="filteredLogs.length === 0" class="text-gray-500 text-center py-8 italic">
                    暂无日志
                </div>
            </div>
            
            <!-- Footer -->
            <div class="bg-gray-800 text-gray-400 px-4 py-1 text-[10px] flex justify-between rounded-b-lg">
                <span>日志来源：后端实时输出</span>
                <span>{{ lastUpdated ? `最后更新：${lastUpdated}` : '等待日志中...' }}</span>
            </div>
        </div>
    </div>
</template>
