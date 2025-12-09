<template>
  <el-dialog
    v-model="visible"
    :title="'日志详情 - ' + currentAppCode"
    fullscreen
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div ref="logContent" class="log-content">
      <div v-for="(log, index) in logs" :key="index" class="log-line" :class="'log-' + (log.logLevel || 'info').toLowerCase()">
        <span class="log-time">{{ log.logTime }}</span>
        <span class="log-level">{{ log.logLevel }}</span>
        <span class="log-text">{{ log.logContent }}</span>
      </div>
      <div v-if="logs.length === 0" class="empty-tip">暂无日志</div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button 
          :type="autoRefreshEnabled ? 'warning' : 'success'"
          size="small"
          @click="toggleAutoRefresh"
        >
          {{ autoRefreshEnabled ? '停止刷新' : '自动刷新' }}
        </el-button>
        <el-button type="info" size="small" @click="scrollToBottom">到底部</el-button>
        <el-button type="warning" size="small" @click="clearLogContent">清空</el-button>
        <el-button size="small" @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import { ref, onUnmounted, nextTick } from 'vue'
import { logApi } from '../api'

export default {
  name: 'LogModal',
  setup() {
    const visible = ref(false)
    const currentAppCode = ref('')
    const logs = ref([])
    const autoRefreshEnabled = ref(false)
    const logContent = ref(null)
    
    let autoRefreshInterval = null

    const showLog = (appCode) => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
        autoRefreshInterval = null
      }
      autoRefreshEnabled.value = false
      
      currentAppCode.value = appCode
      logs.value = []
      visible.value = true
      refreshLogContent()
      
      setTimeout(() => {
        if (visible.value && !autoRefreshEnabled.value) {
          toggleAutoRefresh()
        }
      }, 2000)
    }

    const refreshLogContent = async () => {
      try {
        // 从缓冲区读取实时日志
        const response = await logApi.bufferLogs(currentAppCode.value, 1000)
        logs.value = response.logs || []
        await nextTick()
        scrollToBottom()
      } catch (error) {
        console.error('加载日志失败:', error)
      }
    }

    const toggleAutoRefresh = () => {
      if (autoRefreshEnabled.value) {
        if (autoRefreshInterval) {
          clearInterval(autoRefreshInterval)
          autoRefreshInterval = null
        }
        autoRefreshEnabled.value = false
      } else {
        refreshLogContent()
        autoRefreshInterval = setInterval(refreshLogContent, 3000)
        autoRefreshEnabled.value = true
      }
    }

    const scrollToBottom = () => {
      if (logContent.value) {
        nextTick(() => { logContent.value.scrollTop = logContent.value.scrollHeight })
      }
    }

    const clearLogContent = () => { logs.value = [] }

    const handleClose = () => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
        autoRefreshInterval = null
      }
      autoRefreshEnabled.value = false
      logs.value = []
    }

    onUnmounted(() => {
      if (autoRefreshInterval) clearInterval(autoRefreshInterval)
    })

    return {
      visible, currentAppCode, logs, autoRefreshEnabled, logContent,
      showLog, toggleAutoRefresh, scrollToBottom, clearLogContent, handleClose
    }
  }
}
</script>

<style scoped>
.log-content {
  height: calc(100vh - 180px);
  overflow-y: auto;
  background-color: #fff;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}
.log-line {
  padding: 4px 8px;
  border-radius: 3px;
  margin-bottom: 2px;
  display: flex;
  gap: 10px;
}
.log-time { color: #909399; white-space: nowrap; }
.log-level { font-weight: 600; width: 50px; }
.log-text { flex: 1; word-break: break-all; }
.log-debug { background: #f4f4f5; }
.log-debug .log-level { color: #909399; }
.log-info { background: #f0f9eb; }
.log-info .log-level { color: #67c23a; }
.log-warn { background: #fdf6ec; }
.log-warn .log-level { color: #e6a23c; }
.log-error { background: #fef0f0; }
.log-error .log-level { color: #f56c6c; }
.empty-tip { text-align: center; color: #909399; padding: 50px; }
.dialog-footer { display: flex; gap: 8px; justify-content: flex-end; }
</style>
