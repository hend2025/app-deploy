<template>
  <el-dialog
    v-model="visible"
    :title="'日志详情 - ' + currentLogFile"
    fullscreen
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div 
      ref="logContent"
      class="log-content"
    >
      {{ logContentText }}
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
        <el-button type="primary" size="small" @click="downloadLog">下载</el-button>
        <el-button size="small" @click="visible = false">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script>
import { ref, onUnmounted, nextTick } from 'vue'
import { logFileApi } from '../api'
import { ElMessage } from 'element-plus'

export default {
  name: 'LogModal',
  setup() {
    const visible = ref(false)
    const currentLogFile = ref('')
    const logContentText = ref('加载中...')
    const logLineCount = ref(0)
    const autoRefreshEnabled = ref(false)
    const logContent = ref(null)
    
    let autoRefreshInterval = null

    const showLog = (fileName) => {
      currentLogFile.value = fileName
      logLineCount.value = 0
      logContentText.value = '加载中...'
      visible.value = true
      refreshLogContent()
      
      setTimeout(() => {
        if (!autoRefreshEnabled.value) {
          toggleAutoRefresh()
        }
      }, 3000)
    }

    const limitLogLines = (content, maxLines) => {
      const estimatedLines = content.length / 100
      if (estimatedLines <= maxLines * 0.8) return content
      const lines = content.split('\n')
      if (lines.length > maxLines) return lines.slice(-maxLines).join('\n')
      return content
    }

    const refreshLogContent = async () => {
      try {
        if (logLineCount.value === 0) {
          const response = await logFileApi.readFileLastLines(currentLogFile.value, 2000)
          logContentText.value = response.content
          logLineCount.value = response.totalLines
          if (response.warning) ElMessage.warning(response.warning)
          await nextTick()
          scrollToBottom()
        } else {
          const response = await logFileApi.readFileIncremental(currentLogFile.value, logLineCount.value)
          if (response.fileRotated) {
            const reloadResponse = await logFileApi.readFileLastLines(currentLogFile.value, 2000)
            logContentText.value += reloadResponse.content
            logContentText.value = limitLogLines(logContentText.value, 3000)
            logLineCount.value = reloadResponse.totalLines
            await nextTick()
            scrollToBottom()
          } else if (response.hasNewContent) {
            logContentText.value += response.content
            logContentText.value = limitLogLines(logContentText.value, 2000)
            logLineCount.value = response.totalLines
            await nextTick()
            scrollToBottom()
          }
        }
      } catch (error) {
        if (logLineCount.value === 0) {
          logContentText.value = '加载日志失败: ' + error.message
        }
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
        nextTick(() => {
          logContent.value.scrollTop = logContent.value.scrollHeight
        })
      }
    }

    const clearLogContent = () => {
      logContentText.value = ''
    }

    const downloadLog = () => {
      if (currentLogFile.value) {
        window.open(logFileApi.downloadFile(currentLogFile.value), '_blank')
      }
    }

    const handleClose = () => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
        autoRefreshInterval = null
      }
      autoRefreshEnabled.value = false
      logContentText.value = ''
      logLineCount.value = 0
    }

    onUnmounted(() => {
      if (autoRefreshInterval) clearInterval(autoRefreshInterval)
    })

    return {
      visible,
      currentLogFile,
      logContentText,
      autoRefreshEnabled,
      logContent,
      showLog,
      toggleAutoRefresh,
      scrollToBottom,
      clearLogContent,
      downloadLog,
      handleClose
    }
  }
}
</script>

<style scoped>
.log-content {
  height: calc(100vh - 180px);
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #f5f7fa;
  padding: 15px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-family: monospace;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-size: 14px;
  line-height: 1.4;
}
.dialog-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
