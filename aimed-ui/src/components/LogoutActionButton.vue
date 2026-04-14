<template>
  <button
    class="logout-action-button"
    type="button"
    :disabled="disabled"
    @click="$emit('click')"
  >
    <span class="logout-action-icon">
      <svg viewBox="0 0 24 24" aria-hidden="true" class="logout-action-icon-svg">
        <path d="M10 3h2v8h-2V3Zm1 19a9 9 0 1 1 6.4-15.4l-1.4 1.4A7 7 0 1 0 18 13h2a9 9 0 0 1-9 9Z" />
      </svg>
    </span>
    <span>{{ label }}</span>
  </button>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    default: '退出登录',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['click'])
</script>

<style scoped>
.logout-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(46, 136, 140, 0.18);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(236, 247, 246, 0.92) 100%);
  box-shadow: 0 14px 28px rgba(33, 82, 89, 0.08);
  color: #1c5c64;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, opacity 0.2s ease;
}

.logout-action-button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.28);
  box-shadow: 0 18px 30px rgba(33, 82, 89, 0.12);
}

.logout-action-button:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.logout-action-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: rgba(87, 173, 163, 0.14);
  color: #237179;
  font-size: 14px;
}

.logout-action-icon-svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
}
</style>
