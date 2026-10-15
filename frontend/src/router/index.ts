import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/OverviewView.vue') },
    { path: '/solve', component: () => import('@/views/SolveView.vue') },
    { path: '/experiments', component: () => import('@/views/ExperimentsView.vue') },
    { path: '/records', component: () => import('@/views/RecordsView.vue') },
    { path: '/analysis', component: () => import('@/views/AnalysisView.vue') },
    { path: '/algorithms', component: () => import('@/views/AlgorithmsView.vue') },
    { path: '/about', component: () => import('@/views/AboutView.vue') },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

export default router
