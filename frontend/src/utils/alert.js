/**
 * 显示提示消息
 * 
 * 在页面右上角显示Bootstrap风格的提示框，3秒后自动消失
 * 注意：此函数为备用方案，推荐使用Element Plus的ElMessage组件
 * 
 * @param {string} message - 提示消息内容
 * @param {string} type - 消息类型：success/danger/warning/info
 */
export const showAlert = (message, type = 'info') => {
  // 根据类型映射Bootstrap样式类
  const typeClassMap = {
    success: 'alert-success',
    danger: 'alert-danger',
    warning: 'alert-warning',
    info: 'alert-info'
  }
  const alertClass = typeClassMap[type] || 'alert-info'
  
  // 创建提示框DOM元素
  const notification = document.createElement('div')
  notification.className = `alert ${alertClass} alert-dismissible fade show position-fixed`
  notification.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;'
  notification.innerHTML = `
    ${message}
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  `
  
  document.body.appendChild(notification)
  
  // 3秒后自动移除
  setTimeout(() => {
    notification.remove()
  }, 3000)
}

