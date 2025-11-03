export const showAlert = (message, type = 'info') => {
  const alertClass = type === 'success' ? 'alert-success' : 
                    type === 'danger' ? 'alert-danger' : 
                    type === 'warning' ? 'alert-warning' : 'alert-info'
  
  const notification = document.createElement('div')
  notification.className = `alert ${alertClass} alert-dismissible fade show position-fixed`
  notification.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;'
  notification.innerHTML = `
    ${message}
    <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
  `
  
  document.body.appendChild(notification)
  
  // 3秒后自动消失
  setTimeout(() => {
    notification.remove()
  }, 3000)
}

