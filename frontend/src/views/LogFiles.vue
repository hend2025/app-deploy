<!--
  日志文件浏览页面
  功能：
  - 浏览日志目录和文件
  - 查看日志文件内容（支持分页加载）
  - 下载和删除日志文件
-->
<template>
  <div class="page-container">
    <el-card class="log-browser">
      <div class="split-container">
        <!-- 左侧：文件列表 -->
        <div class="left-panel" :style="{ width: collapsed ? '0px' : leftWidth + 'px' }" v-show="!collapsed">
          <!-- 路径导航栏 -->
          <div class="path-bar">
            <el-breadcrumb separator=">">
              <el-breadcrumb-item @click="goToRoot" class="clickable">
                <el-icon><HomeFilled /></el-icon>
              </el-breadcrumb-item>
              <el-breadcrumb-item v-if="currentApp">{{ currentApp }}</el-breadcrumb-item>
            </el-breadcrumb>
            <div class="path-actions">
              <el-button size="small" circle @click="toggleCollapse" :title="collapsed ? '展开' : '收起'">
                <el-icon><DArrowLeft v-if="!collapsed" /><DArrowRight v-else /></el-icon>
              </el-button>
              <el-button size="small" :icon="Refresh" circle @click="refresh" title="刷新" />
            </div>
          </div>
          
          <!-- 文件列表 -->
          <div class="file-list" v-loading="loading">
            <!-- 返回上级 -->
            <div v-if="currentApp" class="file-item" @dblclick="goToRoot">
              <el-icon class="file-icon folder"><FolderOpened /></el-icon>
              <span class="file-name">..</span>
            </div>
            
            <!-- 应用目录列表 -->
            <template v-if="!currentApp">
              <div 
                v-for="app in appList" 
                :key="app.appCode"
                class="file-item"
                :class="{ selected: selectedItem === app.appCode }"
                @click="selectItem(app.appCode)"
                @dblclick="openApp(app.appCode)"
              >
                <el-icon class="file-icon folder"><Folder /></el-icon>
                <span class="file-name">{{ app.appCode }}</span>
                <span class="file-info">{{ app.fileCount }} 个文件</span>
              </div>
            </template>
            
            <!-- 文件列表 -->
            <template v-else>
              <div 
                v-for="file in fileList" 
                :key="file.fileName"
                class="file-item"
                :class="{ selected: selectedItem === file.fileName }"
                @click="selectItem(file.fileName)"
                @dblclick="viewFile(file)"
              >
                <el-icon class="file-icon file"><Document /></el-icon>
                <span class="file-name">{{ file.fileName }}</span>
              </div>
            </template>
            
            <!-- 空状态 -->
            <div v-if="isEmpty" class="empty-state">
              <el-icon :size="32"><FolderOpened /></el-icon>
              <p>此文件夹为空</p>
            </div>
          </div>
        </div>

        <!-- 拖拽分隔条 -->
        <div class="resize-bar" v-show="!collapsed">
          <div class="resize-handle" @mousedown="startResize"></div>
        </div>

        <!-- 右侧：日志内容 -->
        <div class="right-panel">
          <div class="panel-header">
            <div class="header-title">
              <span v-if="currentFile">{{ currentFile.fileName }}</span>
              <span v-else>请双击选择日志文件查看</span>
              <el-tag v-if="currentFile" size="small" type="info" class="size-tag">{{ currentFile.sizeText }}</el-tag>
            </div>
            <div class="header-actions" v-if="currentFile">
              <el-button size="small" type="primary" @click="downloadFile">
                <el-icon><Download /></el-icon> 下载
              </el-button>
              <el-button size="small" type="danger" @click="deleteFile">
                <el-icon><Delete /></el-icon> 删除
              </el-button>
            </div>
          </div>
          
          <div class="log-content-wrapper" v-loading="loadingContent">
            <textarea 
              ref="logContentRef" 
              class="log-content" 
              :value="logContent" 
              readonly 
              @scroll="handleScroll"
              placeholder="双击左侧文件查看内容"
            ></textarea>
          </div>
          

        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
/**
 * 日志文件浏览页面组件
 * 提供日志文件的浏览、查看和下载功能
 */
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { Folder, FolderOpened, Document, Refresh, Download, Delete, DocumentCopy, HomeFilled, DArrowLeft, DArrowRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

export default {
  name: 'LogFiles',
  components: { Folder, FolderOpened, Document, Refresh, Download, Delete, DocumentCopy, HomeFilled, DArrowLeft, DArrowRight },
  setup() {
    const logContentRef = ref(null)
    const loading = ref(false)
    const loadingContent = ref(false)
    const appList = ref([])
    const fileList = ref([])
    const currentApp = ref(null)
    const currentFile = ref(null)
    const selectedItem = ref(null)
    const logContent = ref('')
    const currentOffset = ref(0)
    const hasMore = ref(false)
    const CHUNK_SIZE = 102400

    // 拖拽调整大小
    const leftWidth = ref(380)
    const collapsed = ref(false)
    const isResizing = ref(false)
    const MIN_WIDTH = 200
    const MAX_WIDTH = 500

    const toggleCollapse = () => {
      collapsed.value = !collapsed.value
    }

    const startResize = (e) => {
      if (collapsed.value) return
      isResizing.value = true
      document.addEventListener('mousemove', doResize)
      document.addEventListener('mouseup', stopResize)
      e.preventDefault()
    }

    const doResize = (e) => {
      if (!isResizing.value) return
      const container = document.querySelector('.split-container')
      if (container) {
        const rect = container.getBoundingClientRect()
        let newWidth = e.clientX - rect.left
        newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, newWidth))
        leftWidth.value = newWidth
      }
    }

    const stopResize = () => {
      isResizing.value = false
      document.removeEventListener('mousemove', doResize)
      document.removeEventListener('mouseup', stopResize)
    }

    const isEmpty = computed(() => {
      if (!currentApp.value) return appList.value.length === 0
      return fileList.value.length === 0
    })

    const loadApps = async () => {
      loading.value = true
      try {
        const res = await api.get('/logFiles/apps')
        if (res.success) {
          appList.value = res.data
        }
      } catch (e) {
        ElMessage.error('加载应用列表失败')
      } finally {
        loading.value = false
      }
    }

    const loadFiles = async (appCode) => {
      loading.value = true
      try {
        const res = await api.get('/logFiles/list', { params: { appCode } })
        if (res.success) {
          fileList.value = res.data
        }
      } catch (e) {
        ElMessage.error('加载文件列表失败')
      } finally {
        loading.value = false
      }
    }

    const goToRoot = () => {
      currentApp.value = null
      currentFile.value = null
      selectedItem.value = null
      logContent.value = ''
      fileList.value = []
      loadApps()
    }

    const openApp = (appCode) => {
      currentApp.value = appCode
      currentFile.value = null
      selectedItem.value = null
      logContent.value = ''
      loadFiles(appCode)
    }

    const selectItem = (item) => {
      selectedItem.value = item
    }

    const viewFile = async (file) => {
      currentFile.value = file
      logContent.value = ''
      currentOffset.value = 0
      hasMore.value = true
      await loadContent()
    }

    const loadContent = async () => {
      if (!currentFile.value || loadingContent.value) return
      
      loadingContent.value = true
      try {
        const res = await api.get('/logFiles/content', {
          params: {
            appCode: currentApp.value,
            fileName: currentFile.value.fileName,
            offset: currentOffset.value,
            limit: CHUNK_SIZE
          }
        })
        if (res.success) {
          logContent.value += res.content
          currentOffset.value = res.offset
          hasMore.value = res.hasMore
        }
      } catch (e) {
        ElMessage.error('加载文件内容失败')
      } finally {
        loadingContent.value = false
      }
    }

    const loadMore = () => {
      if (hasMore.value) loadContent()
    }

    const downloadFile = () => {
      if (!currentFile.value) return
      const url = `/deploy/logFiles/download?appCode=${currentApp.value}&fileName=${encodeURIComponent(currentFile.value.fileName)}`
      window.open(url, '_blank')
    }

    const deleteFile = async () => {
      if (!currentFile.value) return
      
      try {
        await ElMessageBox.confirm(
          `确定要删除日志文件 "${currentFile.value.fileName}" 吗？`,
          '删除确认',
          { type: 'warning' }
        )
        
        const res = await api.post('/logFiles/delete', {
          appCode: currentApp.value,
          fileName: currentFile.value.fileName
        })
        
        if (res.success) {
          ElMessage.success('删除成功')
          currentFile.value = null
          logContent.value = ''
          loadFiles(currentApp.value)
        } else {
          ElMessage.error(res.message || '删除失败')
        }
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('删除失败')
      }
    }

    const refresh = () => {
      if (currentApp.value) {
        loadFiles(currentApp.value)
      } else {
        loadApps()
      }
    }

    const scrollToBottom = () => {
      if (logContentRef.value) {
        nextTick(() => {
          logContentRef.value.scrollTop = logContentRef.value.scrollHeight
        })
      }
    }

    const handleScroll = (e) => {
      const el = e.target
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 50) {
        if (hasMore.value && !loadingContent.value) loadMore()
      }
    }

    const formatSize = (size) => {
      if (size < 1024) return size + ' B'
      if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
      return (size / (1024 * 1024)).toFixed(1) + ' MB'
    }

    onMounted(() => loadApps())
    
    onUnmounted(() => {
      document.removeEventListener('mousemove', doResize)
      document.removeEventListener('mouseup', stopResize)
    })

    return {
      logContentRef, loading, loadingContent, appList, fileList,
      currentApp, currentFile, selectedItem, logContent, currentOffset, hasMore, isEmpty,
      leftWidth, collapsed, startResize, toggleCollapse,
      Folder, FolderOpened, Document, Refresh, Download, Delete, DocumentCopy, HomeFilled, DArrowLeft, DArrowRight,
      goToRoot, openApp, selectItem, viewFile, loadMore, downloadFile, deleteFile,
      refresh, scrollToBottom, handleScroll, formatSize
    }
  }
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: calc(100vh - 61px);
  background-color: #f5f7fa;
  overflow: hidden;
}

.log-browser {
  height: 100%;
  overflow: hidden;
}

.log-browser :deep(.el-card__body) {
  height: calc(100% - 20px);
  padding: 10px;
  overflow: hidden;
}

.split-container {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.left-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
  transition: width 0.35s ease;
}

.resize-bar {
  width: 8px;
  flex-shrink: 0;
  background: #f5f7fa;
  border-left: 1px solid #e4e7ed;
  border-right: 1px solid #e4e7ed;
}

.resize-handle {
  height: 100%;
  width: 100%;
  cursor: col-resize;
  transition: background-color 0.2s;
}

.resize-handle:hover {
  background: #409eff;
}

.path-actions {
  display: flex;
  gap: 4px;
}

.right-panel {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-left: 10px;
  overflow: hidden;
  min-width: 0;
}

.path-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 8px;
  border: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.path-bar .clickable {
  cursor: pointer;
}

.path-bar .clickable:hover {
  color: #409eff;
}

.file-list {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background-color 0.2s;
}

.file-item:hover {
  background-color: #f5f7fa;
}

.file-item.selected {
  background-color: #d9ecff;
}

.file-icon {
  margin-right: 8px;
  font-size: 18px;
  flex-shrink: 0;
}

.file-icon.folder {
  color: #e6a23c;
}

.file-icon.file {
  color: #909399;
}

.file-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  min-width: 0;
}

.file-info {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
  flex-shrink: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.empty-state p {
  margin-top: 8px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 10px;
  font-weight: 600;
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size-tag {
  font-weight: normal;
}

.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.log-content-wrapper {
  flex: 1;
  position: relative;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fafafa;
  min-height: 0;
}

.log-content {
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 12px;
  overflow: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  background: #ffffff;
  color: #333333;
  border: none;
  outline: none;
  resize: none;
  box-sizing: border-box;
}

.log-content::placeholder {
  color: #909399;
}

.bottom-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-top: 1px solid #e4e7ed;
  margin-top: 10px;
  flex-shrink: 0;
}

.file-info-text {
  margin-left: auto;
  color: #909399;
  font-size: 12px;
}
</style>
