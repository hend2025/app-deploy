<template>
  <div class="modal fade" id="logModal" tabindex="-1" ref="modalElement">
    <div class="modal-dialog modal-fullscreen">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">日志详情 - {{ currentLogFile }}</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
        </div>
        <div class="modal-body" style="padding: 0;">
          <div 
            id="logContent" 
            ref="logContent"
            style="height: calc(100vh - 130px); overflow-y: auto; background-color: #f8f9fa; padding: 15px; border: 1px solid #dee2e6; font-family: monospace; white-space: pre-wrap; font-size: 14px; line-height: 1.4;"
          >
            {{ logContentText }}
          </div>
        </div>
        <div class="modal-footer">
          <div class="d-flex gap-2">
            <button 
              class="btn btn-sm" 
              :class="autoRefreshEnabled ? 'btn-warning' : 'btn-success'"
              @click="toggleAutoRefresh"
            >
              {{ autoRefreshEnabled ? '停止刷新' : '自动刷新' }}
            </button>
            <button class="btn btn-sm btn-info" @click="scrollToBottom">到底部</button>
            <button class="btn btn-sm btn-warning" @click="clearLogContent">清空</button>
            <button class="btn btn-sm btn-primary" @click="downloadLog">下载</button>
          </div>
          <button type="button" class="btn btn-sm btn-primary" data-bs-dismiss="modal">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onUnmounted, nextTick } from 'vue'
import { Modal } from 'bootstrap'
import { logFileApi } from '../api'
import { showAlert } from '../utils/alert'

export default {
  name: 'LogModal',
  setup() {
    const currentLogFile = ref('')
    const logContentText = ref('加载中...')
    const logLineCount = ref(0)
    const autoRefreshEnabled = ref(false)
    const modalElement = ref(null)
    const logContent = ref(null)
    
    let autoRefreshInterval = null
    let modalInstance = null

    // 显示日志
    const showLog = (fileName) => {
      currentLogFile.value = fileName
      logLineCount.value = 0
      logContentText.value = '加载中...'
      
      if (!modalInstance) {
        modalInstance = new Modal(modalElement.value)
      }
      modalInstance.show()
      
      // 加载日志内容
      refreshLogContent()
      
      // 3秒后自动开启自动刷新
      setTimeout(() => {
        if (!autoRefreshEnabled.value) {
          toggleAutoRefresh()
        }
      }, 3000)
    }

    // 限制日志行数（优化：避免频繁split）
    const limitLogLines = (content, maxLines) => {
      // 预估：如果内容长度合理，可能不需要限制
      const estimatedLines = content.length / 100 // 假设平均每行100字符
      if (estimatedLines <= maxLines * 0.8) {
        return content // 小于80%限制，直接返回
      }
      
      const lines = content.split('\n')
      if (lines.length > maxLines) {
        return lines.slice(-maxLines).join('\n')
      }
      return content
    }

    // 刷新日志内容
    const refreshLogContent = async () => {
      try {
        if (logLineCount.value === 0) {
          // 首次加载，读取最后2000行
          const response = await logFileApi.readFileLastLines(currentLogFile.value, 2000)
          logContentText.value = response.content
          logLineCount.value = response.totalLines
          
          // 显示警告信息
          if (response.warning) {
            showAlert(response.warning, 'warning')
          }
          
          // 等待DOM更新后滚动到底部
          await nextTick()
          scrollToBottom()
        } else {
          // 增量加载
          const response = await logFileApi.readFileIncremental(currentLogFile.value, logLineCount.value)
          
          // 检测文件是否被轮转
          if (response.fileRotated) {

            // 重新加载最后2000行
            const reloadResponse = await logFileApi.readFileLastLines(currentLogFile.value, 2000)
            logContentText.value += reloadResponse.content
            
            // 限制总日志行数，避免内存占用过大
            logContentText.value = limitLogLines(logContentText.value, 3000)
            
            // 重置行计数器为新文件的总行数
            logLineCount.value = reloadResponse.totalLines
            
            // 等待DOM更新后滚动到底部
            await nextTick()
            scrollToBottom()
          } else if (response.hasNewContent) {
            logContentText.value += response.content
            // 限制只保留最新的2000行
            logContentText.value = limitLogLines(logContentText.value, 2000)
            logLineCount.value = response.totalLines
            
            // 显示警告信息（只在首次显示，避免频繁弹窗）
            if (response.warning && !logContentText.value.includes('warning-shown')) {
              showAlert(response.warning, 'warning')
            }
            
            // 等待DOM更新后滚动到底部
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

    // 切换自动刷新
    const toggleAutoRefresh = () => {
      if (autoRefreshEnabled.value) {
        // 停止自动刷新
        if (autoRefreshInterval) {
          clearInterval(autoRefreshInterval)
          autoRefreshInterval = null
        }
        autoRefreshEnabled.value = false
      } else {
        // 开启自动刷新
        refreshLogContent()
        autoRefreshInterval = setInterval(() => {
          refreshLogContent()
        }, 3000)
        autoRefreshEnabled.value = true
      }
    }

    // 滚动到底部
    const scrollToBottom = () => {
      if (logContent.value) {
        logContent.value.scrollTop = logContent.value.scrollHeight
      }
    }

    // 清空日志内容
    const clearLogContent = () => {
      logContentText.value = ''
    }

    // 下载日志
    const downloadLog = () => {
      if (currentLogFile.value) {
        window.open(logFileApi.downloadFile(currentLogFile.value), '_blank')
      }
    }

    // 监听模态框关闭事件
    if (modalElement.value) {
      modalElement.value.addEventListener('hidden.bs.modal', () => {
        if (autoRefreshInterval) {
          clearInterval(autoRefreshInterval)
          autoRefreshInterval = null
        }
        autoRefreshEnabled.value = false
      })
    }

    onUnmounted(() => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
      }
    })

    return {
      currentLogFile,
      logContentText,
      autoRefreshEnabled,
      modalElement,
      logContent,
      showLog,
      toggleAutoRefresh,
      scrollToBottom,
      clearLogContent,
      downloadLog
    }
  }
}
</script>

