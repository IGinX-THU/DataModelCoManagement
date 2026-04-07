<script setup>
import * as echarts from 'echarts'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import {
  createTaskChain,
  deleteTaskChain,
  fetchTaskChainDetail,
  fetchTaskChainRuleOptions,
  fetchTaskChainRunDetail,
  fetchTaskChainRuns,
  fetchTaskChains,
  stopTaskChainRun,
  submitTaskChainRun,
  updateTaskChain
} from '../api/taskChain'

const chains = ref([])
const compatibleRules = ref([])
const selectedChainId = ref(null)
const runs = ref([])
const selectedRunId = ref(null)
const selectedRun = ref(null)
const loading = ref(false)
const saving = ref(false)
const showRunModal = ref(false)
const submittingRun = ref(false)
const centerViewMode = ref('editor')
const showGuide = ref(true)
const showAdvanced = ref(false)
const showRunAdvanced = ref(false)
const topologyChartRef = ref(null)

const editor = reactive({
  id: null,
  chainName: '',
  chainMode: '',
  nodes: []
})

const runForm = reactive({
  runName: '',
  startTime: '',
  endTime: '',
  scheduledStartTime: '',
  scheduledEndTime: ''
})

let pollTimer = null
let topologyChart = null

const deepCopy = (value) => JSON.parse(JSON.stringify(value ?? null))

const formatTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ')
}

const normalizeInputTime = (value) => {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.length === 16 ? `${text}:00` : text.split('.')[0]
}

const parseInputMillis = (value) => {
  if (!value) return 0
  const millis = Date.parse(String(value).replace(' ', 'T'))
  return Number.isFinite(millis) ? millis : 0
}

const resolveStatusClass = (status) => {
  switch (status) {
    case 'RUNNING': return 'bg-blue-50 text-blue-700 border-blue-200'
    case 'SUCCESS': return 'bg-green-50 text-green-700 border-green-200'
    case 'FAILED': return 'bg-red-50 text-red-700 border-red-200'
    case 'PENDING': return 'bg-amber-50 text-amber-700 border-amber-200'
    case 'ABORTED': return 'bg-gray-100 text-gray-700 border-gray-200'
    default: return 'bg-gray-100 text-gray-600 border-gray-200'
  }
}

const ruleMap = computed(() => {
  const map = {}
  compatibleRules.value.forEach((item) => {
    map[String(item.ruleId)] = item
  })
  return map
})

const editorChainMode = computed(() => {
  if (editor.chainMode) return editor.chainMode
  const modes = editor.nodes.map(node => node.chainMode).filter(Boolean)
  return modes[0] || ''
})

const activeRuns = computed(() => runs.value.filter(item => ['PENDING', 'RUNNING'].includes(item.status)))

/**
 * 用于给首次使用者展示最短操作路径，避免一次看到过多高级信息。
 */
const setupChecklist = computed(() => {
  const hasName = Boolean(String(editor.chainName || '').trim())
  const hasNodes = editor.nodes.length > 0
  const allInputsReady = editor.nodes.every((node) => {
    const sources = Object.values(node.inputSources || {})
    if (!sources.length) return true
    return sources.every((source) => {
      if (!source) return false
      if (source.sourceType === 'UPSTREAM') {
        return Boolean(String(source.sourceNodeId || '').trim())
          && Boolean(String(source.sourceOutputName || '').trim())
      }
      return Boolean(String(source.path || '').trim())
    })
  })
  return [
    { title: '第 1 步', label: '填写任务链名称', done: hasName },
    { title: '第 2 步', label: '至少配置 1 个节点', done: hasNodes },
    { title: '第 3 步', label: '完成每个输入的来源配置', done: hasNodes && allInputsReady },
    { title: '第 4 步', label: '保存后即可运行任务链', done: Boolean(editor.id) }
  ]
})

const showRightPanel = computed(() => showAdvanced.value || runs.value.length > 0 || Boolean(selectedRun.value))

/**
 * 统一构建任务链拓扑图，编辑区和拓扑预览共用同一套层级关系。
 */
const buildTopologyGraph = (nodes = []) => {
  const nodeMap = {}
  const outgoing = {}
  const indegree = {}
  const edges = []
  ;(nodes || []).forEach((node) => {
    if (!node?.nodeId) return
    nodeMap[node.nodeId] = node
    outgoing[node.nodeId] = new Set()
    indegree[node.nodeId] = 0
  })
  ;(nodes || []).forEach((node) => {
    Object.entries(node?.inputSources || {}).forEach(([inputName, source]) => {
      if (!source || String(source.sourceType || '').trim().toUpperCase() !== 'UPSTREAM' || !nodeMap[source.sourceNodeId]) {
        return
      }
      edges.push({
        fromId: source.sourceNodeId,
        fromName: nodeMap[source.sourceNodeId]?.nodeName || source.sourceNodeId,
        outputName: source.sourceOutputName,
        toId: node.nodeId,
        toName: node.nodeName || node.nodeId,
        inputName
      })
      if (!outgoing[source.sourceNodeId].has(node.nodeId)) {
        outgoing[source.sourceNodeId].add(node.nodeId)
        indegree[node.nodeId] += 1
      }
    })
  })

  const queue = Object.keys(indegree).filter(id => indegree[id] === 0)
  const levelMap = {}
  queue.forEach(id => {
    levelMap[id] = 0
  })
  const order = []
  while (queue.length) {
    const current = queue.shift()
    order.push(current)
    ;[...outgoing[current]].forEach((nextId) => {
      levelMap[nextId] = Math.max(levelMap[nextId] || 0, (levelMap[current] || 0) + 1)
      indegree[nextId] -= 1
      if (indegree[nextId] === 0) {
        queue.push(nextId)
      }
    })
  }

  const levels = []
  order.forEach((id) => {
    const level = levelMap[id] || 0
    if (!levels[level]) {
      levels[level] = []
    }
    levels[level].push(nodeMap[id])
  })
  if (order.length < (nodes || []).length) {
    const unresolved = (nodes || []).filter(node => !order.includes(node.nodeId))
    if (unresolved.length) {
      levels.push(unresolved)
    }
  }

  return {
    edges,
    levels,
    hasCycle: order.length < Object.keys(nodeMap).length
  }
}

const graphPreview = computed(() => buildTopologyGraph(editor.nodes))
const isTopologyView = computed(() => centerViewMode.value === 'topology' && Boolean(selectedChainId.value))

/**
 * 把任务链 DAG 映射成 ECharts 固定坐标图，确保多父节点场景也能稳定展示。
 */
const buildTopologyChartData = (graph) => {
  const levels = graph?.levels || []
  const edges = graph?.edges || []
  const levelGap = 260
  const nodeGap = 118
  const leftPadding = 90
  const topPadding = 90
  const maxLevelSize = Math.max(...levels.map(level => level.length), 1)
  const totalHeight = Math.max((maxLevelSize - 1) * nodeGap, 0)
  const indegreeMap = {}
  const outdegreeMap = {}

  edges.forEach((edge) => {
    indegreeMap[edge.toId] = (indegreeMap[edge.toId] || 0) + 1
    outdegreeMap[edge.fromId] = (outdegreeMap[edge.fromId] || 0) + 1
  })

  const nodes = []
  levels.forEach((level, levelIndex) => {
    const levelHeight = Math.max((level.length - 1) * nodeGap, 0)
    const startY = topPadding + (totalHeight - levelHeight) / 2
    level.forEach((node, nodeIndex) => {
      const indegree = indegreeMap[node.nodeId] || 0
      const outdegree = outdegreeMap[node.nodeId] || 0
      let color = '#64748b'
      if (graph?.hasCycle && levelIndex === levels.length - 1 && indegree > 0 && outdegree > 0) {
        color = '#ef4444'
      } else if (indegree === 0 && outdegree > 0) {
        color = '#3b82f6'
      } else if (indegree > 0 && outdegree === 0) {
        color = '#10b981'
      } else if (indegree > 0 && outdegree > 0) {
        color = '#f59e0b'
      }
      nodes.push({
        id: node.nodeId,
        name: node.nodeName || node.nodeId,
        x: leftPadding + levelIndex * levelGap,
        y: startY + nodeIndex * nodeGap,
        ruleName: node.ruleName || '-',
        functionName: node.functionName || '-',
        modelName: node.modelName || '-',
        itemStyle: {
          color,
          borderColor: '#ffffff',
          borderWidth: 2,
          shadowBlur: 10,
          shadowColor: 'rgba(148, 163, 184, 0.18)'
        }
      })
    })
  })

  const links = edges.map((edge) => ({
    source: edge.fromId,
    target: edge.toId,
    outputName: edge.outputName || '-',
    inputName: edge.inputName || '-',
    sourceName: edge.fromName || edge.fromId,
    targetName: edge.toName || edge.toId
  }))

  return {
    nodes,
    links
  }
}

const disposeTopologyChart = () => {
  if (topologyChart) {
    topologyChart.dispose()
    topologyChart = null
  }
}

const resizeTopologyChart = () => {
  if (topologyChart) {
    topologyChart.resize()
  }
}

const renderTopologyChart = () => {
  if (!isTopologyView.value || !topologyChartRef.value) return
  if (!topologyChart) {
    topologyChart = echarts.init(topologyChartRef.value)
  }

  const chartData = buildTopologyChartData(graphPreview.value)
  topologyChart.setOption({
    animationDuration: 260,
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: {
        color: '#e2e8f0',
        fontSize: 12
      },
      formatter: (params) => {
        if (params.dataType === 'edge') {
          return `${params.data.sourceName}.${params.data.outputName}<br/>→ ${params.data.targetName}.${params.data.inputName}`
        }
        return `${params.data.name}<br/>规则：${params.data.ruleName}<br/>模型：${params.data.modelName}<br/>函数：${params.data.functionName}`
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        coordinateSystem: null,
        roam: true,
        draggable: false,
        data: chartData.nodes,
        links: chartData.links,
        symbolSize: 18,
        edgeSymbol: ['circle', 'arrow'],
        edgeSymbolSize: [5, 10],
        lineStyle: {
          color: '#cbd5e1',
          width: 2,
          curveness: 0.18
        },
        label: {
          show: true,
          position: 'right',
          color: '#334155',
          fontSize: 12,
          backgroundColor: '#ffffff',
          padding: [4, 8],
          borderRadius: 8,
          shadowBlur: 8,
          shadowColor: 'rgba(15, 23, 42, 0.06)'
        },
        emphasis: {
          focus: 'adjacency',
          scale: 1.08,
          lineStyle: {
            width: 3
          }
        }
      }
    ]
  }, true)
  resizeTopologyChart()
}

const makeNodeId = () => {
  let index = editor.nodes.length + 1
  let nodeId = `node_${index}`
  const existed = new Set(editor.nodes.map(item => item.nodeId))
  while (existed.has(nodeId)) {
    index += 1
    nodeId = `node_${index}`
  }
  return nodeId
}

const mapInputSource = (source) => ({
  sourceType: source?.sourceType || 'PATH',
  path: source?.path || '',
  sourceNodeId: source?.sourceNodeId || '',
  sourceOutputName: source?.sourceOutputName || ''
})

const applyRuleMeta = (node, rule, preserveSources = true) => {
  if (!rule) {
    node.ruleId = ''
    node.ruleName = ''
    node.functionName = ''
    node.modelName = ''
    node.modelVersion = ''
    node.modelType = ''
    node.chainMode = ''
    node.availableInputs = []
    node.availableOutputs = []
    node.inputSources = {}
    return
  }
  const previousSources = preserveSources ? deepCopy(node.inputSources) || {} : {}
  node.ruleId = rule.ruleId
  node.ruleName = rule.ruleName
  node.functionName = rule.functionName
  node.modelName = rule.modelName
  node.modelVersion = rule.modelVersion
  node.modelType = rule.modelType
  node.chainMode = rule.chainMode
  node.availableInputs = deepCopy(rule.inputs || [])
  node.availableOutputs = deepCopy(rule.outputs || [])
  const nextSources = {}
  node.availableInputs.forEach((input) => {
    const previous = previousSources[input.name]
    nextSources[input.name] = previous
      ? mapInputSource(previous)
      : {
          sourceType: 'PATH',
          path: input.defaultPath || '',
          sourceNodeId: '',
          sourceOutputName: ''
        }
  })
  node.inputSources = nextSources
}

const createNodeFromRule = (rule) => {
  const node = reactive({
    nodeId: makeNodeId(),
    nodeName: '',
    ruleId: '',
    ruleName: '',
    functionName: '',
    modelName: '',
    modelVersion: '',
    modelType: '',
    chainMode: '',
    availableInputs: [],
    availableOutputs: [],
    inputSources: {}
  })
  applyRuleMeta(node, rule, false)
  node.nodeName = rule?.ruleName || node.nodeId
  return node
}

const hydrateNode = (node, chainMode) => ({
  nodeId: node.nodeId,
  nodeName: node.nodeName || node.ruleName || node.nodeId,
  ruleId: node.ruleId,
  ruleName: node.ruleName || '',
  functionName: node.functionName || '',
  modelName: node.modelName || '',
  modelVersion: node.modelVersion || '',
  modelType: node.modelType || '',
  chainMode: node.chainMode || chainMode || '',
  availableInputs: deepCopy(node.availableInputs || []),
  availableOutputs: deepCopy(node.availableOutputs || []),
  inputSources: deepCopy(node.inputSources || {})
})

const populateEditor = (chain) => {
  editor.id = chain?.id || null
  editor.chainName = chain?.chainName || ''
  editor.chainMode = chain?.chainMode || ''
  editor.nodes = (chain?.nodes || []).map(node => hydrateNode(node, chain?.chainMode))
}

const resetEditor = () => {
  const fallbackRule = compatibleRules.value[0] || null
  editor.id = null
  editor.chainName = ''
  editor.chainMode = fallbackRule?.chainMode || ''
  editor.nodes = fallbackRule ? [createNodeFromRule(fallbackRule)] : []
  selectedChainId.value = null
  runs.value = []
  selectedRunId.value = null
  selectedRun.value = null
  centerViewMode.value = 'editor'
}

const getNodeRuleOptions = (node) => {
  if (editor.nodes.length <= 1) {
    return compatibleRules.value
  }
  const targetMode = editorChainMode.value || node.chainMode || ''
  return compatibleRules.value.filter(rule => !targetMode || rule.chainMode === targetMode || String(rule.ruleId) === String(node.ruleId))
}

const getNodeInputMeta = (node, inputName) => (node.availableInputs || []).find(item => item.name === inputName)
const getUpstreamCandidates = (node) => editor.nodes.filter(item => item.nodeId !== node.nodeId)
const getUpstreamOutputOptions = (node, inputName) => {
  const sourceNodeId = node.inputSources?.[inputName]?.sourceNodeId
  const upstream = editor.nodes.find(item => item.nodeId === sourceNodeId)
  return upstream?.availableOutputs || []
}

const resetInputToPath = (node, inputName) => {
  const meta = getNodeInputMeta(node, inputName)
  node.inputSources[inputName] = {
    sourceType: 'PATH',
    path: meta?.defaultPath || '',
    sourceNodeId: '',
    sourceOutputName: ''
  }
}

const handleSourceTypeChange = (node, inputName) => {
  const source = node.inputSources?.[inputName]
  if (!source) {
    resetInputToPath(node, inputName)
    return
  }
  if (source.sourceType === 'UPSTREAM') {
    const candidates = getUpstreamCandidates(node)
    const sourceNodeId = candidates[0]?.nodeId || ''
    const sourceOutputName = candidates[0]?.availableOutputs?.[0]?.name || ''
    node.inputSources[inputName] = {
      sourceType: 'UPSTREAM',
      path: '',
      sourceNodeId,
      sourceOutputName
    }
    return
  }
  resetInputToPath(node, inputName)
}

const handleUpstreamNodeChange = (node, inputName) => {
  const outputs = getUpstreamOutputOptions(node, inputName)
  node.inputSources[inputName].sourceOutputName = outputs[0]?.name || ''
}

const cleanupDependencies = (removedNodeId) => {
  editor.nodes.forEach((node) => {
    Object.keys(node.inputSources || {}).forEach((inputName) => {
      const source = node.inputSources[inputName]
      if (source?.sourceType === 'UPSTREAM' && source.sourceNodeId === removedNodeId) {
        resetInputToPath(node, inputName)
      }
    })
  })
}

const addNode = () => {
  const mode = editorChainMode.value
  const rule = compatibleRules.value.find(item => !mode || item.chainMode === mode) || compatibleRules.value[0]
  if (!rule) {
    alert('当前没有可用于任务链的规则，请先创建输入类型一致的关联规则。')
    return
  }
  editor.nodes.push(createNodeFromRule(rule))
  if (!editor.chainMode) {
    editor.chainMode = rule.chainMode
  }
}

const removeNode = (index) => {
  const node = editor.nodes[index]
  if (!node) return
  cleanupDependencies(node.nodeId)
  editor.nodes.splice(index, 1)
  editor.chainMode = editor.nodes[0]?.chainMode || ''
}

const handleRuleChange = (node) => {
  const rule = ruleMap.value[String(node.ruleId)]
  applyRuleMeta(node, rule, false)
  if (!node.nodeName || node.nodeName.startsWith('节点_')) {
    node.nodeName = rule?.ruleName || node.nodeId
  }
  if (!editor.chainMode || editor.nodes.length <= 1) {
    editor.chainMode = rule?.chainMode || ''
  }
}

const buildSavePayload = () => ({
  chainName: String(editor.chainName || '').trim(),
  nodes: editor.nodes.map(node => ({
    nodeId: node.nodeId,
    nodeName: String(node.nodeName || '').trim(),
    ruleId: Number(node.ruleId),
    inputs: Object.fromEntries(
      Object.entries(node.inputSources || {}).map(([name, source]) => [name, {
        sourceType: source?.sourceType || 'PATH',
        path: source?.path || '',
        sourceNodeId: source?.sourceNodeId || '',
        sourceOutputName: source?.sourceOutputName || ''
      }])
    )
  }))
})

const loadChains = async () => {
  chains.value = await fetchTaskChains()
}

const loadChainDetail = async (chainId) => {
  if (!chainId) {
    resetEditor()
    runs.value = []
    selectedRunId.value = null
    selectedRun.value = null
    return
  }
  const detail = await fetchTaskChainDetail(chainId)
  populateEditor(detail)
  selectedChainId.value = detail.id
  await loadRuns(detail.id)
}

const openChainEditor = async (chainId) => {
  if (chainId && selectedChainId.value !== chainId) {
    await loadChainDetail(chainId)
  }
  centerViewMode.value = 'editor'
}

const openChainTopology = async (chainId) => {
  if (!chainId) return
  const shouldCollapse = selectedChainId.value === chainId && centerViewMode.value === 'topology'
  if (selectedChainId.value !== chainId) {
    await loadChainDetail(chainId)
  }
  centerViewMode.value = shouldCollapse ? 'editor' : 'topology'
  if (centerViewMode.value === 'topology') {
    await nextTick()
    renderTopologyChart()
  }
}

const loadRuns = async (chainId) => {
  runs.value = await fetchTaskChainRuns(chainId)
  if (selectedRunId.value) {
    const existed = runs.value.find(item => item.id === selectedRunId.value)
    if (existed) {
      await loadRunDetail(selectedRunId.value)
      return
    }
  }
  if (runs.value.length) {
    selectedRunId.value = runs.value[0].id
    await loadRunDetail(selectedRunId.value)
    return
  }
  selectedRunId.value = null
  selectedRun.value = null
}

const loadRunDetail = async (runId) => {
  if (!runId) {
    selectedRun.value = null
    return
  }
  selectedRun.value = await fetchTaskChainRunDetail(runId)
  selectedRunId.value = runId
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const refreshPolling = () => {
  const shouldPoll = ['PENDING', 'RUNNING'].includes(selectedRun.value?.status)
    || activeRuns.value.length > 0
  if (!shouldPoll) {
    stopPolling()
    return
  }
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    if (!selectedChainId.value) return
    await loadRuns(selectedChainId.value)
  }, 3000)
}

const saveChain = async () => {
  const payload = buildSavePayload()
  if (!payload.chainName) {
    alert('请输入任务链名称。')
    return
  }
  if (!payload.nodes.length) {
    alert('任务链至少需要一个节点。')
    return
  }
  saving.value = true
  try {
    if (editor.id) {
      await updateTaskChain(editor.id, payload)
      await loadChains()
      await loadChainDetail(editor.id)
      if (centerViewMode.value === 'topology') {
        await nextTick()
        renderTopologyChart()
      }
      alert('任务链已更新。')
    } else {
      const chainId = await createTaskChain(payload)
      await loadChains()
      await loadChainDetail(chainId)
      centerViewMode.value = 'editor'
      alert('任务链已创建。')
    }
  } catch (error) {
    alert(error.message || '任务链保存失败')
  } finally {
    saving.value = false
  }
}

const deleteCurrentChain = async () => {
  if (!editor.id) {
    resetEditor()
    return
  }
  if (!confirm(`确认删除任务链“${editor.chainName}”吗？`)) {
    return
  }
  try {
    await deleteTaskChain(editor.id)
    await loadChains()
    if (chains.value.length) {
      centerViewMode.value = 'editor'
      await loadChainDetail(chains.value[0].id)
    } else {
      resetEditor()
    }
  } catch (error) {
    alert(error.message || '任务链删除失败')
  }
}

const openRunDialog = () => {
  if (!editor.id) {
    alert('请先保存任务链后再运行。')
    return
  }
  runForm.runName = ''
  runForm.startTime = ''
  runForm.endTime = ''
  runForm.scheduledStartTime = ''
  runForm.scheduledEndTime = ''
  showRunAdvanced.value = false
  showRunModal.value = true
}

const submitRun = async () => {
  if (!editor.id) return
  const scheduledStart = parseInputMillis(runForm.scheduledStartTime)
  if (runForm.scheduledStartTime && !scheduledStart) {
    alert('计划开始时间无效。')
    return
  }
  const scheduledEnd = parseInputMillis(runForm.scheduledEndTime)
  if (runForm.scheduledEndTime && !scheduledEnd) {
    alert('计划终止时间无效。')
    return
  }
  const now = Date.now()
  const effectiveStart = scheduledStart && scheduledStart > now ? scheduledStart : now
  if (scheduledEnd && scheduledEnd <= effectiveStart) {
    alert('计划终止时间必须晚于当前生效开始时间。')
    return
  }

  const payload = {}
  if (runForm.runName.trim()) {
    payload.runName = runForm.runName.trim()
  }
  if (editorChainMode.value === 'TIME_SERIES') {
    const start = parseInputMillis(runForm.startTime)
    const end = parseInputMillis(runForm.endTime)
    if (!start || !end || end <= start) {
      alert('时序任务链必须设置合法的时间区间。')
      return
    }
    payload.timeRange = {
      start: normalizeInputTime(runForm.startTime),
      end: normalizeInputTime(runForm.endTime)
    }
  }
  if (runForm.scheduledStartTime) {
    payload.scheduledStartTime = normalizeInputTime(runForm.scheduledStartTime)
  }
  if (runForm.scheduledEndTime) {
    payload.scheduledEndTime = normalizeInputTime(runForm.scheduledEndTime)
  }

  submittingRun.value = true
  try {
    const runId = await submitTaskChainRun(editor.id, payload)
    showRunModal.value = false
    await loadRuns(editor.id)
    await loadRunDetail(runId)
    alert(`任务链已提交，运行ID：${runId}`)
  } catch (error) {
    alert(error.message || '任务链运行提交失败')
  } finally {
    submittingRun.value = false
  }
}

const handleStopRun = async (runId) => {
  if (!runId) return
  if (!confirm('确认终止当前任务链运行吗？')) {
    return
  }
  try {
    await stopTaskChainRun(runId)
    await loadRuns(selectedChainId.value)
  } catch (error) {
    alert(error.message || '停止任务链运行失败')
  }
}

watch(selectedRun, refreshPolling, { deep: true })
watch(activeRuns, refreshPolling, { deep: true })
watch(graphPreview, async () => {
  if (!isTopologyView.value) return
  await nextTick()
  renderTopologyChart()
}, { deep: true })
watch(isTopologyView, async (value) => {
  if (!value) {
    disposeTopologyChart()
    return
  }
  await nextTick()
  renderTopologyChart()
})

onMounted(async () => {
  window.addEventListener('resize', resizeTopologyChart)
  loading.value = true
  try {
    compatibleRules.value = await fetchTaskChainRuleOptions()
    await loadChains()
    if (chains.value.length) {
      await loadChainDetail(chains.value[0].id)
    } else {
      resetEditor()
    }
  } catch (error) {
    alert(error.message || '任务链页面初始化失败')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  stopPolling()
  window.removeEventListener('resize', resizeTopologyChart)
  disposeTopologyChart()
})
</script>

<template>
  <div class="h-full bg-[#f5f7fb] p-5">
    <div v-if="showRunModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/45 backdrop-blur-sm">
      <div class="w-[420px] rounded-2xl bg-white shadow-2xl border border-slate-200">
        <div class="px-6 py-4 border-b border-slate-100 flex items-center justify-between">
          <div>
            <div class="text-lg font-bold text-slate-800">提交任务链运行</div>
            <div class="text-xs text-slate-400 mt-1">模式：{{ editorChainMode === 'TIME_SERIES' ? '时序' : '结构化' }}</div>
          </div>
          <button class="text-slate-400 hover:text-slate-700" @click="showRunModal = false">
            <i class="ri-close-line text-xl"></i>
          </button>
        </div>
        <div class="px-6 py-5 space-y-4">
          <div>
            <label class="block text-xs font-bold text-slate-500 mb-1">运行名称</label>
            <input v-model="runForm.runName" type="text" maxlength="120" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none">
          </div>
          <template v-if="editorChainMode === 'TIME_SERIES'">
            <div>
              <label class="block text-xs font-bold text-slate-500 mb-1">开始时间</label>
              <input v-model="runForm.startTime" type="datetime-local" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none">
            </div>
            <div>
              <label class="block text-xs font-bold text-slate-500 mb-1">结束时间</label>
              <input v-model="runForm.endTime" type="datetime-local" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none">
            </div>
          </template>
          <div v-if="editorChainMode !== 'TIME_SERIES'" class="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-xs leading-6 text-emerald-700">
            当前是结构化任务链，不需要填写时间区间，保持默认即可直接运行。
          </div>
          <div class="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <button class="w-full flex items-center justify-between text-left" @click="showRunAdvanced = !showRunAdvanced">
              <div>
                <div class="text-sm font-semibold text-slate-700">高级调度设置</div>
                <div class="text-[11px] text-slate-500 mt-1">只有需要定时开始或设置最晚终止时间时才需要展开。</div>
              </div>
              <i :class="showRunAdvanced ? 'ri-arrow-up-s-line' : 'ri-arrow-down-s-line'" class="text-lg text-slate-400"></i>
            </button>
            <div v-if="showRunAdvanced" class="mt-4 space-y-3">
              <div class="text-[11px] text-slate-500 leading-5">
                开始时间留空表示立即执行；终止时间留空表示不限制任务链最晚结束时间。
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-500 mb-1">计划开始时间</label>
                <input v-model="runForm.scheduledStartTime" type="datetime-local" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm bg-white focus:border-sky-400 focus:outline-none">
              </div>
              <div>
                <label class="block text-xs font-bold text-slate-500 mb-1">计划终止时间</label>
                <input v-model="runForm.scheduledEndTime" type="datetime-local" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm bg-white focus:border-sky-400 focus:outline-none">
              </div>
            </div>
          </div>
        </div>
        <div class="px-6 py-4 border-t border-slate-100 flex justify-end gap-2">
          <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50" @click="showRunModal = false">取消</button>
          <button class="rounded-xl bg-sky-600 px-4 py-2 text-sm text-white hover:bg-sky-700 disabled:opacity-60" :disabled="submittingRun" @click="submitRun">
            {{ submittingRun ? '提交中...' : '提交运行' }}
          </button>
        </div>
      </div>
    </div>

    <div class="grid h-full gap-5" :style="showRightPanel ? 'grid-template-columns: 260px minmax(0, 1fr) 360px;' : 'grid-template-columns: 260px minmax(0, 1fr);'">
      <div class="rounded-3xl border border-slate-200 bg-white shadow-sm flex flex-col overflow-hidden">
        <div class="px-5 py-4 border-b border-slate-100 flex items-center justify-between bg-[linear-gradient(135deg,#f8fbff,#eef4ff)]">
          <div>
            <div class="text-sm font-bold text-slate-800">任务链列表</div>
            <div class="text-[11px] text-slate-400 mt-1">独立于原有任务定义的编排视图</div>
          </div>
          <button class="h-9 w-9 rounded-xl bg-sky-50 text-sky-600 hover:bg-sky-100" @click="resetEditor">
            <i class="ri-add-line text-lg"></i>
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-3 space-y-3">
          <div v-if="loading" class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400">
            正在加载任务链...
          </div>
          <div
            v-for="chain in chains"
            :key="chain.id"
            class="group w-full rounded-2xl border text-left transition overflow-hidden"
            :class="selectedChainId === chain.id
              ? 'border-sky-300 bg-sky-50 shadow-sm'
              : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50'"
          >
            <div class="p-4">
              <div class="flex items-start justify-between gap-3">
                <button type="button" class="min-w-0 flex-1 text-left" @click="openChainEditor(chain.id)">
                  <div class="truncate text-sm font-bold text-slate-800">{{ chain.chainName }}</div>
                  <div class="text-[11px] text-slate-400 mt-1">
                    {{ chain.chainMode === 'TIME_SERIES' ? '时序' : '结构化' }} · {{ chain.nodeCount }} 节点 / {{ chain.edgeCount }} 连线
                  </div>
                </button>
                <div class="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    class="rounded p-1 transition-all"
                    :class="selectedChainId === chain.id && isTopologyView
                      ? 'opacity-100 bg-sky-100 text-sky-600'
                      : 'opacity-0 text-slate-400 group-hover:opacity-100 hover:bg-sky-100 hover:text-sky-600'"
                    :title="selectedChainId === chain.id && isTopologyView ? '收起拓扑图' : '展开拓扑图'"
                    @click.stop="openChainTopology(chain.id)"
                  >
                    <i class="ri-node-tree text-sm"></i>
                  </button>
                  <span class="rounded-full border px-2 py-0.5 text-[10px] font-bold"
                        :class="chain.chainMode === 'TIME_SERIES'
                          ? 'border-blue-200 bg-blue-50 text-blue-700'
                          : 'border-emerald-200 bg-emerald-50 text-emerald-700'">
                    {{ chain.chainMode === 'TIME_SERIES' ? 'TS' : 'RT' }}
                  </span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!loading && !chains.length" class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-400">
            还没有任务链，点击右上角开始创建。
          </div>
        </div>
      </div>

      <div class="rounded-3xl border border-slate-200 bg-white shadow-sm overflow-hidden flex flex-col">
        <template v-if="isTopologyView">
          <div class="px-6 py-5 border-b border-slate-100 bg-[radial-gradient(circle_at_top_left,#eef6ff,transparent_48%),linear-gradient(135deg,#ffffff,#f7fbff)]">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <i class="ri-node-tree text-xl text-sky-500"></i>
                  <div class="text-xl font-bold text-slate-800">任务链拓扑预览：{{ editor.chainName || '未命名任务链' }}</div>
                </div>
                <div class="text-sm text-slate-500 mt-1">
                  交互方式和数据模块保持一致，点左侧记录旁边的小拓扑按钮即可切换到这里。
                </div>
              </div>
              <div class="flex flex-wrap gap-2">
                <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50" @click="centerViewMode = 'editor'">
                  返回编排
                </button>
                <button class="rounded-xl bg-sky-600 px-4 py-2 text-sm text-white hover:bg-sky-700" @click="openRunDialog">
                  运行任务链
                </button>
              </div>
            </div>
          </div>

          <div class="relative flex-1 overflow-hidden bg-[radial-gradient(circle_at_top_left,#f8fbff,transparent_38%),linear-gradient(180deg,#ffffff,#f8fafc)]">
            <div class="absolute left-6 top-5 z-10 flex flex-wrap items-center gap-2">
              <span class="rounded-full border px-2.5 py-1 text-[10px] font-bold"
                    :class="editorChainMode === 'TIME_SERIES'
                      ? 'border-blue-200 bg-blue-50 text-blue-700'
                      : 'border-emerald-200 bg-emerald-50 text-emerald-700'">
                {{ editorChainMode === 'TIME_SERIES' ? '时序任务链' : '结构化任务链' }}
              </span>
              <span class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] text-slate-600">{{ editor.nodes.length }} 个节点</span>
              <span class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] text-slate-600">{{ graphPreview.edges.length }} 条连接</span>
            </div>

            <div v-if="graphPreview.hasCycle" class="absolute right-6 top-5 z-10 rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-600">
              当前依赖存在成环风险
            </div>

            <div v-if="!editor.nodes.length" class="flex h-full items-center justify-center px-6">
              <div class="rounded-3xl border border-dashed border-slate-200 bg-white/90 px-8 py-12 text-center shadow-sm">
                <div class="text-base font-semibold text-slate-700">当前任务链还没有节点</div>
                <div class="mt-2 text-sm text-slate-400">返回编排页新增节点后，这里会自动生成对应的拓扑图。</div>
              </div>
            </div>

            <div v-else ref="topologyChartRef" class="h-full w-full"></div>

            <div class="absolute bottom-4 left-6 z-10 rounded-2xl border border-slate-200 bg-white/90 px-4 py-3 shadow-sm backdrop-blur">
              <div class="flex flex-wrap items-center gap-4 text-[11px] text-slate-600">
                <div class="flex items-center"><span class="mr-2 h-2.5 w-2.5 rounded-full bg-blue-500"></span>起始节点</div>
                <div class="flex items-center"><span class="mr-2 h-2.5 w-2.5 rounded-full bg-amber-500"></span>中间节点</div>
                <div class="flex items-center"><span class="mr-2 h-2.5 w-2.5 rounded-full bg-emerald-500"></span>结果节点</div>
              </div>
            </div>

            <div class="absolute bottom-4 right-6 z-10 rounded-2xl border border-slate-200 bg-white/90 px-3 py-2 text-[11px] text-slate-500 shadow-sm backdrop-blur">
              <i class="ri-mouse-line mr-1"></i>滚轮缩放 / 拖拽平移
            </div>
          </div>
        </template>

        <template v-else>
        <div class="px-6 py-5 border-b border-slate-100 bg-[radial-gradient(circle_at_top_left,#eef6ff,transparent_48%),linear-gradient(135deg,#ffffff,#f7fbff)]">
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="min-w-0">
              <div class="text-xl font-bold text-slate-800">任务链编排</div>
              <div class="text-sm text-slate-500 mt-1">
                默认只显示最常用的编排信息。你先完成名称、节点和输入来源，保存后再运行即可。
              </div>
            </div>
            <div class="flex flex-wrap gap-2">
              <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50" @click="showGuide = !showGuide">
                {{ showGuide ? '收起操作说明' : '显示操作说明' }}
              </button>
              <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50" @click="showAdvanced = !showAdvanced">
                {{ showRightPanel ? '收起高级信息' : '显示高级信息' }}
              </button>
              <button class="rounded-xl border border-rose-200 px-4 py-2 text-sm text-rose-600 hover:bg-rose-50" @click="deleteCurrentChain">
                {{ editor.id ? '删除任务链' : '清空编辑器' }}
              </button>
            </div>
          </div>
        </div>

        <div class="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <div v-if="showGuide" class="rounded-3xl border border-sky-100 bg-[linear-gradient(135deg,#f8fbff,#eef7ff)] p-5">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div class="text-base font-bold text-slate-800">第一次用可以按这 4 步走</div>
                <div class="text-sm text-slate-500 mt-1">先把 A、B、C 当成 3 个节点来理解，页面里只需要配置“谁的输入来自外部，谁的输入来自上游”。</div>
              </div>
              <div v-if="graphPreview.hasCycle" class="rounded-full border border-rose-200 bg-rose-50 px-3 py-1 text-xs font-semibold text-rose-600">
                当前连接存在成环风险
              </div>
            </div>
            <div class="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <div v-for="item in setupChecklist" :key="item.title" class="rounded-2xl border border-white/70 bg-white/90 p-4 shadow-sm">
                <div class="flex items-center justify-between gap-3">
                  <div class="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400">{{ item.title }}</div>
                  <span class="rounded-full px-2 py-0.5 text-[10px] font-bold" :class="item.done ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'">
                    {{ item.done ? '已完成' : '待处理' }}
                  </span>
                </div>
                <div class="mt-2 text-sm font-semibold text-slate-700">{{ item.label }}</div>
              </div>
            </div>
            <div class="mt-4 rounded-2xl border border-slate-200 bg-white p-4 text-sm leading-7 text-slate-600">
              <div><span class="font-semibold text-slate-800">A → B：</span>在 B 的某个输入里，把来源从“外部路径”切到“上游输出”，上游节点选 A，上游输出选 A 的输出参数。</div>
              <div><span class="font-semibold text-slate-800">A + B → C：</span>C 的不同输入可以分别选择 A 和 B 的输出，后端会自动校验类型、数量和是否成环。</div>
              <div><span class="font-semibold text-slate-800">不想串起来：</span>保持“外部路径”即可，表示这个输入直接读 `ts.*` 或 `rt.*` 数据。</div>
            </div>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-slate-50/70 p-4">
            <label class="block text-xs font-bold text-slate-500 mb-2">第 1 步：任务链名称</label>
            <input v-model="editor.chainName" type="text" maxlength="120" class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm focus:border-sky-400 focus:outline-none">
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="text-sm font-bold text-slate-800">第 2 步：配置节点</div>
                <div class="text-[11px] text-slate-400 mt-1">一个节点就是一个任务。你可以把它理解成任务 A、任务 B、任务 C。</div>
              </div>
              <button class="rounded-xl border border-slate-200 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50" @click="addNode">新增节点</button>
            </div>
          </div>

          <div v-for="(node, index) in editor.nodes" :key="node.nodeId" class="rounded-3xl border border-slate-200 bg-white shadow-sm overflow-hidden">
            <div class="px-5 py-4 border-b border-slate-100 bg-[linear-gradient(135deg,#fffaf0,#f8fbff)] flex items-start justify-between gap-3">
              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="rounded-full bg-slate-900 px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.2em] text-white">{{ node.nodeId }}</span>
                  <span class="rounded-full border px-2 py-0.5 text-[10px] font-bold"
                        :class="node.chainMode === 'TIME_SERIES'
                          ? 'border-blue-200 bg-blue-50 text-blue-700'
                          : 'border-emerald-200 bg-emerald-50 text-emerald-700'">
                    {{ node.chainMode === 'TIME_SERIES' ? '时序' : '结构化' }}
                  </span>
                </div>
                <input v-model="node.nodeName" type="text" maxlength="120" class="mt-3 w-full rounded-xl border border-slate-200 px-3 py-2 text-sm font-semibold text-slate-800 focus:border-sky-400 focus:outline-none">
                <div class="mt-3 grid gap-3 md:grid-cols-[minmax(0,1fr),200px]">
                  <div class="rounded-2xl border border-slate-200 bg-slate-50 p-3">
                    <div class="text-[11px] text-slate-500">模型 / 函数</div>
                    <div class="mt-1 text-sm font-medium text-slate-700">{{ node.modelName || '-' }} / {{ node.functionName || '-' }}</div>
                    <div class="mt-1 text-[11px] text-slate-400">版本 {{ node.modelVersion || '-' }} · {{ node.modelType || '-' }}</div>
                  </div>
                  <div>
                    <label class="block text-xs font-bold text-slate-500 mb-1">选择规则</label>
                    <select v-model.number="node.ruleId" class="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm focus:border-sky-400 focus:outline-none" @change="handleRuleChange(node)">
                      <option v-for="rule in getNodeRuleOptions(node)" :key="rule.ruleId" :value="rule.ruleId">
                        {{ rule.ruleName }}（{{ rule.chainMode === 'TIME_SERIES' ? '时序' : '结构化' }}）
                      </option>
                    </select>
                  </div>
                </div>
              </div>
              <button class="h-10 w-10 rounded-xl border border-rose-200 text-rose-500 hover:bg-rose-50" @click="removeNode(index)">
                <i class="ri-delete-bin-line text-lg"></i>
              </button>
            </div>

            <div class="px-5 py-5 grid gap-5 lg:grid-cols-[minmax(0,1fr),280px]">
              <div class="space-y-4">
                <div class="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                  <div class="text-sm font-bold text-slate-700 mb-3">输入来源配置</div>
                  <div class="mb-3 rounded-2xl border border-slate-200 bg-white px-3 py-2 text-xs leading-6 text-slate-500">
                    保持“外部路径”表示直接读取原始数据；切换为“上游输出”表示把前面节点的结果喂给当前节点。
                  </div>
                  <div v-if="!(node.availableInputs || []).length" class="text-sm text-slate-400">当前规则没有可用输入参数。</div>
                  <div v-for="input in node.availableInputs || []" :key="input.name" class="rounded-2xl border border-slate-200 bg-white p-4 mb-3 last:mb-0">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                      <div>
                        <div class="text-sm font-semibold text-slate-800">{{ input.name }}</div>
                        <div class="text-[11px] text-slate-400">类型：{{ input.type }} · 默认路径：{{ input.defaultPath || '未设置' }}</div>
                      </div>
                      <select v-model="node.inputSources[input.name].sourceType" class="rounded-xl border border-slate-200 px-3 py-2 text-xs focus:border-sky-400 focus:outline-none" @change="handleSourceTypeChange(node, input.name)">
                        <option value="PATH">外部路径</option>
                        <option value="UPSTREAM">上游输出</option>
                      </select>
                    </div>

                    <div v-if="node.inputSources[input.name].sourceType === 'PATH'" class="mt-3">
                      <input v-model="node.inputSources[input.name].path" type="text" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none" placeholder="请输入 ts.* 或 rt.* 路径">
                    </div>

                    <div v-else class="mt-3 grid gap-3 md:grid-cols-2">
                      <div>
                        <label class="block text-[11px] font-bold text-slate-500 mb-1">上游节点</label>
                        <select v-model="node.inputSources[input.name].sourceNodeId" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none" @change="handleUpstreamNodeChange(node, input.name)">
                          <option value="" disabled>请选择上游节点</option>
                          <option v-for="upstream in getUpstreamCandidates(node)" :key="upstream.nodeId" :value="upstream.nodeId">
                            {{ upstream.nodeName || upstream.nodeId }}
                          </option>
                        </select>
                      </div>
                      <div>
                        <label class="block text-[11px] font-bold text-slate-500 mb-1">上游输出</label>
                        <select v-model="node.inputSources[input.name].sourceOutputName" class="w-full rounded-xl border border-slate-200 px-3 py-2 text-sm focus:border-sky-400 focus:outline-none">
                          <option value="" disabled>请选择输出参数</option>
                          <option v-for="output in getUpstreamOutputOptions(node, input.name)" :key="output.name" :value="output.name">
                            {{ output.name }}（{{ output.type }}）
                          </option>
                        </select>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="space-y-4">
                <div class="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
                  <div class="text-sm font-bold text-slate-700 mb-3">输出预览</div>
                  <div v-if="!(node.availableOutputs || []).length" class="text-sm text-slate-400">当前规则没有可用输出参数。</div>
                  <div class="flex flex-wrap gap-2">
                    <span v-for="output in node.availableOutputs || []" :key="output.name" class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-600">
                      {{ output.name }} · {{ output.type }}
                    </span>
                  </div>
                </div>
                <div class="rounded-2xl border border-slate-200 bg-[linear-gradient(135deg,#f8fbff,#f0fff7)] p-4">
                  <div class="text-sm font-bold text-slate-700 mb-2">链路写回说明</div>
                  <div class="text-xs leading-6 text-slate-500">
                    当前节点输出会自动写入
                    <span class="font-mono text-slate-700">
                      {{ node.chainMode === 'TIME_SERIES' ? 'ts.chain.chain_链ID.run_运行ID' : 'rt.chain.chain_链ID.run_运行ID' }}.{{ node.nodeId }}.*
                    </span>
                    ，供下游节点稳定引用，不会改动你原来的规则定义。
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div v-if="!editor.nodes.length" class="rounded-3xl border border-dashed border-slate-200 bg-slate-50 px-6 py-12 text-center text-sm text-slate-400">
            当前没有节点，可点击“新增节点”开始编排。
          </div>

          <div class="rounded-2xl border border-slate-200 bg-[linear-gradient(135deg,#ffffff,#f8fbff)] p-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
              <div>
                <div class="text-sm font-bold text-slate-800">第 3 步：保存并运行</div>
                <div class="text-[11px] text-slate-400 mt-1">先保存任务链，再运行。运行时如果是时序链，再补充时间范围即可。</div>
              </div>
              <div class="flex flex-wrap gap-2">
                <button class="rounded-xl bg-slate-900 px-4 py-2 text-sm text-white hover:bg-slate-800 disabled:opacity-60" :disabled="saving" @click="saveChain">
                  {{ saving ? '保存中...' : (editor.id ? '保存修改' : '创建任务链') }}
                </button>
                <button class="rounded-xl bg-sky-600 px-4 py-2 text-sm text-white hover:bg-sky-700" @click="openRunDialog">运行任务链</button>
              </div>
            </div>
          </div>
        </div>
        </template>
      </div>

      <div v-if="showRightPanel" class="rounded-3xl border border-slate-200 bg-white shadow-sm overflow-hidden flex flex-col">
        <div class="px-5 py-4 border-b border-slate-100 bg-[linear-gradient(135deg,#f9fafb,#eff6ff)]">
          <div class="text-sm font-bold text-slate-800">依赖预览与运行记录</div>
          <div class="text-[11px] text-slate-400 mt-1">这里是高级信息区，主要用于检查依赖关系、查看运行历史和排查问题。</div>
        </div>
        <div class="flex-1 overflow-y-auto p-4 space-y-4">
          <div class="rounded-2xl border border-slate-200 bg-slate-50/80 p-4">
            <div class="flex flex-wrap items-center gap-2">
              <span class="rounded-full border px-2.5 py-1 text-[10px] font-bold"
                    :class="editorChainMode === 'TIME_SERIES'
                      ? 'border-blue-200 bg-blue-50 text-blue-700'
                      : 'border-emerald-200 bg-emerald-50 text-emerald-700'">
                {{ editorChainMode === 'TIME_SERIES' ? '时序任务链' : '结构化任务链' }}
              </span>
              <span class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] text-slate-600">{{ editor.nodes.length }} 个节点</span>
              <span class="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-[10px] text-slate-600">{{ graphPreview.edges.length }} 条输入连接</span>
            </div>
            <div v-if="graphPreview.hasCycle" class="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-600">
              当前编辑视图存在环形依赖风险，后端保存时会拒绝。
            </div>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-4">
            <div class="text-sm font-bold text-slate-700 mb-3">层级结构</div>
            <div v-if="!graphPreview.levels.length" class="text-sm text-slate-400">暂无节点。</div>
            <div v-for="(level, levelIndex) in graphPreview.levels" :key="levelIndex" class="mb-3 last:mb-0">
              <div class="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-400 mb-2">Level {{ levelIndex + 1 }}</div>
              <div class="space-y-2">
                <div v-for="node in level" :key="node.nodeId" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2">
                  <div class="text-sm font-semibold text-slate-700">{{ node.nodeName || node.nodeId }}</div>
                  <div class="text-[11px] text-slate-400">{{ node.ruleName || '-' }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-4">
            <div class="text-sm font-bold text-slate-700 mb-3">输入连接</div>
            <div v-if="!graphPreview.edges.length" class="text-sm text-slate-400">当前没有上游输出连接，所有输入都将走外部路径。</div>
            <div v-for="edge in graphPreview.edges" :key="`${edge.fromId}-${edge.toId}-${edge.inputName}`" class="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 mb-2 last:mb-0">
              <div class="text-xs text-slate-600">
                <span class="font-semibold text-slate-800">{{ edge.fromName }}</span>.{{ edge.outputName }}
                <span class="mx-2 text-slate-300">→</span>
                <span class="font-semibold text-slate-800">{{ edge.toName }}</span>.{{ edge.inputName }}
              </div>
            </div>
          </div>

          <div class="rounded-2xl border border-slate-200 bg-white p-4">
            <div class="flex items-center justify-between gap-3 mb-3">
              <div class="text-sm font-bold text-slate-700">运行记录</div>
              <button
                v-if="selectedRun && ['PENDING', 'RUNNING'].includes(selectedRun.status)"
                class="rounded-xl border border-rose-200 px-3 py-1.5 text-xs text-rose-600 hover:bg-rose-50"
                @click="handleStopRun(selectedRun.id)"
              >
                终止当前运行
              </button>
            </div>
            <div v-if="!runs.length" class="text-sm text-slate-400">当前任务链还没有运行记录。</div>
            <div class="space-y-2">
              <button
                v-for="run in runs"
                :key="run.id"
                type="button"
                class="w-full rounded-xl border px-3 py-3 text-left transition"
                :class="selectedRunId === run.id
                  ? 'border-sky-300 bg-sky-50'
                  : 'border-slate-200 bg-slate-50 hover:border-slate-300'"
                @click="loadRunDetail(run.id)"
              >
                <div class="flex items-center justify-between gap-3">
                  <div class="min-w-0">
                    <div class="truncate text-sm font-semibold text-slate-800">{{ run.runName || run.id }}</div>
                    <div class="text-[11px] text-slate-400 mt-1">{{ formatTime(run.createTime) }}</div>
                  </div>
                  <span class="rounded-full border px-2 py-0.5 text-[10px] font-bold" :class="resolveStatusClass(run.status)">
                    {{ run.status }}
                  </span>
                </div>
              </button>
            </div>
          </div>

          <div v-if="selectedRun" class="rounded-2xl border border-slate-200 bg-white p-4">
            <div class="text-sm font-bold text-slate-700 mb-3">运行详情</div>
            <div class="grid grid-cols-2 gap-3 text-[11px] text-slate-500">
              <div>
                <div class="text-slate-400">运行名称</div>
                <div class="text-slate-700 mt-1">{{ selectedRun.runName }}</div>
              </div>
              <div>
                <div class="text-slate-400">状态</div>
                <div class="text-slate-700 mt-1">{{ selectedRun.status }}</div>
              </div>
              <div>
                <div class="text-slate-400">开始时间</div>
                <div class="text-slate-700 mt-1">{{ formatTime(selectedRun.startTime) }}</div>
              </div>
              <div>
                <div class="text-slate-400">结束时间</div>
                <div class="text-slate-700 mt-1">{{ formatTime(selectedRun.endTime) }}</div>
              </div>
              <div class="col-span-2">
                <div class="text-slate-400">结果前缀</div>
                <div class="mt-1 break-all font-mono text-slate-700">{{ selectedRun.resultPrefix || '-' }}</div>
              </div>
            </div>
            <div class="mt-4 space-y-3">
              <div v-for="node in selectedRun.nodes || []" :key="node.nodeId" class="rounded-2xl border border-slate-200 bg-slate-50 p-3">
                <div class="flex items-center justify-between gap-3">
                  <div>
                    <div class="text-sm font-semibold text-slate-800">{{ node.nodeName }}</div>
                    <div class="text-[11px] text-slate-400">{{ node.ruleName }} / {{ node.functionName }}</div>
                  </div>
                  <span class="rounded-full border px-2 py-0.5 text-[10px] font-bold" :class="resolveStatusClass(node.status)">
                    {{ node.status }}
                  </span>
                </div>
                <div v-if="node.outputPaths && Object.keys(node.outputPaths).length" class="mt-3 rounded-xl border border-slate-200 bg-white p-3 text-[11px] text-slate-600 space-y-1">
                  <div v-for="(path, name) in node.outputPaths" :key="name">
                    <span class="font-semibold text-slate-700">{{ name }}</span> -> <span class="font-mono">{{ path }}</span>
                  </div>
                </div>
                <pre class="mt-3 rounded-xl border border-slate-200 bg-white p-3 text-[11px] leading-5 text-slate-600 whitespace-pre-wrap">{{ node.execLog || '暂无日志' }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
