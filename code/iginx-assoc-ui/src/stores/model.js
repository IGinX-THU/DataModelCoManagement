import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import {
  fetchModelList,
  uploadModel,
  updateModelProfile,
  deleteModelProfile,
  deleteModelVersion as deleteModelVersionApi,
  parseModelSchema,
  getModelDownloadUrl
} from '../api/model'

export const useModelStore = defineStore('model', () => {
  const models = ref([])
  const selectedModel = ref(null)

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
    outputs: []
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
    }))
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
  }

  const selectModel = (model) => {
    selectedModel.value = model
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

  const buildSchema = (inputs, outputs) => ({
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
    dependencies: []
  })

  const uploadModelAsset = async () => {
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('name', uploadForm.name || uploadForm.file?.name || '')
    formData.append('version', uploadForm.version)
    formData.append('type', uploadForm.type)
    formData.append('ioSchema', JSON.stringify(buildSchema(uploadForm.inputs, uploadForm.outputs)))
    const profile = await uploadModel(formData)
    await loadModels()
    selectedModel.value = models.value.find(m => m.id === profile.id) || null
    return profile
  }

  const updateModelMetadata = async (modelId, oldVersion, newMeta) => {
    await updateModelProfile(modelId, {
      name: newMeta.name,
      description: newMeta.desc,
      ioSchema: JSON.stringify(buildSchema(newMeta.inputs, newMeta.outputs))
    })
    await loadModels()
  }

  const deleteModel = async (modelId) => {
    await deleteModelProfile(modelId)
    await loadModels()
    if (selectedModel.value?.id === modelId) {
      selectedModel.value = null
    }
    return { success: true }
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
    selectModel,
    showUploadModal,
    showMetaModal,
    showDeleteModal,
    uploadStep,
    uploadForm,
    autoParseCode,
    parseSchemaByFile,
    loadModels,
    uploadModelAsset,
    deleteModel,
    deleteModelVersion,
    updateModelMetadata,
    downloadModel
  }
})
