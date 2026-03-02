<script setup>
import { useUIStore } from '../stores/ui'

const uiStore = useUIStore()

const shortcuts = [
    { key: 'Ctrl + S', desc: 'Save current changes' },
    { key: 'Ctrl + Enter', desc: 'Execute SQL query (in Console)' },
    { key: 'F11', desc: 'Toggle Fullscreen' },
    { key: 'Esc', desc: 'Close modals / Cancel selection' },
    { key: 'Ctrl + /', desc: 'Toggle Sidebar visibility' }
]
</script>

<template>
    <div v-if="uiStore.showShortcuts" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] flex flex-col">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50 rounded-t-lg">
                <h3 class="font-bold text-gray-800 flex items-center">
                    <i class="ri-keyboard-box-line mr-2 text-purple-600"></i> Keyboard Shortcuts
                </h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="uiStore.showShortcuts = false"></i>
            </div>
            <div class="p-6">
                <div class="grid grid-cols-1 gap-3">
                    <div v-for="(item, index) in shortcuts" :key="index" class="flex justify-between items-center border-b border-gray-50 pb-2 last:border-0 last:pb-0">
                        <span class="text-sm text-gray-600">{{ item.desc }}</span>
                        <kbd class="px-2 py-1 bg-gray-100 border border-gray-300 rounded text-xs font-mono text-gray-700 font-bold shadow-sm min-w-[60px] text-center">{{ item.key }}</kbd>
                    </div>
                </div>
            </div>
            <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                <button @click="uiStore.showShortcuts = false" class="px-4 py-2 bg-gray-100 text-gray-700 rounded text-sm hover:bg-gray-200">Close</button>
            </div>
        </div>
    </div>
</template>