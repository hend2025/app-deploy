<template>
  <div class="container-fluid h-100" style="padding: 15px;">
    <div class="row h-100">
      <div class="col-12 d-flex flex-column">
        <!-- 搜索和操作区域 -->
        <div class="card mb-3">
          <div class="card-body">
            <div class="row align-items-center mb-2">
              <div class="col-md-9">
                <div class="d-flex align-items-center gap-2">
                  <label class="form-label mb-0" style="min-width: 80px;">搜索应用</label>
                  <input 
                    type="text" 
                    v-model="searchText" 
                    @keypress.enter="searchApps"
                    class="form-control flex-fill" 
                    placeholder="输入应用名称搜索..."
                  >
                  <button class="btn btn-primary" @click="searchApps" style="min-width: 80px; padding: 8px 16px;">
                    搜索
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 应用列表 -->
            <div class="table-responsive">
              <table class="table table-striped table-hover">
                <thead class="table-dark">
                  <tr>
                    <th>应用名称</th>
                    <th>版本号</th>
                    <th>状态</th>
                    <th>更新时间</th>
                    <th style="width: 200px; text-align: center;">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="loading">
                    <td colspan="5" class="text-center text-muted">加载中...</td>
                  </tr>
                  <tr v-else-if="appList.length === 0">
                    <td colspan="5" class="text-center text-muted">暂无数据</td>
                  </tr>
                  <tr v-else v-for="app in sortedAppList" :key="app.appCode">
                    <td><strong>{{ app.appCode }}</strong></td>
                    <td><span class="badge bg-info">{{ app.version || '-' }}</span></td>
                    <td><span :class="getStatusClass(app.status)">{{ getStatusText(app.status) }}</span></td>
                    <td>{{ formatDateTime(app.updateTime) }}</td>
                    <td style="text-align: right;">
                      <div class="d-flex gap-1 justify-content-end">
                        <button 
                          v-if="!app.pid"
                          class="btn btn-sm btn-outline-success" 
                          @click="startApp(app)"
                          title="启动"
                        >
                          启动
                        </button>
                        <button v-else class="btn btn-sm btn-outline-secondary" disabled title="进程已运行">
                          启动
                        </button>
                        <button 
                          v-if="app.pid"
                          class="btn btn-sm btn-outline-warning" 
                          @click="stopApp(app)"
                          title="停止进程"
                        >
                          停止
                        </button>
                        <button v-else class="btn btn-sm btn-outline-secondary" disabled title="进程未运行">
                          停止
                        </button>
                        <button 
                          class="btn btn-sm btn-outline-info" 
                          @click="viewLogs(app)"
                          title="日志"
                        >
                          日志
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 启动应用模态框 -->
    <div class="modal fade" id="startModal" tabindex="-1" ref="startModal">
      <div class="modal-dialog modal-dialog-centered modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">启动应用</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <form>
              <div class="mb-3">
                <div class="row align-items-end">
                  <div class="col-md-6">
                    <div class="d-flex align-items-center gap-2">
                      <label class="form-label mb-0" style="min-width: 80px;">应用编码</label>
                      <input type="text" class="form-control" :value="currentApp.appCode" readonly>
                    </div>
                  </div>
                  <div class="col-md-6">
                    <div class="d-flex align-items-center gap-2">
                      <label class="form-label mb-0" style="min-width: 60px;">版本号</label>
                      <input type="text" class="form-control" v-model="startForm.version">
                    </div>
                  </div>
                </div>
              </div>
              <div class="mb-3">
                <label for="params" class="form-label">启动参数</label>
                <textarea 
                  class="form-control" 
                  v-model="startForm.params"
                  rows="10" 
                  style="font-family: monospace; font-size: 14px;"
                ></textarea>
                <div class="form-text">支持多行输入，每行一个参数</div>
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
            <button type="button" class="btn btn-success" @click="confirmStart">确定启动</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 停止应用模态框 -->
    <div class="modal fade" id="stopModal" tabindex="-1" ref="stopModal">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header bg-warning text-dark">
            <h5 class="modal-title">确认停止应用</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <div class="alert alert-warning mb-3">
              <i class="bi bi-exclamation-circle"></i> 此操作将强制终止应用进程
            </div>
            <div class="mb-3">
              <label class="form-label fw-bold">应用名称</label>
              <div class="form-control-plaintext border rounded p-2 bg-light">
                {{ currentApp.appCode }}
              </div>
            </div>
            <div class="mb-3">
              <label class="form-label fw-bold">进程ID</label>
              <div class="form-control-plaintext border rounded p-2 bg-light">
                <code class="text-danger">{{ currentApp.pid }}</code>
              </div>
            </div>
            <p class="text-muted mb-0">
              <small>注意：停止后应用将无法继续运行，请确认后再执行此操作。</small>
            </p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
            <button type="button" class="btn btn-danger" @click="confirmStop">确定停止</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 日志模态框 -->
    <LogModal ref="logModal" />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Modal } from 'bootstrap'
import { appMgtApi } from '../api'
import LogModal from '../components/LogModal.vue'
import { showAlert } from '../utils/alert'

export default {
  name: 'AppMgt',
  components: {
    LogModal
  },
  setup() {
    const searchText = ref('')
    const appList = ref([])
    const loading = ref(false)
    const currentApp = ref({})
    const startForm = ref({
      version: '',
      params: ''
    })
    
    let autoRefreshInterval = null
    let startModal = null
    let stopModal = null

    const defaultParams = `-Xmx1048m
-Xms512m
-Dfile.encoding=utf-8
-Dsun.jnu.encoding=UTF-8
-Ddubbo.registry.address=zookeeper://127.0.0.1:2181
-Ddubbo.metadata-report.address=zookeeper://127.0.0.1:2181
-Dnacos_host=192.168.10.147:8848
-Dspring.cloud.nacos.config.namespace=hsa-ims-scen-hend
-Dlogging.level.root=info`

    // 排序后的应用列表
    const sortedAppList = computed(() => {
      return [...appList.value].sort((a, b) => {
        const aIsNumber = !isNaN(a.appCode) && !isNaN(parseInt(a.appCode))
        const bIsNumber = !isNaN(b.appCode) && !isNaN(parseInt(b.appCode))
        
        if (aIsNumber && bIsNumber) {
          return parseInt(a.appCode) - parseInt(b.appCode)
        } else if (aIsNumber && !bIsNumber) {
          return -1
        } else if (!aIsNumber && bIsNumber) {
          return 1
        } else {
          return a.appCode.localeCompare(b.appCode)
        }
      })
    })

    // 搜索应用
    const searchApps = async () => {
      loading.value = true
      try {
        const response = await appMgtApi.getAppList(searchText.value)
        if (response.success) {
          appList.value = response.data
        } else {
          showAlert('搜索失败: ' + response.message, 'danger')
        }
      } catch (error) {
        showAlert('搜索失败: ' + error.message, 'danger')
      } finally {
        loading.value = false
      }
    }

    // 启动应用
    const startApp = (app) => {
      currentApp.value = app
      startForm.value.version = app.version || '-'
      startForm.value.params = app.params || defaultParams
      
      if (!startModal) {
        startModal = new Modal(document.getElementById('startModal'))
      }
      startModal.show()
    }

    // 确认启动
    const confirmStart = async () => {
      try {
        const response = await appMgtApi.startApp({
          appCode: currentApp.value.appCode,
          version: startForm.value.version,
          params: startForm.value.params
        })
        
        if (response.success) {
          showAlert(`应用启动成功: ${currentApp.value.appCode}`, 'success')
          startModal.hide()
          searchApps()
        } else {
          showAlert('启动应用失败: ' + response.message, 'danger')
        }
      } catch (error) {
        showAlert('启动应用失败: ' + error.message, 'danger')
      }
    }

    // 停止应用
    const stopApp = (app) => {
      currentApp.value = app
      
      if (!stopModal) {
        stopModal = new Modal(document.getElementById('stopModal'))
      }
      stopModal.show()
    }

    // 确认停止
    const confirmStop = async () => {
      try {
        const response = await appMgtApi.stopApp({
          appCode: currentApp.value.appCode,
          pid: currentApp.value.pid
        })
        
        if (response.success) {
          showAlert('应用已停止', 'success')
          stopModal.hide()
          searchApps()
        } else {
          showAlert('停止应用失败: ' + response.message, 'danger')
        }
      } catch (error) {
        showAlert('停止应用失败: ' + error.message, 'danger')
      }
    }

    // 查看日志
    const logModal = ref(null)
    const viewLogs = (app) => {
      if (!app.logFile) {
        showAlert('该应用暂无日志文件', 'warning')
        return
      }
      
      const fileName = app.logFile.split(/[/\\]/).pop()
      logModal.value.showLog(fileName)
    }

    // 获取状态样式
    const getStatusClass = (status) => {
      const classes = {
        '1': 'badge bg-primary',
        '2': 'badge bg-success'
      }
      return classes[status] || 'badge bg-secondary'
    }

    // 获取状态文本
    const getStatusText = (status) => {
      const texts = {
        '1': '就绪',
        '2': '运行'
      }
      return texts[status] || '未知'
    }

    // 格式化日期时间
    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '-'
      const date = new Date(dateTimeStr)
      return date.toLocaleString('zh-CN')
    }

    // 启动自动刷新
    const startAutoRefresh = () => {
      autoRefreshInterval = setInterval(() => {
        searchApps()
      }, 15000)
    }

    onMounted(() => {
      searchApps()
      startAutoRefresh()
    })

    onUnmounted(() => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
      }
    })

    return {
      searchText,
      appList,
      loading,
      currentApp,
      startForm,
      sortedAppList,
      searchApps,
      startApp,
      confirmStart,
      stopApp,
      confirmStop,
      viewLogs,
      getStatusClass,
      getStatusText,
      formatDateTime,
      logModal
    }
  }
}
</script>

<style scoped>
html, body {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  display: flex;
  flex-direction: column;
}

.container-fluid.h-100 {
  height: calc(100vh - 76px);
}
</style>

