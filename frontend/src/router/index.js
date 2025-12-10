import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/deploy',
    name: 'root',
    component: () => import('../views/AppBuild.vue')
  },
  {
    path: '/deploy/appBuild',
    name: 'AppBuild',
    component: () => import('../views/AppBuild.vue')
  },
  {
    path: '/deploy/appDeploy',
    name: 'AppDeploy',
    component: () => import('../views/AppDeploy.vue')
  },
  {
    path: '/deploy/logFiles',
    name: 'LogFiles',
    component: () => import('../views/LogFiles.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router

