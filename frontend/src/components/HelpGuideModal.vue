<script setup>
import { useUIStore } from '../stores/ui'

const uiStore = useUIStore()
</script>

<template>
    <div v-if="uiStore.showHelpGuide" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/50 backdrop-blur-sm">
        <div class="bg-white rounded-lg shadow-xl w-[700px] h-[500px] flex flex-col">
            <div class="px-6 py-4 border-b border-gray-100 flex justify-between items-center bg-gray-50 rounded-t-lg">
                <h3 class="font-bold text-gray-800 flex items-center">
                    <i class="ri-book-open-line mr-2 text-blue-600"></i> User Guide
                </h3>
                <i class="ri-close-line cursor-pointer text-gray-500 hover:text-black" @click="uiStore.showHelpGuide = false"></i>
            </div>
            <div class="flex-1 overflow-y-auto p-6 text-sm text-gray-700 space-y-4">
                <h4 class="font-bold text-lg text-gray-900 border-b pb-1">Introduction</h4>
                <p>Welcome to IGinX Data Manager. This system allows you to manage time-series and relational data, define association rules, and perform analytical tasks.</p>
                
                <h4 class="font-bold text-lg text-gray-900 border-b pb-1 mt-6">Core Modules</h4>
                <ul class="list-disc pl-5 space-y-2">
                    <li><strong>Data Management:</strong> Connect to various data sources (InfluxDB, PostgreSQL), browse schema, and import/export data.</li>
                    <li><strong>Model Assets:</strong> Upload and manage machine learning models (.py files) for data processing.</li>
                    <li><strong>Association Rules:</strong> Create rules that bind data inputs to model inputs and define output destinations.</li>
                    <li><strong>Analysis & Tasks:</strong> Monitor execution tasks and visualize results using charts.</li>
                </ul>

                <h4 class="font-bold text-lg text-gray-900 border-b pb-1 mt-6">Common Workflows</h4>
                <div class="bg-blue-50 p-3 rounded border border-blue-100">
                    <p class="font-bold mb-1">Creating a New Task:</p>
                    <ol class="list-decimal pl-5">
                        <li>Go to <strong>Relation</strong> tab.</li>
                        <li>Click "Create Association Rule" to bind data to a model.</li>
                        <li>Go to <strong>Task Monitor</strong> (in Analysis) to run the rule.</li>
                        <li>View results in the <strong>Result Analysis</strong> charts.</li>
                    </ol>
                </div>
            </div>
            <div class="px-6 py-4 border-t border-gray-100 flex justify-end">
                <button @click="uiStore.showHelpGuide = false" class="px-4 py-2 bg-blue-600 text-white rounded text-sm hover:bg-blue-700">Got it</button>
            </div>
        </div>
    </div>
</template>