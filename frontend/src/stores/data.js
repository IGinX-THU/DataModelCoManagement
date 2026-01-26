import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const useDataStore = defineStore('data', () => {
  const dataSourceTree = ref([
      {
          id: 'influx_local', name: 'influx_local', type: 'ts', expanded: true,
          children: [
              { 
                  id: 'root.vehicle', name: 'root.vehicle', type: 'group', expanded: true, 
                  children: [
                      { id: 'root.vehicle.speed', name: 'speed', type: 'point' },
                      { id: 'root.vehicle.temp', name: 'temp', type: 'point' },
                      { id: 'root.vehicle.gear', name: 'gear', type: 'point' },
                      { id: 'root.vehicle.status', name: 'status', type: 'point' }
                  ]
              },
              { 
                  id: 'root.factory', name: 'root.factory', type: 'group', expanded: false,
                  children: [
                       { id: 'root.factory.line1.volt', name: 'line1.volt', type: 'point' },
                       { id: 'root.factory.line1.amp', name: 'line1.amp', type: 'point' },
                       { id: 'root.factory.line2.power', name: 'line2.power', type: 'point' }
                  ]
              },
              {
                  id: 'root.weather', name: 'root.weather', type: 'group', expanded: false,
                  children: [
                      { id: 'root.weather.city1.temp', name: 'city1.temp', type: 'point' },
                      { id: 'root.weather.city1.humid', name: 'city1.humid', type: 'point' }
                  ]
              }
          ]
      },
      {
          id: 'pg_meta_db', name: 'pg_meta_db', type: 'rel', expanded: true,
          children: [
              { id: 'public.device_list', name: 'device_list', type: 'table' },
              { id: 'public.user_logs', name: 'user_logs', type: 'table' },
              { id: 'public.system_config', name: 'system_config', type: 'table' }
          ]
      }
  ])

  const currentNode = reactive({ id: '', type: '', viewMode: 'default' }) // viewMode: 'default' | 'topology'
  const showTopologyDrawer = ref(false)
  const topologyRootNode = ref(null)

  // Data Tool States
  const showAddSourceModal = ref(false)
  const showRemoveSourceModal = ref(false)
  const showSourceDetailsModal = ref(false)
  const showExportModal = ref(false)

  const showMaintenanceModal = ref(false)

  // Import Wizard State
  const showImportModal = ref(false)
  const importType = ref('ts') // 'ts' or 'struct'
  const importStep = ref(1)
  const importForm = reactive({
      source: '',
      file: null,
      mapping: [], 
      conflictStrategy: 'update', 
      autoCreateTable: false // Added for structured import
  })

  const showTopology = (type, id) => {
      currentNode.type = type
      currentNode.id = id
      // If the node is a group or schema, force viewMode to topology
      if (['group', 'schema'].includes(type)) {
          currentNode.viewMode = 'topology'
      } else {
          currentNode.viewMode = 'topology' // Default behavior for explicitly calling showTopology
      }
  }

  const selectNode = (type, id) => {
    currentNode.type = type
    currentNode.id = id
    // Always default to list/table view, user must explicitly request topology
    currentNode.viewMode = 'default'
  }

  const openTopology = (node) => {
    topologyRootNode.value = node
    showTopologyDrawer.value = true
  }

  const openImportWizard = (type = 'ts') => {
      importType.value = type
      importStep.value = 1
      importForm.file = null
      importForm.mapping = []
      importForm.autoCreateTable = false
      showImportModal.value = true
  }

  // --- Actions ---
  const testConnection = async (config) => {
      // Mock connection test
      return new Promise((resolve, reject) => {
          setTimeout(() => {
              if (config.host === 'error') reject('Connection refused')
              else resolve(true)
          }, 1000)
      })
  }

  const addSource = (sourceConfig) => {
      const newSource = {
          id: sourceConfig.name,
          name: sourceConfig.name,
          type: sourceConfig.type === 'influx' ? 'ts' : 'rel',
          expanded: true,
          children: []
      }
      // Add mock initial children based on type to make it look realistic
      if (newSource.type === 'ts') {
          newSource.children.push({
              id: `${newSource.id}.default_group`,
              name: 'default_group',
              type: 'group',
              children: []
          })
      } else {
          newSource.children.push({
              id: `${newSource.id}.public`,
              name: 'public',
              type: 'schema', // Changed to 'schema' for better distinction
              children: []
          })
      }
      dataSourceTree.value.push(newSource)
  }

  const removeSource = (id) => {
      const index = dataSourceTree.value.findIndex(s => s.id === id)
      if (index !== -1) {
          dataSourceTree.value.splice(index, 1)
          if (currentNode.id === id) {
              currentNode.id = ''
              currentNode.type = ''
          }
      }
  }

  const addChildrenToSource = (sourceId, newChildren) => {
      const source = dataSourceTree.value.find(s => s.id === sourceId)
      if (source) {
          // If source has children (groups/schemas), add to the first group for simplicity
          if (source.children.length > 0 && ['group', 'schema'].includes(source.children[0].type)) {
               source.children[0].children.push(...newChildren)
               source.children[0].expanded = true
          } else {
               source.children.push(...newChildren)
          }
          source.expanded = true
      }
  }

  const createStorageGroup = (sourceId, groupName) => {
      const source = dataSourceTree.value.find(s => s.id === sourceId)
      if (source && source.type === 'ts') {
          source.children.push({
              id: `${source.id}.${groupName}`,
              name: groupName,
              type: 'group',
              children: []
          })
          source.expanded = true
      }
  }

  const createTable = (schemaId, tableName) => {
      // Find schema node (schemaId is unique enough in mock)
      for (const source of dataSourceTree.value) {
          if (source.type === 'rel') {
             const schema = source.children.find(c => c.id === schemaId)
             if (schema) {
                 schema.children.push({
                     id: `${schemaId}.${tableName}`,
                     name: tableName,
                     type: 'table'
                 })
                 schema.expanded = true
                 return
             }
          }
      }
  }
  
  const deleteRange = (nodeId, range) => {
      console.log(`Deleting data for ${nodeId} in range`, range)
      // Mock deletion logic
  }

  const removeChild = (nodeId) => {
      // Mock validation logic
      const node = findNodeInTree(dataSourceTree.value, nodeId)
      if (node && (node.type === 'group' || node.type === 'schema') && node.children && node.children.length > 0) {
          return { success: false, msg: `Cannot delete non-empty ${node.type}: ${node.name}. Please remove all children first.` }
      }
      
      // Helper to recursively find parent and remove child
      const removeFromList = (list) => {
          const idx = list.findIndex(n => n.id === nodeId)
          if (idx !== -1) {
              list.splice(idx, 1)
              return true
          }
          for (const node of list) {
              if (node.children && removeFromList(node.children)) return true
          }
          return false
      }
      removeFromList(dataSourceTree.value)
      
      // If current selected node was deleted, deselect it
      if (currentNode.id === nodeId) {
          currentNode.id = ''
          currentNode.type = ''
      }
      return { success: true }
  }

  const findNodeInTree = (nodes, id) => {
    for (const node of nodes) {
        if (node.id === id) return node
        if (node.children) {
            const found = findNodeInTree(node.children, id)
            if (found) return found
        }
    }
    return null
  }

  return { 
      dataSourceTree, currentNode, selectNode, showTopology, showTopologyDrawer, topologyRootNode, openTopology,
      showAddSourceModal, showRemoveSourceModal, showSourceDetailsModal, showExportModal, showMaintenanceModal,
      showImportModal, importType, importStep, importForm, openImportWizard,
      addSource, removeSource, addChildrenToSource, testConnection, createStorageGroup, createTable, deleteRange, removeChild
  }
})
