<script setup>
import { useUIStore } from '../stores/ui'

const uiStore = useUIStore()
</script>

<template>
    <div v-if="uiStore.showAbout" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[400px] flex flex-col overflow-hidden">
            <div class="h-32 bg-gradient-to-br from-blue-600 to-indigo-700 flex flex-col items-center justify-center text-white relative">
                <i class="ri-close-line cursor-pointer text-white/70 hover:text-white absolute top-4 right-4 text-xl" @click="uiStore.showAbout = false"></i>
                <div class="w-16 h-16 bg-white rounded-full flex items-center justify-center shadow-lg mb-2 text-blue-600 text-3xl font-bold">
                    <i class="ri-database-2-fill"></i>
                </div>
                <h3 class="font-bold text-xl">IGinX Data Manager</h3>
                <span class="text-xs opacity-80">Enterprise Edition</span>
            </div>
            <div class="p-6 text-center space-y-4">
                <div>
                    <div class="text-xs text-gray-500 uppercase tracking-wider mb-1">Version</div>
                    <div class="font-mono font-bold text-gray-800">v1.2.0 (Build 20250124)</div>
                </div>
                <div>
                    <div class="text-xs text-gray-500 uppercase tracking-wider mb-1">Core Engine</div>
                    <div class="font-medium text-gray-700">IGinX v0.6.0-SNAPSHOT</div>
                </div>
                <div class="text-xs text-gray-400 pt-4 border-t border-gray-100">
                    &copy; 2025 Tsinghua University & IGinX Contributors.<br>All rights reserved.
                </div>
            </div>
        </div>
    </div>
</template>