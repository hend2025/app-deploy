<!--
  日志模态框组件
  功能：
  - 全屏显示应用日志
  - WebSocket 实时推送日志
  - 支持断线自动重连
  - 支持 Ctrl+A 全选日志
-->
<template>
  <el-dialog
    v-model="visible"
    fullscreen
    :close-on-click-modal="false"
    :show-close="false"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">日志详情 - {{ currentAppCode }}</span>
        <div class="dialog-actions">
          <el-tag :type="wsConnected ? 'success' : 'danger'" size="small" style="margin-right: 8px;">
            {{ wsConnected ? '已连接' : (reconnecting ? '重连中...' : '未连接') }}
          </el-tag>
          <el-button :type="paused ? 'success' : 'danger'" size="small" @click="togglePause">
            {{ paused ? '继续' : '暂停' }}
          </el-button>
          <el-button type="info" size="small" @click="scrollToBottom">到底部</el-button>
          <el-button type="warning" size="small" @click="clearLogContent">清空</el-button>
          <el-button size="small" @click="closeDialog">关闭</el-button>
        </div>
      </div>
    </template>
    <div class="log-container">
      <el-input
        ref="logContent"
        v-model="logText"
        type="textarea"
        class="log-textarea"
        readonly
        resize="none"
      />
    </div>
  </el-dialog>
</template>

<script>
/**
 * 日志模态框组件
 * 提供实时日志查看功能，支持 WebSocket 推送和断线重连
 */
import { ref, onUnmounted, onMounted, nextTick, computed } from 'vue'
import { logApi } from '../api'

export default {
  name: 'LogModal',
  setup() {
    const visible = ref(false)
    const currentAppCode = ref('')
    const logs = ref([])
    const logContent = ref(null)
    const wsConnected = ref(false)
    const reconnecting = ref(false)
    const paused = ref(false)              // 是否暂停日志刷新
    
    let ws = null
    let pendingLogs = []                   // 暂停时缓存的日志
    let maxLogSize = 5000 // 默认值，会从后端获取
    let reconnectTimer = null
    let reconnectAttempts = 0
    const MAX_RECONNECT_ATTEMPTS = 5
    const RECONNECT_INTERVAL = 3000

    // 组件挂载时从后端获取配置
    onMounted(async () => {
      try {
        const config = await logApi.getConfig()
        if (config && config.cacheSize) {
          maxLogSize = config.cacheSize
        }
      } catch (e) {
        console.warn('获取日志配置失败，使用默认值:', e)
      }
    })

    /**
     * HTML实体解码，将 &gt; &lt; 等转换为实际字符
     * 后端返回的日志可能已经被转义，需要解码显示
     */
    const decodeHtml = (text) => {
      if (!text) return ''
      const div = document.createElement('div')
      div.innerHTML = text
      return div.textContent || div.innerText || ''
    }

    const logText = computed(() => {
      return logs.value.map(log => {
        // 解码内容，保留基本的日志格式
        return decodeHtml(log.logContent)
      }).join('\n')
    })

    const getWsUrl = (appCode) => {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      return `${protocol}//${host}/deploy/ws/logs?appCode=${appCode}`
    }

    const connectWebSocket = (appCode) => {
      if (ws) {
        ws.close()
        ws = null
      }
      
      const url = getWsUrl(appCode)
      ws = new WebSocket(url)
      
      ws.onopen = () => {
        wsConnected.value = true
        reconnecting.value = false
        reconnectAttempts = 0
      }
      
      ws.onmessage = (event) => {
        try {
          const log = JSON.parse(event.data)
          if (paused.value) {
            // 暂停时缓存日志
            pendingLogs.push(log)
            // 限制缓存大小
            while (pendingLogs.length > maxLogSize) {
              pendingLogs.shift()
            }
          } else {
            logs.value.push(log)
            // 超过最大条数时删除最旧的日志
            while (logs.value.length > maxLogSize) {
              logs.value.shift()
            }
            nextTick(() => scrollToBottom())
          }
        } catch (e) {
          console.error('解析日志失败:', e)
        }
      }
      
      ws.onclose = (event) => {
        wsConnected.value = false
        // 非正常关闭且对话框仍打开时，尝试重连
        if (visible.value && event.code !== 1000 && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
          scheduleReconnect(appCode)
        }
      }
      
      ws.onerror = () => {
        wsConnected.value = false
      }
    }

    /**
     * 安排重连
     */
    const scheduleReconnect = (appCode) => {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
      }
      
      reconnecting.value = true
      reconnectAttempts++
      console.log(`WebSocket断开，${RECONNECT_INTERVAL/1000}秒后尝试第${reconnectAttempts}次重连...`)
      
      reconnectTimer = setTimeout(() => {
        if (visible.value) {
          connectWebSocket(appCode)
        }
      }, RECONNECT_INTERVAL)
    }

    const closeWebSocket = () => {
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      reconnectAttempts = 0
      reconnecting.value = false
      
      if (ws) {
        ws.close(1000, 'User closed')
        ws = null
      }
      wsConnected.value = false
    }

    const fetchHistoryLogs = async () => {
      try {
        const response = await logApi.incremental(currentAppCode.value, 0, 5000)
        const historyLogs = response.logs || []
        if (historyLogs.length > 0) {
          logs.value = historyLogs
          await nextTick()
          scrollToBottom()
        }
      } catch (error) {
        console.error('加载历史日志失败:', error)
      }
    }

    const showLog = async (appCode) => {
      closeWebSocket()
      currentAppCode.value = appCode
      logs.value = []
      visible.value = true
      await fetchHistoryLogs()
      connectWebSocket(appCode)
    }

    const scrollToBottom = () => {
      if (logContent.value && logContent.value.textarea) {
        const textarea = logContent.value.textarea
        textarea.scrollTop = textarea.scrollHeight
      } else if (logContent.value && logContent.value.$el) {
        // Fallback if ref is the component itself
        const textarea = logContent.value.$el.querySelector('textarea')
        if (textarea) {
             textarea.scrollTop = textarea.scrollHeight
        }
      }
    }

    const clearLogContent = () => { 
      logs.value = []
      pendingLogs = []
    }

    /**
     * 切换暂停/继续状态
     */
    const togglePause = () => {
      paused.value = !paused.value
      if (!paused.value && pendingLogs.length > 0) {
        // 恢复时将缓存的日志追加到显示列表
        logs.value.push(...pendingLogs)
        pendingLogs = []
        // 超过最大条数时删除最旧的日志
        while (logs.value.length > maxLogSize) {
          logs.value.shift()
        }
        nextTick(() => scrollToBottom())
      }
    }

    const handleClose = () => { closeWebSocket(); logs.value = [] }

    const closeDialog = () => { 
      closeWebSocket()
      logs.value = []
      pendingLogs = []
      paused.value = false
      visible.value = false
    }

    // Removed selectAllLogs as textarea natively supports Ctrl+A

    onUnmounted(() => { closeWebSocket() })

    return {
      visible, currentAppCode, logs, logContent, wsConnected, reconnecting, paused, logText,
      showLog, scrollToBottom, clearLogContent, handleClose, closeDialog, decodeHtml, togglePause
    }
  }
}
</script>

<style scoped>
.log-container {
  height: calc(100vh - 80px);
  padding: 0;
  overflow: hidden; /* Textarea handles scroll */
}

.log-textarea {
  height: 100%;
  width: 100%;
}

:deep(.el-textarea__inner) {
  height: 100%;
  border: none;
  border-radius: 0;
  resize: none;
  background-color: #fff;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  padding: 10px;
  color: #333;
}

.dialog-header { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.dialog-title { font-size: 18px; font-weight: 600; }
.dialog-actions { display: flex; gap: 8px; align-items: center; }
.dialog-actions .el-button { height: 32px; padding: 0 16px; }
.empty-tip { text-align: center; color: #909399; padding: 50px; }
</style>
