<script setup>
import { ref, reactive, watch, onMounted, computed } from 'vue'
import { useModelStore } from '../stores/model'

const modelStore = useModelStore()
// showMetaModal is now in store
const showDiffModal = ref(false) // New state for diff view
const diffData = ref({ v1: null, v2: null }) // Data for comparison
const selectedVersions = ref([])
const activeVersion = ref(null)
const metaForm = ref({ name: '', desc: '', version: '', inputs: [], outputs: [] })

const deleteTargetModels = computed(() => {
    const ids = modelStore.selectedModelIds || []
    if (ids.length > 0) {
        return modelStore.models.filter(model => ids.includes(model.id))
    }
    return modelStore.selectedModel ? [modelStore.selectedModel] : []
})

const deleteBlockedModels = computed(() =>
    deleteTargetModels.value.filter(model => (model.refCount || 0) > 0)
)

const getDefaultVersion = (model) => {
    const history = model?.history || []
    if (!history.length) return null
    return history.find(item => item.latest) || history[history.length - 1]
}

const syncActiveVersion = (model) => {
    const history = model?.history || []
    if (!history.length) {
        activeVersion.value = null
        selectedVersions.value = []
        return
    }
    if (activeVersion.value?.version) {
        const matched = history.find(item => item.version === activeVersion.value.version)
        activeVersion.value = matched || getDefaultVersion(model)
    } else {
        activeVersion.value = getDefaultVersion(model)
    }
    selectedVersions.value = selectedVersions.value.filter(version =>
        history.some(item => item.version === version)
    )
}

const selectActiveVersion = (version) => {
    if (!modelStore.selectedModel?.history?.length) return
    const target = modelStore.selectedModel.history.find(item => item.version === version)
    if (target) activeVersion.value = target
}

// Upload Wizard State
const uploadStep = ref(1)
const uploadProgress = ref(0)
const uploadForm = modelStore.uploadForm
const uploadMode = ref('single')
const directoryFiles = ref([])
const directoryIgnoredCount = ref(0)

const applyFunctionSchema = async (functionName) => {
    if (!uploadForm.file || !functionName) return
    const schema = await modelStore.parseSchemaByFunction(uploadForm.file, functionName)
    uploadForm.inputs = schema?.inputs || []
    uploadForm.outputs = schema?.outputs || []
    uploadForm.parseMode = schema?.parseMode || ''
    uploadForm.parseMessage = schema?.message || ''
}

const handleUploadFile = async (e) => {
    uploadMode.value = 'single'
    uploadForm.file = e.target.files[0]
    directoryFiles.value = []
    directoryIgnoredCount.value = 0
    modelStore.resetUploadParseState()
    if (!uploadForm.file) return
    uploadForm.name = uploadForm.file.name
    try {
        const functions = await modelStore.listFunctionsByFile(uploadForm.file)
        uploadForm.functionOptions = functions || []
        if (uploadForm.functionOptions.length > 0) {
            uploadForm.selectedFunction = uploadForm.functionOptions[0].name
            await applyFunctionSchema(uploadForm.selectedFunction)
        } else {
            const schema = await modelStore.parseSchemaByFile(uploadForm.file)
            uploadForm.inputs = schema?.inputs || []
            uploadForm.outputs = schema?.outputs || []
            uploadForm.parseMode = 'COMMENT_FALLBACK'
            uploadForm.parseMessage = 'No function detected. Fallback to comment-based parsing.'
        }
    } catch (err) {
        modelStore.resetUploadParseState()
        alert(err.message || '模型解析失败')
    }
}

const handleUploadDirectory = (e) => {
    uploadMode.value = 'directory'
    modelStore.resetUploadParseState()
    const files = Array.from(e.target.files || [])
    const supported = files.filter(file => modelStore.isSupportedModelFile(file.name))
    directoryFiles.value = supported
    directoryIgnoredCount.value = files.length - supported.length
    uploadForm.file = supported[0] || null
    uploadForm.name = supported[0]?.webkitRelativePath || supported[0]?.name || ''
    if (!supported.length) {
        alert('目录中未发现可上传的模型文件（支持 py/mat/ame/dll/fmu/zip/csv/xlsx）')
    }
}

const switchUploadMode = (mode) => {
    if (uploadMode.value === mode) return
    uploadMode.value = mode
    uploadForm.file = null
    uploadForm.name = ''
    modelStore.resetUploadParseState()
    directoryFiles.value = []
    directoryIgnoredCount.value = 0
}

const handleFunctionChange = async () => {
    if (!uploadForm.selectedFunction) return
    try {
        await applyFunctionSchema(uploadForm.selectedFunction)
    } catch (err) {
        alert(err.message || '函数解析失败')
    }
}

const closeUploadModal = () => {
    modelStore.showUploadModal = false
    uploadStep.value = 1
    uploadProgress.value = 0
    uploadForm.file = null
    uploadMode.value = 'single'
    directoryFiles.value = []
    directoryIgnoredCount.value = 0
    modelStore.resetUploadParseState()
}

const startUpload = async () => {
    uploadStep.value = 2
    uploadProgress.value = 0
    try {
        if (uploadMode.value === 'directory') {
            const result = await modelStore.uploadModelDirectoryAssets(directoryFiles.value, {
                version: 'AUTO',
                onProgress: ({ processed, total }) => {
                    uploadProgress.value = Math.round((processed / total) * 100)
                }
            })
            syncActiveVersion(modelStore.selectedModel)
            uploadProgress.value = 100
            setTimeout(() => {
                const summary = `目录上传完成：成功 ${result.successCount}，失败 ${result.failedCount}`
                if (result.failedCount > 0) {
                    const preview = result.errors
                        .slice(0, 3)
                        .map(item => `${item.file}: ${item.message}`)
                        .join('\n')
                    alert(`${summary}\n\n失败详情（最多展示3条）：\n${preview}`)
                } else {
                    alert(summary)
                }
                closeUploadModal()
            }, 300)
            return
        }

        uploadProgress.value = 10
        await modelStore.uploadModelAsset()
        syncActiveVersion(modelStore.selectedModel)
        uploadProgress.value = 100
        setTimeout(() => {
            alert('模型上传成功')
            closeUploadModal()
        }, 300)
    } catch (err) {
        uploadStep.value = 1
        uploadProgress.value = 0
        alert(err.message || '模型上传失败')
    }
}

const editMetadata = () => {
    if (!modelStore.selectedModel) return
    const currentVer = activeVersion.value || getDefaultVersion(modelStore.selectedModel)
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

watch(() => modelStore.selectedModel, (model) => {
    syncActiveVersion(model)
}, { immediate: true })

onMounted(() => {
    modelStore.loadModels()
})

const autoParseCode = () => {
    if (!modelStore.selectedModel) return
    const currentVer = activeVersion.value || getDefaultVersion(modelStore.selectedModel)
    if (!currentVer) return
    metaForm.value.inputs = JSON.parse(JSON.stringify(currentVer.inputs || []))
    metaForm.value.outputs = JSON.parse(JSON.stringify(currentVer.outputs || []))
    alert('已根据当前选中版本接口定义刷新')
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
    if (!deleteTargetModels.value.length) return
    if (deleteBlockedModels.value.length > 0) {
        const names = deleteBlockedModels.value.map(model => model.name).join('、')
        alert(`选中模型中存在被引用项，无法删除：${names}`)
        return
    }
    try {
        if (deleteTargetModels.value.length === 1) {
            await modelStore.deleteModel(deleteTargetModels.value[0].id)
        } else {
            await modelStore.deleteModelsBatch(deleteTargetModels.value.map(model => model.id))
        }
        modelStore.clearSelectedModelIds()
        modelStore.showDeleteModal = false
    } catch (err) {
        alert(err.message || '删除失败')
    }
}

const handleDeleteVersion = async (version) => {
    if (!modelStore.selectedModel) return
    if (confirm(`确认删除版本 ${version} 吗？该操作不可恢复。`)) {
        try {
            await modelStore.deleteModelVersion(modelStore.selectedModel.id, version)
        } catch (err) {
            alert(err.message || '删除失败')
        }
    }
}

const removeActiveVersion = async () => {
    const history = modelStore.selectedModel?.history || []
    if (!activeVersion.value?.version) return
    if (history.length <= 1) {
        alert('至少保留一个版本，无法删除最后一个版本')
        return
    }
    await handleDeleteVersion(activeVersion.value.version)
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

const findParam = (list, name) => (list || []).find(item => item.name === name)

const isParamChanged = (baseList, target) => {
    const base = findParam(baseList, target?.name)
    if (!base) return false
    return (base.type || '') !== (target.type || '') ||
        (base.unit || '') !== (target.unit || '') ||
        (base.desc || '') !== (target.desc || '')
}
</script>

<template>
  <div class="h-full flex flex-col p-4 relative">
    
    <!-- Delete Modal -->
    <div v-if="modelStore.showDeleteModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] p-6">
            <h3 class="font-bold text-gray-800 mb-4 text-red-600">移除模型</h3>
            <div class="mb-4">
                <p class="text-sm text-gray-600 mb-2">
                    {{ deleteTargetModels.length > 1 ? `确认批量删除 ${deleteTargetModels.length} 个模型吗？` : '确认永久删除该模型吗？' }}
                </p>
                <div class="p-3 bg-gray-50 rounded border border-gray-200 text-sm text-gray-800 max-h-32 overflow-y-auto space-y-1">
                    <div v-for="item in deleteTargetModels" :key="item.id" class="font-medium truncate">
                        {{ item.name }}
                    </div>
                </div>
            </div>
            <div v-if="deleteBlockedModels.length > 0" class="p-3 bg-red-50 border border-red-100 rounded text-xs text-red-700 mb-4">
                <i class="ri-alert-line mr-1"></i>
                含被规则引用模型，无法删除：{{ deleteBlockedModels.map(item => item.name).join('、') }}
            </div>
            <div class="flex justify-end space-x-2 mt-6">
                <button @click="modelStore.showDeleteModal = false" class="px-4 py-2 border border-gray-300 rounded text-sm text-gray-600 hover:bg-gray-50">取消</button>
                <button
                    @click="handleDeleteModel"
                    :disabled="deleteTargetModels.length === 0 || deleteBlockedModels.length > 0"
                    class="px-4 py-2 bg-red-600 text-white rounded text-sm hover:bg-red-700 disabled:opacity-50"
                >
                    确认删除
                </button>
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
                            <div v-for="p in diffData.v1.inputs" :key="`v1-input-${p.name}`"
                                 :class="!findParam(diffData.v2.inputs, p.name) ? 'bg-red-50 border border-red-200' : 'bg-gray-50'"
                                 class="text-xs p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                                <span v-if="!findParam(diffData.v2.inputs, p.name)" class="text-[10px] text-red-600 font-bold">REMOVED</span>
                            </div>
                        </div>
                        <div>
                            <span class="text-xs font-bold text-gray-500 block mb-1">Outputs</span>
                            <div v-for="p in diffData.v1.outputs" :key="`v1-output-${p.name}`"
                                 :class="!findParam(diffData.v2.outputs, p.name) ? 'bg-red-50 border border-red-200' : 'bg-gray-50'"
                                 class="text-xs p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                                <span v-if="!findParam(diffData.v2.outputs, p.name)" class="text-[10px] text-red-600 font-bold">REMOVED</span>
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
                                 :class="!findParam(diffData.v1.inputs, p.name) ? 'bg-green-50 border border-green-200' : (isParamChanged(diffData.v1.inputs, p) ? 'bg-yellow-50 border border-yellow-200' : 'bg-gray-50')"
                                 class="text-xs p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                                <span v-if="!findParam(diffData.v1.inputs, p.name)" class="text-[10px] text-green-600 font-bold">NEW</span>
                                <span v-else-if="isParamChanged(diffData.v1.inputs, p)" class="text-[10px] text-yellow-700 font-bold">CHANGED</span>
                            </div>
                        </div>
                        <div>
                            <span class="text-xs font-bold text-gray-500 block mb-1">Outputs</span>
                            <div v-for="p in diffData.v2.outputs" :key="`v2-output-${p.name}`"
                                 :class="!findParam(diffData.v1.outputs, p.name) ? 'bg-green-50 border border-green-200' : (isParamChanged(diffData.v1.outputs, p) ? 'bg-yellow-50 border border-yellow-200' : 'bg-gray-50')"
                                 class="text-xs p-1 mb-1 rounded flex justify-between">
                                <span class="font-mono">{{ p.name }}</span>
                                <span class="text-gray-500">{{ p.type }}</span>
                                <span v-if="!findParam(diffData.v1.outputs, p.name)" class="text-[10px] text-green-600 font-bold">NEW</span>
                                <span v-else-if="isParamChanged(diffData.v1.outputs, p)" class="text-[10px] text-yellow-700 font-bold">CHANGED</span>
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
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="closeUploadModal"></i>
            </div>
            
            <div class="p-6 space-y-4">
                <div v-if="uploadStep === 1" class="space-y-4">
                    <div class="grid grid-cols-2 gap-2">
                        <button
                            @click="switchUploadMode('single')"
                            :class="uploadMode === 'single' ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-white text-gray-600 border-gray-300'"
                            class="px-3 py-2 text-xs border rounded"
                        >
                            单文件上传
                        </button>
                        <button
                            @click="switchUploadMode('directory')"
                            :class="uploadMode === 'directory' ? 'bg-blue-50 text-blue-700 border-blue-200' : 'bg-white text-gray-600 border-gray-300'"
                            class="px-3 py-2 text-xs border rounded"
                        >
                            目录递归上传
                        </button>
                    </div>

                    <div v-if="uploadMode === 'single'" class="space-y-4">
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
                                 <span class="text-xs text-gray-500 mt-2">{{ uploadForm.file ? uploadForm.file.name : '选择模型文件（.py/.mat/.dll/.fmu 等）' }}</span>
                             </label>
                        </div>
                        <div v-if="uploadForm.functionOptions?.length" class="space-y-2">
                            <label class="block text-xs font-bold text-gray-500 mb-1">Function</label>
                            <select v-model="uploadForm.selectedFunction" @change="handleFunctionChange" class="w-full border border-gray-300 rounded px-2 py-1.5 text-xs">
                                <option v-for="item in uploadForm.functionOptions" :key="item.name" :value="item.name">
                                    {{ item.displayName || item.name }}
                                </option>
                            </select>
                        </div>
                        <div v-if="uploadForm.parseMode" :class="uploadForm.parseMode === 'SYNTAX' ? 'bg-green-50 border-green-200 text-green-700' : 'bg-yellow-50 border-yellow-200 text-yellow-700'" class="text-xs border rounded px-3 py-2">
                            <span class="font-bold mr-1">{{ uploadForm.parseMode }}:</span>{{ uploadForm.parseMessage || '-' }}
                        </div>
                    </div>

                    <div v-else class="space-y-3">
                        <div class="text-xs text-gray-500 bg-blue-50 border border-blue-100 rounded px-3 py-2">
                            目录上传会自动递归包含子目录，按文件后缀自动识别模型类型，版本默认使用 AUTO。
                        </div>
                        <div class="border-2 border-dashed border-gray-300 rounded-lg h-32 flex flex-col items-center justify-center bg-gray-50">
                            <input type="file" webkitdirectory directory multiple @change="handleUploadDirectory" class="hidden" id="modelUploadDir">
                            <label for="modelUploadDir" class="cursor-pointer flex flex-col items-center">
                                <i class="ri-folder-upload-line text-3xl text-gray-400"></i>
                                <span class="text-xs text-gray-500 mt-2">
                                    {{ directoryFiles.length ? `已选择 ${directoryFiles.length} 个可上传模型文件` : '选择模型目录（支持递归子目录）' }}
                                </span>
                            </label>
                        </div>
                        <div class="text-xs text-gray-500 space-y-1">
                            <div>可上传文件：{{ directoryFiles.length }}</div>
                            <div v-if="directoryIgnoredCount > 0">已忽略不支持文件：{{ directoryIgnoredCount }}</div>
                            <div v-if="directoryFiles.length > 0">示例：{{ directoryFiles[0].webkitRelativePath || directoryFiles[0].name }}</div>
                        </div>
                    </div>
                </div>

                <div v-if="uploadStep === 2">
                    <div class="flex justify-between text-xs text-gray-500 mb-1">
                        <span>{{ uploadMode === 'directory' ? '正在上传目录中的模型文件...' : '正在上传并计算 MD5...' }}</span>
                        <span>{{ uploadProgress }}%</span>
                    </div>
                    <div class="w-full bg-gray-200 rounded-full h-2">
                        <div class="bg-blue-600 h-2 rounded-full transition-all" :style="{ width: uploadProgress + '%' }"></div>
                    </div>
                </div>
            </div>

            <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                <button
                    v-if="uploadStep === 1"
                    @click="startUpload"
                    :disabled="uploadMode === 'single' ? !uploadForm.file : directoryFiles.length === 0"
                    class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
                >
                    开始上传
                </button>
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
                        <span class="text-xs text-gray-500">v{{ activeVersion?.version || modelStore.selectedModel.version }}</span>
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
                                    <tr v-for="p in activeVersion?.inputs || []" :key="`input-${p.name}`">
                                        <td class="px-4 py-2"><span class="text-green-600 font-bold text-xs">INPUT</span></td>
                                        <td class="px-4 py-2 font-mono text-gray-700">{{ p.name }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.type }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.unit || '-' }}</td>
                                        <td class="px-4 py-2 text-gray-500">{{ p.desc || '-' }}</td>
                                    </tr>
                                    <tr v-for="p in activeVersion?.outputs || []" :key="`output-${p.name}`">
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
                            <div class="flex justify-between"><span class="text-gray-500">ID:</span> <span class="font-mono text-gray-700">{{ activeVersion?.id || modelStore.selectedModel.id }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">Size:</span> <span class="text-gray-700">{{ formatSize(activeVersion?.fileSize ?? modelStore.selectedModel.fileSize) }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">Uploaded:</span> <span class="text-gray-700">{{ formatTime(activeVersion?.uploadTime || modelStore.selectedModel.uploadTime) }}</span></div>
                            <div class="flex justify-between"><span class="text-gray-500">References:</span> <span class="text-gray-700">{{ modelStore.selectedModel.refCount || 0 }} tasks</span></div>
                        </div>
                    </div>

                    <div>
                        <div class="flex justify-between items-center mb-2">
                            <h3 class="text-sm font-bold text-gray-900 uppercase tracking-wide">Version History</h3>
                            <div class="flex items-center space-x-3">
                                <button
                                    @click="removeActiveVersion"
                                    :disabled="!activeVersion || (modelStore.selectedModel.history?.length || 0) <= 1"
                                    class="text-xs text-red-600 hover:underline disabled:text-gray-300 disabled:no-underline disabled:cursor-not-allowed"
                                >
                                    移除当前版本
                                </button>
                                <button @click="compareVersions" :disabled="selectedVersions.length !== 2" class="text-xs text-blue-600 hover:underline disabled:text-gray-300 disabled:no-underline disabled:cursor-not-allowed">
                                    Compare Selected ({{ selectedVersions.length }}/2)
                                </button>
                            </div>
                        </div>
                        <div class="space-y-2 max-h-48 overflow-y-auto">
                            <div
                                v-for="v in modelStore.selectedModel.history"
                                :key="v.version"
                                @click="selectActiveVersion(v.version)"
                                :class="activeVersion?.version === v.version ? 'bg-blue-50 border border-blue-200' : 'hover:bg-gray-50 border border-transparent'"
                                class="flex items-center text-xs group p-1 rounded cursor-pointer"
                            >
                                <input type="checkbox" :value="v.version" v-model="selectedVersions" @click.stop class="mr-2 rounded border-gray-300 text-blue-600 focus:ring-0">
                                <span :class="activeVersion?.version === v.version ? 'text-blue-700 font-semibold' : 'text-gray-600'" class="flex-1">{{ v.version }}</span>
                                <span v-if="activeVersion?.version === v.version" class="text-[10px] text-blue-600 mr-2">当前查看</span>
                                <span class="text-gray-400 mr-2">{{ formatTime(v.uploadTime).split(' ')[0] }}</span>
                                <i @click.stop="handleDeleteVersion(v.version)" title="移除此版本" class="ri-delete-bin-line text-gray-300 hover:text-red-500 cursor-pointer opacity-0 group-hover:opacity-100"></i>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

