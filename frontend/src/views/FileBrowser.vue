<template>
  <div class="page-container">
    <el-card class="file-browser">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="path-nav">
          <el-button size="small" :icon="Back" @click="goUp" :disabled="!currentPath" title="返回上级" />
          <el-button size="small" :icon="Refresh" @click="refresh" title="刷新" />
          <div class="breadcrumb-wrapper">
            <el-breadcrumb separator="/">
              <el-breadcrumb-item @click="goToPath('')" class="clickable">
                <el-icon><HomeFilled /></el-icon> 根目录
              </el-breadcrumb-item>
              <el-breadcrumb-item 
                v-for="(segment, index) in pathSegments" 
                :key="index"
                @click="goToPath(pathSegments.slice(0, index + 1).join('/'))"
                class="clickable"
              >
                {{ segment }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>
        <div class="toolbar-actions">
          <el-button 
            v-if="selectedFiles.length > 0" 
            size="small" 
            type="primary" 
            @click="downloadSelected"
          >
            <el-icon><Download /></el-icon> 下载 ({{ selectedFiles.length }})
          </el-button>
        </div>
      </div>

      <!-- 文件列表区域 -->
      <div class="file-area" v-loading="loading">
        <!-- 列表头 -->
        <div class="file-header">
          <span class="col-checkbox">
            <el-checkbox 
              :model-value="isAllSelected" 
              :indeterminate="isIndeterminate"
              @change="toggleSelectAll"
            />
          </span>
          <span class="col-name">名称</span>
          <span class="col-time">修改日期</span>
          <span class="col-size">大小</span>
        </div>

        <!-- 文件列表 -->
        <div class="file-list">
          <!-- 返回上级 -->
          <div v-if="currentPath" class="file-row" @dblclick="goUp">
            <span class="col-checkbox"></span>
            <span class="col-name">
              <el-icon class="file-icon folder"><FolderOpened /></el-icon>
              <span>..</span>
            </span>
            <span class="col-time"></span>
            <span class="col-size"></span>
          </div>

          <!-- 文件/目录列表 -->
          <div 
            v-for="item in fileList" 
            :key="item.name"
            class="file-row"
            :class="{ selected: isSelected(item) }"
            @click="selectItem(item, $event)"
            @dblclick="openItem(item)"
          >
            <span class="col-checkbox">
              <el-checkbox 
                :model-value="isSelected(item)"
                @change="toggleSelect(item)"
                @click.stop
              />
            </span>
            <span class="col-name">
              <el-icon class="file-icon" :class="item.isDirectory ? 'folder' : 'file'">
                <Folder v-if="item.isDirectory" />
                <Document v-else />
              </el-icon>
              <span>{{ item.name }}</span>
            </span>
            <span class="col-time">{{ item.lastModified }}</span>
            <span class="col-size">{{ item.sizeText }}</span>
          </div>

          <!-- 空状态 -->
          <div v-if="fileList.length === 0 && !loading" class="empty-state">
            <el-icon :size="48"><FolderOpened /></el-icon>
            <p>此文件夹为空</p>
          </div>
        </div>
      </div>

      <!-- 状态栏 -->
      <div class="status-bar">
        <span>{{ fileList.length }} 个项目</span>
        <span v-if="selectedFiles.length > 0">，已选择 {{ selectedFiles.length }} 项</span>
      </div>
    </el-card>

    <!-- 文件预览覆盖层 -->
    <div v-if="previewVisible" class="preview-overlay" @click.self="closePreview">
      <div class="preview-panel" v-loading="loadingContent">
        <div class="preview-header">
          <span class="preview-title">{{ previewFile?.name }}</span>
          <span class="preview-size">{{ previewFile?.sizeText }}</span>
          <div class="preview-actions">
            <el-button size="small" type="primary" @click="downloadPreviewFile">
              <el-icon><Download /></el-icon> 下载
            </el-button>
            <el-button size="small" @click="closePreview">
              <el-icon><Close /></el-icon> 关闭
            </el-button>
          </div>
        </div>
        <div class="preview-body">
          <textarea 
            ref="previewRef"
            class="preview-textarea" 
            :value="fileContent" 
            readonly
            @scroll="handleScroll"
          ></textarea>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { Folder, FolderOpened, Document, Refresh, Download, HomeFilled, Back, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'

export default {
  name: 'FileBrowser',
  components: { Folder, FolderOpened, Document, Refresh, Download, HomeFilled, Back, Close },
  setup() {
    const previewRef = ref(null)
    const loading = ref(false)
    const loadingContent = ref(false)
    const fileList = ref([])
    const currentPath = ref('')
    const selectedFiles = ref([])
    const previewVisible = ref(false)
    const previewFile = ref(null)
    const previewFilePath = ref('')
    const fileContent = ref('')
    const currentOffset = ref(0)
    const hasMore = ref(false)
    const CHUNK_SIZE = 102400

    const pathSegments = computed(() => {
      if (!currentPath.value) return []
      return currentPath.value.split('/').filter(s => s)
    })

    const isAllSelected = computed(() => {
      return fileList.value.length > 0 && selectedFiles.value.length === fileList.value.length
    })

    const isIndeterminate = computed(() => {
      return selectedFiles.value.length > 0 && selectedFiles.value.length < fileList.value.length
    })

    const loadDirectory = async (path = '') => {
      loading.value = true
      try {
        const res = await api.get('/fileBrowser/list', { params: { path } })
        if (res.success) {
          fileList.value = res.data
          currentPath.value = path
          selectedFiles.value = []
        } else {
          ElMessage.error(res.message || '加载目录失败')
        }
      } catch (e) {
        ElMessage.error('加载目录失败')
      } finally {
        loading.value = false
      }
    }

    const goToPath = (path) => loadDirectory(path)

    const goUp = () => {
      const segments = currentPath.value.split('/').filter(s => s)
      segments.pop()
      loadDirectory(segments.join('/'))
    }

    const refresh = () => loadDirectory(currentPath.value)

    const openItem = (item) => {
      if (item.isDirectory) {
        const newPath = currentPath.value ? `${currentPath.value}/${item.name}` : item.name
        loadDirectory(newPath)
      } else if (item.isTextFile) {
        openPreview(item)
      }
    }

    const selectItem = (item, event) => {
      if (event.ctrlKey) {
        toggleSelect(item)
      } else {
        selectedFiles.value = [item]
      }
    }

    const toggleSelect = (item) => {
      const index = selectedFiles.value.findIndex(f => f.name === item.name)
      if (index >= 0) {
        selectedFiles.value.splice(index, 1)
      } else {
        selectedFiles.value.push(item)
      }
    }

    const toggleSelectAll = (val) => {
      selectedFiles.value = val ? [...fileList.value] : []
    }

    const isSelected = (item) => selectedFiles.value.some(f => f.name === item.name)

    const openPreview = async (file) => {
      previewFile.value = file
      previewFilePath.value = currentPath.value ? `${currentPath.value}/${file.name}` : file.name
      fileContent.value = ''
      currentOffset.value = 0
      hasMore.value = true
      previewVisible.value = true
      await loadContent()
    }

    const closePreview = () => {
      previewVisible.value = false
      previewFile.value = null
      fileContent.value = ''
    }

    const loadContent = async () => {
      if (!previewFile.value || loadingContent.value) return
      loadingContent.value = true
      try {
        const res = await api.get('/fileBrowser/content', {
          params: { path: previewFilePath.value, offset: currentOffset.value, limit: CHUNK_SIZE }
        })
        if (res.success) {
          fileContent.value += res.content
          currentOffset.value = res.offset
          hasMore.value = res.hasMore
        }
      } catch (e) {
        ElMessage.error('加载文件内容失败')
      } finally {
        loadingContent.value = false
      }
    }

    const handleScroll = (e) => {
      const el = e.target
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 50) {
        if (hasMore.value && !loadingContent.value) loadContent()
      }
    }

    const downloadPreviewFile = () => {
      if (!previewFile.value) return
      window.open(`/deploy/fileBrowser/download?path=${encodeURIComponent(previewFilePath.value)}`, '_blank')
    }

    const downloadSelected = async () => {
      if (selectedFiles.value.length === 0) return
      
      if (selectedFiles.value.length === 1 && !selectedFiles.value[0].isDirectory) {
        const filePath = currentPath.value 
          ? `${currentPath.value}/${selectedFiles.value[0].name}` 
          : selectedFiles.value[0].name
        window.open(`/deploy/fileBrowser/download?path=${encodeURIComponent(filePath)}`, '_blank')
      } else {
        const paths = selectedFiles.value.map(f => 
          currentPath.value ? `${currentPath.value}/${f.name}` : f.name
        )
        try {
          const response = await fetch('/deploy/fileBrowser/downloadMultiple', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ paths })
          })
          if (response.ok) {
            const blob = await response.blob()
            const url = window.URL.createObjectURL(blob)
            const a = document.createElement('a')
            a.href = url
            a.download = `files_${Date.now()}.zip`
            a.click()
            window.URL.revokeObjectURL(url)
          } else {
            ElMessage.error('下载失败')
          }
        } catch (e) {
          ElMessage.error('下载失败')
        }
      }
    }

    onMounted(() => loadDirectory())

    return {
      previewRef, loading, loadingContent, fileList, currentPath, selectedFiles,
      previewVisible, previewFile, fileContent, hasMore, pathSegments,
      isAllSelected, isIndeterminate,
      Folder, FolderOpened, Document, Refresh, Download, HomeFilled, Back, Close,
      goToPath, goUp, refresh, openItem, selectItem, toggleSelect, toggleSelectAll,
      isSelected, downloadSelected, downloadPreviewFile, closePreview, handleScroll
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

.file-browser {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.file-browser :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 15px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

.path-nav {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  margin-right: 15px;
}

.breadcrumb-wrapper {
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  flex: 1;
}

.breadcrumb-wrapper .clickable {
  cursor: pointer;
}

.breadcrumb-wrapper .clickable:hover {
  color: #409eff;
}

.file-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.file-header {
  display: flex;
  align-items: center;
  padding: 8px 15px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 13px;
  font-weight: 500;
  color: #606266;
}

.file-list {
  flex: 1;
  overflow-y: auto;
}

.file-row {
  display: flex;
  align-items: center;
  padding: 8px 15px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background-color 0.15s;
}

.file-row:hover {
  background-color: #f5f7fa;
}

.file-row.selected {
  background-color: #d9ecff;
}

.col-checkbox {
  width: 30px;
  flex-shrink: 0;
}

.col-name {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}

.col-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.col-time {
  width: 160px;
  flex-shrink: 0;
  color: #909399;
  font-size: 13px;
}

.col-size {
  width: 80px;
  flex-shrink: 0;
  text-align: right;
  color: #909399;
  font-size: 13px;
}

.file-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.file-icon.folder {
  color: #e6a23c;
}

.file-icon.file {
  color: #909399;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  color: #909399;
}

.empty-state p {
  margin-top: 12px;
}

.status-bar {
  padding: 8px 15px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  font-size: 12px;
  color: #909399;
}

/* 预览覆盖层 */
.preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.preview-panel {
  width: 90%;
  height: 90%;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.preview-header {
  display: flex;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
  border-radius: 8px 8px 0 0;
}

.preview-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-size {
  color: #909399;
  font-size: 13px;
  margin-right: 20px;
}

.preview-actions {
  display: flex;
  gap: 10px;
}

.preview-body {
  flex: 1;
  overflow: hidden;
  padding: 0;
}

.preview-textarea {
  width: 100%;
  height: 100%;
  padding: 15px 20px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: #fff;
  color: #333;
  border: none;
  outline: none;
  resize: none;
  box-sizing: border-box;
}
</style>
