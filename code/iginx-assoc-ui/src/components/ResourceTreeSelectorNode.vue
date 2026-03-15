<script setup>
defineOptions({ name: 'ResourceTreeSelectorNode' })

const props = defineProps({
  nodes: {
    type: Array,
    default: () => []
  },
  rootType: {
    type: String,
    default: ''
  },
  allowGroupSelect: {
    type: Boolean,
    default: false
  },
  onSelect: {
    type: Function,
    required: true
  }
})

const isGroupNode = (node) => ['group', 'schema', 'ts', 'rt', 'models'].includes(node.type) || (node.children && node.children.length)
const isLeafNode = (node) => ['point', 'table', 'file'].includes(node.type)
const resolveRootType = (node) => node.rootType || props.rootType
const resolveGroupIcon = (node) => {
  if (node.type === 'ts') return 'ri-pulse-line text-blue-500'
  if (node.type === 'rt') return 'ri-table-line text-green-500'
  if (node.type === 'models') return 'ri-folder-3-line text-orange-500'
  if (node.type === 'schema') return 'ri-layout-grid-line text-orange-500'
  return 'ri-folder-3-line text-yellow-500'
}
const resolveLeafIcon = (node) => {
  const rootType = resolveRootType(node)
  if (node.type === 'file' || rootType === 'models') return 'ri-file-2-line text-amber-500'
  if (node.type === 'table' || rootType === 'rt') return 'ri-table-line text-green-500'
  return 'ri-pulse-line text-purple-400'
}

const toggleNode = (node) => {
  if (node.children && node.children.length) {
    node.selectorExpanded = !node.selectorExpanded
  }
}

const handleClick = (node) => {
  if (isGroupNode(node)) {
    toggleNode(node)
    if (props.allowGroupSelect) {
      props.onSelect(node)
    }
    return
  }
  if (isLeafNode(node)) {
    props.onSelect(node)
  }
}
</script>

<template>
  <div class="ml-4 pl-2 border-l border-gray-200 mt-1 space-y-0.5">
    <template v-for="node in nodes" :key="node.id">
      <div v-if="isGroupNode(node)"
           @click="handleClick(node)"
           class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors hover:bg-gray-100 mb-0.5 select-none">
        <i :class="node.selectorExpanded ? 'ri-arrow-down-s-fill' : 'ri-arrow-right-s-fill'" class="text-gray-400 mr-1 text-[10px]"></i>
        <i :class="resolveGroupIcon(node)" class="mr-2"></i>
        <span class="truncate font-medium text-gray-700">{{ node.name }}</span>
      </div>

      <div v-else
           @click="handleClick(node)"
           class="flex items-center px-2 py-1 cursor-pointer rounded text-xs transition-colors ml-2 text-gray-600 hover:bg-blue-50 hover:text-blue-600 select-none">
        <i :class="resolveLeafIcon(node)" class="mr-2 text-sm"></i>
        <span class="truncate">{{ node.name }}</span>
      </div>

      <ResourceTreeSelectorNode
        v-if="isGroupNode(node) && node.selectorExpanded && node.children && node.children.length"
        :nodes="node.children"
        :root-type="resolveRootType(node)"
        :allow-group-select="allowGroupSelect"
        :on-select="onSelect"
      />
    </template>
  </div>
</template>
