<script setup>
import { useModelStore } from '../stores/model'
import { useUIStore } from '../stores/ui'
import { ref, computed, onMounted, watch } from 'vue'

const modelStore = useModelStore()
const uiStore = useUIStore()
const searchQuery = ref('')
const selectedModelIds = computed({
    get: () => modelStore.selectedModelIds,
    set: (ids) => modelStore.setSelectedModelIds(ids)
})

const filteredModels = computed(() => {
    if (!searchQuery.value) return modelStore.models
    return modelStore.models.filter(m => m.name.toLowerCase().includes(searchQuery.value.toLowerCase()))
})

const selectedModels = computed(() =>
    modelStore.models.filter(model => selectedModelIds.value.includes(model.id))
)

const blockedSelectedModels = computed(() =>
    selectedModels.value.filter(model => (model.refCount || 0) > 0)
)

const allFilteredSelected = computed(() =>
    filteredModels.value.length > 0 &&
    filteredModels.value.every(model => selectedModelIds.value.includes(model.id))
)

const toggleSelectAllFiltered = () => {
    const filteredIds = filteredModels.value.map(model => model.id)
    if (!filteredIds.length) return
    if (allFilteredSelected.value) {
        selectedModelIds.value = selectedModelIds.value.filter(id => !filteredIds.includes(id))
        return
    }
    selectedModelIds.value = Array.from(new Set([...selectedModelIds.value, ...filteredIds]))
}

const handleBatchDeleteModels = async () => {
    if (!selectedModelIds.value.length) return
    if (blockedSelectedModels.value.length > 0) {
        const names = blockedSelectedModels.value.map(model => model.name).join('、')
        alert(`选中模型中存在被引用项，无法批量移除：${names}`)
        return
    }
    if (!confirm(`确认批量移除 ${selectedModelIds.value.length} 个模型吗？该操作不可恢复。`)) {
        return
    }
    try {
        const result = await modelStore.deleteModelsBatch(selectedModelIds.value)
        selectedModelIds.value = []
        alert(`已批量移除 ${result.deletedCount} 个模型`)
    } catch (err) {
        alert(err.message || '批量删除失败')
    }
}

watch(() => modelStore.models, (models) => {
    const validIds = new Set((models || []).map(model => model.id))
    modelStore.setSelectedModelIds(selectedModelIds.value.filter(id => validIds.has(id)))
}, { deep: true })

const getIcon = (type) => {
    const key = (type || '').toUpperCase()
    const icons = {
        PY: 'ri-code-s-slash-line text-yellow-500',
        PYTHON: 'ri-code-s-slash-line text-yellow-500',
        MAT: 'ri-functions-line text-orange-500',
        MATLAB: 'ri-functions-line text-orange-500',
        FMU: 'ri-box-3-line text-blue-500',
        DLL: 'ri-file-code-line text-gray-500',
        AME: 'ri-file-code-line text-gray-500'
    }
    return icons[key] || 'ri-file-line text-gray-400'
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

onMounted(() => {
    modelStore.loadModels()
})
</script>

<template>
  <div class="w-72 bg-white border-l border-gray-200 flex flex-col h-full">
    <!-- Header -->
    <div class="h-10 border-b border-gray-200 flex items-center justify-between px-4 bg-gray-50">
        <span class="font-bold text-xs text-gray-700">模型资产库</span>
        <div class="flex space-x-2">
            <i @click="uiStore.showRightSidebar = false" class="ri-close-line text-gray-400 cursor-pointer hover:text-gray-800"></i>
        </div>
    </div>

    <!-- Search -->
    <div class="p-3 border-b border-gray-100">
        <div class="relative">
            <input v-model="searchQuery" type="text" placeholder="搜索..." class="w-full bg-gray-50 border border-gray-200 rounded-full pl-8 pr-3 py-1.5 text-xs text-gray-600 focus:outline-none focus:border-blue-400 focus:ring-1 focus:ring-blue-100 transition-all">
            <i class="ri-search-line absolute left-2.5 top-1.5 text-gray-400 text-xs"></i>
        </div>
    </div>

    <div class="px-3 py-2 border-b border-gray-100 space-y-2">
        <div class="flex items-center justify-between">
            <span class="text-[11px] text-gray-500">已勾选 {{ selectedModelIds.length }} 个模型</span>
            <div class="space-x-2">
                <button @click="toggleSelectAllFiltered" class="text-[11px] text-gray-600 hover:underline">
                    {{ allFilteredSelected ? '取消全选' : '全选当前' }}
                </button>
                <button
                    @click="handleBatchDeleteModels"
                    :disabled="selectedModelIds.length === 0 || blockedSelectedModels.length > 0"
                    class="text-[11px] text-red-600 hover:underline disabled:text-gray-300 disabled:no-underline disabled:cursor-not-allowed"
                >
                    批量移除
                </button>
            </div>
        </div>
        <div v-if="blockedSelectedModels.length > 0" class="text-[11px] text-red-600 bg-red-50 border border-red-100 rounded px-2 py-1">
            选中项包含被规则引用的模型，无法批量删除
        </div>
    </div>
    
    <!-- List -->
    <div class="flex-1 overflow-y-auto p-2 space-y-1">
        <div v-for="model in filteredModels" :key="model.id"
             @click="modelStore.selectModel(model)"
             :class="modelStore.selectedModel?.id === model.id ? 'bg-orange-50 border-orange-200' : 'border-transparent hover:bg-gray-50'"
             class="flex items-center p-2 rounded border cursor-pointer group transition-all">
             <input
                type="checkbox"
                :value="model.id"
                v-model="selectedModelIds"
                @click.stop
                class="mr-2 rounded border-gray-300 text-blue-600 focus:ring-0"
             >
             <div class="w-8 h-8 rounded bg-white border border-gray-100 flex items-center justify-center mr-3 shadow-sm group-hover:shadow">
                 <i :class="getIcon(model.type)" class="text-lg"></i>
             </div>
             <div class="flex-1 min-w-0">
                 <div class="font-medium text-xs text-gray-700 truncate group-hover:text-orange-600">{{ model.name }}</div>
                 <div class="text-[10px] text-gray-400 flex justify-between">
                     <span>{{ model.version }}</span>
                     <span>{{ formatType(model.type) }}<template v-if="model.refCount > 0"> · 引用{{ model.refCount }}</template></span>
                 </div>
             </div>
        </div>
    </div>
  </div>
</template>
