<script setup>
import { useUIStore } from '../stores/ui'
import { ref, onMounted, computed, nextTick } from 'vue'

const uiStore = useUIStore()
const logs = ref([])
const filterLevel = ref('All')
const logContainer = ref(null)
const autoScroll = ref(true)

const levels = ['INFO', 'WARN', 'ERROR', 'DEBUG']

// Mock log generation
const generateLog = () => {
    const level = levels[Math.floor(Math.random() * levels.length)]
    const components = ['StorageEngine', 'QueryProcessor', 'NetworkLayer', 'AuthService', 'MetadataManager']
    const component = components[Math.floor(Math.random() * components.length)]
    const messages = [
        'Connection established',
        'Query executed successfully',
        'Slow query detected (>500ms)',
        'Packet dropped due to timeout',
        'User authentication failed',
        'Metadata cache refreshed',
        'Compaction task started',
        'Replication factor updated'
    ]
    const msg = messages[Math.floor(Math.random() * messages.length)]
    
    return {
        id: Date.now() + Math.random(),
        time: new Date().toISOString().replace('T', ' ').slice(0, 23),
        level,
        component,
        message: msg
    }
}

onMounted(() => {
    // Generate initial logs
    for(let i=0; i<50; i++) {
        logs.value.push(generateLog())
    }
    
    // Simulate incoming logs
    setInterval(() => {
        if (uiStore.showSystemLogs) {
            logs.value.push(generateLog())
            if (logs.value.length > 1000) logs.value.shift() // Keep limit
            if (autoScroll.value) scrollToBottom()
        }
    }, 2000)
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
                    <span class="font-mono font-bold">IGinX System Logs</span>
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
                    <button @click="clearLogs" class="px-2 py-1 bg-white border border-gray-300 rounded text-xs hover:bg-gray-50 text-gray-700 flex items-center">
                        <i class="ri-delete-bin-line mr-1"></i> Clear
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
                <div v-for="log in filteredLogs" :key="log.id" class="flex space-x-2 hover:bg-gray-800 p-0.5 rounded">
                    <span class="text-gray-500 shrink-0">[{{ log.time }}]</span>
                    <span :class="getLevelColor(log.level)" class="w-12 shrink-0 font-bold">{{ log.level }}</span>
                    <span class="text-blue-400 w-32 shrink-0 truncate" :title="log.component">{{ log.component }}</span>
                    <span class="text-gray-300 break-all">{{ log.message }}</span>
                </div>
                <div v-if="filteredLogs.length === 0" class="text-gray-500 text-center py-8 italic">
                    No logs to display
                </div>
            </div>
            
            <!-- Footer -->
            <div class="bg-gray-800 text-gray-400 px-4 py-1 text-[10px] flex justify-between rounded-b-lg">
                <span>IGinX Core v1.2.0</span>
                <span>Connected: localhost:6324</span>
            </div>
        </div>
    </div>
</template>