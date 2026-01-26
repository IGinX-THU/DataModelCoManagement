<script setup>
import { useDataStore } from '../stores/data'
import { onMounted, ref, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const dataStore = useDataStore()
const chartRef = ref(null)
let chartInstance = null

const initChart = () => {
    if (!chartRef.value || !dataStore.topologyRootNode) return
    
    if (chartInstance) chartInstance.dispose()
    chartInstance = echarts.init(chartRef.value)

    const root = dataStore.topologyRootNode

    // Transform Data
    const transformData = (node, depth = 0) => {
        let symbolColor = '#3b82f6' // Blue for root
        let labelColor = '#1d4ed8'
        
        if (depth === 1) {
            symbolColor = '#f59e0b' // Yellow for schema/group
            labelColor = '#b45309'
        } else if (depth === 2) {
            symbolColor = '#22c55e' // Green for point/table
            labelColor = '#15803d'
        }

        const item = {
            name: node.name,
            value: node.type,
            children: [],
            itemStyle: {
                color: symbolColor,
                borderColor: '#fff',
                borderWidth: 2
            },
            label: {
                color: '#374151',
                backgroundColor: '#fff',
                padding: [2, 4],
                borderRadius: 2
            }
        }

        // Always expand to level 2 (Measurements) as per screenshot
        if (node.children) {
            item.children = node.children.map(child => transformData(child, depth + 1))
        }

        return item
    }

    const data = transformData(root)

    const option = {
        tooltip: { trigger: 'item', triggerOn: 'mousemove' },
        series: [
            {
                type: 'tree',
                data: [data],
                top: '5%',
                left: '10%',
                bottom: '10%',
                right: '20%',
                symbolSize: 16,
                symbol: 'circle', // Clean circles as per screenshot
                
                layout: 'orthogonal',
                orient: 'LR',
                
                expandAndCollapse: true,
                initialTreeDepth: -1, // Expand All
                roam: true,
                
                edgeShape: 'curve', // Bezier curves as per screenshot
                
                label: {
                    position: 'top',
                    verticalAlign: 'middle',
                    align: 'center',
                    fontSize: 12,
                    distance: 10
                },
                
                leaves: {
                    label: {
                        position: 'right',
                        verticalAlign: 'middle',
                        align: 'left'
                    }
                },
                
                lineStyle: {
                    color: '#cbd5e1',
                    width: 1.5,
                    curveness: 0.5
                }
            }
        ]
    }
    
    chartInstance.setOption(option)
}

watch(() => dataStore.showTopologyDrawer, (val) => {
    if (val) {
        nextTick(() => {
            initChart()
        })
    }
})

window.addEventListener('resize', () => chartInstance && chartInstance.resize())
</script>

<template>
    <div v-if="dataStore.showTopologyDrawer" class="fixed inset-0 z-50 flex justify-end bg-black/20 backdrop-blur-sm" @click.self="dataStore.showTopologyDrawer = false">
        <div class="w-[600px] h-full bg-white shadow-2xl flex flex-col animate-slide-in-right">
            <!-- Header -->
            <div class="h-14 border-b border-gray-100 flex items-center justify-between px-6 bg-white">
                <div>
                    <h3 class="font-bold text-gray-800 flex items-center">
                        <i class="ri-node-tree text-blue-500 mr-2"></i> 资源拓扑预览
                    </h3>
                    <div class="text-[10px] text-gray-400 mt-0.5 font-mono">数据源: {{ dataStore.topologyRootNode?.id }}</div>
                </div>
                <button @click="dataStore.showTopologyDrawer = false" class="text-gray-400 hover:text-gray-800 transition-colors">
                    <i class="ri-close-line text-2xl"></i>
                </button>
            </div>

            <!-- Chart -->
            <div class="flex-1 bg-gray-50/50 relative">
                <div ref="chartRef" class="w-full h-full"></div>
            </div>

            <!-- Legend / Footer -->
            <div class="h-16 border-t border-gray-100 px-6 flex items-center justify-between bg-white">
                <div class="flex space-x-4 text-xs">
                    <div class="flex items-center"><span class="w-2 h-2 rounded-full bg-blue-500 mr-2"></span><span class="text-gray-600">数据源 (Root)</span></div>
                    <div class="flex items-center"><span class="w-2 h-2 rounded-full bg-yellow-500 mr-2"></span><span class="text-gray-600">组/Schema</span></div>
                    <div class="flex items-center"><span class="w-2 h-2 rounded-full bg-green-500 mr-2"></span><span class="text-gray-600">测点/表</span></div>
                </div>
                <button @click="dataStore.showTopologyDrawer = false; dataStore.selectNode(dataStore.topologyRootNode.type, dataStore.topologyRootNode.id)" class="px-3 py-1.5 bg-blue-50 text-blue-600 text-xs font-bold rounded hover:bg-blue-100 transition-colors border border-blue-200">
                    进入详细管理 <i class="ri-arrow-right-line ml-1"></i>
                </button>
            </div>
        </div>
    </div>
</template>

<style scoped>
.animate-slide-in-right {
    animation: slideInRight 0.3s ease-out forwards;
}

@keyframes slideInRight {
    from { transform: translateX(100%); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
}
</style>