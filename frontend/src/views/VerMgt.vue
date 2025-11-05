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
                    v-model="searchTerm"
                    @keypress.enter="searchVersions"
                    class="form-control flex-fill" 
                    placeholder="输入应用名称搜索..."
                  >
                  <button class="btn btn-primary" @click="searchVersions" style="min-width: 80px; padding: 8px 16px;">
                    搜索
                  </button>
                </div>
              </div>
            </div>
            
            <!-- 版本列表 -->
            <div class="table-responsive">
              <table class="table table-striped table-hover">
                <thead class="table-dark">
                  <tr>
                    <th>应用编码</th>
                    <th>应用名称</th>
                    <th>版本号</th>
                    <th>更新时间</th>
                    <th style="width: 200px; text-align: center;">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="loading">
                    <td colspan="5" class="text-center text-muted">加载中...</td>
                  </tr>
                  <tr v-else-if="versionList.length === 0">
                    <td colspan="5" class="text-center text-muted">暂无数据</td>
                  </tr>
                  <tr v-else v-for="version in sortedVersionList" :key="version.appCode">
                    <td>{{ version.appCode }}</td>
                    <td><strong>{{ version.appName }}</strong></td>
                    <td><span class="badge bg-info">{{ version.version }}</span></td>
                    <td>{{ formatDateTime(version.updateTime) }}</td>
                    <td style="text-align: right;">
                      <div class="d-flex gap-1 justify-content-end">
                        <button 
                          v-if="version.status === '0'"
                          class="btn btn-sm btn-outline-info" 
                          @click="buildApp(version)"
                          title="构建"
                        >
                          构建
                        </button>
                        <button v-else class="btn btn-sm btn-outline-secondary" disabled title="当前状态不允许构建">
                          构建
                        </button>
                        <button 
                          v-if="version.status === '1'"
                          class="btn btn-sm btn-outline-warning" 
                          @click="stopApp(version)"
                          title="停止"
                        >
                          停止
                        </button>
                        <button v-else class="btn btn-sm btn-outline-secondary" disabled title="当前状态不允许停止">
                          停止
                        </button>
                        <button 
                          class="btn btn-sm btn-outline-secondary" 
                          @click="viewLogs(version)"
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

    <!-- 构建应用模态框 -->
    <div class="modal fade" id="buildModal" tabindex="-1" ref="buildModal">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">应用构建 - {{ currentVersion.appName }}</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
          </div>
          <div class="modal-body">
            <form>
              <div class="mb-3">
                <label class="form-label">当前版本号</label>
                <input type="text" class="form-control" :value="currentVersion.version" readonly>
              </div>
              <div class="mb-3">
                <label for="targetVersion" class="form-label">目标版本号</label>
                <input 
                  type="text" 
                  class="form-control" 
                  id="targetVersion"
                  v-model="buildForm.targetVersion"
                  placeholder="请输入目标版本号" 
                  required
                >
              </div>
            </form>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
            <button type="button" class="btn btn-primary" @click="confirmBuild">确定构建</button>
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
import { verBuildApi } from '../api'
import LogModal from '../components/LogModal.vue'
import { showAlert } from '../utils/alert'

export default {
  name: 'VerMgt',
  components: {
    LogModal
  },
  setup() {
    const searchTerm = ref('')
    const versionList = ref([])
    const loading = ref(false)
    const currentVersion = ref({})
    const buildForm = ref({
      targetVersion: ''
    })
    
    let autoRefreshInterval = null
    let buildModal = null

    // 排序后的版本列表
    const sortedVersionList = computed(() => {
      return [...versionList.value].sort((a, b) => {
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

    // 搜索版本
    const searchVersions = async () => {
      loading.value = true
      try {
        const response = await verBuildApi.search(searchTerm.value)
        versionList.value = response.data
      } catch (error) {
        showAlert('搜索失败: ' + error.message, 'danger')
      } finally {
        loading.value = false
      }
    }

    // 构建应用
    const buildApp = (version) => {
      currentVersion.value = version
      buildForm.value.targetVersion = version.version
      
      if (!buildModal) {
        buildModal = new Modal(document.getElementById('buildModal'))
      }
      buildModal.show()
    }

    // 确认构建
    const confirmBuild = async () => {
      if (!buildForm.value.targetVersion.trim()) {
        showAlert('请输入目标版本号', 'warning')
        return
      }

      try {
        await verBuildApi.build({
          appCode: currentVersion.value.appCode,
          appName: currentVersion.value.appName,
          targetVersion: buildForm.value.targetVersion
        })
        
        showAlert('构建任务已启动', 'success')
        buildModal.hide()
        setTimeout(() => { searchVersions() }, 1000)
      } catch (error) {
        showAlert('启动构建失败: ' + error.message, 'danger')
      }
    }

    // 停止应用
    const stopApp = async (version) => {
      try {
        await verBuildApi.stop({
          appCode: version.appCode
        })
        
        showAlert('停止操作已执行', 'success')
        searchVersions()
      } catch (error) {
        showAlert('停止操作失败: ' + error.message, 'danger')
      }
    }

    // 查看日志
    const logModal = ref(null)
    const viewLogs = (version) => {
      if (!version.logFile) {
        showAlert('该应用没有日志文件', 'warning')
        return
      }
      
      const fileName = version.logFile.split(/[/\\]/).pop()
      logModal.value.showLog(fileName)
    }

    // 格式化日期时间
    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '-'
      const date = new Date(dateTimeStr)
      return date.toLocaleString('zh-CN')
    }

    // 检查是否有模态框打开
    const hasModalOpen = () => {
      const openModals = document.querySelectorAll('.modal.show')
      return openModals.length > 0
    }

    // 启动自动刷新
    const startAutoRefresh = () => {
      autoRefreshInterval = setInterval(() => {
        if (!hasModalOpen()) {
          searchVersions()
        }
      }, 3000)
    }

    onMounted(() => {
      searchVersions()
      // startAutoRefresh()
    })

    onUnmounted(() => {
      if (autoRefreshInterval) {
        clearInterval(autoRefreshInterval)
      }
    })

    return {
      searchTerm,
      versionList,
      loading,
      currentVersion,
      buildForm,
      sortedVersionList,
      searchVersions,
      buildApp,
      confirmBuild,
      stopApp,
      viewLogs,
      formatDateTime,
      logModal
    }
  }
}
</script>

<style scoped>
.container-fluid.h-100 {
  height: calc(100vh - 76px);
}
</style>

