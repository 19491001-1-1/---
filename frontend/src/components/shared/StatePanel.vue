<script setup lang="ts">
defineProps<{
  title: string
  description: string
  tone?: 'neutral' | 'error'
  actionLabel?: string
}>()

defineEmits<{ action: [] }>()
</script>

<template>
  <div :class="['state-panel', `state-${tone ?? 'neutral'}`]" role="status">
    <div class="state-symbol" aria-hidden="true">{{ tone === 'error' ? '!' : '···' }}</div>
    <div>
      <strong>{{ title }}</strong>
      <p>{{ description }}</p>
    </div>
    <button v-if="actionLabel" class="text-button" type="button" @click="$emit('action')">{{ actionLabel }}</button>
  </div>
</template>
