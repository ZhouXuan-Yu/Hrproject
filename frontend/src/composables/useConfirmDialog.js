import { reactive } from 'vue';

export function useConfirmDialog() {
  let resolver = null;
  const confirmDialog = reactive({
    visible: false,
    title: '确认操作',
    message: '',
    detail: '',
    type: 'info',
    confirmText: '确定',
    cancelText: '取消',
    busyText: '处理中...',
    busy: false,
    showCancel: true,
  });

  function askConfirm(options = {}) {
    Object.assign(confirmDialog, {
      visible: true,
      title: options.title || '确认操作',
      message: options.message || '',
      detail: options.detail || '',
      type: options.type || 'info',
      confirmText: options.confirmText || '确定',
      cancelText: options.cancelText || '取消',
      busyText: options.busyText || '处理中...',
      busy: false,
      showCancel: options.showCancel !== false,
    });
    return new Promise(resolve => {
      resolver = resolve;
    });
  }

  function settle(value) {
    confirmDialog.visible = false;
    confirmDialog.busy = false;
    if (resolver) resolver(value);
    resolver = null;
  }

  function onConfirmDialogConfirm() {
    settle(true);
  }

  function onConfirmDialogCancel() {
    settle(false);
  }

  return {
    confirmDialog,
    askConfirm,
    onConfirmDialogConfirm,
    onConfirmDialogCancel,
  };
}
