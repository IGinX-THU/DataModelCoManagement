<script setup>
import { ref, reactive, watch, onMounted } from 'vue'
import { useModelStore } from '../stores/model'

const modelStore = useModelStore()
// showMetaModal is now in store
const showDiffModal = ref(false) // New state for diff view
const diffData = ref({ v1: null, v2: null }) // Data for comparison
const selectedVersions = ref([])
const metaForm = ref({ name: '', desc: '', version: '', inputs: [], outputs: [] })

// Upload Wizard State
const uploadStep = ref(1)
const uploadProgress = ref(0)
const uploadForm = modelStore.uploadForm

const handleUploadFile = async (e) => {
    uploadForm.file = e.target.files[0]
    if (uploadForm.file) {
        uploadForm.name = uploadForm.file.name
        try {
            const schema = await modelStore.parseSchemaByFile(uploadForm.file)
            uploadForm.inputs = schema?.inputs || []
            uploadForm.outputs = schema?.outputs || []
        } catch (err) {
            uploadForm.inputs = []
            uploadForm.outputs = []
            alert(err.message || '模型解析失败')
        }
    }
}

const startUpload = async () => {
    uploadStep.value = 2
    uploadProgress.value = 10
    try {
        await modelStore.uploadModelAsset()
        uploadProgress.value = 100
        setTimeout(() => {
            alert('模型上传成功')
            modelStore.showUploadModal = false
            uploadStep.value = 1
            uploadProgress.value = 0
            uploadForm.file = null
            uploadForm.inputs = []
            uploadForm.outputs = []
        }, 300)
    } catch (err) {
        uploadStep.value = 1
        uploadProgress.value = 0
        alert(err.message || '模型上传失败')
    }
}

const editMetadata = () => {
    if (!modelStore.selectedModel) return
    const currentVer = modelStore.selectedModel.history?.[modelStore.selectedModel.history.length - 1]
    if (!currentVer) return
    metaForm.value = {
        name: modelStore.selectedModel.name,
        desc: modelStore.selectedModel.description || '',
        version: currentVer.version,
        originalVersion: currentVer.version,
        inputs: JSON.parse(JSON.stringify(currentVer.inputs || [])),
        outputs: JSON.parse(JSON.stringify(currentVer.outputs || []))
    }
    modelStore.showMetaModal = true
}

// Watch store state to initialize form if opened from Ribbon
watch(() => modelStore.showMetaModal, (val) => {
    if (val && modelStore.selectedModel) {
        editMetadata()
    }
})

onMounted(() => {
    modelStore.loadModels()
})

const autoParseCode = () => {
    if (!modelStore.selectedModel) return
    const latest = modelStore.selectedModel.history?.[modelStore.selectedModel.history.length - 1]
    if (!latest) return
    metaForm.value.inputs = JSON.parse(JSON.stringify(latest.inputs || []))
    metaForm.value.outputs = JSON.parse(JSON.stringify(latest.outputs || []))
    alert('已根据最新版本接口定义刷新')
}

const saveMetadata = async () => {
    if (!modelStore.selectedModel) return
    try {
        await modelStore.updateModelMetadata(
            modelStore.selectedModel.id, 
            metaForm.value.originalVersion, 
            metaForm.value
        )
        modelStore.showMetaModal = false
    } catch (err) {
        alert(err.message || '保存失败')
    }
}

const compareVersions = () => {
    if (!modelStore.selectedModel || selectedVersions.value.length !== 2) return
    const hist = modelStore.selectedModel.history
    
    const obj1 = hist.find(h => h.version === selectedVersions.value[0])
    const obj2 = hist.find(h => h.version === selectedVersions.value[1])
    
    // Sort by index in history (assuming history is sorted chronologically)
    const idx1 = hist.indexOf(obj1)
    const idx2 = hist.indexOf(obj2)
    
    if (idx1 < idx2) {
        diffData.value = { v1: obj1, v2: obj2 }
    } else {
        diffData.value = { v1: obj2, v2: obj1 }
    }
    
    showDiffModal.value = true
}

const handleDownload = () => {
    if (!modelStore.selectedModel) return
    modelStore.downloadModel(modelStore.selectedModel)
}

const handleDeleteModel = async () => {
    if (!modelStore.selectedModel) return
    try {
        await modelStore.deleteModel(modelStore.selectedModel.id)
        modelStore.showDeleteModal = false
    } catch (err) {
        alert(err.message || '删除失败')
    }
}

const handleDeleteVersion = async (version) => {
    if (!modelStore.selectedModel) return
    if (confirm(`Delete version ${version}?`)) {
        try {
            await modelStore.deleteModelVersion(modelStore.selectedModel.id, version)
        } catch (err) {
            alert(err.message || '删除失败')
        }
    }
}

const getTypeIcon = (type) => {
    const key = (type || '').toUpperCase()
    const icons = {
        PY: 'ri-code-s-slash-line',
        PYTHON: 'ri-code-s-slash-line',
        MATLAB: 'ri-functions-line',
        MAT: 'ri-functions-line',
        FMU: 'ri-box-3-line',
        DLL: 'ri-file-code-line',
        AME: 'ri-file-code-line'
    }
    return icons[key] || 'ri-file-line'
}

const formatType = (type) => {
    const key = (type || '').toUpperCase()
    const map = {
        PY: 'Python',
        PYTHON: 'Python',
        MAT: 'MATLAB',
        MATLAB: 'MATLAB',
        AME: 'AMESim',
        DLL: 'DLL',
        FMU: 'FMU'
    }
    return map[key] || type || '-'
}

const formatSize = (size) => {
    if (size === null || size === undefined) return '-'
    if (size < 1024) return `${size} B`
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
    return `${(size / (1024 * 1024)).toFixed(2)} MB`
}

const formatTime = (value) => {
    if (!value) return '-'
    return value.replace('T', ' ')
}
</script>

<template>
  <div class="h-full flex flex-col p-4 relative">
    
    <!-- Delete Modal -->
    <div v-if="modelStore.showDeleteModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4 text-red-600">Remove Model Asset</h3>
            <div class="mb-4">
                <p class="text-sm text-gray-600 mb-2">Are you sure you want to permanently delete this model?</p>
                <div class="p-3 bg-gray-50 rounded border border-gray-200 text-sm font-bold text-gray-800">
                    {{ modelStore.selectedModel?.name }}
                </div>
            </div>
            <div v-if="modelStore.selectedModel?.refCount > 0" class="p-3 bg-red-50 border border-red-100 rounded text-xs text-red-700 mb-4">
                <i class="ri-alert-line mr-1"></i> Cannot delete: Model is referenced by {{ modelStore.selectedModel.refCount }} active rules.
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="modelStore.showDeleteModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="handleDeleteModel" :disabled="modelStore.selectedModel?.refCount > 0" class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50">Confirm Delete</button>
            </div>
        </div>
    </div>

    <!-- Diff Modal -->
    <div v-if="showDiffModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[800px] flex flex-col max-h-[85vh]">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 class="font-bold text-gray-800">Version Comparison</h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="showDiffModal = false"></i>
            </div>
            <div class="p-6 grid grid-cols-2 gap-6 overflow-y-auto">
                <!-- Version 1 -->
                <div class="border border-gray-200 rounded p-4">
                    <h4 class="font-bold text-sm mb-4 flex justify-between">
                        <span>{{ diffData.v1.version }}</span>
                        <span class="text-xs text-gray-500">{{ formatTime(diffData.v1.uploadTime) }}</span>
                    </h4>
                    <div class="space-y-4">
                        <div>
                            <span class="text-xs font-bold text-gray-500 block mb-1">Inputs</span>
                            <div v-for="p in diffData.v1.inputs" :key="p.name" class="text-xs bg-gray-50 p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                            </div>
                        </div>
                    </div>
                </div>
                <!-- Version 2 -->
                <div class="border border-gray-200 rounded p-4">
                    <h4 class="font-bold text-sm mb-4 flex justify-between">
                        <span>{{ diffData.v2.version }}</span>
                        <span class="text-xs text-gray-500">{{ formatTime(diffData.v2.uploadTime) }}</span>
                    </h4>
                    <div class="space-y-4">
                        <div>
                            <span class="text-xs font-bold text-gray-500 block mb-1">Inputs</span>
                            <div v-for="p in diffData.v2.inputs" :key="p.name" 
                                 :class="diffData.v1.inputs.find(x => x.name === p.name) ? 'bg-gray-50' : 'bg-green-50 border border-green-200'"
                                 class="text-xs p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                                <span v-if="!diffData.v1.inputs.find(x => x.name === p.name)" class="text-[10px] text-green-600 font-bold">NEW</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Upload Wizard Modal -->
    <div v-if="modelStore.showUploadModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[500px] flex flex-col">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
                <h3 class="font-bold text-gray-800">Upload Model Asset</h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="modelStore.showUploadModal = false"></i>
            </div>
            
            <div class="p-6 space-y-4">
                <div v-if="uploadStep === 1" class="space-y-4">
                    <div class="grid grid-cols-2 gap-4">
                        <div class="col-span-2">
                            <label class="block text-xs font-bold text-gray-500 mb-1">模型名称</label>
                            <input v-model="uploadForm.name" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs" placeholder="例如 PID 控制模型">
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-500 mb-1">模型类型</label>
                            <select v-model="uploadForm.type" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                                <option>Python</option><option>MATLAB</option><option>AMESim</option><option>FMU</option><option>DLL</option>
                            </select>
                        </div>
                        <div>
                            <label class="block text-xs font-bold text-gray-500 mb-1">版本号</label>
                            <input v-model="uploadForm.version" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs" placeholder="v1.0.0 或 AUTO">
                        </div>
                    </div>
                    <div class="border-2 border-dashed border-gray-300 rounded-lg h-32 flex flex-col items-center justify-center bg-gray-50">
                         <input type="file" @change="handleUploadFile" class="hidden" id="modelUpload">
                         <label for="modelUpload" class="cursor-pointer flex flex-col items-center">
                             <i class="ri-upload-cloud-2-line text-3xl text-gray-400"></i>
                             <span class="text-xs text-gray-500 mt-2">{{ uploadForm.file ? uploadForm.file.name : 'Drag model file here (.py, .dll, .zip)' }}</span>
                         </label>
                    </div>
                </div>

                <div v-if="uploadStep === 2">
                    <div class="flex justify-between text-xs text-gray-500 mb-1">
                        <span>Uploading & Calculating MD5...</span>
                        <span>{{ uploadProgress }}%</span>
                    </div>
                    <div class="w-full bg-gray-200 rounded-full h-2">
                        <div class="bg-blue-600 h-2 rounded-full transition-all" :style="{ width: uploadProgress + '%' }"></div>
                    </div>
                </div>
            </div>

            <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                <button v-if="uploadStep === 1" @click="startUpload" :disabled="!uploadForm.file" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50">Start Upload</button>
            </div>
        </div>
    </div>

    <!-- Meta Editor Modal -->
    <div v-if="modelStore.showMetaModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[700px] flex flex-col max-h-[85vh]">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50">
                <h3 class="font-bold text-gray-800">Meta-Model Profile Editor</h3>
                <div class="flex items-center space-x-2">
                    <button @click="autoParseCode" class="text-xs bg-purple-100 text-purple-700 px-2 py-1 rounded border border-purple-200 hover:bg-purple-200">
                        <i class="ri-magic-line mr-1"></i>Auto-Parse Code
                    </button>
                    <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="modelStore.showMetaModal = false"></i>
                </div>
            </div>
            
            <div class="p-6 overflow-y-auto space-y-6">
                <!-- Basic Info -->
                <div class="grid grid-cols-2 gap-4">
                    <div class="col-span-2">
                        <label class="block text-xs font-bold text-gray-500 mb-1">Model Name</label>
                        <input v-model="metaForm.name" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                    </div>
                    <div>
                        <label class="block text-xs font-bold text-gray-500 mb-1">Version</label>
                        <input v-model="metaForm.version" readonly class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs bg-gray-100 cursor-not-allowed">
                    </div>
                    <div class="col-span-2">
                        <label class="block text-xs font-bold text-gray-500 mb-1">Description</label>
                        <textarea v-model="metaForm.desc" rows="2" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs"></textarea>
                    </div>
                </div>

                <!-- Inputs -->
                <div>
                    <h4 class="text-xs font-bold text-gray-500 uppercase mb-2 border-b pb-1">Input Interface</h4>
                    <table class="w-full text-xs text-left">
                        <thead class="bg-gray-50 text-gray-500">
                            <tr><th class="p-2">Name</th><th class="p-2">Type</th><th class="p-2">Unit</th><th class="p-2">Description</th></tr>
                        </thead>
                        <tbody>
                            <tr v-for="(input, i) in metaForm.inputs" :key="i" class="border-b border-gray-100">
                                <td class="p-2"><input v-model="input.name" class="border border-gray-300 rounded w-full px-1"></td>
                                <td class="p-2">
                                    <select v-model="input.type" class="border border-gray-300 rounded w-full px-1">
                                        <option>Float</option><option>Int</option><option>String</option>
                                    </select>
                                </td>
                                <td class="p-2"><input v-model="input.unit" class="border border-gray-300 rounded w-full px-1" placeholder="-"></td>
                                <td class="p-2"><input v-model="input.desc" class="border border-gray-300 rounded w-full px-1"></td>
                            </tr>
                        </tbody>
                    </table>
                    <button class="text-xs text-blue-500 mt-2 hover:underline" @click="metaForm.inputs.push({name:'', type:'Float', unit:'', desc:''})">+ Add Input</button>
                </div>

                <!-- Outputs -->
                <div>
                    <h4 class="text-xs font-bold text-gray-500 uppercase mb-2 border-b pb-1">Output Interface</h4>
                    <table class="w-full text-xs text-left">
                        <thead class="bg-gray-50 text-gray-500">
                            <tr><th class="p-2">Name</th><th class="p-2">Type</th><th class="p-2">Unit</th><th class="p-2">Description</th></tr>
                        </thead>
                        <tbody>
                            <tr v-for="(output, i) in metaForm.outputs" :key="i" class="border-b border-gray-100">
                                <td class="p-2"><input v-model="output.name" class="border border-gray-300 rounded w-full px-1"></td>
                                <td class="p-2">
                                    <select v-model="output.type" class="border border-gray-300 rounded w-full px-1">
                                        <option>Float</option><option>Int</option><option>String</option>
                                    </select>
                                </td>
                                <td class="p-2"><input v-model="output.unit" class="border border-gray-300 rounded w-full px-1" placeholder="-"></td>
                                <td class="p-2"><input v-model="output.desc" class="border border-gray-300 rounded w-full px-1"></td>
                            </tr>
                        </tbody>
                    </table>
                    <button class="text-xs text-blue-500 mt-2 hover:underline" @click="metaForm.outputs.push({name:'', type:'Float', unit:'', desc:''})">+ Add Output</button>
                </div>
            </div>

            <div class="px-6 py-4 border-t border-gray-100 flex justify-end space-x-2">
                <button @click="modelStore.showMetaModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">Cancel</button>
                <button @click="saveMetadata" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Save Profile</button>
            </div>
        </div>
    </div>

    <div v-if="!modelStore.selectedModel" class="flex-1 flex flex-col items-center justify-center text-gray-400">
        <i class="ri-box-3-line text-6xl mb-4 opacity-20"></i>
        <p>Select a model from the right panel to view details</p>
    </div>

    <div v-else class="flex-1 flex flex-col bg-white rounded border border-gray-100 shadow-sm overflow-hidden">
        <!-- Header -->
        <div class="p-6 border-b border-gray-100 flex items-start justify-between bg-gray-50/50">
            <div class="flex items-center">
                <div class="w-16 h-16 bg-white border border-gray-200 rounded-lg flex items-center justify-center mr-4 shadow-sm text-4xl text-blue-500">
                    <i :class="getTypeIcon(modelStore.selectedModel.type)"></i>
                </div>
                <div>
                    <h2 class="text-xl font-bold text-gray-800">{{ modelStore.selectedModel.name }}</h2>
                    <div class="flex items-center space-x-2 mt-1">
                        <span class="px-2 py-0.5 bg-blue-50 text-blue-600 rounded text-xs font-medium border border-blue-100">{{ formatType(modelStore.selectedModel.type) }}</span>
                        <span class="text-xs text-gray-500">v{{ modelStore.selectedModel.version }}</span>
                    </div>
                </div>
            </div>
            <div class="flex space-x-2">
            </div>
        </div>

        <!-- Details -->
        <div class="p-6 space-y-6 overflow-y-auto">
            <div class="grid grid-cols-3 gap-6">
                <div class="col-span-2 space-y-6">
                    <div>
                        <h3 class="text-sm font-bold text-gray-900 mb-2 uppercase tracking-wide">Description</h3>
                        <p class="text-sm text-gray-600 leading-relaxed bg-gray-50 p-4 rounded border border-gray-100">
                            {{ modelStore.selectedModel.description || '暂无描述' }}
                        </p>
                    </div>
                    
                    <div>
                        <h3 class="text-sm font-bold text-gray-900 mb-2 uppercase tracking-wide">Interface Definition</h3>
                        <div class="border border-gray-200 rounded overflow-hidden">
                            <table class="w-full text-sm text-left">
                                <thead class="bg-gray-50 text-gray-500 text-xs">
                                    <tr><th class="px-4 py-2 border-b">Direction</th><th class="px-4 py-2 border-b">Name</th><th class="px-4 py-2 border-b">Type</th><th class="px-4 py-2 border-b">Unit</th><th class="px-4 py-2 border-b">Description</th></tr>
                                </thead>
                                <tbody class="divide-y divide-gray-100">
                                    <tr v-for="p in modelStore.selectedModel.history?.[modelStore.selectedModel.history.length-1]?.inputs || []" :key="p.name">
                                        <td class="px-4 py-2"><span class="text-green-600 font-bold text-xs">INPUT</span></td>
                                        <td class="px-4 py-2 font-mono text-gray-700">{{ p.name }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.type }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.unit || '-' }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.desc || '-' }}</td>
                                    </tr>
                                    <tr v-for="p in modelStore.selectedModel.history?.[modelStore.selectedModel.history.length-1]?.outputs || []" :key="p.name">
                                        <td class="px-4 py-2"><span class="text-purple-600 font-bold text-xs">OUTPUT</span></td>
                                        <td class="px-4 py-2 font-mono text-gray-700">{{ p.name }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.type }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.unit || '-' }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.desc || '-' }}</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="space-y-6">
                    <div>
                        <h3 class="text-sm font-bold text-gray-900 mb-2 uppercase tracking-wide">Metadata</h3>
                        <div class="bg-gray-50 rounded border border-gray-100 p-4 space-y-2 text-xs">
                            <div class="flex justify-between"><span class="text-gray-500">ID:</span> <span class="font-mono text-gray-700">{{ modelStore.selectedModel.id }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">Size:</span> <span class="text-gray-700">{{ formatSize(modelStore.selectedModel.fileSize) }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">Uploaded:</span> <span class="text-gray-700">{{ formatTime(modelStore.selectedModel.uploadTime) }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">References:</span> <span class="text-gray-700">{{ modelStore.selectedModel.refCount || 0 }} tasks</span></div>
                        </div>
                    </div>

                    <div>
                        <div class="flex justify-between items-center mb-2">
                            <h3 class="text-sm font-bold text-gray-900 uppercase tracking-wide">Version History</h3>
                            <button @click="compareVersions" :disabled="selectedVersions.length !== 2" class="text-xs text-blue-600 hover:underline disabled:text-gray-300 disabled:no-underline disabled:cursor-not-allowed">
                                Compare Selected ({{ selectedVersions.length }}/2)
                            </button>
                        </div>
                        <div class="space-y-2 max-h-48 overflow-y-auto">
                            <div v-for="v in modelStore.selectedModel.history" :key="v.version" class="flex items-center text-xs group hover:bg-gray-50 p-1 rounded">
                                <input type="checkbox" :value="v.version" v-model="selectedVersions" class="mr-2 rounded border-gray-300 text-blue-600 focus:ring-0">
                                <span class="text-gray-600 flex-1">{{ v.version }}</span>
                                <span class="text-gray-400 mr-2">{{ formatTime(v.uploadTime).split(' ')[0] }}</span>
                                <i @click.stop="handleDeleteVersion(v.version)" class="ri-delete-bin-line text-gray-300 hover:text-red-500 cursor-pointer opacity-0 group-hover:opacity-100"></i>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
  </div>
</template>
