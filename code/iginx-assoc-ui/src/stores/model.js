import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchModelList,
  uploadModel,
  updateModelProfile,
  deleteModelProfile,
  deleteModelVersion as deleteModelVersionApi,
  parseModelSchema,
  listModelFunctions,
  parseModelSchemaByFunction,
  listAssetFunctions,
  parseAssetSchemaByFunction as parseAssetSchemaByFunctionApi,
  getModelDownloadUrl
} from '../api/model'

export const useModelStore = defineStore('model', () => {
  const models = ref([])
  const selectedModel = ref(null)
  const selectedModelIds = ref([])

  const UPLOAD_MODEL_TYPE_BY_EXTENSION = {
    PY: 'Python',
    M: 'MATLAB',
    CPP: 'CPP'
  }

  const showUploadModal = ref(false)
  const showMetaModal = ref(false)
  const showDeleteModal = ref(false)

  const uploadStep = ref(1)
  const uploadForm = reactive({
    name: '',
    version: 'v1.0.0',
    type: 'Python',
    file: null,
    inputs: [],
    outputs: [],
    functionOptions: [],
    selectedFunction: '',
    parseMode: '',
    parseMessage: ''
  })

  const normalizeVersion = (version) => ({
    ...version,
    inputs: (version.inputs || []).map(item => ({
      ...item,
      desc: item.desc || item.description || ''
    })),
    outputs: (version.outputs || []).map(item => ({
      ...item,
      desc: item.desc || item.description || ''
    })),
    functions: Array.isArray(version.functions) ? [...version.functions] : [],
    dependencies: Array.isArray(version.dependencies) ? [...version.dependencies] : []
  })

  const loadModels = async () => {
    const list = await fetchModelList()
    models.value = (list || []).map(model => ({
      ...model,
      history: (model.history || []).map(normalizeVersion)
    }))
    if (selectedModel.value) {
      selectedModel.value = models.value.find(m => m.id === selectedModel.value.id) || null
    }
    const validIds = new Set(models.value.map(model => model.id))
    selectedModelIds.value = selectedModelIds.value.filter(id => validIds.has(id))
  }

  const selectModel = (model) => {
    selectedModel.value = model
  }

  const setSelectedModelIds = (ids = []) => {
    selectedModelIds.value = Array.from(new Set(ids || []))
  }

  const clearSelectedModelIds = () => {
    selectedModelIds.value = []
  }

  const autoParseCode = (fileContent) => {
    const inputs = []
    const outputs = []
    const inputRegex = /@Input:\s*(\w+)\s*(?:\(([^)]+)\))?\s*(?:-\s*(.*))?/gi
    const outputRegex = /@Output:\s*(\w+)\s*(?:\(([^)]+)\))?\s*(?:-\s*(.*))?/gi

    let match
    while ((match = inputRegex.exec(fileContent)) !== null) {
      inputs.push({ name: match[1], type: match[2] || 'STRING', desc: match[3] || '', unit: '-' })
    }
    while ((match = outputRegex.exec(fileContent)) !== null) {
      outputs.push({ name: match[1], type: match[2] || 'STRING', desc: match[3] || '', unit: '-' })
    }
    return { inputs, outputs }
  }

  const parseSchemaByFile = async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return parseModelSchema(formData)
  }

  const listFunctionsByFile = async (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return listModelFunctions(formData)
  }

  const parseSchemaByFunction = async (file, functionName) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('functionName', functionName)
    return parseModelSchemaByFunction(formData)
  }

  const listFunctionsByAsset = async (assetId) => {
    if (!assetId) return []
    return listAssetFunctions(assetId)
  }

  const parseSchemaByAssetFunction = async (assetId, functionName) => {
    if (!assetId || !functionName) return { inputs: [], outputs: [], parseMode: '', message: '' }
    return parseAssetSchemaByFunctionApi(assetId, functionName)
  }

  const resetUploadParseState = () => {
    uploadForm.functionOptions = []
    uploadForm.selectedFunction = ''
    uploadForm.parseMode = ''
    uploadForm.parseMessage = ''
    uploadForm.inputs = []
    uploadForm.outputs = []
  }

  const ENTRY_FUNCTION_PREFIX = 'entryFunction:'
  const ENTRY_FUNCTION_EQUAL_PREFIX = 'entryFunction='
  const LEGACY_FUNCTION_PREFIX = 'function:'
  const LEGACY_FUNCTION_EQUAL_PREFIX = 'function='

  const parseEntryFunctionDependency = (dependency) => {
    const text = String(dependency || '').trim()
    if (!text) return ''
    if (text.startsWith(ENTRY_FUNCTION_PREFIX)) {
      return text.substring(ENTRY_FUNCTION_PREFIX.length).trim()
    }
    if (text.startsWith(ENTRY_FUNCTION_EQUAL_PREFIX)) {
      return text.substring(ENTRY_FUNCTION_EQUAL_PREFIX.length).trim()
    }
    if (text.startsWith(LEGACY_FUNCTION_PREFIX)) {
      return text.substring(LEGACY_FUNCTION_PREFIX.length).trim()
    }
    if (text.startsWith(LEGACY_FUNCTION_EQUAL_PREFIX)) {
      return text.substring(LEGACY_FUNCTION_EQUAL_PREFIX.length).trim()
    }
    return ''
  }

  const normalizeDependencies = (dependencies = [], selectedFunction = '') => {
    let entryFunction = String(selectedFunction || '').trim()
    const normalized = []
    const source = Array.isArray(dependencies) ? dependencies : []
    source.forEach(item => {
      const text = String(item || '').trim()
      if (!text) return
      const parsedFunction = parseEntryFunctionDependency(text)
      if (parsedFunction) {
        if (!entryFunction) entryFunction = parsedFunction
        return
      }
      normalized.push(text)
    })
    if (entryFunction) {
      normalized.unshift(`${ENTRY_FUNCTION_PREFIX}${entryFunction}`)
    }
    return normalized
  }

  const buildSchema = (inputs, outputs, options = {}) => {
    const config = typeof options === 'string' ? { selectedFunction: options } : (options || {})
    const selectedFunction = config.selectedFunction || ''
    const dependencies = config.dependencies || []
    return {
      inputs: (inputs || []).map(item => ({
        name: item.name,
        type: item.type,
        unit: item.unit || '-',
        description: item.desc || '',
        required: true
      })),
      outputs: (outputs || []).map(item => ({
        name: item.name,
        type: item.type,
        unit: item.unit || '-',
        description: item.desc || '',
        required: false
      })),
      dependencies: normalizeDependencies(dependencies, selectedFunction)
    }
  }

  const getFileExtension = (fileName = '') => {
    const index = fileName.lastIndexOf('.')
    if (index < 0) return ''
    return fileName.substring(index + 1).toUpperCase()
  }

  const getModelTypeByFileName = (fileName = '') => {
    const ext = getFileExtension(fileName)
    return UPLOAD_MODEL_TYPE_BY_EXTENSION[ext] || ''
  }

  const isTextFunctionModel = (fileName = '') => {
    const ext = getFileExtension(fileName)
    return ext === 'PY' || ext === 'M' || ext === 'CPP'
  }

  const detectFunctionLikeContent = async (file) => {
    if (!file || typeof file.text !== 'function') return false
    const ext = getFileExtension(file.name)
    if (ext !== 'PY' && ext !== 'M' && ext !== 'CPP') return false
    try {
      const content = await file.text()
      if (ext === 'PY') {
        return /(^|\n)\s*def\s+\w+\s*\(/m.test(content)
      }
      if (ext === 'M') {
        return /(^|\n)\s*function\b/m.test(content)
      }
      return /(^|\n)\s*(?:template\s*<[^>]+>\s*)?(?:[\w:<>*&]+\s+)+[A-Za-z_]\w*\s*\([^;{}]*\)\s*(?:const\s*)?(?:noexcept\s*)?(?:->\s*[\w:<>*&]+\s*)?\{/m.test(content)
    } catch (error) {
      return false
    }
  }

  const isSupportedModelFile = (fileName = '') => Boolean(getModelTypeByFileName(fileName))

  const uploadModelWithPayload = async ({ file, name, version = 'AUTO', type, ioSchema }) => {
    if (!file) {
      throw new Error('模型文件不能为空')
    }
    const resolvedType = type || getModelTypeByFileName(file.name)
    if (!resolvedType) {
      throw new Error(`不支持的模型文件类型: ${file.name}`)
    }
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', name || file.name)
    formData.append('version', version || 'AUTO')
    formData.append('type', resolvedType)
    if (ioSchema) {
      formData.append('ioSchema', ioSchema)
    }
    return uploadModel(formData)
  }

  const resolveIoSchemaByFile = async (file) => {
    let parsed = null
    let selectedFunction = ''
    try {
      const functions = await listFunctionsByFile(file)
      if (functions?.length) {
        selectedFunction = functions[0].name || ''
        parsed = await parseSchemaByFunction(file, selectedFunction)
      } else {
        parsed = await parseSchemaByFile(file)
      }
    } catch (error) {
      parsed = await parseSchemaByFile(file)
    }
    const schema = buildSchema(parsed?.inputs || [], parsed?.outputs || [], { selectedFunction })
    const emptyIo = (schema.inputs?.length || 0) === 0 && (schema.outputs?.length || 0) === 0
    if (emptyIo && isTextFunctionModel(file?.name) && await detectFunctionLikeContent(file)) {
      throw new Error('检测到函数定义，但未解析到输入输出，请检查函数签名或类型注释')
    }
    return schema
  }

  const uploadModelAsset = async () => {
    const profile = await uploadModelWithPayload({
      file: uploadForm.file,
      name: uploadForm.name || uploadForm.file?.name || '',
      version: uploadForm.version,
      type: uploadForm.type,
      ioSchema: JSON.stringify(buildSchema(uploadForm.inputs, uploadForm.outputs, { selectedFunction: uploadForm.selectedFunction }))
    })
    await loadModels()
    selectedModel.value = models.value.find(m => m.id === profile.id) || null
    return profile
  }

  const uploadModelDirectoryAssets = async (files, options = {}) => {
    const items = Array.isArray(files) ? files : Array.from(files || [])
    if (!items.length) {
      throw new Error('目录中没有可上传的模型文件')
    }
    const total = items.length
    let successCount = 0
    let failedCount = 0
    let lastProfileId = null
    const errors = []

    for (let index = 0; index < items.length; index += 1) {
      const file = items[index]
      const relativePath = (file.webkitRelativePath || file.name || '').replace(/\\/g, '/')
      const modelName = relativePath.replace(/\.[^/.]+$/, '') || file.name
      try {
        const ioSchema = JSON.stringify(await resolveIoSchemaByFile(file))
        const profile = await uploadModelWithPayload({
          file,
          name: modelName,
          version: options.version || 'AUTO',
          type: getModelTypeByFileName(file.name),
          ioSchema
        })
        successCount += 1
        lastProfileId = profile?.id || lastProfileId
      } catch (error) {
        failedCount += 1
        errors.push({
          file: relativePath || file.name,
          message: error?.message || '上传失败'
        })
      } finally {
        if (typeof options.onProgress === 'function') {
          options.onProgress({
            processed: index + 1,
            total
          })
        }
      }
    }

    await loadModels()
    if (lastProfileId) {
      selectedModel.value = models.value.find(model => model.id === lastProfileId) || selectedModel.value
    }

    return {
      total,
      successCount,
      failedCount,
      errors
    }
  }

  const updateModelMetadata = async (modelId, assetId, newMeta) => {
    await updateModelProfile(modelId, {
      assetId,
      name: newMeta.name,
      description: newMeta.desc,
      ioSchema: JSON.stringify(buildSchema(newMeta.inputs, newMeta.outputs, { dependencies: newMeta.dependencies }))
    })
    await loadModels()
  }

  const deleteModel = async (modelId) => {
    await deleteModelProfile(modelId)
    await loadModels()
    if (selectedModel.value?.id === modelId) {
      selectedModel.value = null
    }
    selectedModelIds.value = selectedModelIds.value.filter(id => id !== modelId)
    return { success: true }
  }

  const deleteModelsBatch = async (modelIds = []) => {
    const ids = Array.from(new Set(modelIds || []))
    if (!ids.length) {
      return { success: true, deletedCount: 0 }
    }

    const selected = models.value.filter(model => ids.includes(model.id))
    const blocked = selected.filter(model => (model.refCount || 0) > 0)
    if (blocked.length > 0) {
      const names = blocked.map(model => model.name).join('、')
      throw new Error(`存在被规则引用的模型，无法批量删除：${names}`)
    }

    for (const modelId of ids) {
      await deleteModelProfile(modelId)
    }

    await loadModels()
    if (selectedModel.value?.id && ids.includes(selectedModel.value.id)) {
      selectedModel.value = null
    }
    selectedModelIds.value = selectedModelIds.value.filter(id => !ids.includes(id))
    return { success: true, deletedCount: ids.length }
  }

  const deleteModelVersion = async (modelId, version) => {
    const model = models.value.find(m => m.id === modelId)
    if (!model) return { success: false, msg: '模型不存在' }
    const target = model.history?.find(item => item.version === version)
    if (!target) return { success: false, msg: '版本不存在' }
    await deleteModelVersionApi(target.id)
    await loadModels()
    return { success: true }
  }

  const downloadModel = (model) => {
    if (!model?.history?.length) return
    const latest = model.history.find(item => item.latest) || model.history[model.history.length - 1]
    window.open(getModelDownloadUrl(latest.id))
  }

  return {
    models,
    selectedModel,
    selectedModelIds,
    setSelectedModelIds,
    clearSelectedModelIds,
    selectModel,
    showUploadModal,
    showMetaModal,
    showDeleteModal,
    uploadStep,
    uploadForm,
    autoParseCode,
    parseSchemaByFile,
    listFunctionsByFile,
    parseSchemaByFunction,
    listFunctionsByAsset,
    parseSchemaByAssetFunction,
    resetUploadParseState,
    getModelTypeByFileName,
    isSupportedModelFile,
    loadModels,
    uploadModelAsset,
    uploadModelDirectoryAssets,
    deleteModel,
    deleteModelsBatch,
    deleteModelVersion,
    updateModelMetadata,
    downloadModel
  }
})
