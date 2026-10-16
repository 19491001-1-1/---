import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('@/views/OverviewView.vue'), meta: { title: '概览' } },
    { path: '/solve', component: () => import('@/views/SolveView.vue'), meta: { title: '单次求解' } },
    { path: '/experiments', component: () => import('@/views/ExperimentsView.vue'), meta: { title: '对比实验' } },
    { path: '/records', component: () => import('@/views/RecordsView.vue'), meta: { title: '实验记录' } },
    { path: '/analysis', component: () => import('@/views/AnalysisView.vue'), meta: { title: '结果分析' } },
    { path: '/algorithms', component: () => import('@/views/AlgorithmsView.vue'), meta: { title: '算法档案' } },
    { path: '/about', component: () => import('@/views/AboutView.vue'), meta: { title: '项目说明' } },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFoundView.vue'), meta: { title: '页面未找到' } },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '排样平台')} · 二维排样`
})

export default router
