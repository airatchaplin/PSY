import { createRouter, createWebHistory } from 'vue-router'
import HomeView from './views/HomeView.vue'
import ClientsView from './views/ClientsView.vue'
import ChatView from './views/ChatView.vue'
import ProgressView from './views/ProgressView.vue'
import CasesView from './views/CasesView.vue'
import SharedCaseView from './views/SharedCaseView.vue'
import SettingsView from './views/SettingsView.vue'
import AdminView from './views/AdminView.vue'
import { authenticated, hasAdminRole, isAdmin } from './auth/session'
import { stashPendingShareToken } from './casesUi'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView, meta: { page: 0, bookmark: 'home' } },
    { path: '/clients', name: 'clients', component: ClientsView, meta: { page: 1, bookmark: 'clients' } },
    { path: '/clients/:id', name: 'chat', component: ChatView, props: true, meta: { page: 1, bookmark: 'clients' } },
    {
      path: '/cases/shared/:token',
      name: 'shared-case',
      component: SharedCaseView,
      meta: { page: 2, bookmark: 'cases' }
    },
    { path: '/cases', name: 'cases', component: CasesView, meta: { page: 2, bookmark: 'cases' } },
   
    { path: '/progress', name: 'progress', component: ProgressView, meta: { page: 3, bookmark: 'progress' } },
    { path: '/settings', name: 'settings', component: SettingsView, meta: { page: 4, bookmark: 'settings' } },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      meta: { page: 5, bookmark: 'admin', requiresAdmin: true }
    }
  ]
})

router.beforeEach((to) => {
  if (to.name === 'shared-case') {
    const raw = to.params.token
    const token = typeof raw === 'string' ? raw.trim() : ''
    if (token && !authenticated.value) {
      stashPendingShareToken(token)
    }
  }
  if (!to.meta.requiresAdmin) return true
  if (!authenticated.value || !(isAdmin.value || hasAdminRole())) {
    return { name: 'home' }
  }
  return true
})
