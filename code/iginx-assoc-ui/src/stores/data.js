import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchDataSourcePage,
  createDataSource,
  testDataSourceConnection,
  fetchDataSourceStructure,
  fetchDataSourceDetail
} from '../api/dataSource'
import {
  importTimeSeries,
  importStructured,
  exportData,
  fetchResourceTree,
  fetchExportTask,
  queryTimeSeries,
  queryStructured,
  deleteTimeSeries,
  createStructuredRow,
  updateStructuredRow,
  deleteStructuredRow,
  deleteColumns,
  createStorageGroup as createStorageGroupApi,
  createMeasurement as createMeasurementApi,
  createTable as createTableApi,
  fetchTableColumns,
  buildDownloadUrl
} from '../api/dataResource'

export const useDataStore = defineStore('data', () => {
  const dataSourceTree = ref([])
  const resourceTree = ref([])
  const detailMap = reactive({})

  const currentNode = reactive({
    id: '',
    type: '',
    name: '',
    viewMode: 'default',
    sourceId: null,
    sourceType: '',
    rootType: '',
    mountPath: '',
    path: '',
    schema: '',
    table: '',
    parentType: ''
  })
  const showTopologyDrawer = ref(false)
  const topologyRootNode = ref(null)

  const showAddSourceModal = ref(false)
  const showSourceDetailsModal = ref(false)
  const showExportModal = ref(false)
  const showMaintenanceModal = ref(false)
  const showDeletePathModal = ref(false)

  const showImportModal = ref(false)
  const importType = ref('ts')
  const importStep = ref(1)
  const importForm = reactive({
    path: '',
    file: null,
    columns: [],
    storageGroup: '',
    timestampColumn: '',
    timestampFormat: 'yyyy-MM-dd HH:mm:ss',
    mapping: [],
    schema: '',
    table: '',
    fileType: '',
    sheetIndex: 0,
    primaryKeys: '',
    conflictStrategy: 'update',
    autoCreateTable: false
  })

  const resetImportForm = () => {
    importForm.path = ''
    importForm.file = null
    importForm.columns = []
    importForm.storageGroup = ''
    importForm.timestampColumn = ''
    importForm.timestampFormat = 'yyyy-MM-dd HH:mm:ss'
    importForm.mapping = []
    importForm.schema = ''
    importForm.table = ''
    importForm.fileType = ''
    importForm.sheetIndex = 0
    importForm.primaryKeys = ''
    importForm.conflictStrategy = 'update'
    importForm.autoCreateTable = false
  }

  const showTopology = (nodeOrType, id) => {
    selectNode(nodeOrType, id)
    if (['group', 'schema', 'ts', 'rt', 'models'].includes(currentNode.type)) {
      currentNode.viewMode = 'topology'
    }
  }

  const selectNode = (nodeOrType, id) => {
    const node = resolveNode(nodeOrType, id)
    if (!node) return
    const context = findNodeContext(resourceTree.value, node.id)
    const root = context?.root || node
    const rootType = node.rootType || root.rootType || root.type || ''
    const resolvedSourceId = node.sourceId ?? root.sourceId ?? null
    currentNode.id = node.id
    currentNode.type = node.type
    currentNode.name = node.name || node.id
    currentNode.parentType = context?.parent?.type || ''
    currentNode.sourceId = resolvedSourceId != null ? Number(resolvedSourceId) : null
    currentNode.sourceType = rootType
    currentNode.rootType = rootType
    currentNode.mountPath = node.mountPath || root.mountPath || ''
    currentNode.path = node.path || (['group', 'point', 'file'].includes(node.type) ? node.id : '')
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

  const decorateResourceTree = (nodes, rootType = '') => {
    if (!Array.isArray(nodes)) return []
    return nodes
      .filter(node => !(rootType === 'ts' && isInternalTsNode(node)))
      .map(node => {
        const nextRoot = rootType || (['ts', 'rt', 'models'].includes(node.type) ? node.type : rootType)
        const children = decorateResourceTree(node.children || [], nextRoot)
        return {
          ...node,
          rootType: node.rootType || nextRoot,
          expanded: node.expanded ?? true,
          selectorExpanded: node.selectorExpanded ?? false,
          children
        }
      })
  }

  const loadResourceTree = async () => {
    const tree = await fetchResourceTree()
    resourceTree.value = decorateResourceTree(tree || [])
  }

  const loadDataSources = async () => {
    const page = await fetchDataSourcePage({ pageNum: 1, pageSize: 100 })
    dataSourceTree.value = (page.records || []).map(toTreeNode)
  }

  const loadDataSourceDetail = async (id, limit = 200) => {
    if (!id) return null
    const detail = await fetchDataSourceDetail(id, limit)
    detailMap[String(id)] = detail
    return detail
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

    const database = sourceType === 'POSTGRESQL'
      ? (sourceConfig.database || sourceConfig.schema || 'postgres')
      : (sourceConfig.database || 'default')

    await createDataSource({
      name: sourceConfig.name,
      sourceType,
      mountPath: sourceConfig.mountPath,
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
    await loadResourceTree()
  }

  const importTimeSeriesData = async () => {
    if (!importForm.file) {
      throw new Error('请先选择导入文件')
    }
    const rawPath = String(importForm.storageGroup || importForm.path || '').trim()
    if (!rawPath) {
      throw new Error('请输入导入路径')
    }
    const lower = rawPath.toLowerCase()
    const normalized = lower.startsWith('root.') ? lower.slice(5) : lower
    if (!(normalized === 'ts' || normalized.startsWith('ts.'))) {
      throw new Error('时序数据导入路径必须以 ts 开头')
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
      storageGroup: rawPath,
      timestampColumn: importForm.timestampColumn,
      timestampFormat: importForm.timestampFormat || undefined,
      mappings: mappings.length > 0 ? mappings : undefined
    }
    const result = await importTimeSeries(payload, importForm.file)
    await loadResourceTree()
    return result
  }

  const importStructuredData = async () => {
    if (!importForm.file) {
      throw new Error('请先选择导入文件')
    }
    const rawPath = String(importForm.path || '').trim()
    if (!rawPath) {
      throw new Error('请输入导入路径')
    }
    const segments = rawPath.split('.').map(item => item.trim()).filter(Boolean)
    if (segments.length < 2) {
      throw new Error('结构化导入路径格式不正确')
    }
    const table = segments.pop()
    const schema = segments.join('.')
    const schemaLower = schema.toLowerCase()
    const normalizedSchema = schemaLower.startsWith('root.') ? schemaLower.slice(5) : schemaLower
    if (!(normalizedSchema === 'rt' || normalizedSchema.startsWith('rt.'))) {
      throw new Error('结构化数据导入路径必须以 rt 开头')
    }
    if (!table) {
      throw new Error('请填写目标表名')
    }
    const primaryKeys = importForm.primaryKeys
      ? importForm.primaryKeys.split(',').map(item => item.trim()).filter(Boolean)
      : undefined
    const payload = {
      schema,
      table,
      autoCreateTable: importForm.autoCreateTable,
      conflictStrategy: importForm.conflictStrategy,
      fileType: importForm.fileType || undefined,
      sheetIndex: importForm.sheetIndex || 0,
      primaryKeys
    }
    const result = await importStructured(payload, importForm.file)
    await loadResourceTree()
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
    await loadResourceTree()
  }

  const createMeasurement = async (sourceId, path, dataType = 'DOUBLE') => {
    await createMeasurementApi({ sourceId: Number(sourceId), path, dataType })
    await refreshStructure(sourceId)
    await loadResourceTree()
  }

  const createTable = async (payload) => {
    await createTableApi(payload)
    await refreshStructure(payload.sourceId)
    await loadResourceTree()
  }

  const removeChild = async (nodeId) => {
    const context = findNodeContext(resourceTree.value, nodeId)
    if (!context) {
      return { success: false, msg: '节点不存在' }
    }
    const { node } = context
    const path = node.path || node.id
    if (!path) {
      return { success: false, msg: '未获取到有效路径，无法删除' }
    }
    await deleteColumns({ path, includeChildren: true })
    await loadResourceTree()
    return { success: true }
  }

  const deletePath = async (path, includeChildren = false) => {
    const normalized = String(path || '').trim()
    if (!normalized) {
      return { success: false, msg: '路径不能为空' }
    }
    await deleteColumns({ path: normalized, includeChildren: !!includeChildren })
    await loadResourceTree()
    return { success: true }
  }

  const resolveNode = (nodeOrType, id) => {
    if (nodeOrType && typeof nodeOrType === 'object') {
      return nodeOrType
    }
    return findNodeContext(resourceTree.value, id)?.node
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
    resourceTree,
    detailMap,
    currentNode,
    selectNode,
    showTopology,
    showTopologyDrawer,
    topologyRootNode,
    openTopology,
    showAddSourceModal,
    showSourceDetailsModal,
    showExportModal,
    showMaintenanceModal,
    showDeletePathModal,
    showImportModal,
    importType,
    importStep,
    importForm,
    resetImportForm,
    openImportWizard,
    addSource,
    testConnection,
    loadDataSources,
    loadResourceTree,
    loadDataSourceDetail,
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
    createMeasurement,
    createTable,
    removeChild,
    deletePath,
    fetchTableColumns,
    buildDownloadUrl
  }
})
