<template>
  <div class="page-container">
    <!-- 左侧应用列表 -->
    <div class="left-panel">
      <el-card class="app-card">
        <template #header>
          <div class="card-header">
            <span>应用列表</span>
            <el-button type="primary" size="small" @click="refreshAppList">刷新</el-button>
          </div>
        </template>
        <div class="app-list">
          <div 
            v-for="app in appList" 
            :key="app.appCode" 
            class="app-item"
            :class="{ 'is-selected': selectedAppCode === app.appCode }"
            @click="selectApp(app)"
          >
            <span class="app-name">{{ app.appCode }}</span>
            <span class="app-version">{{ app.version || '-' }}</span>
          </div>
          <el-empty v-if="appList.length === 0" description="暂无应用" />
        </div>
      </el-card>
      
      <!-- 缓冲区状态 -->
      <el-card class="buffer-card">
        <template #header>
          <span>缓冲区状态</span>
        </template>
        <div class="buffer-info">
          <p>状态: {{ bufferStatus.enabled ? '启用' : '禁用' }}</p>
          <p>当前条数: {{ bufferStatus.currentSize }} / {{ bufferStatus.maxSize }}</p>
          <p>刷新间隔: {{ bufferStatus.flushIntervalMinutes }} 分钟</p>
          <el-button type="warning" size="small" @click="flushBuffer">立即刷新到数据库</el-button>
        </div>
      </el-card>
    </div>

    <!-- 右侧日志显示 -->
    <div class="right-panel">
      <!-- 查询条件 -->
      <el-card class="query-card">
        <el-form :inline="true" size="small">
          <el-form-item label="日志级别">
            <el-select v-model="queryParams.logLevel" clearable placeholder="全部" style="width: 100px;">
              <el-option label="DEBUG" value="DEBUG" />
              <el-option label="INFO" value="INFO" />
              <el-option label="WARN" value="WARN" />
              <el-option label="ERROR" value="ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 340px;"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="queryLogs">查询</el-button>
            <el-button :type="autoRefresh ? 'warning' : 'success'" @click="toggleAutoRefresh">
              {{ autoRefresh ? '停止刷新' : '自动刷新' }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 日志内容 -->
      <el-card v-if="!selectedAppCode" class="log-card empty-card">
        <el-empty description="请选择左侧应用查看日志" />
      </el-card>
      <div v-else class="log-content-wrapper">
        <div class="log-header">
          <span>{{ selectedAppCode }} - 共 {{ logs.length }} 条日志</span>
          <el-button size="small" @click="scrollToBottom">到底部</el-button>
        </div>
        <div ref="logContainer" class="log-content-area">
          <div v-for="(log, index) in logs" :key="log.logId || index" class="log-line" :class="'log-' + (log.logLevel || 'info').toLowerCase()">
            <span class="log-time">{{ log.logTime }}</span>
            <span class="log-level">{{ log.logLevel }}</span>
            <span class="log-text">{{ log.logContent }}</span>
          </div>
          <el-empty v-if="logs.length === 0" description="暂无日志" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { appMgtApi, logApi } from '../api'
import { ElMessage } from 'element-plus'

export default {
  name: 'LogMgt',
  setup() {
    const appList = ref([])
    const selectedAppCode = ref('')
    const logs = ref([])
    const autoRefresh = ref(false)
    const logContainer = ref(null)
    const bufferStatus = ref({ enabled: false, currentSize: 0, maxSize: 3000, flushIntervalMinutes: 5 })
    const dateRange = ref(null)
    const queryParams = ref({ logLevel: '' })
    
    let refreshInterval = null
    let lastSeq = 0 // 记录上次读取的最后序号

    const refreshAppList = async () => {
      try {
        const res = await appMgtApi.getAppList()
        appList.value = res.data || []
      } catch (e) {
        ElMessage.error('获取应用列表失败')
      }
    }

    const selectApp = (app) => {
      selectedAppCode.value = app.appCode
      lastSeq = 0 // 切换应用时重置序号
      logs.value = []
      queryLogs()
    }

    // 全量查询（用于首次加载或手动查询）
    const queryLogs = async () => {
      if (!selectedAppCode.value) return
      try {
        const params = {
          appCode: selectedAppCode.value,
          logLevel: queryParams.value.logLevel || undefined,
          limit: 1000
        }
        if (dateRange.value && dateRange.value.length === 2) {
          params.startTime = dateRange.value[0]
          params.endTime = dateRange.value[1]
        }
        const res = await logApi.query(params)
        logs.value = (res.logs || []).reverse()
        lastSeq = res.currentSeq || 0 // 记录当前序号
        await nextTick()
        scrollToBottom()
      } catch (e) {
        ElMessage.error('查询日志失败: ' + e.message)
      }
    }

    // 增量查询（用于自动刷新，只获取新日志）
    const queryLogsIncremental = async () => {
      if (!selectedAppCode.value) return
      try {
        const res = await logApi.incremental(selectedAppCode.value, lastSeq, 1000)
        const newLogs = res.logs || []
        if (newLogs.length > 0) {
          logs.value = [...logs.value, ...newLogs]
          // 限制最大条数，防止内存溢出
          const maxSize = bufferStatus.value.maxSize || 5000
          if (logs.value.length > maxSize) {
            logs.value = logs.value.slice(-maxSize)
          }
          lastSeq = res.currentSeq || lastSeq
          await nextTick()
          scrollToBottom()
        }
        // 更新缓冲区状态
        bufferStatus.value.currentSize = res.bufferSize || bufferStatus.value.currentSize
      } catch (e) {
        console.error('增量查询日志失败:', e.message)
      }
    }

    const toggleAutoRefresh = () => {
      if (autoRefresh.value) {
        clearInterval(refreshInterval)
        refreshInterval = null
        autoRefresh.value = false
      } else {
        if (!selectedAppCode.value) {
          ElMessage.warning('请先选择应用')
          return
        }
        // 首次加载全量，之后增量刷新
        queryLogs()
        refreshInterval = setInterval(queryLogsIncremental, 3000)
        autoRefresh.value = true
      }
    }

    const scrollToBottom = () => {
      if (logContainer.value) {
        nextTick(() => { logContainer.value.scrollTop = logContainer.value.scrollHeight })
      }
    }

    const loadBufferStatus = async () => {
      try {
        const res = await logApi.bufferStatus()
        bufferStatus.value = res
      } catch (e) { /* ignore */ }
    }

    const flushBuffer = async () => {
      try {
        await logApi.flushBuffer()
        ElMessage.success('缓冲区已刷新')
        loadBufferStatus()
        if (selectedAppCode.value) queryLogs()
      } catch (e) {
        ElMessage.error('刷新失败')
      }
    }

    onMounted(() => {
      refreshAppList()
      loadBufferStatus()
    })

    onUnmounted(() => {
      if (refreshInterval) clearInterval(refreshInterval)
    })

    return {
      appList, selectedAppCode, logs, autoRefresh, logContainer, bufferStatus,
      dateRange, queryParams,
      refreshAppList, selectApp, queryLogs, toggleAutoRefresh, scrollToBottom, flushBuffer
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
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.right-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.app-card, .buffer-card {
  border-radius: 8px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.app-list {
  max-height: 400px;
  overflow-y: auto;
}
.app-item {
  display: flex;
  justify-content: space-between;
  padding: 10px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}
.app-item:hover { background-color: #f5f7fa; }
.app-item.is-selected { background-color: #ecf5ff; }
.app-name { font-weight: 500; }
.app-version { color: #909399; font-size: 12px; }
.buffer-info p { margin: 5px 0; font-size: 13px; color: #606266; }
.query-card { flex-shrink: 0; border-radius: 8px; }
.log-card.empty-card {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}
.log-content-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  overflow: hidden;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 15px;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}
.log-content-area {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
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
</style>
