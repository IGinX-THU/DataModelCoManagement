import { defineStore } from 'pinia'
import localforage from 'localforage'

export const useAppStore = defineStore('app', {
  state: () => ({
    isPanelOpen: true,
    lastActiveRoute: '/dashboard',
    theme: 'dark'
  }),
  actions: {
    async init() {
      try {
        const savedState = await localforage.getItem('app-state')
        if (savedState) {
          this.$patch(savedState)
        }
      } catch (e) {
        console.error('Failed to load state', e)
      }
    },
    togglePanel() {
      this.isPanelOpen = !this.isPanelOpen
      this.saveState()
    },
    setRoute(route) {
      this.lastActiveRoute = route
      this.saveState()
    },
    async saveState() {
      try {
        await localforage.setItem('app-state', {
            isPanelOpen: this.isPanelOpen,
            lastActiveRoute: this.lastActiveRoute,
            theme: this.theme
        })
      } catch (e) {
        console.error('Failed to save state', e)
      }
    }
  }
})
