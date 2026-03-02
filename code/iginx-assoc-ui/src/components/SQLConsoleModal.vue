<script setup>
import { useUIStore } from '../stores/ui'
import { ref, reactive } from 'vue'
import { executeSqlConsole } from '../api/system'

const uiStore = useUIStore()
const query = ref('SELECT * FROM demo.result LIMIT 10')
const isExecuting = ref(false)
const result = reactive({
  columns: [],
  rows: [],
  error: null,
  message: '',
  executionTime: 0
})

const executeQuery = async () => {
  if (!query.value.trim()) {
    result.error = 'SQL 不能为空'
    return
  }
  isExecuting.value = true
  result.error = null
  result.message = ''
  result.columns = []
  result.rows = []

  const start = performance.now()
  try {
    const data = await executeSqlConsole({
      sql: query.value,
      limit: 500,
      formatTime: true
    })
    result.columns = data?.columns || []
    result.rows = data?.rows || []
    result.message = data?.message || ''
    const cost = data?.executionTimeMs ?? (performance.now() - start)
    result.executionTime = Number(cost).toFixed(2)
  } catch (err) {
    result.error = err?.message || 'SQL 执行失败'
  } finally {
    isExecuting.value = false
  }
}

const clearQuery = () => {
  query.value = ''
  result.columns = []
  result.rows = []
  result.error = null
  result.message = ''
}

const handleEditorKeydown = (event) => {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    executeQuery()
  }
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
                        <h3 class="font-bold text-gray-800">SQL 控制台</h3>
                        <p class="text-xs text-gray-500">直接执行 IGinX SQL 查询</p>
                    </div>
                </div>
                <button @click="uiStore.showSQLConsole = false" class="text-gray-400 hover:text-gray-800">
                    <i class="ri-close-line text-2xl"></i>
                </button>
            </div>

            <div class="flex-1 min-h-0 flex flex-col overflow-hidden">
                <!-- Editor -->
                <div class="h-48 border-b border-gray-200 flex flex-col bg-white">
                    <div class="flex-1 p-4 relative">
                        <textarea v-model="query"
                                  @keydown="handleEditorKeydown"
                                  class="w-full h-full resize-none font-mono text-sm text-gray-800 focus:outline-none"
                                  placeholder="请输入 IGinX SQL..."></textarea>
                    </div>
                    <div class="px-4 py-2 bg-gray-50 border-t border-gray-100 flex justify-between items-center">
                        <span class="text-xs text-gray-500">Ctrl+Enter 执行</span>
                        <div class="flex space-x-2">
                            <button @click="clearQuery" class="px-3 py-1.5 bg-white border border-gray-300 text-gray-600 rounded text-xs hover:bg-gray-50">清空</button>
                            <button @click="executeQuery" :disabled="isExecuting" class="px-4 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 disabled:opacity-50 flex items-center">
                                <i v-if="isExecuting" class="ri-loader-4-line animate-spin mr-1"></i>
                                {{ isExecuting ? '执行中...' : '执行' }}
                            </button>
                        </div>
                    </div>
                </div>

                <!-- Result -->
                <div class="flex-1 min-h-0 flex flex-col bg-white overflow-hidden">
                    <div v-if="result.error" class="p-6 text-red-600 bg-red-50 flex items-start space-x-3 border-b border-red-100">
                        <i class="ri-error-warning-fill text-xl mt-0.5"></i>
                        <div>
                            <div class="font-bold text-sm">执行失败</div>
                            <div class="text-xs font-mono mt-1">{{ result.error }}</div>
                        </div>
                    </div>
                    <div v-else-if="result.columns.length > 0" class="flex-1 min-h-0 h-0 flex flex-col">
                        <div class="px-4 py-2 bg-green-50 text-green-700 text-xs border-b border-green-100 flex justify-between">
                            <span class="font-bold"><i class="ri-check-double-line mr-1"></i> 执行成功</span>
                            <span>{{ result.rows.length }} 行，耗时 {{ result.executionTime }}ms</span>
                        </div>
                        <div class="flex-1 min-h-0 overflow-auto">
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
                    <div v-else-if="result.message" class="p-6 text-green-700 bg-green-50 text-sm border-b border-green-100">
                        <i class="ri-check-line mr-1"></i>{{ result.message }}
                    </div>
                    <div v-else class="flex-1 flex flex-col items-center justify-center text-gray-400">
                        <i class="ri-table-2 text-5xl mb-3 opacity-20"></i>
                        <p class="text-sm">执行查询以查看结果</p>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>
