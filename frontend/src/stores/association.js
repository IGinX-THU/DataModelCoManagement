import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAssociationStore = defineStore('association', () => {
    const rules = ref([
        { 
            id: 'RULE_2025_01', 
            name: 'PID_Simulation_Highway', 
            desc: 'High speed PID validation',
            modelId: 'MDL_001',
            modelName: 'PID_Temp_Control.py', 
            modelVersion: 'v1.2.0', 
            bindings: { 'target': 'root.vehicle.engine.target_temp', 'current': 'root.vehicle.engine.temp', 'p_gain': 'root.config.pid.p', 'i_gain': 'root.config.pid.i' },
            results: { 'control': 'root.results.job_01.control_signal' },
            enabled: true, 
            updateTime: '2025-01-14 10:00', 
            lastRunStatus: 'SUCCESS'
        }
    ])

    const tasks = ref([])
    const selectedTask = ref(null)
    const showWizard = ref(false) // Global state for wizard
    const showExportReportModal = ref(false)
    const showExportResourceModal = ref(false)

    const selectTask = (task) => {
        selectedTask.value = task
    }

    const addRule = (rule) => {
        rules.value.push(rule)
    }

    const updateRule = (id, newRule) => {
        const idx = rules.value.findIndex(r => r.id === id)
        if (idx !== -1) {
            rules.value[idx] = { ...rules.value[idx], ...newRule }
        }
    }

    const copyRule = (id) => {
        const rule = rules.value.find(r => r.id === id)
        if (!rule) return
        
        const newId = `RULE_${Date.now()}`
        const copy = {
            ...JSON.parse(JSON.stringify(rule)),
            id: newId,
            name: `${rule.name}_Copy`,
            enabled: false, // Default to disabled for safety
            isRunning: false,
            updateTime: new Date().toISOString().slice(0, 16).replace('T', ' ')
        }
        rules.value.push(copy)
        return newId
    }

    const deleteRule = (id) => {
        const idx = rules.value.findIndex(r => r.id === id)
        if (idx === -1) return { success: false, msg: 'Rule not found' }
        
        const rule = rules.value[idx]
        if (rule.isRunning) {
            return { success: false, msg: 'Cannot delete: Rule has running tasks.' }
        }
        
        rules.value.splice(idx, 1)
        return { success: true }
    }

    const toggleRule = (id) => {
        const rule = rules.value.find(r => r.id === id)
        if (rule) rule.enabled = !rule.enabled
    }

    const createTask = (ruleId, timeRange) => {
        const rule = rules.value.find(r => r.id === ruleId)
        if (!rule) return null

        const now = new Date()
        const timeStr = now.getFullYear().toString() +
            (now.getMonth() + 1).toString().padStart(2, '0') +
            now.getDate().toString().padStart(2, '0') + '_' +
            now.getHours().toString().padStart(2, '0') +
            now.getMinutes().toString().padStart(2, '0')
            
        const taskId = `job_${timeStr}_${Math.floor(Math.random() * 1000)}`
        
        const newTask = {
            id: taskId,
            ruleId: rule.id,
            ruleName: rule.name,
            modelName: rule.modelName,
            startTime: timeRange.startTime,
            endTime: timeRange.endTime,
            status: 'PENDING', // PENDING, RUNNING, SUCCESS, FAILED, ABORTED
            progress: 0,
            createdAt: new Date().toLocaleString()
        }
        
        tasks.value.unshift(newTask)
        
        // Mock execution logic...
        rule.isRunning = true
        setTimeout(() => {
            const t = tasks.value.find(x => x.id === taskId)
            if (t) t.status = 'RUNNING'
            
            let p = 0
            const interval = setInterval(() => {
                p += 5
                const currentTask = tasks.value.find(x => x.id === taskId)
                if (currentTask && currentTask.status === 'RUNNING') {
                    currentTask.progress = p
                    if (p >= 100) {
                        currentTask.status = 'SUCCESS'
                        currentTask.finishedAt = new Date().toLocaleString()
                        rule.isRunning = false // Reset rule status
                        clearInterval(interval)
                    }
                } else {
                    rule.isRunning = false // Reset rule status if aborted
                    clearInterval(interval)
                }
            }, 200)
        }, 1000)

        return taskId
    }

    const stopTask = (taskId) => {
        const task = tasks.value.find(t => t.id === taskId)
        if (task && (task.status === 'PENDING' || task.status === 'RUNNING')) {
            task.status = 'ABORTED'
        }
    }

    return {
        rules,
        tasks,
        selectedTask,
        selectTask,
        addRule,
        updateRule,
        copyRule,
        deleteRule,
        toggleRule,
        createTask,
        stopTask,
        showWizard,
        showExportReportModal,
        showExportResourceModal
    }
})
