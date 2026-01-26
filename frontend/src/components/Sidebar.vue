<script setup>
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const navItems = [
  { icon: 'ri-dashboard-line', route: '/dashboard', label: '系统总览' },
  { icon: 'ri-database-2-line', route: '/data', label: '数据资源' },
  { icon: 'ri-node-tree', route: '/models', label: '模型资产' },
  { icon: 'ri-links-line', route: '/relations', label: '关联管理' },
  { icon: 'ri-line-chart-line', route: '/analysis', label: '可视化分析' },
  { icon: 'ri-settings-3-line', route: '/settings', label: '系统设置' },
]
</script>

<template>
  <aside class="w-12 bg-[#252526] border-r border-black flex flex-col items-center py-2 z-20 shrink-0">
    <div class="mb-4 text-blue-500 font-bold text-xl">
        <i class="ri-apps-fill"></i>
    </div>
    <div 
      v-for="item in navItems" 
      :key="item.route"
      @click="router.push(item.route)"
      :class="[
        'w-8 h-8 flex items-center justify-center rounded mb-3 cursor-pointer transition-colors',
        route.path.startsWith(item.route) 
          ? 'bg-boxy-accent text-white shadow-lg shadow-blue-900/50' 
          : 'text-gray-500 hover:text-gray-200 hover:bg-[#333]'
      ]"
      :title="item.label"
    >
      <i :class="[item.icon, 'text-lg']"></i>
    </div>
    
    <div class="mt-auto mb-2">
         <div class="w-8 h-8 flex items-center justify-center rounded cursor-pointer text-gray-500 hover:text-white">
            <i class="ri-user-3-line"></i>
         </div>
    </div>
  </aside>
</template>
