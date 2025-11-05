<template>
  <div class="log-page-container">
    <div class="log-content-wrapper">
      <!-- 文件控制区域 -->
      <div class="file-controls-container">
        <div class="file-controls d-flex align-items-center gap-2">
          <select 
            v-model="selectedFile" 
            @change="onFileChange"
            class="form-select me-2" 
            style="flex: 1; min-width: 500px;"
          >
            <option value="">请选择日志文件...</option>
            <option v-for="file in fileList" :key="file" :value="file">
              {{ file }}
            </option>
          </select>
          <select v-model="maxLines" class="form-select me-2" style="width: 100px;">
            <option value="2000">2000</option>
            <option value="5000">5000</option>
            <option value="10000">10000</option>
            <option value="30000">30000</option>
          </select>
          <button 
            class="btn me-2"
            :class="autoRefreshEnabled ? 'btn-outline-warning' : 'btn-outline-success'"
            @click="toggleAutoRefresh"
            :title="autoRefreshEnabled ? '停止自动刷新' : '开启自动刷新'"
          >
            {{ autoRefreshEnabled ? '停止刷新' : '自动刷新' }}
          </button>
          <button 
            class="btn btn-outline-success me-2" 
            @click="downloadCurrentLog"
            :disabled="!selectedFile"
            title="下载文件"
          >
            下载
          </button>
          <button 
            class="btn btn-outline-info me-2" 
            @click="scrollToBottom"
            :disabled="!selectedFile"
            title="滚动到底部"
          >
            到底部
          </button>
          <button 
            class="btn btn-outline-danger me-2" 
            @click="clearContent"
            :disabled="!selectedFile"
            title="清空内容"
          >
            清空
          </button>
        </div>
      </div>
      
      <!-- 默认内容区域 -->
      <div v-if="!selectedFile" class="log-empty-container">
        <div class="text-center text-muted">
          <p>请选择一个日志文件查看实时内容</p>
        </div>
      </div>
      
      <!-- 日志内容区域 -->
      <div 
        v-else
        ref="logContent"
        class="log-content-area"
        v-html="formattedLogContent"
      >
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { logFileApi } from '../api'
import { showAlert } from '../utils/alert'

export default {
  name: 'LogMgt',
  setup() {
    const fileList = ref([])
    const selectedFile = ref('')
    const maxLines = ref('2000')
    const logContentText = ref('')
    const logLineCount = ref(0)
    const autoRefreshEnabled = ref(false)
    const logContent = ref(null)
    
    let autoRefreshInterval = null
    
    // 格式化日志内容，限制显示行数以提高性能
    const formattedLogContent = computed(() => {
      if (!logContentText.value) return ''
      
      // 限制最大显示行数，避免DOM过大导致性能问题
      const maxDisplayLines = 30000
      const lines = logContentText.value.split('\n')
      
      // 优化：使用更高效的正则表达式一次性替换所有特殊字符
      const escapeHtml = (text) => {
        return text.replace(/[&<>"']/g, (match) => {
          const escapeMap = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
          }
          return escapeMap[match]
        })
      }
      
      // 根据行数决定处理方式
      const displayLines = lines.length > maxDisplayLines ? lines.slice(-maxDisplayLines) : lines
      
      // 优化：减少字符串拼接次数，使用数组join提高性能
      const result = []
      result.push('<div class="log-line">')
      
      for (let i = 0; i < displayLines.length; i++) {
        if (i > 0) {
          result.push('</div><div class="log-line">')
        }
        result.push(escapeHtml(displayLines[i]))
      }
      
      result.push('</div>')
      return result.join('')
    })

    // 获取文件列表
    const loadFileList = async () => {
      try {
        const response = await logFileApi.getFileList()
        if (response.success && Array.isArray(response.logFiles)) {
          // 提取 fullPath 字段作为选项值
          fileList.value = response.logFiles.map(file => file.fullPath)
        } else {
          fileList.value = []
        }
      } catch (error) {
        showAlert('获取文件列表失败: ' + error.message, 'danger')
      }
    }


    // 文件选择改变
    const onFileChange = () => {
      if (selectedFile.value) {
        logLineCount.value = 0
        logContentText.value = '加载中...'
        loadLogContent()
        
        // 自动开启刷新
        if (!autoRefreshEnabled.value) {
          setTimeout(() => {
            toggleAutoRefresh()
          }, 1000)
        }
      }
    }

    // 限制日志行数，保留最新的指定行数
    const limitLogLines = (content, maxLines) => {
      // 预估行数，如果明显小于限制，直接返回，避免不必要的split操作
      const estimatedLines = content.length / 100 // 假设平均每行100字符
      if (estimatedLines <= maxLines * 0.8) {
        return content
      }
      
      // 实际检查行数，如果超过限制则只保留最后N行
      const lines = content.split('\n')
      if (lines.length > maxLines) {
        return lines.slice(-maxLines).join('\n')
      }
      return content
    }

    // 加载日志内容
    const loadLogContent = async () => {
      if (!selectedFile.value) return

      try {
        if (logLineCount.value === 0) {
          // 首次加载
          const response = await logFileApi.readFileLastLines(
            selectedFile.value, 
            parseInt(maxLines.value)
          )
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
          const response = await logFileApi.readFileIncremental(
            selectedFile.value,
            logLineCount.value
          )
          
          // 检测文件是否被轮转
          if (response.fileRotated) {

            // 重新加载最后N行
            const reloadResponse = await logFileApi.readFileLastLines(
              selectedFile.value, 
              parseInt(maxLines.value)
            )
            logContentText.value += reloadResponse.content
            
            // 限制总日志行数，允许多一点空间存储轮转前的历史
            const totalMaxLines = Math.floor(parseInt(maxLines.value) * 1.5)
            logContentText.value = limitLogLines(logContentText.value, totalMaxLines)
            
            // 重置行计数器为新文件的总行数
            logLineCount.value = reloadResponse.totalLines
            
            // 等待DOM更新后滚动到底部
            await nextTick()
            scrollToBottom()
          } else if (response.hasNewContent) {
            logContentText.value += response.content
            // 限制只保留最新的指定行数
            logContentText.value = limitLogLines(logContentText.value, parseInt(maxLines.value))
            logLineCount.value = response.totalLines
            
            // 显示警告信息
            if (response.warning) {
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
        if (!selectedFile.value) {
          showAlert('请先选择日志文件', 'warning')
          return
        }
        loadLogContent()
        // 优化：调整自动刷新间隔为5秒，减少服务器压力
        autoRefreshInterval = setInterval(() => {
          loadLogContent()
        }, 3000)
        autoRefreshEnabled.value = true
      }
    }

    // 滚动到底部（自动刷新时始终滚动到底部）
    const scrollToBottom = () => {
      if (logContent.value) {
        // 使用 nextTick 确保 DOM 已更新
        nextTick(() => {
            logContent.value.scrollTop = logContent.value.scrollHeight
        })
      }
    }

    // 清空内容
    const clearContent = () => {
      logContentText.value = ''
    }

    // 下载当前日志
    const downloadCurrentLog = () => {
      if (selectedFile.value) {
        window.open(logFileApi.downloadFile(selectedFile.value), '_blank')
      }
    }

    onMounted(() => {
      loadFileList()
    })

    onUnmounted(() => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
      }
    })

    return {
      fileList,
      selectedFile,
      maxLines,
      logContentText,
      formattedLogContent,
      autoRefreshEnabled,
      logContent,
      onFileChange,
      toggleAutoRefresh,
      scrollToBottom,
      clearContent,
      downloadCurrentLog
    }
  }
}
</script>

<style scoped>
.log-page-container {
  height: 100%;
  width: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.log-content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 15px;
  gap: 10px;
}

.file-controls-container {
  flex-shrink: 0;
  padding: 15px;
  background-color: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.log-empty-container {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.log-content-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  background-color: #f8f9fa;
  padding: 15px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-family: monospace;
  font-size: 14px;
  line-height: 1.4;
  word-break: break-all;
}

.log-content-area .log-line {
  white-space: pre-wrap;
  min-height: 1.4em;
}
</style>

