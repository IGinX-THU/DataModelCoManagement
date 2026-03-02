<script setup>
import { ref } from 'vue'

const activeTab = ref('datasources')

const dataSources = ref([
    { id: 1, alias: 'influx_local', type: 'InfluxDB', ip: '192.168.1.10', port: '8086', status: 'Connected', createTime: '2025-01-01 10:00' },
    { id: 2, alias: 'pg_meta_db', type: 'PostgreSQL', ip: '192.168.1.12', port: '5432', status: 'Connected', createTime: '2025-01-02 14:30' },
    { id: 3, alias: 'iotdb_edge', type: 'IoTDB', ip: '10.0.0.5', port: '6667', status: 'Disconnected', createTime: '2025-01-05 09:15' }
])

const showModal = ref(false)
const form = ref({ type: 'InfluxDB', alias: '', ip: '', port: '' })

const openModal = () => {
    form.value = { type: 'InfluxDB', alias: '', ip: '', port: '' }
    showModal.value = true
}

const saveSource = () => {
    if (!form.value.alias) return alert('Alias is required')
    dataSources.value.push({
        id: Date.now(),
        ...form.value,
        status: 'Connected',
        createTime: new Date().toISOString().slice(0, 16).replace('T', ' ')
    })
    showModal.value = false
}
</script>

<template>
  <div class="h-full flex bg-white rounded-lg overflow-hidden border border-gray-200">
    <!-- Left: Settings Nav -->
    <div class="w-48 bg-gray-50 border-r border-gray-200 flex flex-col shrink-0">
        <div class="h-10 border-b border-gray-200 flex items-center px-3 font-bold text-xs text-gray-600 uppercase bg-gray-100">
            Settings
        </div>
        <div class="p-2 space-y-1">
             <div @click="activeTab = 'general'" 
                  :class="activeTab === 'general' ? 'bg-white border border-gray-200 shadow-sm text-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'"
                  class="px-3 py-2 rounded cursor-pointer text-xs font-medium transition-colors border border-transparent">
                  <i class="ri-settings-3-line mr-2"></i> General
             </div>
             <div @click="activeTab = 'datasources'" 
                  :class="activeTab === 'datasources' ? 'bg-white border border-gray-200 shadow-sm text-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'"
                  class="px-3 py-2 rounded cursor-pointer text-xs font-medium transition-colors border border-transparent">
                  <i class="ri-database-2-line mr-2"></i> Data Sources
             </div>
             <div @click="activeTab = 'storage'" 
                  :class="activeTab === 'storage' ? 'bg-white border border-gray-200 shadow-sm text-blue-600' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'"
                  class="px-3 py-2 rounded cursor-pointer text-xs font-medium transition-colors border border-transparent">
                  <i class="ri-hard-drive-line mr-2"></i> Storage Engine
             </div>
        </div>
    </div>

    <!-- Center: Content -->
    <div class="flex-1 bg-white flex flex-col min-w-0">
        <div class="h-10 border-b border-gray-200 flex items-center px-6 shrink-0 font-bold text-gray-700 bg-gray-50/50">
            {{ activeTab === 'datasources' ? 'Data Source Management' : 'System Settings' }}
        </div>
        
        <div v-if="activeTab === 'datasources'" class="p-6">
            <div class="mb-4 flex justify-between items-center">
                <p class="text-xs text-gray-500">Manage heterogeneous data source connections (IGinX Connectors).</p>
                <button @click="openModal" class="bg-blue-600 text-white px-3 py-1.5 rounded text-xs hover:bg-blue-700 shadow-sm flex items-center">
                    <i class="ri-add-line mr-1"></i> Add Source
                </button>
            </div>

            <div class="bg-white rounded border border-gray-200 overflow-hidden shadow-sm">
                <table class="w-full text-xs text-left text-gray-600">
                    <thead class="bg-gray-50 text-gray-500 font-semibold border-b border-gray-200">
                        <tr>
                            <th class="px-4 py-3">Alias</th>
                            <th class="px-4 py-3">Type</th>
                            <th class="px-4 py-3">Connection (IP:Port)</th>
                            <th class="px-4 py-3">Status</th>
                            <th class="px-4 py-3">Created</th>
                            <th class="px-4 py-3 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-100">
                        <tr v-for="source in dataSources" :key="source.id" class="hover:bg-gray-50">
                            <td class="px-4 py-3 font-bold text-gray-800">{{ source.alias }}</td>
                            <td class="px-4 py-3">
                                <span class="bg-gray-100 border border-gray-200 px-1.5 py-0.5 rounded text-[10px]">{{ source.type }}</span>
                            </td>
                            <td class="px-4 py-3 font-mono text-gray-500">{{ source.ip }}:{{ source.port }}</td>
                            <td class="px-4 py-3">
                                <span v-if="source.status === 'Connected'" class="text-green-600 flex items-center"><i class="ri-checkbox-circle-fill mr-1"></i> Connected</span>
                                <span v-else class="text-red-500 flex items-center"><i class="ri-close-circle-fill mr-1"></i> Disconnected</span>
                            </td>
                            <td class="px-4 py-3 text-gray-400">{{ source.createTime }}</td>
                            <td class="px-4 py-3 text-right space-x-2">
                                <button class="text-blue-600 hover:text-blue-800 hover:underline">Edit</button>
                                <button class="text-red-500 hover:text-red-700 hover:underline">Remove</button>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <div v-else class="flex items-center justify-center flex-1 text-gray-400 text-sm">
            Module under construction...
        </div>
    </div>

    <!-- Modal (Simple implementation) -->
    <div v-if="showModal" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center backdrop-blur-sm">
        <div class="bg-white w-96 rounded-lg shadow-xl border border-gray-200">
            <div class="px-4 py-3 border-b border-gray-200 font-bold text-gray-700 flex justify-between bg-gray-50">
                <span>Add Data Source</span>
                <button @click="showModal = false" class="text-gray-400 hover:text-gray-800"><i class="ri-close-line"></i></button>
            </div>
            <div class="p-4 space-y-3">
                 <div>
                    <label class="block text-xs font-bold text-gray-500 mb-1">Type</label>
                    <select v-model="form.type" class="w-full bg-white border border-gray-300 rounded px-2 py-1.5 text-xs text-gray-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-200">
                        <option>InfluxDB</option>
                        <option>IoTDB</option>
                        <option>PostgreSQL</option>
                    </select>
                 </div>
                 <div>
                    <label class="block text-xs font-bold text-gray-500 mb-1">Alias</label>
                    <input v-model="form.alias" type="text" class="w-full bg-white border border-gray-300 rounded px-2 py-1.5 text-xs text-gray-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-200">
                 </div>
                 <div class="grid grid-cols-2 gap-2">
                     <div>
                        <label class="block text-xs font-bold text-gray-500 mb-1">IP</label>
                        <input v-model="form.ip" type="text" class="w-full bg-white border border-gray-300 rounded px-2 py-1.5 text-xs text-gray-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-200">
                     </div>
                     <div>
                        <label class="block text-xs font-bold text-gray-500 mb-1">Port</label>
                        <input v-model="form.port" type="text" class="w-full bg-white border border-gray-300 rounded px-2 py-1.5 text-xs text-gray-700 outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-200">
                     </div>
                 </div>
            </div>
            <div class="px-4 py-3 border-t border-gray-200 bg-gray-50 flex justify-end space-x-2">
                <button @click="showModal = false" class="px-3 py-1.5 rounded text-xs text-gray-600 hover:text-gray-900 border border-gray-300 hover:bg-gray-100">Cancel</button>
                <button @click="saveSource" class="px-3 py-1.5 rounded text-xs bg-blue-600 text-white hover:bg-blue-700 shadow-sm">Connect</button>
            </div>
        </div>
    </div>
  </div>
</template>
