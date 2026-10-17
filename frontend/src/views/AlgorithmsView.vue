<script setup lang="ts">
import { computed, ref } from 'vue'
import StatePanel from '@/components/shared/StatePanel.vue'
import { useCatalogStore } from '@/stores/catalog'

const catalog = useCatalogStore()
const tab = ref<'search' | 'decoder' | 'history'>('search')
const historyQuery = ref('')
const activeFamily = ref('全部')
const showCompatibility = ref(false)

const families = computed(() => ['全部', ...catalog.algorithmFamilies.map(item => item.name)])
const configurationCount = computed(() => catalog.algorithmFamilies.reduce((sum, family) => sum + family.configurations.length, 0))
const compatibilityCount = computed(() => catalog.algorithmFamilies.reduce((sum, family) => sum + family.configurations.filter(item => item.compatibilityOnly).length, 0))
const visibleFamilies = computed(() => {
  const query = historyQuery.value.trim().toLocaleLowerCase()
  return catalog.algorithmFamilies.filter(family => activeFamily.value === '全部' || family.name === activeFamily.value)
    .map(family => ({ ...family, configurations: family.configurations.filter(item =>
      (showCompatibility.value || !item.compatibilityOnly)
      && (!query || `${family.name} ${item.id} ${item.displayName} ${item.settings}`.toLocaleLowerCase().includes(query))) }))
    .filter(family => family.configurations.length > 0)
})
</script>

<template>
  <div class="archive-page">
    <header class="archive-hero">
      <div><p class="section-kicker">算法与策略</p><h2>按算法家族选择，按策略配置比较。</h2></div>
      <p>搜索器负责优化排列，解码器负责生成布局。评分规则、禁忌记忆和碎片回收属于家族配置；完整遍历会保留每种配置的独立结果。</p>
    </header>
    <StatePanel v-if="catalog.error" tone="error" title="算法目录加载失败" :description="catalog.error" action-label="重试" @action="catalog.load(true)" />
    <div class="archive-tabs">
      <button :class="{ active: tab === 'search' }" @click="tab = 'search'">搜索优化器 <span>{{ catalog.searchAlgorithms.length }}</span></button>
      <button :class="{ active: tab === 'decoder' }" @click="tab = 'decoder'">排样解码器 <span>{{ catalog.decoders.length }}</span></button>
      <button :class="{ active: tab === 'history' }" @click="tab = 'history'">家族与策略配置 <span>{{ catalog.algorithmFamilies.length }}</span></button>
    </div>
    <div v-if="tab === 'search'" class="algorithm-grid">
      <article v-for="(item, index) in catalog.searchAlgorithms" :key="item.machineValue" class="algorithm-entry">
        <header><span>{{ String(index + 1).padStart(2, '0') }}</span><i :class="{ stochastic: item.stochastic }">{{ item.stochastic ? '随机' : '确定' }}</i></header>
        <h3>{{ item.chineseName }}</h3><small>{{ item.englishName }}</small><p>{{ item.description }}</p>
        <details class="algorithm-guide"><summary><span>详细解析</span><i>展开</i></summary><p>{{ item.guide }}</p></details>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?search=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <div v-else-if="tab === 'decoder'" class="algorithm-grid decoder-grid">
      <article v-for="(item, index) in catalog.decoders" :key="item.machineValue" class="algorithm-entry">
        <header><span>{{ String(index + 1).padStart(2, '0') }}</span><i>支持旋转</i></header>
        <h3>{{ item.chineseName }}</h3><small>{{ item.englishName }}</small><p>{{ item.description }}</p>
        <div class="structure-line"><span>空间结构</span><strong>{{ item.spaceStructure }}</strong></div>
        <details class="algorithm-guide"><summary><span>详细解析</span><i>展开</i></summary><p>{{ item.guide }}</p></details>
        <footer><code>{{ item.machineValue }}</code><RouterLink :to="`/solve?decoder=${item.machineValue}`">用于求解 →</RouterLink></footer>
      </article>
    </div>
    <section v-else class="history-archive">
      <header class="history-summary">
        <div><span>ALGORITHM FAMILIES</span><strong>算法家族与配置目录</strong><p>同一家族共享核心实现，配置数不代表独立算法数。兼容配置供旧实验复现与专项对比使用。</p></div>
        <dl><div><dt>家族</dt><dd>{{ catalog.algorithmFamilies.length }}</dd></div><div><dt>配置入口</dt><dd>{{ configurationCount }}</dd></div><div><dt>兼容配置</dt><dd>{{ compatibilityCount }}</dd></div></dl>
      </header>
      <div class="history-toolbar">
        <label><span>搜索档案</span><input v-model="historyQuery" type="search" placeholder="名称、标识、家族或算法思想" /></label>
        <label><input v-model="showCompatibility" type="checkbox" /> 显示兼容配置（{{ compatibilityCount }}）</label>
        <div class="family-filter" aria-label="算法家族筛选">
          <button v-for="family in families" :key="family" :class="{ active: activeFamily === family }" @click="activeFamily = family">{{ family }}</button>
        </div>
      </div>
      <p class="history-result-count">显示 {{ visibleFamilies.length }} 个家族 · 展开查看策略配置；兼容配置默认收起</p>
      <div v-if="visibleFamilies.length" class="family-catalog">
        <article v-for="family in visibleFamilies" :key="family.name" class="analysis-card">
          <details :open="Boolean(historyQuery.trim()) || activeFamily !== '全部'">
            <summary>{{ family.name }} · {{ family.configurations.length }} 个配置</summary>
            <div class="table-scroll"><table class="rank-table">
              <thead><tr><th>策略配置</th><th>配置项与成本</th><th>用途</th></tr></thead>
              <tbody><tr v-for="item in family.configurations" :key="item.id">
                <td><strong>{{ item.displayName }}</strong><br /><code>{{ item.id }}</code></td>
                <td>{{ item.settings }}</td>
                <td>{{ item.compatibilityOnly ? '兼容 / 消融' : item.recommended ? '默认代表' : '专项配置' }}</td>
              </tr></tbody>
            </table></div>
          </details>
        </article>
      </div>
      <StatePanel v-else title="没有匹配的策略配置" description="请更换关键词，或开启兼容配置。" />
    </section>
  </div>
</template>
