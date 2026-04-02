<script setup>

defineOptions({ name: 'DataSourceTreeNode' })

const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  },
  rootType: {
    type: String,
    default: ''
  },
  currentId: {
    type: [String, Number],
    default: ''
  },
  onNodeClick: {
    type: Function,
    required: true
  },
  onContextMenu: {
    type: Function,
    required: true
  }
})

const isGroupNode = (node) => node.type === 'group' || (node.children && node.children.length)
const isSelected = (node) => String(props.currentId) === String(node.id)
const resolveRootType = (node) => node.rootType || props.rootType
/**
 * rt 路径下的叶子 point 视为“列节点”，点击时不触发任何动作。
 */
const isStructuredColumnLeaf = (node) => {
  const rootType = resolveRootType(node)
  const hasChildren = Array.isArray(node?.children) && node.children.length > 0
  return rootType === 'rt' && node?.type === 'point' && !hasChildren
}
const resolveGroupIcon = (node) => {
  const rootType = resolveRootType(node)
  if (rootType === 'rt') return 'ri-folder-3-line text-green-500'
  return 'ri-folder-3-line text-yellow-500'
}
const resolveLeafIcon = (node) => {
  const rootType = resolveRootType(node)
  if (node.type === 'file') return 'ri-file-2-line text-amber-500'
  if (rootType === 'rt') return 'ri-table-line text-green-500'
  return 'ri-pulse-line text-purple-400'
}

const handleNodeClick = (node) => {
  if (isStructuredColumnLeaf(node)) return
  props.onNodeClick(node)
}

const emitContextMenu = (event, node) => {
  const payload = { ...node, rootType: props.rootType }
  props.onContextMenu(event, payload)
}
</script>

<template>
  <div class="ml-4 pl-2 border-l border-gray-200 mt-1 space-y-0.5">
    <template v-for="node in nodes" :key="node.id">
      <div v-if="isGroupNode(node)"
           @click="handleNodeClick(node)"
           @contextmenu.stop="emitContextMenu($event, node)"
           class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors hover:bg-gray-100 mb-0.5"
           :class="isSelected(node) ? 'bg-blue-100 text-blue-700' : 'text-gray-600'">
        <i :class="node.expanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-[10px]"></i>
        <i :class="resolveGroupIcon(node)" class="mr-2"></i>
        <span class="truncate">{{ node.name }}</span>
      </div>

      <div v-else
           @click="handleNodeClick(node)"
           @contextmenu.stop="emitContextMenu($event, node)"
           class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors ml-2"
           :class="isSelected(node) ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'">
        <i :class="resolveLeafIcon(node)" class="mr-2 text-sm"></i>
        <span class="truncate">{{ node.name }}</span>
      </div>

      <DataSourceTreeNode
        v-if="isGroupNode(node) && node.expanded && node.children && node.children.length"
        :nodes="node.children"
        :root-type="rootType"
        :current-id="currentId"
        :on-node-click="onNodeClick"
        :on-context-menu="onContextMenu"
      />
    </template>
  </div>
</template>
