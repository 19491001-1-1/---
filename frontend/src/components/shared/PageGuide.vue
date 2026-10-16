<script setup lang="ts">
export interface GuideSection { title:string; summary:string; body:string; points:string[] }
defineProps<{ label:string; title:string; introduction:string; steps:string[]; sections:GuideSection[] }>()
</script>

<template>
  <section class="page-guide">
    <header>
      <div><p>{{ label }}</p><h3>{{ title }}</h3><span>{{ introduction }}</span></div>
      <ol><li v-for="(step,index) in steps" :key="step"><b>{{ String(index+1).padStart(2,'0') }}</b>{{ step }}</li></ol>
    </header>
    <div class="page-guide-details">
      <details v-for="section in sections" :key="section.title">
        <summary><span>{{ section.title }}</span><small>{{ section.summary }}</small><i>＋</i></summary>
        <div><p>{{ section.body }}</p><ul><li v-for="point in section.points" :key="point">{{ point }}</li></ul></div>
      </details>
    </div>
  </section>
</template>
