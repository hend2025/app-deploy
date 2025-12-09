<!--
  日志查看模态框组件
  
  功能：
  - 全屏显示应用日志
  - 支持自动刷新（增量拉取新日志）
  - 按日志级别高亮显示（DEBUG/INFO/WARN/ERROR）
  - 自动滚动到底部
-->
<template>
  <el-dialog
    v-model="visible"
    fullscreen
    :close-on-click-modal="false"
    :show-close="false"
    @close="handleClose"
  >
    <!-- 自定义头部：标题 + 操作按钮 -->
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">日志详情 - {{ currentAppCode }}</span>
        <div class="dialog-actions">
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
      </div>
    </template>
    <!-- 日志内容区域 -->
    <div ref="logContent" class="log-content">
      <div v-for="(log, index) in logs" :key="index" class="log-line" :class="'log-' + (log.logLevel || 'info').toLowerCase()">
        <span class="log-time">{{ log.logTime }}</span>
        <span class="log-text">{{ log.logContent }}</span>
      </div>
      <div v-if="logs.length === 0" class="empty-tip">暂无日志</div>
    </div>
  </el-dialog>
</template>

<script>
import { ref, onUnmounted, nextTick } from 'vue'
import { logApi } from '../api'

export default {
  name: 'LogModal',
  setup() {
    // 响应式状态
    const visible = ref(false)           // 模态框显示状态
    const currentAppCode = ref('')        // 当前查看的应用编码
    const logs = ref([])                  // 日志列表
    const autoRefreshEnabled = ref(false) // 自动刷新开关
    const logContent = ref(null)          // 日志容器DOM引用
    
    // 非响应式变量
    let autoRefreshInterval = null        // 自动刷新定时器
    let lastSeq = 0                       // 最后一条日志的序号（用于增量读取）

    /**
     * 显示日志模态框
     * 对外暴露的方法，由父组件调用
     * @param {string} appCode - 应用编码
     */
    const showLog = (appCode) => {
      // 清理之前的状态
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
        autoRefreshInterval = null
      }
      autoRefreshEnabled.value = false
      lastSeq = 0
      
      currentAppCode.value = appCode
      logs.value = []
      visible.value = true
      loadInitialLogs()
      
      // 2秒后自动开启刷新
      setTimeout(() => {
        if (visible.value && !autoRefreshEnabled.value) {
          toggleAutoRefresh()
        }
      }, 2000)
    }

    /**
     * 首次加载日志（全量）
     */
    const loadInitialLogs = async () => {
      try {
        const response = await logApi.bufferLogs(currentAppCode.value, 1000)
        const logList = response.logs || []
        logs.value = logList
        // 记录最后一条日志的序号
        if (logList.length > 0) {
          lastSeq = logList[logList.length - 1].seq || 0
        }
        await nextTick()
        scrollToBottom()
      } catch (error) {
        console.error('加载日志失败:', error)
      }
    }

    /**
     * 增量读取新日志
     * 只获取lastSeq之后的新日志，避免重复
     */
    const fetchIncrementalLogs = async () => {
      try {
        const response = await logApi.incremental(currentAppCode.value, lastSeq, 500)
        const newLogs = response.logs || []
        if (newLogs.length > 0) {
          logs.value.push(...newLogs)
          lastSeq = newLogs[newLogs.length - 1].seq || lastSeq
          // 限制日志条数，避免内存过大
          if (logs.value.length > 2000) {
            logs.value = logs.value.slice(-1500)
          }
          await nextTick()
          scrollToBottom()
        }
      } catch (error) {
        console.error('增量加载日志失败:', error)
      }
    }

    /**
     * 切换自动刷新状态
     */
    const toggleAutoRefresh = () => {
      if (autoRefreshEnabled.value) {
        if (autoRefreshInterval) {
          clearInterval(autoRefreshInterval)
          autoRefreshInterval = null
        }
        autoRefreshEnabled.value = false
      } else {
        fetchIncrementalLogs()
        autoRefreshInterval = setInterval(fetchIncrementalLogs, 3000)
        autoRefreshEnabled.value = true
      }
    }

    /**
     * 滚动到日志底部
     */
    const scrollToBottom = () => {
      if (logContent.value) {
        nextTick(() => { logContent.value.scrollTop = logContent.value.scrollHeight })
      }
    }

    /**
     * 清空日志显示
     */
    const clearLogContent = () => {
      logs.value = []
      lastSeq = 0
    }

    /**
     * 模态框关闭时的清理操作
     */
    const handleClose = () => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
        autoRefreshInterval = null
      }
      autoRefreshEnabled.value = false
      logs.value = []
      lastSeq = 0
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
  height: calc(100vh - 120px);
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
.dialog-header { display: flex; justify-content: space-between; align-items: center; width: 100%; }
.dialog-title { font-size: 18px; font-weight: 600; }
.dialog-actions { display: flex; gap: 8px; }
.dialog-actions .el-button { height: 32px; padding: 0 16px; }
</style>
