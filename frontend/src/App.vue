<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useCatalogStore } from '@/stores/catalog'

const route = useRoute()
const catalog = useCatalogStore()

const navigation = [
  { path: '/', label: '概览', short: '概', mobile: '概览' },
  { path: '/solve', label: '单次求解', short: '解', mobile: '求解' },
  { path: '/experiments', label: '对比实验', short: '验', mobile: '实验' },
  { path: '/records', label: '实验记录', short: '录', mobile: '记录' },
  { path: '/analysis', label: '结果分析', short: '析', mobile: '分析' },
  { path: '/algorithms', label: '算法档案', short: '算', mobile: '算法' },
  { path: '/about', label: '项目说明', short: '说', mobile: '说明' },
]

const currentTitle = computed(() => navigation.find((item) => item.path === route.path)?.label ?? '排样平台')

onMounted(() => catalog.load())
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="sidebar" aria-label="主导航">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"><span></span><span></span><span></span></div>
        <div class="brand-copy">
          <strong>二维排样</strong>
          <small>Experiment Lab</small>
        </div>
      </div>
      <nav class="nav-list">
        <RouterLink v-for="item in navigation" :key="item.path" :to="item.path" class="nav-item" :aria-label="item.label">
          <span class="nav-glyph" aria-hidden="true">{{ item.short }}</span>
          <span class="nav-label">{{ item.label }}</span>
          <span class="mobile-nav-label" aria-hidden="true">{{ item.mobile }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-foot">
        <span :class="['connection-dot', { online: !catalog.error && !catalog.loading }]" aria-hidden="true"></span>
        <div>
          <strong>{{ catalog.loading ? '正在连接' : catalog.error ? '后端未连接' : '服务已就绪' }}</strong>
          <small>v{{ catalog.meta?.version ?? '—' }}</small>
        </div>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ currentTitle }}</h1>
        </div>
        <div class="topbar-meta">
          <span class="service-state"><i :class="{ online: !catalog.error && !catalog.loading }"></i>{{ catalog.loading ? '连接中' : catalog.error ? '服务异常' : '服务正常' }}</span>
          <span class="date-chip">Java {{ catalog.meta?.javaVersion ?? '17+' }}</span>
        </div>
      </header>
      <main id="main-content" class="page-stage" tabindex="-1">
        <RouterView />
      </main>
    </section>
  </div>
</template>
