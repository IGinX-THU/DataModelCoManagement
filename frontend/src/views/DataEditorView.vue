<script setup>
import { ref, onMounted, watch, nextTick, reactive } from 'vue'
import { useDataStore } from '../stores/data'
import * as echarts from 'echarts'

const dataStore = useDataStore()
const chartRef = ref(null)
let chartInstance = null

// Mock Data
const tsData = ref([
    { time: '2025-01-14 10:00:00', value: 24.5 },
    { time: '2025-01-14 10:00:10', value: 25.1 },
    { time: '2025-01-14 10:00:20', value: 24.8 },
    { time: '2025-01-14 10:00:30', value: 26.2 },
    { time: '2025-01-14 10:00:40', value: 25.5 },
])

const tableData = ref([
    { id: 1, name: 'Engine_01', type: 'V8', status: 'Active', location: 'Zone A' },
    { id: 2, name: 'Engine_02', type: 'V6', status: 'Maintenance', location: 'Zone B' },
    { id: 3, name: 'Sensor_X', type: 'Temp', status: 'Active', location: 'Zone A' },
    { id: 4, name: 'Pump_Main', type: 'Hydraulic', status: 'Inactive', location: 'Zone C' },
    { id: 5, name: 'Control_Unit', type: 'ECU', status: 'Active', location: 'Zone B' },
])

const showQueryBuilder = ref(false)
const queryConditions = ref([
    { logic: 'AND', field: '', op: '=', value: '' }
])

const addCondition = () => {
    queryConditions.value.push({ logic: 'AND', field: '', op: '=', value: '' })
}

const removeCondition = (index) => {
    if (queryConditions.value.length > 1) {
        queryConditions.value.splice(index, 1)
    } else {
        // Clear the last one instead of removing
        queryConditions.value[0] = { logic: 'AND', field: '', op: '=', value: '' }
    }
}

const applyQuery = () => {
    showQueryBuilder.value = false
    // Implement filter logic here or mock it
    console.log('Query Applied:', queryConditions.value)
}

const showEditModal = ref(false)
const isEditMode = ref(false)
const editingRow = reactive({})
const selectedRowId = ref(null)

const openNewModal = () => {
    isEditMode.value = false
    // Reset editingRow based on tableData schema
    Object.keys(editingRow).forEach(key => delete editingRow[key])
    if (tableData.value.length > 0) {
        Object.keys(tableData.value[0]).forEach(key => {
            editingRow[key] = key === 'id' ? Math.max(...tableData.value.map(d => d.id)) + 1 : ''
        })
    }
    showEditModal.value = true
}

const openEditModal = () => {
    if (!selectedRowId.value) {
        alert('Please select a row first.')
        return
    }
    isEditMode.value = true
    const row = tableData.value.find(d => d.id === selectedRowId.value)
    if (row) {
        Object.assign(editingRow, JSON.parse(JSON.stringify(row)))
        showEditModal.value = true
    }
}

const saveRow = () => {
    if (isEditMode.value) {
        const index = tableData.value.findIndex(d => d.id === editingRow.id)
        if (index !== -1) {
            tableData.value[index] = { ...editingRow }
        }
    } else {
        tableData.value.push({ ...editingRow })
    }
    showEditModal.value = false
}

const selectRow = (id) => {
    selectedRowId.value = id
}

const handleDeleteRow = () => {
    if (!selectedRowId.value) {
        alert('Please select a row to delete.')
        return
    }
    if (confirm(`Are you sure you want to delete row with ID ${selectedRowId.value}?`)) {
        const index = tableData.value.findIndex(d => d.id === selectedRowId.value)
        if (index !== -1) {
            tableData.value.splice(index, 1)
            selectedRowId.value = null
        }
    }
}

const handleRemoveCurrentNode = () => {
    if (!dataStore.currentNode.id) return
    const res = dataStore.removeChild(dataStore.currentNode.id)
    if (res.success) {
        // Node removed successfully
    } else {
        alert(res.msg)
    }
}

const initChart = () => {
    if (!chartRef.value) return
    if (chartInstance) chartInstance.dispose()
    chartInstance = echarts.init(chartRef.value)
    chartInstance.setOption({
        backgroundColor: 'transparent',
        grid: { top: 30, right: 20, bottom: 20, left: 40 },
        tooltip: { trigger: 'axis' },
        xAxis: { 
            type: 'category', 
            data: tsData.value.map(d => d.time.split(' ')[1]),
            axisLine: { lineStyle: { color: '#ccc' } },
            axisLabel: { color: '#666' }
        },
        yAxis: { 
            type: 'value', 
            min: 20,
            splitLine: { lineStyle: { color: '#eee' } },
            axisLabel: { color: '#666' }
        },
        series: [{ 
            data: tsData.value.map(d => d.value), 
            type: 'line', 
            smooth: true, 
            areaStyle: { opacity: 0.2, color: '#3b82f6' }, 
            lineStyle: { color: '#3b82f6' },
            itemStyle: { color: '#3b82f6' }
        }]
    })
}

const treeChartRef = ref(null)
let treeChartInstance = null

const initTreeChart = () => {
    if (!treeChartRef.value) return
    if (treeChartInstance) treeChartInstance.dispose()
    treeChartInstance = echarts.init(treeChartRef.value)

    // Construct Tree Data from Store based on current selection
    const findNode = (nodes, id) => {
        for (const node of nodes) {
            if (node.id === id) return node
            if (node.children) {
                const found = findNode(node.children, id)
                if (found) return found
            }
        }
        return null
    }

    // If a node is selected, find it. If it's a leaf (point), find its parent (group) to show context, 
    // BUT user specifically asked for "Rooted at Source". 
    // Let's implement: If source is selected -> show full source tree. If group selected -> show group tree.
    const sourceNode = findNode(dataStore.dataSourceTree, dataStore.currentNode.id)
    if (!sourceNode) return

    // Transform to ECharts Tree format (Premium Style: Circles + Curves)
    const transformData = (node) => {
        let symbolColor = '#3b82f6' // Blue for root
        let borderColor = '#2563eb'
        
        if (node.type === 'group') {
            symbolColor = '#f59e0b' // Yellow for schema/group
            borderColor = '#d97706'
        } else if (node.type === 'point' || node.type === 'table') {
            symbolColor = '#22c55e' // Green for point/table
            borderColor = '#16a34a'
        } else if (node.type === 'rel') {
            symbolColor = '#3b82f6'
            borderColor = '#2563eb'
        }

        return {
            name: node.name,
            value: node.type,
            children: node.children ? node.children.map(transformData) : [],
            
            // Clean Circle Style
            symbol: 'circle',
            symbolSize: 18,
            itemStyle: {
                color: symbolColor,
                borderColor: '#fff',
                borderWidth: 2,
                shadowBlur: 5,
                shadowColor: 'rgba(0,0,0,0.1)'
            },
            
            label: {
                position: 'top',
                verticalAlign: 'middle',
                align: 'center',
                distance: 8,
                fontSize: 12,
                fontFamily: 'sans-serif',
                color: '#4b5563',
                backgroundColor: '#fff',
                padding: [2, 6],
                borderRadius: 4
            },
            
            // Curve Style
            lineStyle: {
                color: '#cbd5e1',
                width: 1.5,
                type: 'solid',
                curveness: 0.5
            },
            
            // Default Expansion State
            collapsed: false 
        }
    }

    const option = {
        tooltip: {
            trigger: 'item',
            triggerOn: 'mousemove',
            backgroundColor: 'rgba(255, 255, 255, 0.95)',
            borderColor: '#e5e7eb',
            textStyle: { color: '#374151' },
            formatter: (params) => {
                 const n = params.data
                 const typeMap = { 'ts': 'Data Source', 'group': 'Storage Group', 'point': 'Measurement Point' }
                 return `<div class="font-bold text-gray-800">${n.name}</div>
                         <div class="text-xs text-gray-500">${typeMap[n.value] || n.value}</div>`
            }
        },
        series: [
            {
                type: 'tree',
                data: [transformData(sourceNode)],
                top: '10%',
                left: '10%',
                bottom: '10%',
                right: '20%',
                symbolSize: 18,
                
                layout: 'orthogonal',
                orient: 'LR', 
                
                expandAndCollapse: true,
                initialTreeDepth: 1, // Only expand Root -> Groups
                roam: true,
                
                // Curve Edges
                edgeShape: 'curve', 
                
                label: {
                    position: 'top',
                    verticalAlign: 'middle',
                    align: 'center',
                    fontSize: 12
                },
                leaves: {
                    label: {
                        position: 'right',
                        verticalAlign: 'middle',
                        align: 'left',
                        distance: 8
                    }
                },
                animationDuration: 550,
                animationDurationUpdate: 750
            }
        ]
    }
    treeChartInstance.setOption(option)
}

const updateTableDataForSelection = () => {
    // If selecting a group/schema, show its children in the table
    if (['group', 'schema'].includes(dataStore.currentNode.type)) {
        // Helper to find node
        const findNode = (nodes, id) => {
            for (const node of nodes) {
                if (node.id === id) return node
                if (node.children) {
                    const found = findNode(node.children, id)
                    if (found) return found
                }
            }
            return null
        }
        const node = findNode(dataStore.dataSourceTree, dataStore.currentNode.id)
        if (node && node.children) {
            // Map children to table format
            tableData.value = node.children.map(child => ({
                id: child.id,
                name: child.name,
                type: child.type,
                status: 'Active', // Mock status
                items: child.children ? child.children.length : 'N/A'
            }))
        } else {
             tableData.value = []
        }
    } else if (['table', 'rel'].includes(dataStore.currentNode.type)) {
        // Reset to mock table data for tables (or fetch real data)
        tableData.value = [
            { id: 1, name: 'Engine_01', type: 'V8', status: 'Active', location: 'Zone A' },
            { id: 2, name: 'Engine_02', type: 'V6', status: 'Maintenance', location: 'Zone B' },
            { id: 3, name: 'Sensor_X', type: 'Temp', status: 'Active', location: 'Zone A' },
            { id: 4, name: 'Pump_Main', type: 'Hydraulic', status: 'Inactive', location: 'Zone C' },
            { id: 5, name: 'Control_Unit', type: 'ECU', status: 'Active', location: 'Zone B' },
        ]
    }
}

watch(() => [dataStore.currentNode.id, dataStore.currentNode.viewMode], ([newId, newMode]) => {
    if (newId) {
        if (newMode === 'topology' && ['group', 'ts', 'rel', 'schema'].includes(dataStore.currentNode.type)) {
             nextTick(initTreeChart)
        } else {
             // Default to chart/table view
             updateTableDataForSelection()
             nextTick(initChart)
        }
    }
})

onMounted(() => {
    if (dataStore.currentNode.id) {
         if (dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rel', 'schema'].includes(dataStore.currentNode.type)) initTreeChart()
         else initChart()
    }
    window.addEventListener('resize', () => {
        chartInstance && chartInstance.resize()
        treeChartInstance && treeChartInstance.resize()
    })
})
</script>

<template>
  <div class="h-full flex flex-col relative">
     <!-- Empty State (No Selection) -->
     <div v-if="!dataStore.currentNode.id" class="flex-1 flex flex-col items-center justify-center m-10 bg-white">
         <div class="w-32 h-32 bg-blue-50 rounded-full flex items-center justify-center mb-6">
             <i class="ri-database-2-line text-6xl text-blue-200"></i>
         </div>
         <h2 class="text-xl font-bold text-gray-800 mb-2">欢迎使用 IGinX 数据管理器</h2>
         <p class="text-gray-500 text-sm max-w-md text-center mb-8">
             请从左侧数据资源库选择一个数据源以查看详情，或使用上方工具栏添加新的数据源。
         </p>
         <div class="flex space-x-4">
             <button @click="dataStore.showAddSourceModal = true" class="px-6 py-2 bg-blue-600 rounded-full shadow-lg shadow-blue-200 text-white text-sm hover:bg-blue-700 transition-all transform hover:-translate-y-1">
                 <i class="ri-add-line mr-1"></i> 新增数据源
             </button>
             <button @click="dataStore.openImportWizard('ts')" class="px-6 py-2 bg-white border border-gray-200 rounded-full shadow-lg shadow-gray-100 text-gray-600 text-sm hover:bg-gray-50 transition-all transform hover:-translate-y-1">
                 <i class="ri-upload-cloud-line mr-1"></i> 导入数据
             </button>
         </div>
     </div>

     <!-- Content Viewer -->
     <div v-else class="flex-1 flex flex-col h-full">
         <!-- Group/Tree View in Workspace -->
         <div v-show="dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rel', 'schema'].includes(dataStore.currentNode.type)" 
              class="flex-1 bg-white rounded border border-gray-100 shadow-sm p-4 flex flex-col relative overflow-hidden">
             <h3 class="text-lg font-bold text-gray-800 mb-4 flex items-center border-b border-gray-100 pb-2 z-10 relative bg-white justify-between">
                <div class="flex items-center">
                    <i class="ri-node-tree text-blue-500 mr-2"></i>
                    Data Resource Topology: {{ dataStore.currentNode.id }}
                </div>
                <div class="flex items-center space-x-2">
                    <button @click="dataStore.selectNode(dataStore.currentNode.type, dataStore.currentNode.id)" class="text-gray-400 hover:text-gray-800 transition-colors p-1 rounded hover:bg-gray-100">
                        <i class="ri-close-line text-xl"></i>
                    </button>
                </div>
             </h3>
             <div class="absolute inset-0 top-14">
                 <div ref="treeChartRef" class="w-full h-full"></div>
             </div>
             <div class="absolute bottom-4 right-4 z-10 bg-white/80 backdrop-blur px-3 py-1 rounded text-[10px] text-gray-500 border border-gray-200 shadow-sm">
                 <i class="ri-mouse-line mr-1"></i> Scroll to Zoom • Drag to Pan
             </div>
         </div>

         <!-- Leaf Node View / Default Table View -->
         <div v-show="!(dataStore.currentNode.viewMode === 'topology' && ['group', 'ts', 'rel', 'schema'].includes(dataStore.currentNode.type))" 
              class="flex-1 relative bg-white rounded border border-gray-100 shadow-sm p-4 flex flex-col">
            <div class="flex justify-between items-center mb-4">
                <h3 class="text-lg font-bold text-gray-800 flex items-center">
                    <i :class="dataStore.currentNode.type === 'ts' ? 'ri-pulse-line text-blue-500' : 'ri-table-line text-green-500'" class="mr-2"></i>
                    {{ dataStore.currentNode.id }}
                </h3>
                <!-- Query / Aggregation Controls for TS -->
                <div v-if="['ts', 'point'].includes(dataStore.currentNode.type) || (dataStore.currentNode.parentType === 'ts')" class="flex space-x-2 items-center">
                    <div class="flex items-center space-x-1 bg-gray-50 border border-gray-200 rounded px-2 py-1">
                        <i class="ri-calendar-line text-gray-400 text-xs"></i>
                        <input type="datetime-local" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-32">
                        <span class="text-gray-400">-</span>
                        <input type="datetime-local" class="bg-transparent text-xs border-none focus:ring-0 text-gray-600 w-32">
                    </div>
                    <select class="border border-gray-300 rounded text-xs px-2 py-1 text-gray-600 h-8">
                        <option>Raw Data</option>
                        <option>Mean (10s)</option>
                        <option>Max (10s)</option>
                        <option>Min (10s)</option>
                    </select>
                    <button class="px-3 py-1 bg-blue-50 text-blue-600 rounded text-xs hover:bg-blue-100 border border-blue-200 h-8">Query</button>
                    <button @click="dataStore.showMaintenanceModal = true" class="px-3 py-1 bg-red-50 text-red-600 rounded text-xs hover:bg-red-100 border border-red-200 h-8" title="Data Maintenance">
                        <i class="ri-edit-line"></i>
                    </button>
                    <button v-if="dataStore.currentNode.type === 'point'" @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="Delete Measurement">
                        <i class="ri-delete-bin-line"></i>
                    </button>
                </div>

                <!-- Group / Schema Specific Controls -->
                <div v-if="['group', 'schema'].includes(dataStore.currentNode.type)" class="flex space-x-2 items-center">
                    <button @click="dataStore.showTopology(dataStore.currentNode.type, dataStore.currentNode.id)" class="px-3 py-1 bg-indigo-50 text-indigo-600 rounded text-xs hover:bg-indigo-100 border border-indigo-200 h-8 flex items-center">
                        <i class="ri-node-tree mr-1"></i> 查看拓扑结构
                    </button>
                     <div class="h-4 border-l border-gray-300 mx-1"></div>
                     <button @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="Delete Group">
                        <i class="ri-delete-bin-line"></i>
                    </button>
                </div>

                <!-- Query / Filter Controls for Structured -->
                <div v-if="['table', 'rel'].includes(dataStore.currentNode.type)" class="relative flex items-center space-x-2">
                    <button @click="showQueryBuilder = !showQueryBuilder" 
                            :class="showQueryBuilder ? 'bg-blue-100 text-blue-600 border-blue-200' : 'bg-white text-gray-600 border-gray-300'"
                            class="flex items-center space-x-2 border rounded px-3 py-1 h-8 text-xs hover:bg-gray-50 transition-colors">
                        <i class="ri-filter-3-line"></i>
                        <span>Advanced Filter</span>
                        <span v-if="queryConditions.length > 0 && queryConditions[0].field" class="bg-blue-600 text-white text-[10px] px-1.5 rounded-full">{{ queryConditions.length }}</span>
                    </button>
                    
                    <!-- Query Builder Dropdown Panel -->
                    <div v-if="showQueryBuilder" class="absolute top-full right-0 mt-2 w-[550px] bg-white border border-gray-200 shadow-xl rounded-lg z-50 p-4">
                        <div class="flex justify-between items-center mb-3 pb-2 border-b border-gray-100">
                            <span class="font-bold text-gray-700 text-xs">Filter Conditions</span>
                            <button @click="showQueryBuilder = false" class="text-gray-400 hover:text-gray-600"><i class="ri-close-line"></i></button>
                        </div>
                        
                        <div class="space-y-2 max-h-60 overflow-y-auto mb-4">
                            <div v-for="(cond, index) in queryConditions" :key="index" class="flex items-center space-x-2">
                                <!-- Logic Operator (AND/OR) - Skip for first item -->
                                <select v-if="index > 0" v-model="cond.logic" class="w-16 text-xs border border-gray-300 rounded px-1 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="AND">AND</option>
                                    <option value="OR">OR</option>
                                </select>
                                <div v-else class="w-16 text-xs text-gray-400 text-center font-mono py-1">WHERE</div>
                                
                                <!-- Field -->
                                <input type="text" v-model="cond.field" placeholder="Field" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                
                                <!-- Operator -->
                                <select v-model="cond.op" class="w-20 text-xs border border-gray-300 rounded px-1 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                    <option value="=">=</option>
                                    <option value=">">></option>
                                    <option value="<"><</option>
                                    <option value=">=">>=</option>
                                    <option value="<="><=</option>
                                    <option value="LIKE">LIKE</option>
                                    <option value="IN">IN</option>
                                </select>
                                
                                <!-- Value -->
                                <input type="text" v-model="cond.value" placeholder="Value" class="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:ring-1 focus:ring-blue-500 outline-none">
                                
                                <!-- Remove -->
                                <button @click="removeCondition(index)" class="text-red-400 hover:text-red-600 p-1 rounded hover:bg-red-50"><i class="ri-delete-bin-line"></i></button>
                            </div>
                            
                            <button @click="addCondition" class="text-xs text-blue-600 hover:text-blue-700 flex items-center mt-2 px-2 py-1 rounded hover:bg-blue-50">
                                <i class="ri-add-circle-line mr-1"></i> Add Condition
                            </button>
                        </div>
                        
                        <div class="flex justify-end space-x-2 pt-2 border-t border-gray-100">
                            <button @click="queryConditions = [{ logic: 'AND', field: '', op: '=', value: '' }]" class="px-3 py-1.5 text-xs text-gray-500 hover:bg-gray-100 rounded">Clear</button>
                            <button @click="applyQuery" class="px-3 py-1.5 text-xs bg-blue-600 text-white hover:bg-blue-700 rounded shadow-sm">Apply Filter</button>
                        </div>
                    </div>

                    <div class="h-4 border-l border-gray-300 mx-1"></div>
                    <button @click="openNewModal" class="px-3 py-1 bg-green-50 text-green-600 rounded text-xs hover:bg-green-100 border border-green-200 h-8">
                        <i class="ri-add-line mr-1"></i> New
                    </button>
                    <button @click="openEditModal" class="px-3 py-1 bg-gray-50 text-gray-600 rounded text-xs hover:bg-gray-100 border border-gray-200 h-8">
                        <i class="ri-edit-box-line mr-1"></i> Edit
                    </button>
                    <button @click="handleDeleteRow" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="Delete Row">
                        <i class="ri-delete-row"></i>
                    </button>
                    <button v-if="dataStore.currentNode.type === 'table'" @click="handleRemoveCurrentNode" class="px-3 py-1 bg-white border border-red-200 text-red-600 rounded text-xs hover:bg-red-50 h-8" title="Drop Table">
                        <i class="ri-delete-bin-2-line"></i>
                    </button>
                </div>
            </div>
            
            <div v-if="dataStore.currentNode.type === 'ts' || dataStore.currentNode.type === 'point'" class="h-64 w-full mb-4 border border-gray-100 rounded shrink-0">
                <div ref="chartRef" class="w-full h-full"></div>
            </div>

            <div class="flex-1 overflow-auto border border-gray-200 rounded">
                 <table class="w-full text-xs text-left">
                       <thead class="bg-gray-50 text-gray-500 sticky top-0">
                           <tr v-if="['ts', 'point'].includes(dataStore.currentNode.type)">
                               <th class="px-4 py-2 border-b">Timestamp</th><th class="px-4 py-2 border-b">Value</th>
                           </tr>
                           <tr v-else>
                               <th v-for="(val, key) in tableData.length > 0 ? tableData[0] : {}" :key="key" class="px-4 py-2 border-b capitalize">{{ key }}</th>
                           </tr>
                       </thead>
                       <tbody class="divide-y divide-gray-100">
                           <template v-if="['ts', 'point'].includes(dataStore.currentNode.type)">
                               <tr v-for="row in tsData" :key="row.time" class="hover:bg-gray-50">
                                   <td class="px-4 py-2 font-mono text-blue-600">{{ row.time }}</td>
                                   <td class="px-4 py-2">{{ row.value }}</td>
                               </tr>
                           </template>
                           <template v-else>
                               <tr v-if="tableData.length === 0" class="text-gray-400 text-center italic p-4">
                                   <td colspan="100%" class="py-8">No data available or empty group</td>
                               </tr>
                               <tr v-else v-for="row in tableData" :key="row.id" 
                                   @click="selectRow(row.id)"
                                   @dblclick="openEditModal"
                                   :class="selectedRowId === row.id ? 'bg-blue-50 border-l-4 border-blue-500' : 'hover:bg-gray-50 border-l-4 border-transparent'"
                                   class="cursor-pointer transition-colors">
                                   <td v-for="(val, key) in row" :key="key" class="px-4 py-2">{{ val }}</td>
                               </tr>
                           </template>
                       </tbody>
                   </table>
            </div>
         </div>
     </div>
     
     <!-- Edit Modal -->
     <div v-if="showEditModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4">{{ isEditMode ? 'Edit Record' : 'New Record' }}</h3>
            <div class="space-y-3">
                <div v-for="(val, key) in editingRow" :key="key">
                    <label class="block text-xs font-medium text-gray-700 mb-1 capitalize">{{ key }}</label>
                    <input v-model="editingRow[key]" :disabled="key === 'id'" type="text" class="w-full text-sm border border-gray-300 rounded px-2 py-1.5 focus:ring-1 focus:ring-blue-500 outline-none disabled:bg-gray-100 disabled:text-gray-500">
                </div>
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="showEditModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="saveRow" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Save</button>
            </div>
        </div>
     </div>
  </div>
</template>
