<template>
  <div class="page-container">
    <!-- 左侧目录树 -->
    <div class="left-panel">
      <el-card class="dir-card">
        <!-- 路径导航 -->
        <div class="path-nav">
          <el-button :icon="ArrowLeft" size="small" :disabled="!parentPath" @click="goToParent" />
          <el-button :icon="ArrowRight" size="small" disabled />
          <el-input v-model="currentPath" size="small" readonly class="path-input" />
        </div>
        <!-- 统计信息 -->
        <div class="dir-stats">
          共 {{ fileCount }} 个文件, {{ folderCount }} 个文件夹, {{ totalSize }}
        </div>
        <!-- 目录列表 -->
        <div class="dir-list">
          <!-- 返回上级 -->
          <div v-if="parentPath" class="dir-item" @dblclick="goToParent">
            <el-icon class="folder-icon"><Folder /></el-icon>
            <span class="item-name">..</span>
            <span class="item-time">-</span>
          </div>
          <!-- 目录和文件列表 -->
          <div 
            v-for="item in dirItems" 
            :key="item.path" 
            class="dir-item"
            :class="{ 'is-selected': selectedFile === item.path && !item.isDirectory }"
            @dblclick="handleItemDblClick(item)"
          >
            <el-icon class="folder-icon" v-if="item.isDirectory"><Folder /></el-icon>
            <el-icon class="file-icon" v-else><Document /></el-icon>
            <span class="item-name" :title="item.name">{{ item.name }}</span>
            <span class="item-time">{{ item.lastModified }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 右侧日志显示 -->
    <div class="right-panel">
      <!-- 控制区域 -->
      <el-card class="controls-card">
        <el-row :gutter="10" align="middle">
          <el-col :span="8">
            <span class="selected-file-label">{{ selectedFileName || '请选择日志文件' }}</span>
          </el-col>
          <el-col :span="4">
            <el-select v-model="maxLines" size="small" style="width: 100%;">
              <el-option label="2000行" value="2000" />
              <el-option label="5000行" value="5000" />
              <el-option label="10000行" value="10000" />
            </el-select>
          </el-col>
          <el-col :span="12" style="text-align: right;">
            <el-button-group>
              <el-button :type="autoRefreshEnabled ? 'warning' : 'success'" size="small" @click="toggleAutoRefresh">
                {{ autoRefreshEnabled ? '停止刷新' : '自动刷新' }}
              </el-button>
              <el-button type="success" size="small" :disabled="!selectedFile" @click="downloadCurrentLog">下载</el-button>
              <el-button type="info" size="small" :disabled="!selectedFile" @click="scrollToBottom">到底部</el-button>
              <el-button type="danger" size="small" :disabled="!selectedFile" @click="clearContent">清空</el-button>
            </el-button-group>
          </el-col>
        </el-row>
      </el-card>

      <!-- 日志内容区域 -->
      <el-card v-if="!selectedFile" class="log-card empty-card">
        <el-empty description="双击左侧日志文件查看内容" />
      </el-card>
      <pre v-else ref="logContent" class="log-content-area">{{ formattedLogContent }}</pre>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { logFileApi } from '../api'
import { ElMessage } from 'element-plus'
import { Folder, Document, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

export default {
  name: 'LogMgt',
  components: { Folder, Document, ArrowLeft, ArrowRight },
  setup() {
    // 目录相关
    const currentPath = ref('')
    const parentPath = ref('')
    const dirItems = ref([])
    const fileCount = ref(0)
    const folderCount = ref(0)
    const totalSize = ref('0B')
    
    // 日志相关
    const selectedFile = ref('')
    const selectedFileName = ref('')
    const maxLines = ref('2000')
    const logContentText = ref('')
    const logLineCount = ref(0)
    const autoRefreshEnabled = ref(false)
    const logContent = ref(null)
    
    let autoRefreshInterval = null

    // 使用缓存避免重复计算
    const formattedLogContent = computed(() => {
      if (!logContentText.value) return ''
      const maxDisplayLines = 10000
      const lines = logContentText.value.split('\n')
      const displayLines = lines.length > maxDisplayLines ? lines.slice(-maxDisplayLines) : lines
      // 简化渲染：使用 pre 标签的特性，不再为每行创建 div
      const escapeHtml = (text) => text.replace(/[&<>"']/g, (m) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m]))
      return displayLines.map(line => escapeHtml(line)).join('\n')
    })

    // 加载目录内容
    const loadDirectory = async (path = '') => {
      try {
        const response = await logFileApi.browseDirectory(path)
        if (response.success) {
          currentPath.value = response.currentPath
          parentPath.value = response.parentPath
          dirItems.value = response.items
          fileCount.value = response.fileCount
          folderCount.value = response.folderCount
          totalSize.value = response.totalSize
        } else {
          ElMessage.error(response.message || '加载目录失败')
        }
      } catch (error) {
        ElMessage.error('加载目录失败: ' + error.message)
      }
    }

    // 返回上级目录
    const goToParent = () => {
      if (parentPath.value) {
        loadDirectory(parentPath.value)
      }
    }

    // 双击处理
    const handleItemDblClick = (item) => {
      if (item.isDirectory) {
        loadDirectory(item.path)
      } else {
        // 切换文件时先停止之前的自动刷新
        if (autoRefreshInterval) {
          clearInterval(autoRefreshInterval)
          autoRefreshInterval = null
          autoRefreshEnabled.value = false
        }
        
        // 选择文件并加载日志
        selectedFile.value = item.path
        selectedFileName.value = item.name
        logLineCount.value = 0
        logContentText.value = '加载中...'
        loadLogContent()
        // 延迟启动自动刷新
        setTimeout(() => {
          if (selectedFile.value === item.path && !autoRefreshEnabled.value) {
            toggleAutoRefresh()
          }
        }, 1000)
      }
    }

    // 优化：使用更高效的行数限制方法
    const limitLogLines = (content, maxLines) => {
      // 快速估算：如果内容长度较小，直接返回
      if (content.length < maxLines * 50) return content
      
      // 从后向前查找换行符，避免分割整个字符串
      let lineCount = 0
      let pos = content.length
      while (pos > 0 && lineCount < maxLines) {
        pos = content.lastIndexOf('\n', pos - 1)
        if (pos === -1) break
        lineCount++
      }
      
      if (lineCount >= maxLines && pos > 0) {
        return content.substring(pos + 1)
      }
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
            // 文件轮转时，重新加载内容
            const reloadResponse = await logFileApi.readFileLastLines(selectedFile.value, parseInt(maxLines.value))
            logContentText.value = reloadResponse.content
            logLineCount.value = reloadResponse.totalLines
            await nextTick()
            scrollToBottom()
          } else if (response.hasNewContent) {
            // 确保新内容前有换行符
            if (logContentText.value && !logContentText.value.endsWith('\n')) {
              logContentText.value += '\n'
            }
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

    onMounted(() => loadDirectory())
    onUnmounted(() => { if (autoRefreshInterval) clearInterval(autoRefreshInterval) })

    return {
      currentPath, parentPath, dirItems, fileCount, folderCount, totalSize,
      selectedFile, selectedFileName, maxLines, logContentText, formattedLogContent,
      autoRefreshEnabled, logContent, ArrowLeft, ArrowRight,
      loadDirectory, goToParent, handleItemDblClick, toggleAutoRefresh,
      scrollToBottom, clearContent, downloadCurrentLog
    }
  }
}
</script>


<style scoped>
.page-container {
  padding: 15px;
  height: calc(100vh - 61px);
  display: flex;
  gap: 15px;
  background-color: #f5f7fa;
  overflow: hidden;
}
.left-panel {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.dir-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
}
:deep(.dir-card .el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 12px;
  overflow: hidden;
}
.path-nav {
  display: flex;
  gap: 5px;
  margin-bottom: 8px;
}
.path-input {
  flex: 1;
}
.dir-stats {
  font-size: 12px;
  color: #909399;
  padding: 5px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
}
.dir-list {
  flex: 1;
  overflow-y: auto;
}
.dir-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}
.dir-item:hover {
  background-color: #f5f7fa;
}
.dir-item.is-selected {
  background-color: #ecf5ff;
}
.folder-icon {
  color: #e6a23c;
  font-size: 18px;
  margin-right: 8px;
}
.file-icon {
  color: #909399;
  font-size: 18px;
  margin-right: 8px;
}
.item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.item-time {
  font-size: 12px;
  color: #909399;
  margin-left: 10px;
  white-space: nowrap;
}
.controls-card {
  flex-shrink: 0;
  border-radius: 8px;
}
.selected-file-label {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
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
  margin: 0;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
</style>
