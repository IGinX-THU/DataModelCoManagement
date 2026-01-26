import { defineStore } from 'pinia'
import { ref, computed, reactive } from 'vue'

export const useModelStore = defineStore('model', () => {
  const models = ref([
      { 
          id: 'MDL_001', 
          name: 'PID_Temp_Control.py', 
          version: 'v1.2.0', 
          type: 'Python', 
          size: '15 KB', 
          uploadTime: '2025-01-12 14:30', 
          refCount: 2, 
          history: [
              { version: 'v1.0.0', uploadTime: '2025-01-01 10:00', inputs: [{name: 'target', type: 'Float'}, {name: 'current', type: 'Float'}], outputs: [{name: 'control', type: 'Float'}] },
              { version: 'v1.1.0', uploadTime: '2025-01-05 14:00', inputs: [{name: 'target', type: 'Float'}, {name: 'current', type: 'Float'}, {name: 'p_gain', type: 'Float'}], outputs: [{name: 'control', type: 'Float'}] },
              { version: 'v1.2.0', uploadTime: '2025-01-12 14:30', inputs: [{name: 'target', type: 'Float'}, {name: 'current', type: 'Float'}, {name: 'p_gain', type: 'Float'}, {name: 'i_gain', type: 'Float'}], outputs: [{name: 'control', type: 'Float'}] }
          ]
      },
      { 
          id: 'MDL_002', 
          name: 'Vehicle_Dynamics.fmu', 
          version: 'v1.0', 
          type: 'FMU', 
          size: '2.4 MB', 
          uploadTime: '2025-01-10 09:15', 
          refCount: 1, 
          history: [
              { version: 'v1.0', uploadTime: '2025-01-10 09:15', inputs: [], outputs: [] }
          ] 
      },
      { id: 'MDL_003', name: 'Data_Filter_v2.m', version: 'v2.1', type: 'MATLAB', size: '45 KB', uploadTime: '2025-01-08 11:20', refCount: 0, history: [{version: 'v2.1', uploadTime: '2025-01-08 11:20', inputs: [], outputs: []}] },
      { id: 'MDL_004', name: 'Algo_Core.dll', version: 'v1.0.5', type: 'DLL', size: '512 KB', uploadTime: '2025-01-05 16:45', refCount: 0, history: [{version: 'v1.0.5', uploadTime: '2025-01-05 16:45', inputs: [], outputs: []}] },
  ])

  const selectedModel = ref(null)
  
  // UI State
  const showUploadModal = ref(false)
  const showMetaModal = ref(false)
  const showDeleteModal = ref(false)
  
  // Upload Wizard State
  const uploadStep = ref(1)
  const uploadForm = reactive({
      name: '',
      version: 'v1.0.0',
      type: 'Python',
      file: null,
      inputs: [],
      outputs: [],
      env: 'Python 3.8'
  })

  const selectModel = (model) => {
      selectedModel.value = model
  }

  // --- Actions ---
  const autoParseCode = (fileContent) => {
      const inputs = []
      const outputs = []
      
      // Regex for Python/C++ comments like: # @Input: speed (Float) - Speed in km/h
      // Supports: # @Input: name (Type) - Desc
      const inputRegex = /@Input:\s*(\w+)\s*(?:\(([^)]+)\))?\s*(?:-\s*(.*))?/gi
      const outputRegex = /@Output:\s*(\w+)\s*(?:\(([^)]+)\))?\s*(?:-\s*(.*))?/gi
      
      let match
      while ((match = inputRegex.exec(fileContent)) !== null) {
          inputs.push({ name: match[1], type: match[2] || 'String', desc: match[3] || '', unit: '-' })
      }
      while ((match = outputRegex.exec(fileContent)) !== null) {
          outputs.push({ name: match[1], type: match[2] || 'String', desc: match[3] || '', unit: '-' })
      }
      
      return { inputs, outputs }
  }

  const uploadModel = () => {
      // Mock Upload Process
      return new Promise((resolve) => {
          setTimeout(() => {
              const newModel = {
                  id: `MDL_${Date.now().toString().slice(-4)}`,
                  name: uploadForm.name,
                  version: uploadForm.version,
                  type: uploadForm.type,
                  size: (uploadForm.file?.size / 1024).toFixed(1) + ' KB',
                  uploadTime: new Date().toLocaleString(),
                  refCount: 0,
                  history: [
                      { 
                          version: uploadForm.version, 
                          uploadTime: new Date().toLocaleString(),
                          inputs: JSON.parse(JSON.stringify(uploadForm.inputs)),
                          outputs: JSON.parse(JSON.stringify(uploadForm.outputs))
                      }
                  ]
              }
              models.value.unshift(newModel)
              resolve(newModel)
          }, 1500)
      })
  }

  const deleteModel = (modelId) => {
      const modelIdx = models.value.findIndex(m => m.id === modelId)
      if (modelIdx === -1) return { success: false, msg: 'Model not found' }
      
      const model = models.value[modelIdx]
      if (model.refCount > 0) {
          return { success: false, msg: `Cannot delete: Model is referenced by ${model.refCount} active rules.` }
      }
      
      models.value.splice(modelIdx, 1)
      selectedModel.value = null
      return { success: true }
  }

  const deleteModelVersion = (modelId, version) => {
      const modelIdx = models.value.findIndex(m => m.id === modelId)
      if (modelIdx === -1) return { success: false, msg: 'Model not found' }
      
      const model = models.value[modelIdx]
      
      // Reference Count Check (Mock logic: if refCount > 0, assume it's using the latest version or general model)
      if (model.refCount > 0) {
          return { success: false, msg: `Cannot delete: Model is referenced by ${model.refCount} active rules.` }
      }
      
      // Remove version from history
      const vIdx = model.history.findIndex(h => h.version === version)
      if (vIdx !== -1) {
          model.history.splice(vIdx, 1)
          // If no versions left, delete the model entirely
          if (model.history.length === 0) {
              models.value.splice(modelIdx, 1)
              selectedModel.value = null
          } else {
              // Update current display version to the latest
              const latest = model.history[model.history.length - 1]
              model.version = latest.version
          }
          return { success: true }
      }
      return { success: false, msg: 'Version not found' }
  }

  const updateModelMetadata = (modelId, oldVersion, newMeta) => {
      const model = models.value.find(m => m.id === modelId)
      if (!model) return

      // Update Top-level info
      if (newMeta.name) model.name = newMeta.name
      
      // Update Version specific info
      const histIdx = model.history.findIndex(h => h.version === oldVersion)
      if (histIdx !== -1) {
          const entry = model.history[histIdx]
          entry.inputs = newMeta.inputs
          entry.outputs = newMeta.outputs
          // Update version string if changed
          if (newMeta.version && newMeta.version !== oldVersion) {
              entry.version = newMeta.version
              // If this was the displayed version, update top-level
              if (model.version === oldVersion) {
                  model.version = newMeta.version
              }
          }
      }
  }

  const downloadModel = (model) => {
      if (!model) return
      const ver = model.version
      alert(`Downloading ${model.name} (${ver})...\nIn a real app, this would trigger a file download.`)
  }

  return {
      models,
      selectedModel,
      selectModel,
      showUploadModal,
      showMetaModal,
      uploadStep,
      uploadForm,
      autoParseCode,
      uploadModel,
      deleteModel,
      deleteModelVersion,
      updateModelMetadata,
      downloadModel,
      showDeleteModal
  }
})
