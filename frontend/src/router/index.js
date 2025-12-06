import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'root',
    component: () => import('../views/VerMgt.vue')
  },
  {
    path: '/deploy/',
    name: 'VerMgt',
    component: () => import('../views/VerMgt.vue')
  },
  {
    path: '/deploy/appMgt',
    name: 'AppMgt',
    component: () => import('../views/AppMgt.vue')
  },
  {
    path: '/deploy/logMgt',
    name: 'LogMgt',
    component: () => import('../views/LogMgt.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

