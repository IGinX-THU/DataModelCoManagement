<script setup>
import { useDataStore } from '../stores/data'
import { useModelStore } from '../stores/model'
import { useAssociationStore } from '../stores/association'
import { useUIStore } from '../stores/ui'
import { useRouter } from 'vue-router'
import { ref } from 'vue'

const dataStore = useDataStore()
const modelStore = useModelStore()
const associationStore = useAssociationStore()
const uiStore = useUIStore()
const router = useRouter()
const isRibbonCollapsed = ref(false)

const tabs = ['Main', 'Data', 'Model', 'Relation', 'Analysis', 'Tools', 'View', 'Help']

// Auth State
const isLoggedIn = ref(true)
const user = ref({ name: 'Ludens', avatar: 'L' })
const showUserMenu = ref(false)

const handleLogin = () => {
    isLoggedIn.value = true
    user.value = { name: 'Admin', avatar: 'A' }
}

const handleLogout = () => {
    isLoggedIn.value = false
    showUserMenu.value = false
}

const isFullscreen = ref(false)

const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen()
        isFullscreen.value = true
    } else {
        if (document.exitFullscreen) {
            document.exitFullscreen()
            isFullscreen.value = false
        }
    }
}

const checkUpdate = () => {
    alert('Checking for updates...\n\nYou are using the latest version (v1.2.0).')
}

const navigate = async (tab) => {
    uiStore.setActiveTab(tab)
    try {
        switch(tab) {
            case 'Data': await router.push('/data'); break;
            case 'Model': await router.push('/models'); break;
            case 'Relation': await router.push('/relations'); break;
            case 'Analysis': await router.push('/analysis'); break;
            case 'Main': await router.push('/dashboard'); break;
        }
    } catch (e) {
        console.error('Navigation failed:', e)
    }
}
</script>

<template>
  <div class="flex flex-col bg-white border-b border-gray-200 shadow-sm z-50 relative">
    <!-- Top Menu Text -->
    <div class="flex items-center px-2 py-0 bg-gray-50 border-b border-gray-200 text-xs h-9">
        <div class="flex space-x-1 h-full items-end px-2">
            <span v-for="tab in tabs" :key="tab" 
                  @click="navigate(tab)"
                  :class="uiStore.activeRibbonTab === tab ? 'text-blue-600 bg-white border-t border-l border-r border-gray-200 rounded-t shadow-[0_-1px_2px_rgba(0,0,0,0.02)] relative -bottom-px z-10' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded-t'"
                  class="cursor-pointer select-none px-4 py-1.5 transition-all duration-100">
                  {{ tab === 'Main' ? '主页' : tab === 'Data' ? '数据' : tab === 'Model' ? '模型' : tab === 'Relation' ? '关联' : tab === 'Analysis' ? '分析' : tab === 'Tools' ? '工具' : tab === 'View' ? '视图' : '帮助' }}
            </span>
        </div>
        <div class="flex-1 border-b border-gray-200 h-full"></div>
        <div class="flex items-center space-x-3 text-gray-500 relative border-b border-gray-200 h-full pr-2">
             <button @click="isRibbonCollapsed = !isRibbonCollapsed" class="p-1 hover:bg-gray-200 rounded text-gray-400 hover:text-gray-700 transition-colors" title="Toggle Ribbon">
                <i :class="isRibbonCollapsed ? 'ri-arrow-down-s-line' : 'ri-arrow-up-s-line'"></i>
             </button>
             <span class="text-[10px] mr-2 text-gray-400">IGinX Data Manager v1.2</span>
             
             <!-- User Profile Section -->
             <div v-if="isLoggedIn" class="relative">
                 <div @click="showUserMenu = !showUserMenu" class="flex items-center space-x-2 cursor-pointer hover:bg-gray-200 px-2 py-0.5 rounded transition-colors select-none">
                     <div class="w-6 h-6 rounded-full bg-orange-500 text-white flex items-center justify-center text-xs font-bold shadow-sm border border-white">
                        {{ user.avatar }}
                     </div>
                     <span class="font-medium text-gray-700">{{ user.name }}</span>
                     <i class="ri-arrow-down-s-fill text-gray-400"></i>
                 </div>

                 <!-- Dropdown Menu -->
                 <div v-if="showUserMenu" class="absolute right-0 top-full mt-1 w-48 bg-white border border-gray-200 shadow-xl rounded-lg py-1 z-[100] animate-in fade-in slide-in-from-top-2 duration-200">
                     <div class="px-4 py-3 border-b border-gray-100 flex items-center space-x-3">
                         <div class="w-10 h-10 rounded-full bg-orange-500 text-white flex items-center justify-center text-lg font-bold">
                            {{ user.avatar }}
                         </div>
                         <div>
                             <div class="font-bold text-gray-800">{{ user.name }}</div>
                             <div class="text-[10px] text-gray-500">Administrator</div>
                         </div>
                     </div>
                     <div class="py-1">
                         <div class="px-4 py-2 hover:bg-gray-50 cursor-pointer flex items-center text-gray-600 hover:text-blue-600">
                             <i class="ri-user-settings-line mr-2"></i> Profile Settings
                         </div>
                         <div class="px-4 py-2 hover:bg-gray-50 cursor-pointer flex items-center text-gray-600 hover:text-blue-600">
                             <i class="ri-global-line mr-2"></i> Language
                         </div>
                     </div>
                     <div class="border-t border-gray-100 py-1">
                         <div @click="handleLogout" class="px-4 py-2 hover:bg-red-50 cursor-pointer flex items-center text-red-600">
                             <i class="ri-logout-box-line mr-2"></i> Sign Out
                         </div>
                     </div>
                 </div>
             </div>
             
             <!-- Login Button -->
             <button v-else @click="handleLogin" class="flex items-center px-3 py-1 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 transition-colors shadow-sm">
                 <i class="ri-user-line mr-1"></i> Sign In
             </button>

             <!-- Overlay to close menu -->
             <div v-if="showUserMenu" @click="showUserMenu = false" class="fixed inset-0 z-[90]"></div>
        </div>
    </div>

    <!-- Ribbon Toolbar -->
    <div v-show="!isRibbonCollapsed" class="h-28 bg-white flex items-center px-4 overflow-x-auto space-x-6 pb-2 transition-all duration-300 ease-in-out border-b border-gray-100">
        
        <!-- Main Group (Dashboard) -->
        <div v-if="['Main'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 relative pb-4">
             <!-- Dashboard specific tools can go here in future -->
        </div>

        <!-- Data Group -->
        <div v-if="['Main', 'Data'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 pr-6 border-r border-gray-200 relative pb-4">
            <div @click="dataStore.showAddSourceModal = true" class="ribbon-btn group">
                <div class="icon-box bg-blue-50 text-blue-600 group-hover:bg-blue-100"><i class="ri-database-2-line text-2xl"></i></div>
                <span>新增数据源</span>
            </div>
            <div @click="dataStore.showDeletePathModal = true" class="ribbon-btn group">
                <div class="icon-box bg-red-50 text-red-600 group-hover:bg-red-100"><i class="ri-delete-bin-line text-2xl"></i></div>
                <span>删除数据</span>
            </div>
            <div @click="dataStore.showSourceDetailsModal = true" class="ribbon-btn group">
                <div class="icon-box bg-cyan-50 text-cyan-600 group-hover:bg-cyan-100"><i class="ri-file-info-line text-2xl"></i></div>
                <span>数据源详情</span>
            </div>
            <div @click="dataStore.openImportWizard('ts')" class="ribbon-btn group">
                <div class="icon-box bg-green-50 text-green-600 group-hover:bg-green-100"><i class="ri-upload-line text-2xl"></i></div>
                <span>导入数据</span>
            </div>
            <div @click="dataStore.showExportModal = true" class="ribbon-btn group">
                <div class="icon-box bg-purple-50 text-purple-600 group-hover:bg-purple-100"><i class="ri-download-line text-2xl"></i></div>
                <span>导出数据</span>
            </div>
            <!-- <div class="text-[10px] text-gray-400 font-bold absolute bottom-0 left-1/2 transform -translate-x-1/2 uppercase tracking-widest whitespace-nowrap">数据资源管理</div> -->
        </div>

        <!-- Model Group -->
        <div v-if="['Main', 'Model'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 pr-6 border-r border-gray-200 relative pb-4">
            <div @click="modelStore.showUploadModal = true" class="ribbon-btn group">
                <div class="icon-box bg-orange-50 text-orange-600 group-hover:bg-orange-100"><i class="ri-upload-cloud-line text-2xl"></i></div>
                <span>上传模型</span>
            </div>
            <div @click="modelStore.downloadModel(modelStore.selectedModel)" :class="{'opacity-50 pointer-events-none': !modelStore.selectedModel}" class="ribbon-btn group">
                <div class="icon-box bg-orange-50 text-orange-600 group-hover:bg-orange-100"><i class="ri-download-cloud-line text-2xl"></i></div>
                <span>下载模型</span>
            </div>
            <div @click="modelStore.showMetaModal = true" :class="{'opacity-50 pointer-events-none': !modelStore.selectedModel}" class="ribbon-btn group">
                <div class="icon-box bg-orange-50 text-orange-600 group-hover:bg-orange-100"><i class="ri-edit-box-line text-2xl"></i></div>
                <span>元模型编辑器</span>
            </div>
            <div @click="modelStore.showDeleteModal = true" :class="{'opacity-50 pointer-events-none': !modelStore.selectedModel && (modelStore.selectedModelIds?.length || 0) === 0}" class="ribbon-btn group">
                <div class="icon-box bg-red-50 text-red-600 group-hover:bg-red-100"><i class="ri-delete-bin-line text-2xl"></i></div>
                <span>移除模型</span>
            </div>
            <!-- <div class="text-[10px] text-gray-400 font-bold absolute bottom-0 left-1/2 transform -translate-x-1/2 uppercase tracking-widest whitespace-nowrap">模型资产管理</div> -->
        </div>

        <!-- Relation Group -->
        <div v-if="['Main', 'Relation'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 pr-6 border-r border-gray-200 relative pb-4">
            <div @click="associationStore.showWizard = true" class="ribbon-btn group">
                <div class="icon-box bg-indigo-50 text-indigo-600 group-hover:bg-indigo-100"><i class="ri-add-box-line text-2xl"></i></div>
                <span>创建关联规则</span>
            </div>
             <div class="ribbon-btn group">
                <div class="icon-box bg-indigo-50 text-indigo-600 group-hover:bg-indigo-100"><i class="ri-links-line text-2xl"></i></div>
                <span>关联规则配置</span>
            </div>
            <!-- <div class="text-[10px] text-gray-400 font-bold absolute bottom-0 left-1/2 transform -translate-x-1/2 uppercase tracking-widest whitespace-nowrap">关联调度引擎</div> -->
        </div>

        <!-- Analysis Group -->
        <div v-if="['Main', 'Analysis'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 pr-6 border-r border-gray-200 relative pb-4">
            <div @click="associationStore.showExportReportModal = true" class="ribbon-btn group">
                <div class="icon-box bg-red-50 text-red-600 group-hover:bg-red-100"><i class="ri-file-pdf-line text-2xl"></i></div>
                <span>导出实验报告</span>
            </div>
            <div @click="associationStore.showExportResourceModal = true" class="ribbon-btn group">
                <div class="icon-box bg-purple-50 text-purple-600 group-hover:bg-purple-100"><i class="ri-folder-zip-line text-2xl"></i></div>
                <span>导出资源</span>
            </div>
            <!-- <div class="text-[10px] text-gray-400 font-bold absolute bottom-0 left-1/2 transform -translate-x-1/2 uppercase tracking-widest whitespace-nowrap">可视化分析</div> -->
        </div>
        
        <!-- Tools Group -->
        <div v-if="['Main', 'Tools'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 pr-6 border-r border-gray-200 relative pb-4">
            <div @click="uiStore.showSystemLogs = true" class="ribbon-btn group">
                <div class="icon-box bg-gray-50 text-gray-600 group-hover:bg-gray-100"><i class="ri-terminal-box-line text-2xl"></i></div>
                <span>系统日志</span>
            </div>
            <div @click="uiStore.showSQLConsole = true" class="ribbon-btn group">
                <div class="icon-box bg-blue-50 text-blue-600 group-hover:bg-blue-100"><i class="ri-terminal-line text-2xl"></i></div>
                <span>SQL控制台</span>
            </div>
            <div class="ribbon-btn group">
                <div class="icon-box bg-gray-50 text-gray-600 group-hover:bg-gray-100"><i class="ri-settings-3-line text-2xl"></i></div>
                <span>全局设置</span>
            </div>
            <div class="ribbon-btn group">
                <div class="icon-box bg-gray-50 text-gray-600 group-hover:bg-gray-100"><i class="ri-shield-user-line text-2xl"></i></div>
                <span>用户管理</span>
            </div>
        </div>

        <!-- View Group -->
        <div v-if="['Main', 'View'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 relative pb-4">
             <div @click="uiStore.showLeftSidebar = !uiStore.showLeftSidebar" class="ribbon-btn group">
                <div :class="uiStore.showLeftSidebar ? 'bg-blue-100 text-blue-600' : 'bg-gray-50 text-gray-400'" class="icon-box group-hover:bg-blue-200"><i class="ri-layout-left-line text-2xl"></i></div>
                <span>数据资源库</span>
            </div>
            <div @click="uiStore.showRightSidebar = !uiStore.showRightSidebar" class="ribbon-btn group">
                <div :class="uiStore.showRightSidebar ? 'bg-blue-100 text-blue-600' : 'bg-gray-50 text-gray-400'" class="icon-box group-hover:bg-blue-200"><i class="ri-layout-right-line text-2xl"></i></div>
                <span>模型资产库</span>
            </div>
            <div class="w-px bg-gray-200 h-12 mx-2"></div>
            <div @click="uiStore.resetLayout" class="ribbon-btn group">
                <div class="icon-box bg-gray-50 text-gray-600 group-hover:bg-gray-100"><i class="ri-layout-grid-fill text-2xl"></i></div>
                <span>重置布局</span>
            </div>
            <div @click="toggleFullscreen" class="ribbon-btn group">
                <div :class="isFullscreen ? 'bg-blue-100 text-blue-600' : 'bg-gray-50 text-gray-600'" class="icon-box group-hover:bg-blue-200">
                    <i :class="isFullscreen ? 'ri-fullscreen-exit-line' : 'ri-fullscreen-line'" class="text-2xl"></i>
                </div>
                <span>{{ isFullscreen ? '退出全屏' : '全屏模式' }}</span>
            </div>
            <!-- <div class="text-[10px] text-gray-400 font-bold absolute bottom-0 left-1/2 transform -translate-x-1/2 uppercase tracking-widest whitespace-nowrap">视图管理</div> -->
        </div>

        <!-- Help Group -->
        <div v-if="['Main', 'Help'].includes(uiStore.activeRibbonTab)" class="flex space-x-2 relative pb-4">
             <div @click="uiStore.showHelpGuide = true" class="ribbon-btn group">
                <div class="icon-box bg-blue-50 text-blue-600 group-hover:bg-blue-100"><i class="ri-book-open-line text-2xl"></i></div>
                <span>用户指南</span>
            </div>
            <div @click="uiStore.showShortcuts = true" class="ribbon-btn group">
                <div class="icon-box bg-purple-50 text-purple-600 group-hover:bg-purple-100"><i class="ri-keyboard-box-line text-2xl"></i></div>
                <span>快捷键</span>
            </div>
            <div @click="checkUpdate" class="ribbon-btn group">
                <div class="icon-box bg-green-50 text-green-600 group-hover:bg-green-100"><i class="ri-refresh-line text-2xl"></i></div>
                <span>检查更新</span>
            </div>
            <div class="w-px bg-gray-200 h-12 mx-2"></div>
            <div @click="uiStore.showAbout = true" class="ribbon-btn group">
                <div class="icon-box bg-gray-50 text-gray-600 group-hover:bg-gray-100"><i class="ri-information-line text-2xl"></i></div>
                <span>关于系统</span>
            </div>
        </div>

    </div>
  </div>
</template>

<style scoped>
.ribbon-btn {
    @apply flex flex-col items-center justify-center cursor-pointer min-w-[70px] space-y-1 p-1 rounded hover:bg-gray-50 transition-all duration-200;
}
.ribbon-btn:active {
    @apply bg-gray-100 transform scale-95;
}
.icon-box {
    @apply w-10 h-10 rounded-lg flex items-center justify-center transition-all duration-200 shadow-sm border border-gray-100 bg-white;
}
.ribbon-btn:hover .icon-box {
    @apply shadow-md -translate-y-0.5 border-blue-100;
}
.ribbon-btn span {
    @apply text-[10px] text-gray-600 font-medium text-center leading-tight group-hover:text-blue-600 transition-colors;
}
</style>
