<script setup>
import RibbonToolbar from '../components/RibbonToolbar.vue'
import LeftSidebar from '../components/LeftSidebar.vue'
import RightSidebar from '../components/RightSidebar.vue'
import DataModals from '../components/DataModals.vue'
import SystemLogsModal from '../components/SystemLogsModal.vue'
import SQLConsoleModal from '../components/SQLConsoleModal.vue'
import HelpGuideModal from '../components/HelpGuideModal.vue'
import ShortcutsModal from '../components/ShortcutsModal.vue'
import AboutModal from '../components/AboutModal.vue'
import { useUIStore } from '../stores/ui'

const uiStore = useUIStore()
</script>

<template>
  <div class="flex flex-col h-screen w-screen overflow-hidden bg-white text-gray-900 font-sans">
    <!-- Ribbon Toolbar (Fixed Top) -->
    <RibbonToolbar />

    <!-- Main Content Area -->
    <div class="flex flex-1 overflow-hidden relative">
      <!-- Left Sidebar (Collapsible) -->
      <LeftSidebar v-if="uiStore.showLeftSidebar" class="w-64 flex-shrink-0 border-r border-gray-200 bg-gray-50 transition-all duration-300 ease-in-out" />

      <!-- Workspace (Router View) -->
      <div class="flex-1 bg-white relative overflow-hidden flex flex-col">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>

      <!-- Right Sidebar (Collapsible) -->
      <RightSidebar v-if="uiStore.showRightSidebar" class="w-72 flex-shrink-0 border-l border-gray-200 bg-gray-50 transition-all duration-300 ease-in-out" />
      
      <!-- Topology Drawer (Removed as per request) -->
      <!-- <TopologyDrawer /> -->
    </div>

    <!-- Global Modals -->
    <DataModals />
    <SystemLogsModal />
    <SQLConsoleModal />
    <HelpGuideModal />
    <ShortcutsModal />
    <AboutModal />

    <!-- Status Bar -->
    <div class="h-6 bg-blue-600 text-white flex items-center justify-between px-3 text-[10px] select-none z-20">
      <div class="flex space-x-4">
        <span><i class="ri-git-branch-line"></i> master*</span>
        <span><i class="ri-error-warning-line"></i> 0 errors</span>
      </div>
      <div class="flex space-x-4">
        <span>Ln 12, Col 45</span>
        <span>UTF-8</span>
        <span>IGinX Core v1.2.0</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
