/**
 * 显示提示消息（备用方案）
 * 
 * 在页面右上角显示提示框，3秒后自动消失
 * 注意：项目已使用Element Plus的ElMessage组件，此函数仅作为备用
 * 
 * @param {string} message - 提示消息内容
 * @param {string} type - 消息类型：success/danger/warning/info
 * @deprecated 推荐使用 Element Plus 的 ElMessage 组件
 */
export const showAlert = (message, type = 'info') => {
  // 根据类型映射样式
  const typeColorMap = {
    success: '#67c23a',
    danger: '#f56c6c',
    warning: '#e6a23c',
    info: '#909399'
  }
  const bgColor = typeColorMap[type] || typeColorMap.info
  
  // 创建提示框DOM元素
  const notification = document.createElement('div')
  notification.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    z-index: 9999;
    min-width: 300px;
    padding: 12px 20px;
    background-color: ${bgColor};
    color: white;
    border-radius: 4px;
    box-shadow: 0 2px 12px rgba(0,0,0,0.15);
    font-size: 14px;
  `
  notification.textContent = message
  
  document.body.appendChild(notification)
  
  // 3秒后自动移除
  setTimeout(() => {
    notification.remove()
  }, 3000)
}
