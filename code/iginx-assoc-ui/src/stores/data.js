import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchDataSourcePage,
  createDataSource,
  removeDataSource,
  testDataSourceConnection,
  fetchDataSourceDetail
} from '../api/dataSource'
import {
  importData,
  exportData,
  fetchResourceTree,
  fetchExportTask,
  queryTimeSeries,
  queryStructuredSchema,
  queryStructured,
  deleteTimeSeries,
  createStructuredRow,
  updateStructuredRow,
  deleteStructuredRow,
  deleteColumns,
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
    path: '',
    schema: '',
    table: '',
    parentType: '',
    hasChildren: false,
    isLeaf: false,
    isStructuredTable: false,
    isStructuredColumn: false,
    previewMode: '',
    previewRole: '',
    readOnly: false
  })
  const showTopologyDrawer = ref(false)
  const topologyRootNode = ref(null)

  const showAddSourceModal = ref(false)
  const showSourceDetailsModal = ref(false)
  const showExportModal = ref(false)
  const showMaintenanceModal = ref(false)
  const showDeletePathModal = ref(false)

  const showImportModal = ref(false)
  const importForm = reactive({
    path: '',
    file: null,
    keyMode: 'AUTO_GENERATED',
    keyColumn: ''
  })

  const resetImportForm = () => {
    importForm.path = ''
    importForm.file = null
    importForm.keyMode = 'AUTO_GENERATED'
    importForm.keyColumn = ''
  }

  const showTopology = (nodeOrType, id) => {
    selectNode(nodeOrType, id)
    if (['group', 'ts', 'rt', 'task'].includes(currentNode.type)) {
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
    const children = Array.isArray(node.children) ? node.children : []
    const hasChildren = children.length > 0
    const isLeaf = !hasChildren
    const resolvedPath = node.path || (node.id ? String(node.id) : '')
    const previewMode = resolveNodePreviewMode(node, rootType)
    const structuredInfo = rootType === 'rt'
      ? resolveStructuredInfo(resolvedPath, isLeaf)
      : { schema: '', table: '' }
    const hasStructuredTable = isTaskStructuredTableNode(node, rootType, previewMode) || (
      rootType === 'rt'
      && !!structuredInfo.schema
      && !!structuredInfo.table
    )
    // 在资源树中，rt 下无子节点的 point 语义是“列”；其余结构化节点视作“表/路径”。
    const isStructuredColumn = isTaskStructuredColumnNode(node, rootType, previewMode, isLeaf)
      || (rootType === 'rt' && hasStructuredTable && node.type === 'point' && isLeaf)
    currentNode.id = node.id
    currentNode.type = node.type
    currentNode.name = node.name || node.id
    currentNode.parentType = context?.parent?.type || ''
    currentNode.sourceId = resolvedSourceId != null ? Number(resolvedSourceId) : null
    currentNode.sourceType = rootType
    currentNode.rootType = rootType
    currentNode.path = resolvedPath
    currentNode.schema = hasStructuredTable ? structuredInfo.schema : ''
    currentNode.table = hasStructuredTable ? structuredInfo.table : ''
    currentNode.hasChildren = hasChildren
    currentNode.isLeaf = isLeaf
    currentNode.isStructuredTable = hasStructuredTable && !isStructuredColumn
    currentNode.isStructuredColumn = isStructuredColumn
    currentNode.previewMode = previewMode
    currentNode.previewRole = String(node?.previewRole || '').trim().toUpperCase()
    currentNode.readOnly = Boolean(node?.readOnly ?? root?.readOnly ?? rootType === 'task')
    currentNode.viewMode = 'default'
  }

  const openTopology = (node) => {
    topologyRootNode.value = node
    showTopologyDrawer.value = true
  }

  const openImportWizard = () => {
    resetImportForm()
    showImportModal.value = true
  }

  const normalizeType = (sourceType) => {
    if (['INFLUXDB', 'IOTDB'].includes(sourceType)) return 'ts'
    return 'rel'
  }

  const splitPathSegments = (value) => String(value || '')
    .replace(/^root\./i, '')
    .split('.')
    .map(item => item.trim())
    .filter(Boolean)

  /**
   * 根据 IGinX 路径前缀识别导入语义。
   * - `ts.*`：时间键语义（时序导入）
   * - `rt.*`：行键语义（行式导入）
   * - 其他前缀：不合法
   */
  const detectImportTypeByPath = (value) => {
    const normalized = String(value || '').trim().toLowerCase()
    const stripped = normalized.startsWith('root.') ? normalized.slice(5) : normalized
    if (stripped === 'ts' || stripped.startsWith('ts.')) return 'ts'
    if (stripped === 'rt' || stripped.startsWith('rt.')) return 'rt'
    return ''
  }

  const resolveStructuredInfo = (path, hasColumn) => {
    const segments = splitPathSegments(path)
    if (segments.length < 2) {
      return { schema: '', table: '' }
    }
    if (String(segments[0]).toLowerCase() !== 'rt') {
      return { schema: '', table: '' }
    }
    const tableSegments = hasColumn ? segments.slice(1, -1) : segments.slice(1)
    if (tableSegments.length === 0) {
      return { schema: '', table: '' }
    }
    if (tableSegments.length >= 2) {
      return { schema: tableSegments[0], table: tableSegments.slice(1).join('.') }
    }
    // 兼容两段式结构化路径：rt.<table>，此时 schema 应回落为 rt，避免被错误拼成 rt.public.<table>。
    return { schema: 'rt', table: tableSegments[0] }
  }

  const resolveNodePreviewMode = (node, rootType) => {
    const explicitMode = String(node?.previewMode || '').trim().toUpperCase()
    if (explicitMode === 'TIME_SERIES' || explicitMode === 'STRUCTURED') {
      return explicitMode
    }
    if (rootType === 'ts') return 'TIME_SERIES'
    if (rootType === 'rt') return 'STRUCTURED'
    return ''
  }

  const isTaskStructuredTableNode = (node, rootType, previewMode) => {
    return rootType === 'task'
      && previewMode === 'STRUCTURED'
      && String(node?.previewRole || '').trim().toUpperCase() === 'TABLE'
  }

  const isTaskStructuredColumnNode = (node, rootType, previewMode, isLeaf) => {
    if (rootType !== 'task' || previewMode !== 'STRUCTURED') {
      return false
    }
    const previewRole = String(node?.previewRole || '').trim().toUpperCase()
    return previewRole === 'COLUMN' || (node?.type === 'point' && isLeaf)
  }


  const toTreeNode = (source) => ({
    id: String(source.id),
    sourceId: source.id,
    name: source.name,
    sourceType: source.sourceType,
    description: source.description,
    createTime: source.createTime,
    connectionConfig: source.connectionConfig,
    type: normalizeType(source.sourceType)
  })

  const isInternalTsNode = (node) => {
    const candidates = [
      String(node?.id || '').toLowerCase(),
      String(node?.name || '').toLowerCase(),
      String(node?.path || '').toLowerCase()
    ]
    return candidates.some(value =>
      value.includes('iginx_tagkv')
      || value.includes('iginx_end')
      || value.includes('.%.a')
    )
  }

  const decorateResourceTree = (nodes, rootType = '') => {
    if (!Array.isArray(nodes)) return []
    return nodes
      .filter(node => !(rootType === 'ts' && isInternalTsNode(node)))
      .map(node => {
        const nextRoot = rootType || (['ts', 'rt', 'task'].includes(node.type) ? node.type : rootType)
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
    const roots = (tree || []).filter(node => ['ts', 'rt', 'task'].includes(node?.type))
    resourceTree.value = decorateResourceTree(roots)
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
        schemaPrefix: String(config.schemaPrefix || '').trim(),
        dataPrefix: String(config.dataPrefix || '').trim(),
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
      description: '',
      connectionConfig: {
        host: sourceConfig.host,
        port: Number(sourceConfig.port),
        database,
        username: sourceConfig.username,
        password: sourceConfig.password,
        hasData: sourceConfig.hasData ?? true,
        readOnly: sourceConfig.readOnly ?? false,
        schemaPrefix: String(sourceConfig.schemaPrefix || '').trim(),
        dataPrefix: String(sourceConfig.dataPrefix || '').trim(),
        extra: ''
      }
    })
    await loadDataSources()
    await loadResourceTree()
  }

  const uninstallSource = async (id) => {
    const sourceId = Number(id)
    if (!Number.isFinite(sourceId) || sourceId <= 0) {
      throw new Error('数据源 ID 无效')
    }
    await removeDataSource(sourceId)
    delete detailMap[String(sourceId)]
    await loadDataSources()
    await loadResourceTree()
  }

  /**
   * 统一导入入口：CSV + 目标路径 + KEY 方式。
   */
  const importDataByPath = async () => {
    if (!importForm.file) {
      throw new Error('请先选择导入文件')
    }
    const fileName = String(importForm.file.name || '').toLowerCase()
    if (!fileName.endsWith('.csv')) {
      throw new Error('当前仅支持 CSV 文件导入')
    }

    const rawPath = String(importForm.path || '').trim()
    if (!rawPath) {
      throw new Error('请输入导入目标路径')
    }

    const importTypeByPath = detectImportTypeByPath(rawPath)
    if (!importTypeByPath) {
      throw new Error('导入路径必须以 ts 或 rt 开头')
    }

    let keyMode = importForm.keyMode
    let keyColumn = ''
    if (importTypeByPath === 'ts') {
      keyMode = 'COLUMN'
      keyColumn = String(importForm.keyColumn || '').trim()
      if (!keyColumn) {
        throw new Error('ts 路径导入必须先选择时间列作为 KEY')
      }
    } else if (importTypeByPath === 'rt') {
      keyMode = 'AUTO_GENERATED'
      keyColumn = ''
    }

    importForm.keyMode = keyMode
    importForm.keyColumn = keyColumn

    const payload = {
      targetPath: rawPath,
      keyMode
    }

    if (keyMode === 'COLUMN') {
      payload.keyColumn = keyColumn
    }

    const result = await importData(payload, importForm.file)
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

  /**
   * 查询结构化表结构（仅列元数据，不返回行数据）。
   */
  const queryStructuredSchemaData = async (tablePath) => {
    return queryStructuredSchema(tablePath)
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

  /**
   * 按资源路径查找节点。
   * 说明：优先匹配后端返回的 path，其次回退到 id，方便从任务结果路径直接定位资源树节点。
   */
  const findNodeByPath = (path, nodes = resourceTree.value) => {
    const normalized = String(path || '').trim().replace(/\.+$/, '')
    if (!normalized) {
      return null
    }
    for (const node of nodes || []) {
      const nodePath = String(node?.path || node?.id || '').trim().replace(/\.+$/, '')
      if (nodePath === normalized) {
        return node
      }
      if (node?.children?.length) {
        const found = findNodeByPath(normalized, node.children)
        if (found) {
          return found
        }
      }
    }
    return null
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
    importForm,
    resetImportForm,
    openImportWizard,
    addSource,
    uninstallSource,
    testConnection,
    loadDataSources,
    loadResourceTree,
    loadDataSourceDetail,
    importDataByPath,
    detectImportTypeByPath,
    exportDataFile,
    pollExportTask,
    queryTimeSeriesData,
    queryStructuredSchemaData,
    queryStructuredData,
    deleteTimeSeriesRange,
    createRow,
    updateRow,
    deleteRow,
    removeChild,
    deletePath,
    findNodeByPath,
    buildDownloadUrl
  }
})

