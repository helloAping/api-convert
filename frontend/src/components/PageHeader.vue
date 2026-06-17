<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { NIcon, NBreadcrumb, NBreadcrumbItem } from 'naive-ui'

defineProps<{
  /** 主标题 */
  title: string
  /** 副标题 / 描述，长文本会换行 */
  subtitle?: string
  /** 面包屑路径，末项为当前页面（不渲染链接） */
  breadcrumbs?: { label: string; to?: string }[]
  /** 标题左侧图标（可选） */
  icon?: Component
}>()

const iconRender = (icon: Component | undefined) => icon ? () => h(NIcon, { size: 22 }, { default: () => h(icon) }) : null
import { h } from 'vue'
</script>

<template>
  <header class="page-header">
    <div v-if="breadcrumbs && breadcrumbs.length > 0" class="page-header__crumbs">
      <n-breadcrumb separator="›">
        <n-breadcrumb-item v-for="(crumb, index) in breadcrumbs" :key="index"
                           :clickable="!!crumb.to && index < breadcrumbs.length - 1">
          <router-link v-if="crumb.to && index < breadcrumbs.length - 1" :to="crumb.to">{{ crumb.label }}</router-link>
          <span v-else>{{ crumb.label }}</span>
        </n-breadcrumb-item>
      </n-breadcrumb>
    </div>
    <div class="page-header__main">
      <div class="page-header__title-wrap">
        <span v-if="$slots.icon || icon" class="page-header__icon">
          <slot name="icon">
            <n-icon :component="iconRender(icon)" />
          </slot>
        </span>
        <div>
          <h2 class="page-header__title">{{ title }}</h2>
          <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="page-header__actions">
        <slot name="actions" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.page-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 16px;
  border-bottom: 1px solid var(--border-color-soft);
  margin-bottom: 20px;
}

.page-header__crumbs {
  font-size: 12px;
  color: var(--text-muted);
}

.page-header__main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.page-header__title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.page-header__icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--logo-bg);
  color: var(--logo-color);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.page-header__title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
  color: var(--text-primary);
}

.page-header__subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--text-secondary);
  max-width: 720px;
}

.page-header__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
</style>
