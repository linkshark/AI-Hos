import { ElMessageBox } from 'element-plus'

export const confirmDanger = (message, title = '删除确认', options = {}) =>
  ElMessageBox.confirm(message, title, {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
    center: true,
    customClass: 'aimed-confirm-dialog',
    confirmButtonClass: 'aimed-confirm-dialog-confirm',
    cancelButtonClass: 'aimed-confirm-dialog-cancel',
    ...options,
  })
