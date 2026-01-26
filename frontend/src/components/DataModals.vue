<script setup>
import { useDataStore } from '../stores/data'
import { reactive, ref, computed } from 'vue'

const dataStore = useDataStore()

// --- Local State for Forms ---
const addSourceForm = reactive({
    type: 'influx',
    name: '',
    host: '127.0.0.1',
    port: '8086',
    username: '',
    password: '',
    database: 'postgres', // For PG
    schema: 'public' // For PG
})

const maintenanceForm = reactive({
    type: 'delete',
    startTime: '2025-01-01 10:00',
    endTime: '2025-01-01 10:30'
})

const exportForm = reactive({
    range: '24h',
    format: 'csv'
})

const isConnecting = ref(false)

const handleFile = (e) => {
    dataStore.importForm.file = e.target.files[0]
    // Mock Mapping Logic
    if (dataStore.importType === 'ts') {
        dataStore.importForm.mapping = [
            { col: 'Time', target: 'timestamp', type: 'Timestamp' },
            { col: 'Value', target: 'value', type: 'Float' },
            { col: 'Tag', target: 'tag', type: 'String' }
        ]
    }
}

// --- Action Handlers ---

const handleTestConnection = async () => {
    isConnecting.value = true
    try {
        await dataStore.testConnection({ ...addSourceForm })
        alert('Connection Successful!')
    } catch (e) {
        alert('Connection Failed: ' + e)
    } finally {
        isConnecting.value = false
    }
}

const handleAddSource = () => {
    if (!addSourceForm.name) {
        alert('Please enter a source name.')
        return
    }
    // Call Store Action
    dataStore.addSource({ ...addSourceForm })
    
    // Reset Form & Close
    addSourceForm.name = ''
    addSourceForm.username = ''
    addSourceForm.password = ''
    dataStore.showAddSourceModal = false
}

const handleRemoveSource = () => {
    if (!selectedSourceId.value) {
        alert('Please select a data source to remove.')
        return
    }
    if (confirm(`Are you sure you want to remove ${selectedSourceId.value}?`)) {
        dataStore.removeSource(selectedSourceId.value)
        dataStore.showRemoveSourceModal = false
        selectedSourceId.value = ''
    }
}

const selectedSourceId = ref('')
const selectedDetailSourceId = ref('')

const selectedDetailSource = computed(() => {
    return dataStore.dataSourceTree.find(s => s.id === selectedDetailSourceId.value)
})

const executeImport = () => {
    const targetSource = dataStore.importForm.source
    if (!targetSource) {
        alert('Please select a target data source.')
        return
    }

    // Simulate Parsing Delay
    setTimeout(() => {
        // Generate Mock Children based on import type
        const newChildren = []
        const timestamp = new Date().getTime()
        
        if (dataStore.importType === 'ts') {
            newChildren.push(
                { id: `${targetSource}.import_${timestamp}.speed`, name: `import_speed_${timestamp.toString().slice(-4)}`, type: 'point' },
                { id: `${targetSource}.import_${timestamp}.temp`, name: `import_temp_${timestamp.toString().slice(-4)}`, type: 'point' }
            )
        } else {
             if (dataStore.importForm.autoCreateTable) {
                 newChildren.push(
                    { id: `${targetSource}.public.auto_table_${timestamp}`, name: `auto_table_${timestamp.toString().slice(-4)}`, type: 'table' }
                )
             } else {
                 // Assume appending to existing table, so no new child node in tree, just data update
                 alert(`Data appended to existing tables in ${targetSource}`)
                 dataStore.showImportModal = false
                 return
             }
        }

        dataStore.addChildrenToSource(targetSource, newChildren)
        dataStore.showImportModal = false
        alert('Import Successful!')
    }, 800)
}

const handleExport = () => {
    const content = JSON.stringify({
        source: dataStore.currentNode.id,
        exportedAt: new Date().toISOString(),
        config: exportForm,
        data: "Mock Data Payload..."
    }, null, 2)
    
    const blob = new Blob([content], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${dataStore.currentNode.id || 'export'}_${exportForm.range}.${exportForm.format}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    
    dataStore.showExportModal = false
}

const handleMaintenance = () => {
    if (confirm('WARNING: This operation is irreversible. Are you sure?')) {
        dataStore.deleteRange(dataStore.currentNode.id, { start: maintenanceForm.startTime, end: maintenanceForm.endTime })
        alert(`Successfully deleted data from ${maintenanceForm.startTime} to ${maintenanceForm.endTime}`)
        dataStore.showMaintenanceModal = false
    }
}
</script>

<template>
  <div class="relative z-[100]">
     <!-- Import Modal -->
     <div v-if="dataStore.showImportModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[600px] flex flex-col max-h-[80vh]">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">{{ dataStore.importType === 'ts' ? 'Time Series Import Wizard' : 'Structured Data Import Wizard' }}</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showImportModal = false"></i>
             </div>
             
             <div class="p-6 flex-1 overflow-y-auto space-y-6">
                 <!-- Step 0: Select Import Type -->
                 <div v-if="dataStore.importStep === 1">
                     <h4 class="text-sm font-bold text-gray-700 mb-4">1. Select Import Type</h4>
                     <div class="grid grid-cols-2 gap-4">
                         <div @click="dataStore.importType = 'ts'" 
                              :class="dataStore.importType === 'ts' ? 'border-blue-500 bg-blue-50 ring-1 ring-blue-500' : 'border-gray-200 hover:border-blue-300 hover:bg-gray-50'"
                              class="cursor-pointer border rounded-lg p-4 flex flex-col items-center justify-center transition-all h-32">
                             <div class="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center text-blue-600 mb-2">
                                 <i class="ri-pulse-line text-xl"></i>
                             </div>
                             <span class="font-bold text-sm text-gray-800">Time Series Data</span>
                             <span class="text-[10px] text-gray-500 mt-1">For InfluxDB / IoT Data</span>
                         </div>
                         <div @click="dataStore.importType = 'struct'"
                              :class="dataStore.importType === 'struct' ? 'border-green-500 bg-green-50 ring-1 ring-green-500' : 'border-gray-200 hover:border-green-300 hover:bg-gray-50'"
                              class="cursor-pointer border rounded-lg p-4 flex flex-col items-center justify-center transition-all h-32">
                             <div class="w-10 h-10 bg-green-100 rounded-full flex items-center justify-center text-green-600 mb-2">
                                 <i class="ri-table-line text-xl"></i>
                             </div>
                             <span class="font-bold text-sm text-gray-800">Structured Data</span>
                             <span class="text-[10px] text-gray-500 mt-1">For PostgreSQL / MySQL</span>
                         </div>
                     </div>
                 </div>

                 <!-- Step 1: Select Target Source (Renamed to Step 2 logic but keeping variable consistent or increasing step count) -->
                 <!-- Let's increase step count logic. Need to update next/back buttons too. -->
                 <!-- Actually, simpler to keep Step 1 as Type Selection and push others +1 -->
                 
                 <div v-if="dataStore.importStep === 2">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">2. Select Target Source</h4>
                     <select v-model="dataStore.importForm.source" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="">Select Data Source...</option>
                         <!-- Filter sources based on import type -->
                         <option v-for="source in dataStore.dataSourceTree.filter(s => (dataStore.importType === 'ts' ? s.type === 'ts' : s.type === 'rel'))" 
                                 :key="source.id" :value="source.id">
                             {{ source.name }} ({{ source.type === 'ts' ? 'InfluxDB' : 'PostgreSQL' }})
                         </option>
                     </select>
                 </div>

                 <!-- Step 3 -->
                 <div v-if="dataStore.importStep === 3">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">3. Upload File</h4>
                     <div class="border-2 border-dashed border-gray-300 rounded-lg h-32 flex flex-col items-center justify-center bg-gray-50">
                         <input type="file" @change="handleFile" class="hidden" id="fileUpload">
                         <label for="fileUpload" class="cursor-pointer flex flex-col items-center">
                             <i class="ri-upload-cloud-2-line text-3xl text-gray-400"></i>
                             <span class="text-xs text-gray-500 mt-2">{{ dataStore.importForm.file ? dataStore.importForm.file.name : 'Click to upload ' + (dataStore.importType === 'ts' ? 'CSV' : 'Excel/SQL') }}</span>
                         </label>
                     </div>
                 </div>

                 <!-- Step 4 (TS) -->
                 <div v-if="dataStore.importStep === 4 && dataStore.importType === 'ts'">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">4. Field Mapping & Timestamp</h4>
                     <div class="mb-4">
                         <label class="block text-xs font-bold text-gray-600 mb-1">Timestamp Column</label>
                         <select class="w-full border border-gray-300 rounded px-2 py-1 text-xs">
                             <option>Time</option>
                             <option>Date</option>
                             <option>Timestamp</option>
                         </select>
                     </div>
                     <table class="w-full text-xs text-left border border-gray-200">
                         <thead class="bg-gray-50"><tr><th class="p-2">CSV Column</th><th class="p-2">Target Measurement</th><th class="p-2">Type</th></tr></thead>
                         <tbody>
                             <tr v-for="(m, i) in dataStore.importForm.mapping" :key="i" class="border-t border-gray-100">
                                 <td class="p-2">{{ m.col }}</td>
                                 <td class="p-2"><input v-model="m.target" class="border border-gray-300 rounded w-full px-1"></td>
                                 <td class="p-2">{{ m.type }}</td>
                             </tr>
                         </tbody>
                     </table>
                 </div>

                 <!-- Step 4 (Struct) -->
                 <div v-if="dataStore.importStep === 4 && dataStore.importType === 'struct'">
                     <h4 class="text-sm font-bold text-gray-700 mb-2">4. Import Strategy</h4>
                     <div class="space-y-4">
                         <div>
                             <label class="flex items-center text-sm font-bold text-gray-700 mb-2">
                                 <input type="checkbox" v-model="dataStore.importForm.autoCreateTable" class="mr-2"> 
                                 Auto Create Table if not exists
                             </label>
                             <p class="text-xs text-gray-500 ml-6">If enabled, system will infer schema from the first row of Excel file.</p>
                         </div>
                         <div class="border-t border-gray-100 pt-4">
                            <h5 class="text-xs font-bold text-gray-600 mb-2">Conflict Strategy (Primary Key)</h5>
                            <div class="space-y-2">
                                <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="update" class="mr-2"> Upsert (Update if exists)</label>
                                <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="skip" class="mr-2"> Skip duplicates</label>
                                <label class="flex items-center text-sm text-gray-600"><input type="radio" v-model="dataStore.importForm.conflictStrategy" value="error" class="mr-2"> Abort on error</label>
                            </div>
                         </div>
                     </div>
                 </div>
             </div>

             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button v-if="dataStore.importStep > 1" @click="dataStore.importStep--" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Back</button>
                 <button v-if="dataStore.importStep < 4" @click="dataStore.importStep++" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Next</button>
                 <button v-if="dataStore.importStep === 4" @click="executeImport" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700">Finish</button>
             </div>
         </div>
     </div>

     <!-- Add Source Modal -->
     <div v-if="dataStore.showAddSourceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">Add New Data Source</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showAddSourceModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Source Type</label>
                     <select v-model="addSourceForm.type" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="influx">InfluxDB (Time-Series)</option>
                         <option value="postgres">PostgreSQL (Relational)</option>
                         <option value="mysql">MySQL (Relational)</option>
                     </select>
                 </div>
                 
                 <!-- Dynamic Fields based on Type -->
                 <div v-if="addSourceForm.type === 'influx'">
                     <div class="mb-4">
                         <label class="block text-xs font-bold text-gray-700 mb-1">Storage Group (Default)</label>
                         <input type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="root.default">
                     </div>
                 </div>
                 <div v-if="['postgres', 'mysql'].includes(addSourceForm.type)">
                     <div class="grid grid-cols-2 gap-4 mb-4">
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">Database Name</label>
                             <input v-model="addSourceForm.database" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                         <div>
                             <label class="block text-xs font-bold text-gray-700 mb-1">Schema</label>
                             <input v-model="addSourceForm.schema" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         </div>
                     </div>
                 </div>

                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Alias Name</label>
                     <input v-model="addSourceForm.name" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="e.g. factory_db_01">
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">Host / IP</label>
                         <input v-model="addSourceForm.host" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="127.0.0.1">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">Port</label>
                         <input v-model="addSourceForm.port" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" :placeholder="addSourceForm.type === 'influx' ? '8086' : '5432'">
                     </div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">Username</label>
                         <input v-model="addSourceForm.username" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">Password</label>
                         <input v-model="addSourceForm.password" type="password" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                     </div>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-between space-x-2">
                 <button @click="handleTestConnection" :disabled="isConnecting" class="px-4 py-2 border border-blue-200 bg-blue-50 text-blue-600 rounded text-sm hover:bg-blue-100">
                     <i v-if="isConnecting" class="ri-loader-4-line animate-spin mr-1"></i>
                     Test Connection
                 </button>
                 <div class="flex space-x-2">
                    <button @click="dataStore.showAddSourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                    <button @click="handleAddSource" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Save & Register</button>
                 </div>
             </div>
         </div>
     </div>

     <!-- Remove Source Modal -->
     <div v-if="dataStore.showRemoveSourceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[400px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800 text-red-600">Remove Data Source</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showRemoveSourceModal = false"></i>
             </div>
             <div class="p-6 text-sm text-gray-600 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Select Data Source</label>
                     <select v-model="selectedSourceId" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="" disabled>-- Select a source --</option>
                         <option v-for="source in dataStore.dataSourceTree.filter(n => ['ts', 'rel'].includes(n.type))" :key="source.id" :value="source.id">
                             {{ source.name }}
                         </option>
                     </select>
                 </div>
                 <div class="p-3 bg-red-50 rounded border border-red-100 text-red-700 text-xs">
                     <i class="ri-alert-line mr-1"></i> Warning: This action will disconnect the data source from IGinX. Physical data will not be deleted.
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showRemoveSourceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                 <button @click="handleRemoveSource" :disabled="!selectedSourceId" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50">Confirm Remove</button>
             </div>
         </div>
     </div>

     <!-- Source Details Modal -->
     <div v-if="dataStore.showSourceDetailsModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">Data Source Details</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showSourceDetailsModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div class="mb-4">
                     <label class="block text-xs font-bold text-gray-700 mb-1">Select Data Source</label>
                     <select v-model="selectedDetailSourceId" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="" disabled>-- Select a source --</option>
                         <option v-for="source in dataStore.dataSourceTree.filter(n => ['ts', 'rel'].includes(n.type))" :key="source.id" :value="source.id">
                             {{ source.name }}
                         </option>
                     </select>
                 </div>
                 
                 <div v-if="selectedDetailSource" class="space-y-4">
                     <div class="flex items-center space-x-4 mb-6">
                         <div class="w-12 h-12 bg-blue-100 rounded-full flex items-center justify-center text-blue-600">
                             <i class="ri-database-2-line text-2xl"></i>
                         </div>
                         <div>
                             <h4 class="font-bold text-lg text-gray-800">{{ selectedDetailSource.name }}</h4>
                             <span class="text-xs px-2 py-0.5 bg-green-100 text-green-700 rounded-full border border-green-200">Connected</span>
                         </div>
                     </div>
                     <div class="grid grid-cols-2 gap-y-4 text-sm">
                         <div class="text-gray-500">Type</div>
                         <div class="font-mono text-gray-800 capitalize">{{ selectedDetailSource.type === 'ts' ? 'Time Series (InfluxDB)' : 'Relational (PostgreSQL)' }}</div>
                         <div class="text-gray-500">Node ID</div>
                         <div class="font-mono text-gray-800">{{ selectedDetailSource.id }}</div>
                         <div class="text-gray-500">Storage Size</div>
                         <div class="font-mono text-gray-800">45.2 GB</div>
                         <div class="text-gray-500">Uptime</div>
                         <div class="font-mono text-gray-800">14d 2h 12m</div>
                     </div>
                 </div>
                 <div v-else class="text-center text-gray-400 py-8">
                     <i class="ri-search-line text-4xl mb-2"></i>
                     <p>Please select a data source to view details.</p>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                 <button @click="dataStore.showSourceDetailsModal = false" class="px-4 py-2 bg-gray-100 text-gray-700 rounded text-sm hover:bg-gray-200">Close</button>
             </div>
         </div>
     </div>

     <!-- Export Modal -->
     <div v-if="dataStore.showExportModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800">Export Data</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showExportModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Target Node</label>
                     <div class="w-full border border-gray-300 rounded px-3 py-2 text-sm bg-gray-50 text-gray-500">
                         {{ dataStore.currentNode.id || 'No Selection' }}
                     </div>
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Time Range</label>
                     <select v-model="exportForm.range" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="24h">Last 24 Hours</option>
                         <option value="7d">Last 7 Days</option>
                         <option value="30d">Last 30 Days</option>
                         <option value="custom">Custom Range...</option>
                     </select>
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Format</label>
                     <div class="flex space-x-4 mt-1">
                         <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="csv" class="mr-2"> CSV (Wide)</label>
                         <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="json" class="mr-2"> JSON</label>
                         <label class="flex items-center text-sm"><input type="radio" v-model="exportForm.format" value="sql" class="mr-2"> SQL Dump</label>
                     </div>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showExportModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                 <button @click="handleExport" class="px-4 py-2 bg-purple-600 text-white rounded text-sm hover:bg-purple-700">Export</button>
             </div>
         </div>
     </div>
     
     <!-- Maintenance Modal -->
     <div v-if="dataStore.showMaintenanceModal" class="fixed inset-0 flex items-center justify-center bg-black/50 backdrop-blur-sm">
         <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
             <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                 <h3 class="font-bold text-gray-800 text-red-600">Data Maintenance</h3>
                 <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="dataStore.showMaintenanceModal = false"></i>
             </div>
             <div class="p-6 space-y-4">
                 <div class="bg-yellow-50 p-3 rounded text-yellow-800 text-xs border border-yellow-200">
                     <i class="ri-alert-line"></i> Warning: Deletion operations are permanent and cannot be undone.
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Target Node</label>
                     <div class="font-mono text-sm text-gray-800">{{ dataStore.currentNode.id }}</div>
                 </div>
                 <div class="grid grid-cols-2 gap-4">
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">Start Time</label>
                         <input v-model="maintenanceForm.startTime" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="YYYY-MM-DD HH:mm">
                     </div>
                     <div>
                         <label class="block text-xs font-bold text-gray-700 mb-1">End Time</label>
                         <input v-model="maintenanceForm.endTime" type="text" class="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="YYYY-MM-DD HH:mm">
                     </div>
                 </div>
                 <div>
                     <label class="block text-xs font-bold text-gray-700 mb-1">Operation</label>
                     <select v-model="maintenanceForm.type" class="w-full border border-gray-300 rounded px-3 py-2 text-sm">
                         <option value="delete">Delete Data Range</option>
                         <option value="fill">Linear Interpolation (Fill)</option>
                     </select>
                 </div>
             </div>
             <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                 <button @click="dataStore.showMaintenanceModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                 <button @click="handleMaintenance" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700">Execute</button>
             </div>
         </div>
     </div>
  </div>
</template>
