<!--
  文件浏览器页面
  
  功能：
  - 浏览 home-directory 目录下的文件和文件夹
  - 双击目录进入，双击文本文件预览内容
  - 支持 Ctrl+点击多选文件
  - 支持单文件和多文件逐个下载（不支持下载目录）
  - 文本文件预览支持分块加载大文件
  - ESC 键关闭预览窗口
-->
<template>
  <div class="page-container">
    <el-card class="file-browser">
      <!-- 工具栏：导航按钮、路径面包屑、下载按钮 -->
      <div class="toolbar">
        <div class="path-nav">
          <el-button size="small" :icon="Back" @click="goUp" :disabled="!currentPath" title="返回上级" />
          <el-button size="small" :icon="Refresh" @click="refresh" title="刷新" />
          <!-- 路径面包屑导航，点击可跳转到对应目录 -->
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
        <!-- 批量下载按钮，选中文件后显示 -->
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
        <!-- 列表头：全选复选框、列标题 -->
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

        <!-- 文件列表内容 -->
        <div class="file-list">
          <!-- 返回上级目录项，双击返回上级 -->
          <div v-if="currentPath" class="file-row" @dblclick="goUp">
            <span class="col-checkbox"></span>
            <span class="col-name">
              <el-icon class="file-icon folder"><FolderOpened /></el-icon>
              <span>..</span>
            </span>
            <span class="col-time"></span>
            <span class="col-size"></span>
          </div>

          <!-- 文件/目录列表项 -->
          <!-- 单击选中，Ctrl+单击多选，双击打开 -->
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

          <!-- 空目录提示 -->
          <div v-if="fileList.length === 0 && !loading" class="empty-state">
            <el-icon :size="48"><FolderOpened /></el-icon>
            <p>此文件夹为空</p>
          </div>
        </div>
      </div>

      <!-- 状态栏：显示文件数量和选中数量 -->
      <div class="status-bar">
        <span>{{ fileList.length }} 个项目</span>
        <span v-if="selectedFiles.length > 0">，已选择 {{ selectedFiles.length }} 项</span>
      </div>
    </el-card>

    <!-- 文件预览覆盖层，点击遮罩关闭 -->
    <div v-if="previewVisible" class="preview-overlay" @click.self="closePreview">
      <div class="preview-panel" v-loading="loadingContent">
        <!-- 预览头部：文件名、大小、操作按钮 -->
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
        <!-- 预览内容区，滚动到底部自动加载更多 -->
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
/**
 * 文件浏览器组件
 * 
 * 提供类似 Windows 资源管理器的文件浏览体验
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Folder, FolderOpened, Document, Refresh, Download, HomeFilled, Back, Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from '../api'

export default {
  name: 'FileBrowser',
  components: { Folder, FolderOpened, Document, Refresh, Download, HomeFilled, Back, Close },
  setup() {
    // ========== 响应式状态 ==========
    const previewRef = ref(null)           // 预览文本框引用
    const loading = ref(false)             // 目录加载状态
    const loadingContent = ref(false)      // 文件内容加载状态
    const fileList = ref([])               // 当前目录文件列表
    const currentPath = ref('')            // 当前路径（相对于 home-directory）
    const selectedFiles = ref([])          // 已选中的文件列表
    const previewVisible = ref(false)      // 预览窗口是否可见
    const previewFile = ref(null)          // 当前预览的文件信息
    const previewFilePath = ref('')        // 当前预览文件的完整路径
    const fileContent = ref('')            // 预览文件内容
    const currentOffset = ref(0)           // 文件读取偏移量
    const hasMore = ref(false)             // 是否还有更多内容
    const CHUNK_SIZE = 102400              // 每次加载的字节数（100KB）

    // ========== 计算属性 ==========
    
    /** 路径分段数组，用于面包屑导航 */
    const pathSegments = computed(() => {
      if (!currentPath.value) return []
      return currentPath.value.split('/').filter(s => s)
    })

    /** 是否全选 */
    const isAllSelected = computed(() => {
      return fileList.value.length > 0 && selectedFiles.value.length === fileList.value.length
    })

    /** 是否部分选中（用于复选框半选状态） */
    const isIndeterminate = computed(() => {
      return selectedFiles.value.length > 0 && selectedFiles.value.length < fileList.value.length
    })

    // ========== 目录操作方法 ==========

    /**
     * 加载目录内容
     * @param {string} path - 相对路径，空字符串表示根目录
     */
    const loadDirectory = async (path = '') => {
      loading.value = true
      try {
        const res = await api.get('/fileBrowser/list', { params: { path } })
        if (res.success) {
          fileList.value = res.data
          currentPath.value = path
          selectedFiles.value = []  // 切换目录时清空选择
        } else {
          ElMessage.error(res.message || '加载目录失败')
        }
      } catch (e) {
        ElMessage.error('加载目录失败')
      } finally {
        loading.value = false
      }
    }

    /** 跳转到指定路径 */
    const goToPath = (path) => loadDirectory(path)

    /** 返回上级目录 */
    const goUp = () => {
      const segments = currentPath.value.split('/').filter(s => s)
      segments.pop()
      loadDirectory(segments.join('/'))
    }

    /** 刷新当前目录 */
    const refresh = () => loadDirectory(currentPath.value)

    /**
     * 打开文件或目录
     * - 目录：进入该目录
     * - 文本文件：打开预览
     */
    const openItem = (item) => {
      if (item.isDirectory) {
        const newPath = currentPath.value ? `${currentPath.value}/${item.name}` : item.name
        loadDirectory(newPath)
      } else if (item.isTextFile) {
        openPreview(item)
      }
    }

    // ========== 选择操作方法 ==========

    /**
     * 选择文件项
     * - 普通点击：单选
     * - Ctrl+点击：切换选中状态（多选）
     */
    const selectItem = (item, event) => {
      if (event.ctrlKey) {
        toggleSelect(item)
      } else {
        selectedFiles.value = [item]
      }
    }

    /** 切换单个文件的选中状态 */
    const toggleSelect = (item) => {
      const index = selectedFiles.value.findIndex(f => f.name === item.name)
      if (index >= 0) {
        selectedFiles.value.splice(index, 1)
      } else {
        selectedFiles.value.push(item)
      }
    }

    /** 全选/取消全选 */
    const toggleSelectAll = (val) => {
      selectedFiles.value = val ? [...fileList.value] : []
    }

    /** 判断文件是否被选中 */
    const isSelected = (item) => selectedFiles.value.some(f => f.name === item.name)

    // ========== 预览操作方法 ==========

    /**
     * 打开文件预览
     * @param {Object} file - 文件信息对象
     */
    const openPreview = async (file) => {
      previewFile.value = file
      previewFilePath.value = currentPath.value ? `${currentPath.value}/${file.name}` : file.name
      fileContent.value = ''
      currentOffset.value = 0
      hasMore.value = true
      previewVisible.value = true
      // 添加 ESC 键监听，支持快捷键关闭
      document.addEventListener('keydown', handleKeydown)
      await loadContent()
    }

    /** 关闭预览窗口 */
    const closePreview = () => {
      previewVisible.value = false
      previewFile.value = null
      fileContent.value = ''
      // 移除 ESC 键监听
      document.removeEventListener('keydown', handleKeydown)
    }

    /** 键盘事件处理：ESC 关闭预览 */
    const handleKeydown = (e) => {
      if (e.key === 'Escape' && previewVisible.value) {
        closePreview()
      }
    }

    /**
     * 加载文件内容（分块加载）
     * 每次加载 CHUNK_SIZE 字节，支持大文件渐进式加载
     */
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

    /** 滚动事件处理：滚动到底部时自动加载更多 */
    const handleScroll = (e) => {
      const el = e.target
      // 距离底部 50px 时触发加载
      if (el.scrollTop + el.clientHeight >= el.scrollHeight - 50) {
        if (hasMore.value && !loadingContent.value) loadContent()
      }
    }

    // ========== 下载操作方法 ==========

    /** 下载当前预览的文件 */
    const downloadPreviewFile = () => {
      if (!previewFile.value) return
      downloadSingleFile(previewFilePath.value)
    }

    /**
     * 下载单个文件（使用隐藏的a标签触发下载）
     */
    const downloadSingleFile = (filePath) => {
      const a = document.createElement('a')
      a.href = `/deploy/fileBrowser/download?path=${encodeURIComponent(filePath)}`
      a.download = ''  // 触发下载而非打开
      a.style.display = 'none'
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
    }

    /**
     * 下载选中的文件
     * 过滤掉目录，逐个下载选中的文件
     */
    const downloadSelected = () => {
      if (selectedFiles.value.length === 0) return
      
      // 过滤掉目录，只下载文件
      const filesToDownload = selectedFiles.value.filter(f => !f.isDirectory)
      
      if (filesToDownload.length === 0) {
        ElMessage.warning('请选择文件进行下载，目录不支持直接下载')
        return
      }
      
      // 逐个下载文件，使用延时避免浏览器问题
      filesToDownload.forEach((file, index) => {
        const filePath = currentPath.value 
          ? `${currentPath.value}/${file.name}` 
          : file.name
        setTimeout(() => {
          downloadSingleFile(filePath)
        }, index * 500)
      })
      
      // 如果有目录被过滤掉，提示用户
      const skippedDirs = selectedFiles.value.filter(f => f.isDirectory)
      if (skippedDirs.length > 0) {
        ElMessage.info(`已跳过 ${skippedDirs.length} 个目录，目录不支持直接下载`)
      }
    }

    // ========== 生命周期 ==========

    onMounted(() => loadDirectory())

    onUnmounted(() => {
      // 组件卸载时清理事件监听，防止内存泄漏
      document.removeEventListener('keydown', handleKeydown)
    })

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
/* ========== 页面容器 ========== */
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

/* ========== 工具栏 ========== */
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

/* ========== 文件列表区域 ========== */
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

/* ========== 列样式 ========== */
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
  width: 200px;
  flex-shrink: 0;
  color: #909399;
  font-size: 13px;
}

.col-size {
  width: 120px;
  padding-right:20px;
  flex-shrink: 0;
  text-align: right;
  color: #909399;
  font-size: 13px;
}

/* ========== 文件图标 ========== */
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

/* ========== 空状态 ========== */
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

/* ========== 状态栏 ========== */
.status-bar {
  padding: 8px 15px;
  border-top: 1px solid #e4e7ed;
  background: #fafafa;
  font-size: 12px;
  color: #909399;
}

/* ========== 预览覆盖层 ========== */
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
