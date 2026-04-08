import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  fetchRuleList,
  createRule,
  updateRule as updateRuleApi,
  updateRuleStatus,
  deleteRule as deleteRuleApi,
  submitTask,
  stopTask as stopTaskApi,
  deleteTask as deleteTaskApi,
  fetchTasks
} from '../api/association'

export const useAssociationStore = defineStore('association', () => {
  const rules = ref([])
  const tasks = ref([])
  const selectedTask = ref(null)
  const showWizard = ref(false)
  const showExportReportModal = ref(false)
  const showExportResourceModal = ref(false)

  const loadRules = async () => {
    const list = await fetchRuleList()
    rules.value = list || []
  }

  const loadTasks = async (ruleId = null) => {
    const list = await fetchTasks(ruleId)
    tasks.value = list || []
  }

  const addRule = async (payload) => {
    const id = await createRule(payload)
    await loadRules()
    return id
  }

  const updateRule = async (id, payload) => {
    await updateRuleApi(id, payload)
    await loadRules()
  }

  const deleteRule = async (id) => {
    await deleteRuleApi(id)
    await loadRules()
    return { success: true }
  }

  const toggleRule = async (id) => {
    const rule = rules.value.find(r => r.id === id)
    if (!rule) return
    await updateRuleStatus(id, !rule.enabled)
    await loadRules()
  }

  const createTask = async (ruleId, options = {}) => {
    const normalizeTime = (value) => {
      if (!value) return value
      const text = value.replace('T', ' ')
      return text.split('.')[0]
    }
    const payload = { ruleId }
    if (options?.taskName && String(options.taskName).trim()) {
      payload.taskName = String(options.taskName).trim()
    }
    const timeRange = options?.timeRange || null
    if (timeRange && timeRange.startTime && timeRange.endTime) {
      payload.timeRange = {
        start: normalizeTime(timeRange.startTime),
        end: normalizeTime(timeRange.endTime)
      }
    }
    if (options?.scheduledStartTime) {
      payload.scheduledStartTime = normalizeTime(options.scheduledStartTime)
    }
    if (options?.scheduledEndTime) {
      payload.scheduledEndTime = normalizeTime(options.scheduledEndTime)
    }
    const taskId = await submitTask(payload)
    await loadTasks()
    return taskId
  }

  const stopTask = async (taskId) => {
    await stopTaskApi(taskId)
    await loadTasks()
  }

  const deleteTask = async (taskId) => {
    await deleteTaskApi(taskId)
    if (selectedTask.value?.id === taskId) {
      selectedTask.value = null
    }
    await loadTasks()
  }

  const selectTask = (task) => {
    selectedTask.value = task
  }

  return {
    rules,
    tasks,
    selectedTask,
    selectTask,
    addRule,
    updateRule,
    deleteRule,
    toggleRule,
    createTask,
    stopTask,
    deleteTask,
    loadRules,
    loadTasks,
    showWizard,
    showExportReportModal,
    showExportResourceModal
  }
})
