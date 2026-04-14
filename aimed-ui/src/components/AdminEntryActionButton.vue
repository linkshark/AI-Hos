<template>
  <button
    class="admin-entry-button"
    type="button"
    :disabled="disabled"
    @click="$emit('click')"
  >
    <span class="admin-entry-icon">
      <svg viewBox="0 0 24 24" aria-hidden="true" class="admin-entry-icon-svg">
        <path d="M11 2h2l.5 2.3a7.8 7.8 0 0 1 1.7.7l2-1.2 1.4 1.4-1.2 2a7.8 7.8 0 0 1 .7 1.7L22 11v2l-2.3.5a7.8 7.8 0 0 1-.7 1.7l1.2 2-1.4 1.4-2-1.2a7.8 7.8 0 0 1-1.7.7L13 22h-2l-.5-2.3a7.8 7.8 0 0 1-1.7-.7l-2 1.2-1.4-1.4 1.2-2a7.8 7.8 0 0 1-.7-1.7L2 13v-2l2.3-.5a7.8 7.8 0 0 1 .7-1.7l-1.2-2 1.4-1.4 2 1.2a7.8 7.8 0 0 1 1.7-.7L11 2Zm1 6.2A3.8 3.8 0 1 0 12 16a3.8 3.8 0 0 0 0-7.8Z" />
      </svg>
    </span>
    <span>{{ label }}</span>
  </button>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    default: '管理后台',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

defineEmits(['click'])
</script>

<style scoped>
.admin-entry-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(46, 136, 140, 0.2);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(243, 251, 251, 0.95) 0%, rgba(225, 243, 242, 0.94) 100%);
  box-shadow: 0 14px 28px rgba(33, 82, 89, 0.08);
  color: #1c5c64;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease, opacity 0.2s ease;
}

.admin-entry-button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(46, 136, 140, 0.3);
  box-shadow: 0 18px 30px rgba(33, 82, 89, 0.12);
}

.admin-entry-button:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.admin-entry-icon {
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

.admin-entry-icon-svg {
  width: 14px;
  height: 14px;
  fill: currentColor;
}
</style>
