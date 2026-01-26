<script setup>
import { useUIStore } from '../stores/ui'
import { ref, reactive } from 'vue'

const uiStore = useUIStore()
const query = ref('SELECT * FROM root.sg.d1 LIMIT 10')
const isExecuting = ref(false)
const result = reactive({
    columns: [],
    rows: [],
    error: null,
    executionTime: 0
})

const executeQuery = () => {
    isExecuting.value = true
    result.error = null
    result.columns = []
    result.rows = []
    
    // Mock Execution
    setTimeout(() => {
        isExecuting.value = false
        const start = performance.now()
        
        if (query.value.toUpperCase().includes('ERROR')) {
            result.error = 'Syntax Error: Unexpected token near "ERROR"'
        } else {
            result.columns = ['Time', 'root.sg.d1.s1', 'root.sg.d1.s2', 'root.sg.d1.s3']
            for(let i=0; i<10; i++) {
                result.rows.push({
                    Time: new Date(Date.now() - i*1000).toISOString(),
                    'root.sg.d1.s1': (Math.random()*100).toFixed(2),
                    'root.sg.d1.s2': (Math.random()*50).toFixed(2),
                    'root.sg.d1.s3': Math.random() > 0.5 ? 'Active' : 'Idle'
                })
            }
        }
        result.executionTime = (performance.now() - start + Math.random() * 50).toFixed(2)
    }, 600)
}
</script>

<template>
    <div v-if="uiStore.showSQLConsole" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-2xl w-[900px] h-[700px] flex flex-col">
            <!-- Header -->
            <div class="px-6 py-4 border-b border-gray-200 flex justify-between items-center bg-gray-50 rounded-t-lg">
                <div class="flex items-center space-x-2">
                    <div class="bg-blue-600 text-white p-1.5 rounded">
                        <i class="ri-terminal-line"></i>
                    </div>
                    <div>
                        <h3 class="font-bold text-gray-800">SQL Console</h3>
                        <p class="text-xs text-gray-500">Execute IGinX SQL queries directly</p>
                    </div>
                </div>
                <button @click="uiStore.showSQLConsole = false" class="text-gray-400 hover:text-gray-800">
                    <i class="ri-close-line text-2xl"></i>
                </button>
            </div>

            <div class="flex-1 flex flex-col overflow-hidden">
                <!-- Editor -->
                <div class="h-48 border-b border-gray-200 flex flex-col bg-white">
                    <div class="flex-1 p-4 relative">
                        <textarea v-model="query" 
                                  class="w-full h-full resize-none font-mono text-sm text-gray-800 focus:outline-none"
                                  placeholder="Enter your SQL query here..."></textarea>
                    </div>
                    <div class="px-4 py-2 bg-gray-50 border-t border-gray-100 flex justify-between items-center">
                        <span class="text-xs text-gray-500">Ctrl+Enter to execute</span>
                        <div class="flex space-x-2">
                            <button class="px-3 py-1.5 bg-white border border-gray-300 text-gray-600 rounded text-xs hover:bg-gray-50">Clear</button>
                            <button @click="executeQuery" :disabled="isExecuting" class="px-4 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50 flex items-center">
                                <i v-if="isExecuting" class="ri-loader-4-line animate-spin mr-1"></i>
                                {{ isExecuting ? 'Running...' : 'Execute' }}
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Result -->
                <div class="flex-1 flex flex-col bg-white overflow-hidden">
                    <div v-if="result.error" class="p-6 text-red-600 bg-red-50 flex items-start space-x-3 border-b border-red-100">
                        <i class="ri-error-warning-fill text-xl mt-0.5"></i>
                        <div>
                            <div class="font-bold text-sm">Execution Failed</div>
                            <div class="text-xs font-mono mt-1">{{ result.error }}</div>
                        </div>
                    </div>
                    <div v-else-if="result.columns.length > 0" class="flex-1 flex flex-col">
                        <div class="px-4 py-2 bg-green-50 text-green-700 text-xs border-b border-green-100 flex justify-between">
                            <span class="font-bold"><i class="ri-check-double-line mr-1"></i> Query executed successfully</span>
                            <span>{{ result.rows.length }} rows in {{ result.executionTime }}ms</span>
                        </div>
                        <div class="flex-1 overflow-auto">
                            <table class="w-full text-left border-collapse">
                                <thead class="bg-gray-50 sticky top-0">
                                    <tr>
                                        <th v-for="col in result.columns" :key="col" class="px-4 py-2 text-xs font-bold text-gray-600 border-b border-gray-200 border-r last:border-r-0 whitespace-nowrap">{{ col }}</th>
                                    </tr>
                                </thead>
                                <tbody class="font-mono text-xs">
                                    <tr v-for="(row, idx) in result.rows" :key="idx" class="hover:bg-blue-50">
                                        <td v-for="col in result.columns" :key="col" class="px-4 py-1.5 border-b border-gray-100 border-r last:border-r-0 text-gray-700 whitespace-nowrap">{{ row[col] }}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div v-else class="flex-1 flex flex-col items-center justify-center text-gray-400">
                        <i class="ri-table-2 text-5xl mb-3 opacity-20"></i>
                        <p class="text-sm">Execute a query to see results</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>