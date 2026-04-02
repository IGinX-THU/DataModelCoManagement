<script setup>
import { useDataStore } from '../stores/data'
import { useUIStore } from '../stores/ui'
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import DataSourceTreeNode from './DataSourceTreeNode.vue'

const dataStore = useDataStore()
const uiStore = useUIStore()
const searchQuery = ref('')

const toggleExpand = (node) => {
    if (node.children) node.expanded = !node.expanded
}

/**
 * 结构化数据列节点（rt 下无子节点的 point）点击时不触发任何动作。
 */
const isStructuredColumnLeaf = (node) => {
    const rootType = node?.rootType || ''
    const hasChildren = Array.isArray(node?.children) && node.children.length > 0
    return rootType === 'rt' && node?.type === 'point' && !hasChildren
}

/**
 * 点击“非 column 的路径节点”时，自动切到拓扑视图。
 * 这里按节点类型识别路径节点：group / ts / rt。
 */
const shouldOpenTopologyOnClick = (node) => {
    if (!node) return false
    if (isStructuredColumnLeaf(node)) return false
    // 结构化表节点应进入数据查询视图，而不是拓扑视图。
    if (dataStore.currentNode.rootType === 'rt' && dataStore.currentNode.isStructuredTable) return false
    return ['group', 'ts', 'rt'].includes(node.type)
}

const handleNodeClick = (node) => {
    if (isStructuredColumnLeaf(node)) {
        return
    }
    toggleExpand(node)
    dataStore.selectNode(node)
    if (shouldOpenTopologyOnClick(node)) {
        dataStore.showTopology(node)
    }
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
    contextMenu.node = null
}

const contextMenuRef = ref(null)

const handleGlobalMouseDown = (e) => {
    if (!contextMenu.visible) return
    const menuEl = contextMenuRef.value
    if (menuEl && menuEl.contains(e.target)) return
    closeContextMenu()
}

const handleGlobalKeyDown = (e) => {
    if (e.key === 'Escape') {
        closeContextMenu()
    }
}

const handleContextAction = async (action) => {
    const node = contextMenu.node
    closeContextMenu()
    if (!node) return
    try {
        if (action === 'Delete') {
            if (['ts', 'rt'].includes(node.type)) {
                alert('根节点不支持直接删除')
                return
            }
            dataStore.selectNode(node)
            dataStore.showDeletePathModal = true
        }
    } catch (e) {
        alert(e.message || '操作失败')
    }
}

onMounted(() => {
    dataStore.loadResourceTree().catch(err => {
        console.error('加载数据资源树失败', err)
    })
    dataStore.loadDataSources().catch(err => {
        console.error('加载数据源列表失败', err)
    })
    document.addEventListener('mousedown', handleGlobalMouseDown)
    document.addEventListener('keydown', handleGlobalKeyDown)
})

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', handleGlobalMouseDown)
    document.removeEventListener('keydown', handleGlobalKeyDown)
})
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
        <div v-for="source in dataStore.resourceTree" :key="source.id" class="mb-1">
             <!-- Root Node -->
             <div class="flex items-center px-2 py-1.5 hover:bg-blue-50 cursor-pointer rounded select-none group justify-between"
                  :class="dataStore.currentNode.id === source.id ? 'bg-blue-100' : ''">
                 <div @click="handleNodeClick(source)" @contextmenu.stop="showContextMenu($event, { ...source, rootType: source.type })" class="flex items-center flex-1">
                     <i :class="source.expanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-xs"></i>
                     <i :class="source.type === 'ts' ? 'ri-pulse-line text-blue-500' : (source.type === 'rt' ? 'ri-table-line text-green-500' : 'ri-folder-3-line text-orange-500')" class="mr-2 text-lg"></i>
                     <span class="text-xs font-medium text-gray-700 group-hover:text-blue-600" :class="dataStore.currentNode.id === source.id ? 'text-blue-700 font-bold' : ''">{{ source.name }}</span>
                 </div>
                <button @click.stop="dataStore.showTopology(source.type, source.id)" 
                        class="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-blue-600 hover:bg-blue-100 rounded transition-all"
                        title="查看拓扑图">
                    <i class="ri-node-tree text-xs"></i>
                </button>
             </div>
             
             <!-- Children -->
             <DataSourceTreeNode
               v-show="source.expanded"
               :nodes="source.children"
               :root-type="source.type"
               :current-id="dataStore.currentNode.id"
               :on-node-click="handleNodeClick"
               :on-context-menu="showContextMenu"
             />
        </div>
    </div>

    <!-- Context Menu -->
    <div v-if="contextMenu.visible"
         ref="contextMenuRef"
         :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }"
         class="fixed z-[100] bg-white border border-gray-200 shadow-xl rounded w-48 py-1 text-xs text-gray-700">
         
         <!-- Common -->
         <div class="px-4 py-2 hover:bg-red-50 cursor-pointer flex items-center text-red-600" @click="handleContextAction('Delete')">
            <i class="ri-delete-bin-line mr-2"></i>删除
         </div>
    </div>
  </div>
</template>
