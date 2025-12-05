<template>
  <div class="page-container">
    <!-- 文件控制区域 -->
    <el-card class="controls-card">
      <el-row :gutter="10" align="middle">
        <el-col :span="10">
          <el-select v-model="selectedFile" @change="onFileChange" placeholder="请选择日志文件..." style="width: 100%;">
            <el-option v-for="file in fileList" :key="file" :label="file" :value="file" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-select v-model="maxLines" style="width: 100%;">
            <el-option label="2000行" value="2000" />
            <el-option label="5000行" value="5000" />
            <el-option label="10000行" value="10000" />
            <el-option label="30000行" value="30000" />
          </el-select>
        </el-col>
        <el-col :span="11">
          <el-button-group>
            <el-button :type="autoRefreshEnabled ? 'warning' : 'success'" @click="toggleAutoRefresh">
              {{ autoRefreshEnabled ? '停止刷新' : '自动刷新' }}
            </el-button>
            <el-button type="success" :disabled="!selectedFile" @click="downloadCurrentLog">下载</el-button>
            <el-button type="info" :disabled="!selectedFile" @click="scrollToBottom">到底部</el-button>
            <el-button type="danger" :disabled="!selectedFile" @click="clearContent">清空</el-button>
          </el-button-group>
        </el-col>
      </el-row>
    </el-card>

    <!-- 默认内容区域 -->
    <el-card v-if="!selectedFile" class="log-card empty-card">
      <el-empty description="请选择一个日志文件查看实时内容" />
    </el-card>

    <!-- 日志内容区域 -->
    <div v-else ref="logContent" class="log-content-area" v-html="formattedLogContent"></div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { logFileApi } from '../api'
import { ElMessage } from 'element-plus'

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

    const formattedLogContent = computed(() => {
      if (!logContentText.value) return ''
      const maxDisplayLines = 30000
      const lines = logContentText.value.split('\n')
      const escapeHtml = (text) => text.replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]))
      const displayLines = lines.length > maxDisplayLines ? lines.slice(-maxDisplayLines) : lines
      return displayLines.map(line => `<div class="log-line">${escapeHtml(line)}</div>`).join('')
    })

    const loadFileList = async () => {
      try {
        const response = await logFileApi.getFileList()
        if (response.success && Array.isArray(response.logFiles)) {
          fileList.value = response.logFiles.map(file => file.fullPath)
        } else {
          fileList.value = []
        }
      } catch (error) {
        ElMessage.error('获取文件列表失败: ' + error.message)
      }
    }

    const onFileChange = () => {
      if (selectedFile.value) {
        logLineCount.value = 0
        logContentText.value = '加载中...'
        loadLogContent()
        if (!autoRefreshEnabled.value) {
          setTimeout(() => toggleAutoRefresh(), 1000)
        }
      }
    }

    const limitLogLines = (content, maxLines) => {
      const estimatedLines = content.length / 100
      if (estimatedLines <= maxLines * 0.8) return content
      const lines = content.split('\n')
      if (lines.length > maxLines) return lines.slice(-maxLines).join('\n')
      return content
    }

    const loadLogContent = async () => {
      if (!selectedFile.value) return
      try {
        if (logLineCount.value === 0) {
          const response = await logFileApi.readFileLastLines(selectedFile.value, parseInt(maxLines.value))
          logContentText.value = response.content
          logLineCount.value = response.totalLines
          if (response.warning) ElMessage.warning(response.warning)
          await nextTick()
          scrollToBottom()
        } else {
          const response = await logFileApi.readFileIncremental(selectedFile.value, logLineCount.value)
          if (response.fileRotated) {
            const reloadResponse = await logFileApi.readFileLastLines(selectedFile.value, parseInt(maxLines.value))
            logContentText.value += reloadResponse.content
            const totalMaxLines = Math.floor(parseInt(maxLines.value) * 1.5)
            logContentText.value = limitLogLines(logContentText.value, totalMaxLines)
            logLineCount.value = reloadResponse.totalLines
            await nextTick()
            scrollToBottom()
          } else if (response.hasNewContent) {
            logContentText.value += response.content
            logContentText.value = limitLogLines(logContentText.value, parseInt(maxLines.value))
            logLineCount.value = response.totalLines
            if (response.warning) ElMessage.warning(response.warning)
            await nextTick()
            scrollToBottom()
          }
        }
      } catch (error) {
        if (logLineCount.value === 0) logContentText.value = '加载日志失败: ' + error.message
      }
    }

    const toggleAutoRefresh = () => {
      if (autoRefreshEnabled.value) {
        if (autoRefreshInterval) { clearInterval(autoRefreshInterval); autoRefreshInterval = null }
        autoRefreshEnabled.value = false
      } else {
        if (!selectedFile.value) { ElMessage.warning('请先选择日志文件'); return }
        loadLogContent()
        autoRefreshInterval = setInterval(loadLogContent, 3000)
        autoRefreshEnabled.value = true
      }
    }

    const scrollToBottom = () => {
      if (logContent.value) nextTick(() => { logContent.value.scrollTop = logContent.value.scrollHeight })
    }

    const clearContent = () => { logContentText.value = '' }

    const downloadCurrentLog = () => {
      if (selectedFile.value) window.open(logFileApi.downloadFile(selectedFile.value), '_blank')
    }

    onMounted(() => loadFileList())
    onUnmounted(() => { if (autoRefreshInterval) clearInterval(autoRefreshInterval) })

    return {
      fileList, selectedFile, maxLines, logContentText, formattedLogContent,
      autoRefreshEnabled, logContent, onFileChange, toggleAutoRefresh,
      scrollToBottom, clearContent, downloadCurrentLog
    }
  }
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: calc(100vh - 61px);
  display: flex;
  flex-direction: column;
  gap: 15px;
  background-color: #f5f7fa;
}
.controls-card {
  flex-shrink: 0;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.log-card.empty-card {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
.log-content-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  background-color: #fff;
  padding: 15px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
.log-content-area .log-line {
  white-space: pre-wrap;
  min-height: 1.5em;
  word-wrap: break-word;
}
:deep(.el-select) {
  width: 100%;
}
</style>
