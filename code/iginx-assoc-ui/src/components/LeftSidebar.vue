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

const handleNodeClick = (node) => {
    toggleExpand(node)
    if (['ts', 'rel'].includes(node.type)) {
        dataStore.loadDataSourceStructure(node.id).catch(err => {
            console.error('加载数据源结构失败', err)
        })
    }
    dataStore.selectNode(node)
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
        if (action === 'New Storage Group') {
            const name = prompt('请输入存储组路径（相对路径将自动挂载）：')
            if (name) {
                const basePath = node.type === 'ts'
                  ? (node.mountPath || node.name)
                  : (node.path || node.id || node.name)
                const fullPath = name.includes('.') ? name : `${basePath}.${name}`
                await dataStore.createStorageGroup(node.sourceId || node.id, fullPath)
            }
        } else if (action === 'New Measurement') {
            const name = prompt('请输入测点名称：')
            if (!name) return
            const dataType = prompt('请输入数据类型（如 DOUBLE、LONG）：', 'DOUBLE') || 'DOUBLE'
            const fullPath = name.includes('.') ? name : `${node.id}.${name}`
            await dataStore.createMeasurement(node.sourceId || node.id, fullPath, dataType)
        } else if (action === 'New Table') {
            const name = prompt('请输入表名：')
            if (!name) return
            const raw = prompt('请输入字段定义，例如：id:BIGINT:pk,name:TEXT,created_at:TIMESTAMP', '')
            if (!raw) return
            const columnDefs = raw.split(',').map(item => item.trim()).filter(Boolean)
            const columns = []
            const primaryKeys = []
            columnDefs.forEach(def => {
                const parts = def.split(':').map(p => p.trim()).filter(Boolean)
                if (parts.length >= 2) {
                    const [colName, colType, ...flags] = parts
                    const flagSet = new Set(flags.map(f => f.toLowerCase()))
                    const nullable = !flagSet.has('notnull') && !flagSet.has('nn')
                    columns.push({ name: colName, type: colType, nullable })
                    if (flagSet.has('pk')) {
                        primaryKeys.push(colName)
                    }
                }
            })
            if (columns.length === 0) {
                alert('字段定义格式不正确')
                return
            }
            await dataStore.createTable({
                sourceId: Number(node.sourceId || node.id),
                schema: node.schema || node.name,
                table: name,
                columns,
                primaryKeys
            })
        } else if (action === 'Delete') {
            if (['ts', 'rel'].includes(node.type)) {
                 alert('请使用上方工具栏的“卸载数据源”进行删除')
            } else {
                 if (confirm(`确认删除 ${node.name} 吗？该操作不可恢复。`)) {
                     const res = await dataStore.removeChild(node.id)
                     if (!res.success) {
                         alert(res.msg)
                     }
                 }
            }
        }
    } catch (e) {
        alert(e.message || '操作失败')
    }
}

onMounted(() => {
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
        <div v-for="source in dataStore.dataSourceTree" :key="source.id" class="mb-1">
             <!-- Root Node -->
             <div class="flex items-center px-2 py-1.5 hover:bg-blue-50 cursor-pointer rounded select-none group justify-between"
                  :class="dataStore.currentNode.id === source.id ? 'bg-blue-100' : ''">
                 <div @click="handleNodeClick(source)" @contextmenu.stop="showContextMenu($event, { ...source, rootType: source.type })" class="flex items-center flex-1">
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
         
         <!-- Time Series Menus -->
         <div v-if="contextMenu.node.type === 'ts' || (contextMenu.node.type === 'group' && contextMenu.node.rootType === 'ts')" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Storage Group')">
            <i class="ri-folder-add-line mr-2 text-blue-500"></i>新建存储组
         </div>
         <div v-if="contextMenu.node.type === 'group'" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Measurement')">
            <i class="ri-pulse-line mr-2 text-purple-500"></i>新建测点
         </div>

         <!-- Relational Menus -->
         <div v-if="contextMenu.node.type === 'schema'" class="px-4 py-2 hover:bg-gray-100 cursor-pointer flex items-center" @click="handleContextAction('New Table')">
            <i class="ri-table-line mr-2 text-green-500"></i>新建表
         </div>
         
         <!-- Common -->
         <div class="border-t border-gray-100 my-1"></div>
         <div class="px-4 py-2 hover:bg-red-50 cursor-pointer flex items-center text-red-600" @click="handleContextAction('Delete')">
            <i class="ri-delete-bin-line mr-2"></i>删除
         </div>
    </div>
  </div>
</template>
