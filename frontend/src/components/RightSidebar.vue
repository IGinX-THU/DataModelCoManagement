<script setup>
import { useModelStore } from '../stores/model'
import { useUIStore } from '../stores/ui'
import { ref, computed } from 'vue'

const modelStore = useModelStore()
const uiStore = useUIStore()
const searchQuery = ref('')

const filteredModels = computed(() => {
    if (!searchQuery.value) return modelStore.models
    return modelStore.models.filter(m => m.name.toLowerCase().includes(searchQuery.value.toLowerCase()))
})

const getIcon = (type) => {
    const icons = {
        'Python': 'ri-code-s-slash-line text-yellow-500',
        'MATLAB': 'ri-functions-line text-orange-500',
        'FMU': 'ri-box-3-line text-blue-500',
        'DLL': 'ri-file-code-line text-gray-500'
    }
    return icons[type] || 'ri-file-line text-gray-400'
}
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
    
    <!-- List -->
    <div class="flex-1 overflow-y-auto p-2 space-y-1">
        <div v-for="model in filteredModels" :key="model.id"
             @click="modelStore.selectModel(model)"
             :class="modelStore.selectedModel?.id === model.id ? 'bg-orange-50 border-orange-200' : 'border-transparent hover:bg-gray-50'"
             class="flex items-center p-2 rounded border cursor-pointer group transition-all">
             <div class="w-8 h-8 rounded bg-white border border-gray-100 flex items-center justify-center mr-3 shadow-sm group-hover:shadow">
                 <i :class="getIcon(model.type)" class="text-lg"></i>
             </div>
             <div class="flex-1 min-w-0">
                 <div class="font-medium text-xs text-gray-700 truncate group-hover:text-orange-600">{{ model.name }}</div>
                 <div class="text-[10px] text-gray-400 flex justify-between">
                     <span>{{ model.version }}</span>
                     <span>{{ model.type }}</span>
                 </div>
             </div>
        </div>
    </div>
  </div>
</template>
