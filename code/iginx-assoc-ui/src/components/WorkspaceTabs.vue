<script setup>
import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const route = useRoute()
const router = useRouter()

// In a real app, this would be in a Pinia store
const tabs = ref([
  { id: 'dashboard', label: '系统总览', path: '/dashboard', icon: 'ri-dashboard-fill' },
  { id: 'data', label: '数据查询与编辑', path: '/data', icon: 'ri-database-2-fill' }
])

const activeTab = ref(route.path)

watch(() => route.path, (newPath) => {
    // Simple logic to activate tab based on route
    if (newPath.includes('dashboard')) activeTab.value = '/dashboard'
    else if (newPath.includes('data')) activeTab.value = '/data'
})
</script>

<template>
  <div class="h-9 bg-[#1e1e1e] flex items-end px-2 space-x-1 border-b border-black select-none">
    <div 
      v-for="tab in tabs" 
      :key="tab.id"
      @click="router.push(tab.path)"
      :class="[
        'h-8 px-3 flex items-center text-xs rounded-t cursor-pointer border-t border-l border-r min-w-[140px] max-w-[200px] group transition-colors',
        route.path.startsWith(tab.path)
          ? 'bg-[#1e1e1e] text-gray-200 border-gray-800 border-b-[#1e1e1e] relative -mb-px z-10'
          : 'bg-[#2d2d2d] text-gray-500 border-transparent hover:bg-[#333]'
      ]"
    >
      <i :class="[tab.icon, 'mr-2', route.path.startsWith(tab.path) ? 'text-blue-400' : 'text-gray-500']"></i>
      <span class="flex-1 truncate">{{ tab.label }}</span>
      <i class="ri-close-line ml-2 opacity-0 group-hover:opacity-100 hover:bg-gray-600 rounded text-gray-400 hover:text-white transition-opacity"></i>
    </div>
    
    <div class="h-8 w-8 flex items-center justify-center text-gray-500 hover:text-white cursor-pointer">
        <i class="ri-add-line"></i>
    </div>
  </div>
</template>
