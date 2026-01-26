<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useAssociationStore } from '../stores/association'
import { useModelStore } from '../stores/model'
import { useDataStore } from '../stores/data'

const associationStore = useAssociationStore()
const modelStore = useModelStore()
const dataStore = useDataStore()

const selectedRule = ref(null)
const wizardStep = ref(1)
const isEditing = ref(false) // Track if we are editing an existing rule
const editingRuleId = ref(null)
const newRule = reactive({ name: '', modelId: '', bindings: {}, results: {} })
const showRunModal = ref(false)
const runConfig = reactive({ startTime: '', endTime: '' })

// Data Selector State
const showDataSelector = ref(false)
const selectorTarget = reactive({ type: '', key: '' })

const openDataSelector = (type, key) => {
    selectorTarget.type = type
    selectorTarget.key = key
    showDataSelector.value = true
}

const handleDataSelect = (node) => {
    // Construct path: assuming node.id is the full path or unique identifier
    // In a real app, we might need to construct the path from parents if ID isn't the path
    // Here we assume ID is sufficient or name for display
    const path = node.id || node.name 
    
    if (selectorTarget.type === 'input') {
        newRule.bindings[selectorTarget.key] = path
    } else {
        newRule.results[selectorTarget.key] = path
    }
    showDataSelector.value = false
}

// Helper to toggle expand in the selector tree (independent of main sidebar)
const toggleSelectorExpand = (node) => {
    if (node.children) node.selectorExpanded = !node.selectorExpanded
}

// Get models from store
const availableModels = computed(() => modelStore.models)
const selectedModelMeta = ref(null)

// Get tasks for selected rule
const selectedTask = ref(null)

const selectTask = (task) => {
    selectedTask.value = task
}

const recentTasks = computed(() => {
    if (!selectedRule.value) return []
    return associationStore.tasks
        .filter(t => t.ruleId === selectedRule.value.id)
        .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
})

const toggleStatus = (rule) => {
    associationStore.toggleRule(rule.id)
}

// Watch global wizard state to reset form when opened
watch(() => associationStore.showWizard, (val) => {
    if (val && !isEditing.value) {
        openWizard()
    }
})

// --- Wizard Logic ---
const openWizard = () => {
    isEditing.value = false
    editingRuleId.value = null
    wizardStep.value = 1
    newRule.name = ''
    newRule.modelId = ''
    newRule.bindings = {}
    newRule.results = {}
    selectedModelMeta.value = null
    associationStore.showWizard = true
}

const editRule = (rule) => {
    isEditing.value = true
    editingRuleId.value = rule.id
    wizardStep.value = 2 // Jump to config directly
    
    newRule.name = rule.name
    newRule.modelId = rule.modelId
    newRule.bindings = { ...rule.bindings }
    newRule.results = { ...rule.results }
    
    // Find model metadata
    const model = modelStore.models.find(m => m.id === rule.modelId)
    if (model) {
        // Mock using latest version schema for now, ideally should find specific version
        const ver = model.history.find(v => v.version === rule.modelVersion) || model.history[model.history.length-1]
        selectedModelMeta.value = {
            ...model,
            inputs: ver.inputs.map(i => i.name),
            inputTypes: ver.inputs.reduce((acc, i) => ({...acc, [i.name]: i.type}), {}), // Store types for hints
            outputs: ver.outputs.map(o => o.name),
            version: ver.version
        }
    }
    
    associationStore.showWizard = true
}

const handleCopyRule = (rule) => {
    const newId = associationStore.copyRule(rule.id)
    alert(`Rule Copied! New ID: ${newId}`)
}

const handleDeleteRule = (rule) => {
    if (confirm(`Are you sure you want to delete rule "${rule.name}"?`)) {
        const res = associationStore.deleteRule(rule.id)
        if (res.success) {
            if (selectedRule.value?.id === rule.id) selectedRule.value = null
        } else {
            alert(res.msg)
        }
    }
}

const selectModel = (model) => {
    newRule.modelId = model.id
    selectedModelMeta.value = model
    
    // Get inputs/outputs from the latest version of the model
    const latestVer = model.history && model.history.length > 0 
        ? model.history[model.history.length - 1] 
        : { inputs: [], outputs: [] }
        
    latestVer.inputs.forEach(i => newRule.bindings[i.name] = '')
    latestVer.outputs.forEach(o => newRule.results[o.name] = '')
    
    // Store metadata for the wizard UI to display
    selectedModelMeta.value = {
        ...model,
        inputs: latestVer.inputs.map(i => i.name),
        inputTypes: latestVer.inputs.reduce((acc, i) => ({...acc, [i.name]: i.type}), {}),
        outputs: latestVer.outputs.map(o => o.name),
        version: latestVer.version
    }
    
    wizardStep.value = 2
}

const saveRule = () => {
    if (isEditing.value) {
        associationStore.updateRule(editingRuleId.value, {
            name: newRule.name,
            bindings: { ...newRule.bindings },
            results: { ...newRule.results },
            updateTime: new Date().toISOString().slice(0, 16).replace('T', ' '),
            inputCount: Object.keys(newRule.bindings).length,
            outputCount: Object.keys(newRule.results).length
        })
    } else {
        const ruleId = 'RULE_' + Date.now()
        const rule = {
            id: ruleId,
            name: newRule.name,
            desc: `Rule for ${selectedModelMeta.value.name}`,
            modelId: newRule.modelId,
            modelName: selectedModelMeta.value.name,
            modelVersion: selectedModelMeta.value.version,
            bindings: { ...newRule.bindings },
            results: { ...newRule.results },
            enabled: true,
            updateTime: new Date().toISOString().slice(0, 16).replace('T', ' '),
            isRunning: false,
            inputCount: Object.keys(newRule.bindings).length,
            outputCount: Object.keys(newRule.results).length
        }
        associationStore.addRule(rule)
    }
    associationStore.showWizard = false
}

// --- Run Logic ---
const openRunModal = () => {
    if (!selectedRule.value) return
    runConfig.startTime = '2025-01-01 10:00:00'
    runConfig.endTime = '2025-01-01 10:30:00'
    showRunModal.value = true
}

const executeTask = () => {
    if (!selectedRule.value) return
    
    // Validate Time
    const start = new Date(runConfig.startTime).getTime()
    const end = new Date(runConfig.endTime).getTime()
    if (isNaN(start) || isNaN(end) || end <= start) {
        alert('Invalid Time Range: End time must be after Start time.')
        return
    }

    const taskId = associationStore.createTask(selectedRule.value.id, {
        startTime: runConfig.startTime,
        endTime: runConfig.endTime
    })
    
    // Update local UI state if needed, but store handles the task
    selectedRule.value.isRunning = true 
    // In a real app, we would watch the task status from the store
    
    alert(`Task Triggered!\nID: ${taskId}\nRange: ${runConfig.startTime} - ${runConfig.endTime}`)
    showRunModal.value = false
}
</script>

<template>
  <div class="h-full flex bg-white rounded-lg overflow-hidden border border-gray-200 relative">
    
    <!-- Rule Wizard Modal -->
    <div v-if="associationStore.showWizard" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[600px] flex flex-col max-h-[80vh]">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 class="font-bold text-gray-800">Create Association Rule</h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="associationStore.showWizard = false"></i>
            </div>
            
            <div class="p-6 flex-1 overflow-y-auto space-y-6">
                <!-- Step 1: Select Model -->
                <div v-if="wizardStep === 1">
                    <h4 class="text-sm font-bold text-gray-700 mb-2">Step 1: Select Algorithm Model</h4>
                    <div v-if="availableModels.length === 0" class="text-center text-gray-400 py-8">
                        No models available. Please upload models in the Model Assets view first.
                    </div>
                    <div class="space-y-2">
                        <div v-for="model in availableModels" :key="model.id" 
                             @click="selectModel(model)"
                             class="border border-gray-200 p-3 rounded cursor-pointer hover:bg-blue-50 hover:border-blue-300 flex justify-between items-center transition-all">
                            <div>
                                <div class="font-bold text-sm text-gray-800">{{ model.name }}</div>
                                <div class="text-xs text-gray-500">{{ model.version }} | Type: {{ model.type }}</div>
                            </div>
                            <i class="ri-arrow-right-s-line text-gray-400"></i>
                        </div>
                    </div>
                </div>

                <!-- Step 2: Bind Data -->
                <div v-if="wizardStep === 2">
                    <h4 class="text-sm font-bold text-gray-700 mb-2">Step 2: Data Binding & Config</h4>
                    
                    <div class="mb-4">
                        <label class="block text-xs font-bold text-gray-500 mb-1">Rule Name</label>
                        <input v-model="newRule.name" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                    </div>

                    <div class="bg-gray-50 p-3 rounded border border-gray-100 mb-4">
                        <div class="text-xs font-bold text-gray-500 mb-2 uppercase">Input Mapping (Data Source)</div>
                        <div v-for="input in selectedModelMeta.inputs" :key="input" class="flex items-center mb-2 last:mb-0">
                            <div class="w-32 flex flex-col items-end mr-3">
                                <span class="text-xs font-mono text-gray-600">{{ input }}</span>
                                <span v-if="selectedModelMeta.inputTypes && selectedModelMeta.inputTypes[input]" class="text-[10px] text-gray-400">({{ selectedModelMeta.inputTypes[input] }})</span>
                            </div>
                            <i class="ri-link-m text-gray-400 mr-3"></i>
                            <input v-model="newRule.bindings[input]" placeholder="Select Data Source" class="flex-1 border border-gray-300 rounded px-2 py-1 text-xs cursor-not-allowed bg-gray-100" readonly>
                            <button @click="openDataSelector('input', input)" class="ml-2 px-2 py-1 bg-blue-50 text-blue-600 border border-blue-200 rounded text-xs hover:bg-blue-100">Select</button>
                        </div>
                    </div>

                    <div class="bg-gray-50 p-3 rounded border border-gray-100">
                        <div class="text-xs font-bold text-gray-500 mb-2 uppercase">Result Mapping (Output Destination)</div>
                        <div v-for="output in selectedModelMeta.outputs" :key="output" class="flex items-center mb-2 last:mb-0">
                            <span class="w-24 text-xs font-mono text-gray-600 text-right mr-3">{{ output }}</span>
                            <i class="ri-arrow-right-line text-gray-400 mr-3"></i>
                            <input v-model="newRule.results[output]" placeholder="Result Destination" class="flex-1 border border-gray-300 rounded px-2 py-1 text-xs cursor-not-allowed bg-gray-100" readonly>
                             <button @click="openDataSelector('output', output)" class="ml-2 px-2 py-1 bg-blue-50 text-blue-600 border border-blue-200 rounded text-xs hover:bg-blue-100">Select</button>
                        </div>
                    </div>
                </div>
            </div>

            <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                <button v-if="wizardStep === 2" @click="wizardStep = 1" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Back</button>
                <button v-if="wizardStep === 2" @click="saveRule" :disabled="!newRule.name" class="px-4 py-2 bg-green-600 text-white rounded text-sm hover:bg-green-700 disabled:opacity-50">{{ isEditing ? 'Update Rule' : 'Create Rule' }}</button>
            </div>
        </div>
    </div>

    <!-- Data Selector Modal -->
    <div v-if="showDataSelector" class="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[500px] h-[600px] flex flex-col">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 class="font-bold text-gray-800">Select {{ selectorTarget.type === 'input' ? 'Data Source' : 'Output Destination' }}</h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="showDataSelector = false"></i>
            </div>
            <div class="flex-1 overflow-y-auto p-4">
                 <div v-for="source in dataStore.dataSourceTree" :key="source.id" class="mb-2">
                     <!-- Root Node -->
                     <div class="flex items-center px-2 py-1.5 hover:bg-blue-50 cursor-pointer rounded select-none group"
                          @click="toggleSelectorExpand(source)">
                         <i :class="source.selectorExpanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-xs"></i>
                         <i :class="source.type === 'ts' ? 'ri-database-2-fill text-blue-500' : 'ri-server-fill text-indigo-500'" class="mr-2 text-lg"></i>
                         <span class="text-sm font-medium text-gray-700">{{ source.name }}</span>
                     </div>
                     
                     <!-- Children -->
                     <div v-show="source.selectorExpanded" class="ml-6 pl-2 border-l border-gray-200 mt-1 space-y-1">
                         <template v-for="child in source.children" :key="child.id">
                             <!-- Group/Schema Node -->
                             <div v-if="['group', 'schema'].includes(child.type)" 
                                  class="select-none">
                                  <div class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors hover:bg-gray-100"
                                       @click="toggleSelectorExpand(child)">
                                      <i :class="child.selectorExpanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-[10px]"></i>
                                      <i :class="child.type === 'group' ? 'ri-folder-3-line text-yellow-500' : 'ri-layout-grid-line text-orange-500'" class="mr-2"></i>
                                      <span class="truncate font-medium">{{ child.name }}</span>
                                  </div>
                                  
                                  <!-- Grandchildren (Leafs) -->
                                  <div v-show="child.selectorExpanded" class="ml-4 pl-2 border-l border-gray-200 mt-1">
                                     <div v-for="grandChild in child.children" :key="grandChild.id"
                                          @click="handleDataSelect(grandChild)"
                                          class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors text-gray-600 hover:bg-blue-50 hover:text-blue-600">
                                         <i :class="source.type === 'ts' ? 'ri-focus-2-line text-cyan-500' : 'ri-table-line text-green-500'" class="mr-2 text-xs"></i>
                                         <span>{{ grandChild.name }}</span>
                                     </div>
                                  </div>
                             </div>

                             <!-- Leaf Node (Direct Child of Source) -->
                             <div v-else
                                  @click="handleDataSelect(child)"
                                  class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors ml-2 text-gray-600 hover:bg-blue-50 hover:text-blue-600">
                                 <i :class="source.type === 'ts' ? 'ri-pulse-line text-purple-400' : 'ri-table-line text-green-500'" class="mr-2 text-sm"></i>
                                 <span class="truncate">{{ child.name }}</span>
                             </div>
                         </template>
                     </div>
                </div>
            </div>
            <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                <button @click="showDataSelector = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
            </div>
        </div>
    </div>

    <!-- Run Config Modal -->
    <div v-if="showRunModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4">Execute Task</h3>
            <div class="space-y-3">
                <div>
                    <label class="block text-xs font-bold text-gray-500 mb-1">Start Time</label>
                    <input v-model="runConfig.startTime" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                </div>
                <div>
                    <label class="block text-xs font-bold text-gray-500 mb-1">End Time</label>
                    <input v-model="runConfig.endTime" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                </div>
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="showRunModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="executeTask" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Run Now</button>
            </div>
        </div>
    </div>

    <!-- List -->
    <div class="w-64 border-r border-gray-200 bg-gray-50 flex flex-col">
        <div class="p-3 border-b border-gray-200 flex justify-between items-center">
            <span class="font-bold text-xs text-gray-600 uppercase">关联规则列表</span>
            <i class="ri-add-line cursor-pointer text-gray-500 hover:text-blue-600" @click="openWizard" title="Create Rule"></i>
        </div>
        <div class="flex-1 overflow-y-auto">
             <div v-for="rule in associationStore.rules" :key="rule.id" 
                  @click="selectedRule = rule"
                  :class="selectedRule?.id === rule.id ? 'bg-white border-l-4 border-blue-500 shadow-sm' : 'border-l-4 border-transparent hover:bg-gray-100'"
                  class="p-3 border-b border-gray-100 cursor-pointer transition-all">
                <div class="flex justify-between items-start mb-1">
                    <span class="font-bold text-sm text-gray-800">{{ rule.name }}</span>
                    <div class="flex items-center space-x-1">
                        <i @click.stop="handleCopyRule(rule)" class="ri-file-copy-line text-gray-400 hover:text-blue-500 text-xs" title="Copy"></i>
                        <span class="w-2 h-2 rounded-full" :class="rule.enabled ? 'bg-green-500' : 'bg-red-500'"></span>
                    </div>
                </div>
                <div class="text-xs text-gray-500 mb-1 truncate">{{ rule.modelName }}</div>
                <div class="flex items-center space-x-2">
                    <span v-if="rule.isRunning" class="text-[10px] text-blue-500 flex items-center font-medium"><i class="ri-loader-4-line animate-spin mr-1"></i> Running</span>
                    <span v-else class="text-[10px] text-gray-400">Idle</span>
                </div>
            </div>
        </div>
    </div>

    <!-- Detail -->
    <div class="flex-1 flex flex-col bg-white">
         <div v-if="selectedRule" class="flex-1 flex flex-col">
            <div class="h-12 border-b border-gray-200 flex items-center justify-between px-6 bg-gray-50/50">
                <div>
                    <h2 class="font-bold text-gray-800 text-lg">{{ selectedRule.name }}</h2>
                    <span class="text-xs text-gray-500 font-mono">{{ selectedRule.id }}</span>
                </div>
                <div class="flex space-x-2">
                     <button @click="editRule(selectedRule)" class="px-3 py-1.5 bg-white border border-gray-300 rounded text-xs text-gray-600 hover:bg-gray-50 shadow-sm flex items-center">
                        <i class="ri-edit-line mr-1"></i> Edit
                     </button>
                     <button @click="handleDeleteRule(selectedRule)" class="px-3 py-1.5 bg-white border border-red-200 rounded text-xs text-red-600 hover:bg-red-50 shadow-sm flex items-center">
                        <i class="ri-delete-bin-line mr-1"></i> Delete
                     </button>
                     <button @click="openRunModal" class="px-4 py-1.5 bg-green-600 text-white rounded text-xs hover:bg-green-700 shadow-sm flex items-center">
                        <i class="ri-play-fill mr-1"></i> Run
                     </button>
                </div>
            </div>
            
            <div class="p-6 flex-1 overflow-y-auto space-y-6">
                <!-- Status -->
                <div class="p-4 rounded border border-gray-200 bg-gray-50">
                     <div class="flex justify-between items-center mb-2">
                        <h3 class="text-xs font-bold text-gray-500 uppercase">Status</h3>
                        <button @click="toggleStatus(selectedRule)" 
                                :class="selectedRule.enabled ? 'bg-green-100 text-green-700 border-green-200' : 'bg-red-100 text-red-700 border-red-200'"
                                class="px-3 py-1 rounded text-xs border font-medium flex items-center">
                            {{ selectedRule.enabled ? 'Enabled' : 'Disabled' }}
                        </button>
                     </div>
                     <p class="text-sm text-gray-600">{{ selectedRule.desc }}</p>
                </div>

                <!-- Topology -->
                <div class="border border-gray-200 rounded p-8 relative bg-gray-50 flex items-center justify-center">
                    <div class="flex items-center space-x-8">
                        <!-- Inputs -->
                        <div class="flex flex-col space-y-4">
                             <div v-for="(path, key) in selectedRule.bindings" :key="key" class="bg-white p-3 rounded shadow-sm border border-gray-200 w-48 relative group">
                                <div class="text-[10px] text-gray-400 font-bold uppercase mb-1">{{ key }}</div>
                                <div class="text-xs text-gray-700 font-mono truncate" :title="path">{{ path }}</div>
                                <!-- Connector dot -->
                                <div class="absolute -right-1.5 top-1/2 w-3 h-3 bg-gray-300 rounded-full border-2 border-white transform -translate-y-1/2"></div>
                            </div>
                        </div>

                        <!-- Arrow -->
                        <div class="text-gray-300">
                            <i class="ri-arrow-right-line text-4xl"></i>
                        </div>

                        <!-- Model -->
                        <div class="bg-blue-600 text-white p-6 rounded-lg shadow-lg w-40 flex flex-col items-center justify-center relative z-10">
                            <i class="ri-function-line text-3xl mb-2"></i>
                            <div class="font-bold text-sm text-center">{{ selectedRule.modelName }}</div>
                            <div class="text-[10px] opacity-70 mt-1">v{{ selectedRule.modelVersion }}</div>
                        </div>

                        <!-- Arrow -->
                        <div class="text-gray-300">
                            <i class="ri-arrow-right-line text-4xl"></i>
                        </div>

                        <!-- Outputs -->
                         <div class="flex flex-col space-y-4">
                             <div v-for="(path, key) in selectedRule.results" :key="key" class="bg-white p-3 rounded shadow-sm border border-gray-200 w-48 relative">
                                <div class="text-[10px] text-gray-400 font-bold uppercase mb-1">{{ key }}</div>
                                <div class="text-xs text-gray-700 font-mono truncate" :title="path">{{ path }}</div>
                                <!-- Connector dot -->
                                <div class="absolute -left-1.5 top-1/2 w-3 h-3 bg-gray-300 rounded-full border-2 border-white transform -translate-y-1/2"></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Task History -->
                <div v-if="recentTasks.length > 0">
                    <h3 class="text-xs font-bold text-gray-500 uppercase mb-2">Recent Tasks</h3>
                    <div class="border border-gray-200 rounded overflow-hidden">
                        <table class="w-full text-xs text-left">
                            <thead class="bg-gray-50 text-gray-500">
                                <tr>
                                    <th class="p-2 border-b">ID</th>
                                    <th class="p-2 border-b">Time Range</th>
                                    <th class="p-2 border-b">Status</th>
                                    <th class="p-2 border-b">Action</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-gray-100">
                                <tr v-for="task in recentTasks" :key="task.id"
                                    @click="selectTask(task)"
                                    :class="selectedTask?.id === task.id ? 'bg-blue-50' : ''"
                                    class="hover:bg-gray-50 cursor-pointer">
                                    <td class="p-2 font-mono">{{ task.id }}</td>
                                    <td class="p-2 text-gray-600">{{ task.startTime.split(' ')[1] }} - {{ task.endTime.split(' ')[1] }}</td>
                                    <td class="p-2">
                                        <span class="px-2 py-0.5 rounded text-[10px] font-bold"
                                              :class="{
                                                'bg-yellow-100 text-yellow-700': task.status === 'PENDING',
                                                'bg-blue-100 text-blue-700': task.status === 'RUNNING',
                                                'bg-green-100 text-green-700': task.status === 'SUCCESS',
                                                'bg-red-100 text-red-700': task.status === 'FAILED' || task.status === 'ABORTED'
                                              }">
                                            {{ task.status }}
                                            <span v-if="task.status === 'RUNNING'">({{ task.progress }}%)</span>
                                        </span>
                                    </td>
                                    <td class="p-2">
                                        <button v-if="task.status === 'RUNNING' || task.status === 'PENDING'" 
                                                @click.stop="associationStore.stopTask(task.id)"
                                                class="text-red-600 hover:underline">Stop</button>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
         </div>
         <div v-else class="flex-1 flex flex-col items-center justify-center text-gray-400">
            <i class="ri-links-line text-6xl mb-4 opacity-20"></i>
            <p>Select a rule to configure</p>
        </div>
    </div>
  </div>
</template>
