<script setup>
import { useDataStore } from '../stores/data'
import { useUIStore } from '../stores/ui'
import { ref, reactive } from 'vue'

const dataStore = useDataStore()
const uiStore = useUIStore()
const searchQuery = ref('')

const toggleExpand = (node) => {
    if (node.children) node.expanded = !node.expanded
}

const handleNodeClick = (node) => {
    toggleExpand(node)
    // Always show details/list view by default
    dataStore.selectNode(node.type, node.id)
}

// --- Context Menu Logic ---
const contextMenu = reactive({ visible: false, x: 0, y: 0, node: null })

const showContextMenu = (e, node) => {
    e.preventDefault()
    contextMenu.visible = true
    contextMenu.x = e.clientX
    contextMenu.y = e.clientY
    contextMenu.node = node
}

const closeContextMenu = () => {
    contextMenu.visible = false
}

const handleContextAction = (action) => {
    closeContextMenu()
    const node = contextMenu.node
    
    // Dispatch action based on selection
    if (action === 'New Storage Group') {
        const name = prompt('Enter Storage Group Name:')
        if (name) dataStore.createStorageGroup(node.id, name)
    } else if (action === 'New Measurement') {
        alert('Please use the Import Wizard to add measurements in bulk.')
    } else if (action === 'New Table') {
        const name = prompt('Enter Table Name:')
        if (name) dataStore.createTable(node.id, name)
    } else if (action === 'Delete') {
        // Strict Requirement Check: Only root sources can be removed
        if (['ts', 'rel'].includes(node.type)) {
             alert("Please use the 'Uninstall Data Source' button in the top toolbar to unregister this source.")
        } else {
             // Allow deletion of child structures
             if (confirm(`Are you sure you want to delete ${node.name}? This operation is irreversible.`)) {
                 dataStore.removeChild(node.id)
             }
        }
    }
}
</script>

<template>
  <div class="w-72 bg-white border-r border-gray-200 flex flex-col h-full relative" @contextmenu.prevent>
    <!-- Header -->
    <div class="h-10 border-b border-gray-200 flex items-center justify-between px-4 bg-gray-50">
        <span class="font-bold text-xs text-gray-700">数据资源库</span>
        <div class="flex space-x-2">
            <i @click="uiStore.showLeftSidebar = false" class="ri-close-line text-gray-400 cursor-pointer hover:text-gray-800"></i>
        </div>
    </div>
    
    <!-- Tree -->
    <div class="flex-1 overflow-y-auto p-2">
        <div v-for="source in dataStore.dataSourceTree" :key="source.id" class="mb-1">
             <!-- Root Node -->
             <div class="flex items-center px-2 py-1.5 hover:bg-blue-50 cursor-pointer rounded select-none group justify-between"
                  :class="dataStore.currentNode.id === source.id ? 'bg-blue-100' : ''">
                 <div @click="handleNodeClick(source)" @contextmenu.stop="showContextMenu($event, source)" class="flex items-center flex-1">
                     <i :class="source.expanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-xs"></i>
                     <i :class="source.type === 'ts' ? 'ri-database-2-fill text-blue-500' : 'ri-server-fill text-indigo-500'" class="mr-2 text-lg"></i>
                     <span class="text-xs font-medium text-gray-700 group-hover:text-blue-600" :class="dataStore.currentNode.id === source.id ? 'text-blue-700 font-bold' : ''">{{ source.name }}</span>
                 </div>
                 <button @click.stop="dataStore.showTopology(source.type, source.id)" 
                         class="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-100 rounded transition-all"
                         title="查看拓扑图">
                     <i class="ri-node-tree text-xs"></i>
                 </button>
             </div>
             
             <!-- Children -->
             <div v-show="source.expanded" class="ml-4 pl-2 border-l border-gray-200 mt-1 space-y-0.5">
                 <template v-for="child in source.children" :key="child.id">
                     <!-- Group/Schema Node -->
                     <div v-if="['group', 'schema'].includes(child.type)" 
                          @click="handleNodeClick(child)"
                          @contextmenu.stop="showContextMenu($event, { ...child, parentType: source.type })"
                          class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors hover:bg-gray-100 mb-0.5"
                          :class="dataStore.currentNode.id === child.id ? 'bg-blue-100 text-blue-700' : 'text-gray-600'">
                          <i :class="child.expanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-[10px]"></i>
                          <i :class="child.type === 'group' ? 'ri-folder-3-line text-yellow-500' : 'ri-layout-grid-line text-orange-500'" class="mr-2"></i>
                          <span class="truncate">{{ child.name }}</span>
                     </div>

                     <!-- Leaf Node (Point/Table) -->
                     <div v-else
                          @click="handleNodeClick(child)"
                          @contextmenu.stop="showContextMenu($event, { ...child, parentType: source.type })"
                          :class="dataStore.currentNode.id === child.id ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'"
                          class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors ml-2">
                         <i :class="source.type === 'ts' ? 'ri-pulse-line text-purple-400' : 'ri-table-line text-green-500'" class="mr-2 text-sm"></i>
                         <span class="truncate">{{ child.name }}</span>
                     </div>
                     
                     <!-- Render children of group if expanded -->
                     <div v-if="['group', 'schema'].includes(child.type) && child.expanded" class="ml-4 pl-2 border-l border-gray-200 mt-0.5 mb-1">
                         <div v-for="grandChild in child.children" :key="grandChild.id"
                              @click="handleNodeClick(grandChild)"
                              @contextmenu.stop="showContextMenu($event, { ...grandChild, parentType: source.type })"
                              class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors text-gray-500 hover:bg-gray-100"
                              :class="dataStore.currentNode.id === grandChild.id ? 'bg-blue-100 text-blue-700' : ''">
                             <i :class="source.type === 'ts' ? 'ri-focus-2-line text-cyan-500' : 'ri-table-line text-green-500'" class="mr-2 text-xs"></i>
                             <span>{{ grandChild.name }}</span>
                         </div>
                     </div>
                 </template>
             </div>
        </div>
    </div>

    <!-- Context Menu -->
    <div v-if="contextMenu.visible" 
         :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
         class="fixed z-[100] bg-white border border-gray-200 shadow-xl rounded w-48 py-1 text-xs text-gray-700">
         
         <!-- Time Series Menus -->
         <div v-if="contextMenu.node.type === 'ts'" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Storage Group')">
            <i class="ri-folder-add-line mr-2 text-blue-500"></i>New Storage Group
         </div>
         <div v-if="contextMenu.node.type === 'group'" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Measurement')">
            <i class="ri-pulse-line mr-2 text-purple-500"></i>Create Measurement
         </div>

         <!-- Relational Menus -->
         <div v-if="contextMenu.node.type === 'schema'" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Table')">
            <i class="ri-table-line mr-2 text-green-500"></i>New Table
         </div>
         
         <!-- Common -->
         <div class="border-t border-gray-100 my-1"></div>
         <div class="px-4 py-2 hover:bg-red-50 cursor-pointer flex items-center text-red-600" @click="handleContextAction('Delete')">
            <i class="ri-delete-bin-line mr-2"></i>Delete
         </div>
    </div>
  </div>
</template>
