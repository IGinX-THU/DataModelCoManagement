import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('../views/DashboardView.vue')
        },
        {
          path: 'data',
          name: 'data',
          component: () => import('../views/DataEditorView.vue')
        },
        {
          path: 'models',
          name: 'models',
          component: () => import('../views/ModelAssetsView.vue')
        },
        {
          path: 'relations',
          name: 'relations',
          component: () => import('../views/AssociationView.vue')
        },
        {
          path: 'tasks',
          name: 'tasks',
          component: () => import('../views/TaskMonitorView.vue')
        },
        {
          path: 'task-chains',
          name: 'task-chains',
          component: () => import('../views/TaskChainView.vue')
        },
        {
          path: 'analysis',
          name: 'analysis',
          component: () => import('../views/AnalysisView.vue')
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('../views/SettingsView.vue')
        }
      ]
    }
  ]
})

export default router
