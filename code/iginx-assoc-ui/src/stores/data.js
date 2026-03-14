import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchDataSourcePage,
  createDataSource,
  deleteDataSource,
  testDataSourceConnection,
  fetchDataSourceStructure
} from '../api/dataSource'
import {
  importTimeSeries,
  importStructured,
  exportData,
  fetchExportTask,
  queryTimeSeries,
  queryStructured,
  deleteTimeSeries,
  createStructuredRow,
  updateStructuredRow,
  deleteStructuredRow,
  createStorageGroup as createStorageGroupApi,
  dropStorageGroup as dropStorageGroupApi,
  createMeasurement as createMeasurementApi,
  dropMeasurement as dropMeasurementApi,
  createTable as createTableApi,
  dropTable as dropTableApi,
  fetchTableColumns,
  buildDownloadUrl
} from '../api/dataResource'

export const useDataStore = defineStore('data', () => {
  const dataSourceTree = ref([])

  const currentNode = reactive({
    id: '',
    type: '',
    name: '',
    viewMode: 'default',
    sourceId: null,
    sourceType: '',
    mountPath: '',
    path: '',
    schema: '',
    table: '',
    parentType: ''
  })
  const showTopologyDrawer = ref(false)
  const topologyRootNode = ref(null)

  const showAddSourceModal = ref(false)
  const showRemoveSourceModal = ref(false)
  const showSourceDetailsModal = ref(false)
  const showExportModal = ref(false)
  const showMaintenanceModal = ref(false)

  const showImportModal = ref(false)
  const importType = ref('ts')
  const importStep = ref(1)
  const importForm = reactive({
    source: '',
    file: null,
    columns: [],
    storageGroup: '',
    timestampColumn: '',
    timestampFormat: 'yyyy-MM-dd HH:mm:ss',
    mapping: [],
    schema: 'public',
    table: '',
    fileType: '',
    sheetIndex: 0,
    primaryKeys: '',
    conflictStrategy: 'update',
    autoCreateTable: false
  })

  const resetImportForm = () => {
    importForm.source = ''
    importForm.file = null
    importForm.columns = []
    importForm.storageGroup = ''
    importForm.timestampColumn = ''
    importForm.timestampFormat = 'yyyy-MM-dd HH:mm:ss'
    importForm.mapping = []
    importForm.schema = 'public'
    importForm.table = ''
    importForm.fileType = ''
    importForm.sheetIndex = 0
    importForm.primaryKeys = ''
    importForm.conflictStrategy = 'update'
    importForm.autoCreateTable = false
  }

  const showTopology = (nodeOrType, id) => {
    selectNode(nodeOrType, id)
    if (['group', 'schema', 'ts', 'rel'].includes(currentNode.type)) {
      currentNode.viewMode = 'topology'
    }
  }

  const selectNode = (nodeOrType, id) => {
    const node = resolveNode(nodeOrType, id)
    if (!node) return
    const context = findNodeContext(dataSourceTree.value, node.id)
    const root = context?.root || node
    currentNode.id = node.id
    currentNode.type = node.type
    currentNode.name = node.name || node.id
    currentNode.parentType = context?.parent?.type || ''
    currentNode.sourceId = Number(node.sourceId || root.sourceId || root.id) || null
    currentNode.sourceType = root.sourceType || root.type || ''
    currentNode.mountPath = root.mountPath || ''
    currentNode.path = node.path || (['group', 'point'].includes(node.type) ? node.id : '')
    currentNode.schema = node.schema || (node.type === 'schema' ? node.name : '')
    currentNode.table = node.table || (node.type === 'table' ? node.name : '')
    currentNode.viewMode = 'default'
  }

  const openTopology = (node) => {
    topologyRootNode.value = node
    showTopologyDrawer.value = true
  }

  const openImportWizard = (type = 'ts') => {
    importType.value = type
    importStep.value = 1
    resetImportForm()
    showImportModal.value = true
  }

  const normalizeType = (sourceType) => {
    if (['INFLUXDB', 'IOTDB'].includes(sourceType)) return 'ts'
    return 'rel'
  }

  const normalizeMountPath = (sourceType, rawMountPath, fallbackName) => {
    let mountPath = (rawMountPath || fallbackName || '').trim()
    mountPath = mountPath.replace(/\.+$/, '')
    if (!mountPath) return ''
    if (['INFLUXDB', 'IOTDB'].includes(sourceType)) {
      const lower = mountPath.toLowerCase()
      if (lower === 'root') {
        throw new Error('挂载路径必须为 root.xxx，不能仅 root')
      }
      if (lower.startsWith('root.')) {
        mountPath = `root.${mountPath.substring(mountPath.indexOf('.') + 1)}`
      } else {
        mountPath = `root.${mountPath}`
      }
    }
    return mountPath
  }

  const toTreeNode = (source) => ({
    id: String(source.id),
    sourceId: source.id,
    name: source.name,
    mountPath: source.mountPath,
    sourceType: source.sourceType,
    description: source.description,
    createTime: source.createTime,
    connectionConfig: source.connectionConfig,
    type: normalizeType(source.sourceType),
    expanded: true,
    children: [],
    structureLoaded: false,
    loadingStructure: false
  })

  const resolveSchemaFromId = (nodeId) => {
    const segments = String(nodeId || '').split('.')
    if (segments.length >= 2) return segments[1]
    return ''
  }

  const isInternalTsNode = (node) => {
    const candidates = [
      String(node?.id || '').toLowerCase(),
      String(node?.name || '').toLowerCase(),
      String(node?.path || '').toLowerCase()
    ]
    return candidates.some(value =>
      value.includes('.__init__')
      || value.endsWith('.__init__')
      || value.includes('iginx_tagkv')
      || value.includes('iginx_end')
      || value.includes('.%.a')
    )
  }

  const toStructureNode = (node, source) => {
    if (isInternalTsNode(node)) return null
    const item = {
      id: String(node.id),
      name: node.name,
      type: node.type,
      expanded: true,
      children: [],
      sourceId: source.sourceId
    }
    if (node.type === 'schema') {
      item.schema = node.name
    }
    if (node.type === 'table') {
      item.schema = resolveSchemaFromId(node.id) || source.schema
      item.table = node.name
    }
    if (['group', 'point'].includes(node.type)) {
      item.path = node.id
    }
    item.children = (node.children || [])
      .map(child => toStructureNode(child, source))
      .filter(Boolean)
    if (item.type === 'group' && item.children.length === 0) {
      return null
    }
    return item
  }

  const loadDataSources = async () => {
    const page = await fetchDataSourcePage({ pageNum: 1, pageSize: 100 })
    dataSourceTree.value = (page.records || []).map(toTreeNode)
  }

  const loadDataSourceStructure = async (sourceId, force = false) => {
    const source = dataSourceTree.value.find(item => item.id === String(sourceId))
    if (!source || source.loadingStructure) return
    if (source.structureLoaded && !force) return
    source.loadingStructure = true
    try {
      const structure = await fetchDataSourceStructure(sourceId)
      source.children = (structure || [])
        .map(node => toStructureNode(node, source))
        .filter(Boolean)
      source.structureLoaded = true
      source.expanded = true
    } finally {
      source.loadingStructure = false
    }
  }

  const refreshStructure = async (sourceId) => {
    const source = dataSourceTree.value.find(item => item.id === String(sourceId))
    if (source) {
      source.structureLoaded = false
    }
    await loadDataSourceStructure(sourceId, true)
  }

  const testConnection = async (config) => {
    const sourceTypeMap = {
      influx: 'INFLUXDB',
      iotdb: 'IOTDB',
      postgresql: 'POSTGRESQL',
      postgres: 'POSTGRESQL'
    }
    const sourceType = sourceTypeMap[(config.type || '').toLowerCase()] || (config.type || '').toUpperCase()
    await testDataSourceConnection({
      sourceType,
      connectionConfig: {
        host: config.host,
        port: Number(config.port),
        database: config.database || config.schema || 'default',
        username: config.username,
        password: config.password,
        hasData: config.hasData ?? true,
        readOnly: config.readOnly ?? false,
        extra: ''
      }
    })
    return true
  }

  const addSource = async (sourceConfig) => {
    const sourceTypeMap = {
      influx: 'INFLUXDB',
      iotdb: 'IOTDB',
      postgresql: 'POSTGRESQL',
      postgres: 'POSTGRESQL'
    }
    const sourceType = sourceTypeMap[sourceConfig.type]
    if (!sourceType) {
      throw new Error(`不支持的数据源类型: ${sourceConfig.type}`)
    }

    const mountPath = normalizeMountPath(sourceType, sourceConfig.mountPath, sourceConfig.name)
    if (!mountPath) {
      throw new Error('挂载路径不能为空')
    }
    const database = sourceType === 'POSTGRESQL'
      ? (sourceConfig.database || sourceConfig.schema || 'postgres')
      : (sourceConfig.database || 'default')

    await createDataSource({
      name: sourceConfig.name,
      sourceType,
      mountPath,
      description: '',
      connectionConfig: {
        host: sourceConfig.host,
        port: Number(sourceConfig.port),
        database,
        username: sourceConfig.username,
        password: sourceConfig.password,
        hasData: sourceConfig.hasData ?? true,
        readOnly: sourceConfig.readOnly ?? false,
        extra: ''
      }
    })
    await loadDataSources()
  }

  const removeSource = async (id, force = false) => {
    await deleteDataSource(id, force)
    await loadDataSources()
    if (currentNode.id === id) {
      currentNode.id = ''
      currentNode.type = ''
    }
  }

  const importTimeSeriesData = async () => {
    if (!importForm.file) {
      throw new Error('请先选择导入文件')
    }
    const source = dataSourceTree.value.find(item => item.id === String(importForm.source))
    if (!source) {
      throw new Error('请选择目标数据源')
    }
    if (!importForm.timestampColumn) {
      throw new Error('请填写时间戳列')
    }
    const mappings = (importForm.mapping || [])
      .filter(item => item.column && item.target && item.column !== importForm.timestampColumn)
      .map(item => ({
        column: item.column,
        target: item.target,
        dataType: item.dataType || item.type
      }))
    const payload = {
      sourceId: Number(importForm.source),
      storageGroup: importForm.storageGroup || source.mountPath || source.name,
      timestampColumn: importForm.timestampColumn,
      timestampFormat: importForm.timestampFormat || undefined,
      mappings: mappings.length > 0 ? mappings : undefined
    }
    const result = await importTimeSeries(payload, importForm.file)
    await refreshStructure(importForm.source)
    return result
  }

  const importStructuredData = async () => {
    if (!importForm.file) {
      throw new Error('请先选择导入文件')
    }
    if (!importForm.table) {
      throw new Error('请填写目标表名')
    }
    const primaryKeys = importForm.primaryKeys
      ? importForm.primaryKeys.split(',').map(item => item.trim()).filter(Boolean)
      : undefined
    const payload = {
      sourceId: Number(importForm.source),
      schema: importForm.schema || 'public',
      table: importForm.table,
      autoCreateTable: importForm.autoCreateTable,
      conflictStrategy: importForm.conflictStrategy,
      fileType: importForm.fileType || undefined,
      sheetIndex: importForm.sheetIndex || 0,
      primaryKeys
    }
    const result = await importStructured(payload, importForm.file)
    await refreshStructure(importForm.source)
    return result
  }

  const exportDataFile = async (payload) => {
    return exportData(payload)
  }

  const pollExportTask = async (taskId, maxAttempts = 15, intervalMs = 2000) => {
    for (let i = 0; i < maxAttempts; i += 1) {
      // eslint-disable-next-line no-await-in-loop
      const result = await fetchExportTask(taskId)
      if (result.status === 'SUCCESS' || result.status === 'FAILED') {
        return result
      }
      // eslint-disable-next-line no-await-in-loop
      await new Promise(resolve => setTimeout(resolve, intervalMs))
    }
    return fetchExportTask(taskId)
  }

  const queryTimeSeriesData = async (payload) => {
    return queryTimeSeries(payload)
  }

  const queryStructuredData = async (payload) => {
    return queryStructured(payload)
  }

  const deleteTimeSeriesRange = async (payload) => {
    return deleteTimeSeries(payload)
  }

  const createRow = async (payload) => {
    return createStructuredRow(payload)
  }

  const updateRow = async (payload) => {
    return updateStructuredRow(payload)
  }

  const deleteRow = async (payload) => {
    return deleteStructuredRow(payload)
  }

  const createStorageGroup = async (sourceId, path) => {
    await createStorageGroupApi({ sourceId: Number(sourceId), path })
    await refreshStructure(sourceId)
  }

  const dropStorageGroup = async (sourceId, path) => {
    await dropStorageGroupApi({ sourceId: Number(sourceId), path })
    await refreshStructure(sourceId)
  }

  const createMeasurement = async (sourceId, path, dataType = 'DOUBLE') => {
    await createMeasurementApi({ sourceId: Number(sourceId), path, dataType })
    await refreshStructure(sourceId)
  }

  const dropMeasurement = async (sourceId, path) => {
    await dropMeasurementApi({ sourceId: Number(sourceId), path })
    await refreshStructure(sourceId)
  }

  const createTable = async (payload) => {
    await createTableApi(payload)
    await refreshStructure(payload.sourceId)
  }

  const dropTable = async (payload) => {
    await dropTableApi(payload)
    await refreshStructure(payload.sourceId)
  }

  const removeChild = async (nodeId) => {
    const context = findNodeContext(dataSourceTree.value, nodeId)
    if (!context) {
      return { success: false, msg: '节点不存在' }
    }
    const { node, parent, root } = context
    if (node.type === 'group') {
      await dropStorageGroup(root.sourceId || root.id, node.id)
      return { success: true }
    }
    if (node.type === 'point') {
      await dropMeasurement(root.sourceId || root.id, node.id)
      return { success: true }
    }
    if (node.type === 'table') {
      const schema = node.schema || parent?.schema || ''
      await dropTable({ sourceId: root.sourceId || root.id, schema, table: node.name })
      return { success: true }
    }
    if (node.type === 'schema') {
      if (node.children && node.children.length > 0) {
        return { success: false, msg: `Schema ${node.name} 下仍有表，请先清理` }
      }
      return { success: true }
    }
    return { success: false, msg: '不支持的节点类型' }
  }

  const resolveNode = (nodeOrType, id) => {
    if (nodeOrType && typeof nodeOrType === 'object') {
      return nodeOrType
    }
    return findNodeContext(dataSourceTree.value, id)?.node
  }

  const findNodeContext = (nodes, id, parent = null, root = null) => {
    for (const node of nodes) {
      const currentRoot = root || node
      if (node.id === id) {
        return { node, parent, root: currentRoot }
      }
      if (node.children) {
        const found = findNodeContext(node.children, id, node, currentRoot)
        if (found) return found
      }
    }
    return null
  }

  return {
    dataSourceTree,
    currentNode,
    selectNode,
    showTopology,
    showTopologyDrawer,
    topologyRootNode,
    openTopology,
    showAddSourceModal,
    showRemoveSourceModal,
    showSourceDetailsModal,
    showExportModal,
    showMaintenanceModal,
    showImportModal,
    importType,
    importStep,
    importForm,
    resetImportForm,
    openImportWizard,
    addSource,
    removeSource,
    testConnection,
    loadDataSources,
    loadDataSourceStructure,
    refreshStructure,
    importTimeSeriesData,
    importStructuredData,
    exportDataFile,
    pollExportTask,
    queryTimeSeriesData,
    queryStructuredData,
    deleteTimeSeriesRange,
    createRow,
    updateRow,
    deleteRow,
    createStorageGroup,
    dropStorageGroup,
    createMeasurement,
    dropMeasurement,
    createTable,
    dropTable,
    removeChild,
    fetchTableColumns,
    buildDownloadUrl
  }
})
