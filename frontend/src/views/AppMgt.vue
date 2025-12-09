<template>
  <div class="page-container">
    <el-card>
      <!-- 搜索区域 -->
      <el-row :gutter="20" style="margin-bottom: 15px;">
        <el-col :span="12">
          <el-input
            v-model="searchText"
            placeholder="输入应用名称搜索..."
            @keypress.enter="searchApps"
            clearable
            size="large"
          >
            <template #prepend>搜索应用</template>
            <template #append>
              <el-button @click="searchApps">搜索</el-button>
            </template>
          </el-input>
        </el-col>
        <el-col :span="12" style="text-align: right;">
          <el-button type="primary" size="large" @click="addApp">新增</el-button>
          <el-button type="warning" size="large" :disabled="!selectedRow" @click="editApp(selectedRow)">修改</el-button>
          <el-button type="danger" size="large" :disabled="!selectedRow" @click="deleteApp(selectedRow)">删除</el-button>
        </el-col>
      </el-row>

      <!-- 应用列表 -->
      <el-table 
        :data="sortedAppList" 
        v-loading="loading" 
        stripe 
        border 
        size="large"
        highlight-current-row
        @current-change="handleCurrentChange"
      >
        <el-table-column type="index" width="70" label="序号" align="center" />
        <el-table-column prop="appCode" label="应用名称">
          <template #default="{ row }">
            <strong>{{ row.appCode }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本号">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.version || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '2' ? 'success' : ''">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="168" align="center">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" :disabled="!!row.pid" @click="startApp(row)">启动</el-button>
              <el-button type="danger" size="small" :disabled="!row.pid" @click="stopApp(row)">停止</el-button>
              <el-button size="small" @click="viewLogs(row)">日志</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 启动应用对话框 -->
    <el-dialog v-model="startDialogVisible" title="启动应用" width="700px">
      <el-form label-width="80px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="应用编码">
              <el-input :model-value="currentApp.appCode" readonly />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号">
              <el-input v-model="startForm.version" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启动参数">
          <el-input
            v-model="startForm.params"
            type="textarea"
            :rows="10"
            style="font-family: monospace;"
          />
          <div class="el-form-item__help">支持多行输入，每行一个参数</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="success" @click="confirmStart">确定启动</el-button>
      </template>
    </el-dialog>

    <!-- 停止应用对话框 -->
    <el-dialog v-model="stopDialogVisible" title="确认停止应用" width="450px">
      <el-alert type="warning" :closable="false" style="margin-bottom: 15px;">
        <template #title>此操作将强制终止应用进程</template>
      </el-alert>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="应用名称">{{ currentApp.appCode }}</el-descriptions-item>
        <el-descriptions-item label="进程ID">
          <el-tag type="danger">{{ currentApp.pid }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <p style="color: #909399; font-size: 12px; margin-top: 15px;">
        注意：停止后应用将无法继续运行，请确认后再执行此操作。
      </p>
      <template #footer>
        <el-button @click="stopDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmStop">确定停止</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑应用对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑应用' : '新增应用'" width="700px">
      <el-form :model="editForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="应用编码" required>
              <el-input v-model="editForm.appCode" :disabled="isEdit" placeholder="请输入应用编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="版本号">
              <el-input v-model="editForm.version" placeholder="请输入版本号" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="启动参数">
          <el-input v-model="editForm.params" type="textarea" :rows="10" placeholder="启动参数，每行一个" style="font-family: monospace;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveApp">保存</el-button>
      </template>
    </el-dialog>

    <!-- 日志模态框 -->
    <LogModal ref="logModal" />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { appMgtApi } from '../api'
import LogModal from '../components/LogModal.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'AppMgt',
  components: { LogModal },
  setup() {
    const searchText = ref('')
    const appList = ref([])
    const loading = ref(false)
    const currentApp = ref({})
    const startDialogVisible = ref(false)
    const stopDialogVisible = ref(false)
    const startForm = ref({ version: '', params: '' })
    const editDialogVisible = ref(false)
    const isEdit = ref(false)
    const editForm = ref({ appCode: '', version: '', params: '' })
    const logModal = ref(null)
    const selectedRow = ref(null)

    const handleCurrentChange = (row) => {
      selectedRow.value = row
    }

    const defaultParams = `-Xmx1048m
-Xms512m
-Dfile.encoding=utf-8
-Dsun.jnu.encoding=UTF-8
-Ddubbo.registry.address=zookeeper://127.0.0.1:2181
-Ddubbo.metadata-report.address=zookeeper://127.0.0.1:2181
-Dnacos_host=192.168.10.147:8848
-Dspring.cloud.nacos.config.namespace=hsa-ims-scen-hend
-Dlogging.level.root=info`

    const sortedAppList = computed(() => {
      return [...appList.value].sort((a, b) => {
        const aIsNumber = !isNaN(a.appCode) && !isNaN(parseInt(a.appCode))
        const bIsNumber = !isNaN(b.appCode) && !isNaN(parseInt(b.appCode))
        if (aIsNumber && bIsNumber) return parseInt(a.appCode) - parseInt(b.appCode)
        if (aIsNumber && !bIsNumber) return -1
        if (!aIsNumber && bIsNumber) return 1
        return a.appCode.localeCompare(b.appCode)
      })
    })

    const searchApps = async (showLoading = true) => {
      if (showLoading) loading.value = true
      try {
        const response = await appMgtApi.getAppList(searchText.value)
        appList.value = response.data
      } catch (error) {
        ElMessage.error('搜索失败: ' + error.message)
      } finally {
        if (showLoading) loading.value = false
      }
    }

    const startApp = (app) => {
      currentApp.value = app
      startForm.value.version = app.version || '-'
      startForm.value.params = app.params || defaultParams
      startDialogVisible.value = true
    }

    const confirmStart = async () => {
      try {
        await appMgtApi.startApp({
          appCode: currentApp.value.appCode,
          version: startForm.value.version,
          params: startForm.value.params
        })
        ElMessage.success(`应用启动成功: ${currentApp.value.appCode}`)
        startDialogVisible.value = false
        setTimeout(() => searchApps(false), 1000)
      } catch (error) {
        ElMessage.error('启动应用失败: ' + error.message)
      }
    }

    const stopApp = (app) => {
      currentApp.value = app
      stopDialogVisible.value = true
    }

    const confirmStop = async () => {
      try {
        await appMgtApi.stopApp({
          appCode: currentApp.value.appCode,
          pid: currentApp.value.pid
        })
        ElMessage.success('应用已停止')
        stopDialogVisible.value = false
        searchApps(false)
      } catch (error) {
        ElMessage.error('停止应用失败: ' + error.message)
      }
    }

    const viewLogs = (app) => {
      logModal.value.showLog(app.appCode)
    }

    const getStatusText = (status) => {
      const texts = { '1': '就绪', '2': '运行' }
      return texts[status] || '未知'
    }

    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '-'
      return new Date(dateTimeStr).toLocaleString('zh-CN')
    }

    const addApp = () => {
      isEdit.value = false
      editForm.value = { appCode: '', version: '', params: defaultParams }
      editDialogVisible.value = true
    }

    const editApp = (row) => {
      if (!row) {
        ElMessage.warning('请先选择要修改的记录')
        return
      }
      isEdit.value = true
      editForm.value = { 
        appCode: row.appCode, 
        version: row.version || '', 
        params: row.params || defaultParams
      }
      editDialogVisible.value = true
    }

    const saveApp = async () => {
      if (!editForm.value.appCode.trim()) {
        ElMessage.warning('请输入应用编码')
        return
      }
      try {
        await appMgtApi.saveApp(editForm.value)
        ElMessage.success('保存成功')
        editDialogVisible.value = false
        searchApps()
      } catch (error) {
        ElMessage.error('保存失败: ' + error.message)
      }
    }

    const deleteApp = async (row) => {
      if (!row) {
        ElMessage.warning('请先选择要删除的记录')
        return
      }
      try {
        await ElMessageBox.confirm(
          `确定要删除应用 "${row.appCode}" 吗？`,
          '删除确认',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        await appMgtApi.deleteApp({ appCode: row.appCode })
        ElMessage.success('删除成功')
        selectedRow.value = null
        searchApps()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败: ' + error.message)
        }
      }
    }

    onMounted(() => { searchApps() })
    onUnmounted(() => { })

    return {
      searchText, appList, loading, currentApp, startForm,
      startDialogVisible, stopDialogVisible, sortedAppList,
      editDialogVisible, isEdit, editForm, selectedRow, handleCurrentChange,
      searchApps, startApp, confirmStart, stopApp, confirmStop,
      viewLogs, getStatusText, formatDateTime, addApp, editApp, saveApp, deleteApp, logModal
    }
  }
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  height: calc(100vh - 61px);
  overflow-y: auto;
  background-color: #f5f7fa;
}
:deep(.el-card) {
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}
:deep(.el-table) {
  font-size: 14px;
}
:deep(.el-table th.el-table__cell) {
  background-color: #f5f7fa;
  color: #303133;
  font-weight: 600;
}
:deep(.el-table .cell) {
  line-height: 24px;
}
.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}
.action-buttons .el-button {
  margin: 0;
  min-width: 60px;
}
:deep(.el-table__body tr.current-row > td.el-table__cell) {
  background-color: #d9ecff !important;
}
:deep(.el-table__body tr.current-row) {
  font-weight: 500;
}
</style>
