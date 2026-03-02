import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUIStore = defineStore('ui', () => {
  const activeRibbonTab = ref('Main') // Main, Data, Model, Relation, Analysis
  const showLeftSidebar = ref(true)
  const showRightSidebar = ref(true)
  
  // Tool Modals
  const showSystemLogs = ref(false)
  const showSQLConsole = ref(false)
  
  // Help Modals
  const showHelpGuide = ref(false)
  const showShortcuts = ref(false)
  const showAbout = ref(false)
  
  // Navigation helper
  const setActiveTab = (tab) => {
    activeRibbonTab.value = tab
  }
  
  const resetLayout = () => {
      showLeftSidebar.value = true
      showRightSidebar.value = true
  }

  return {
    activeRibbonTab,
    showLeftSidebar,
    showRightSidebar,
    showSystemLogs,
    showSQLConsole,
    showHelpGuide,
    showShortcuts,
    showAbout,
    setActiveTab,
    resetLayout
  }
})
