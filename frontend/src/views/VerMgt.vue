<template>
  <div class="page-container">
    <el-card>
      <!-- 搜索区域 -->
      <el-row :gutter="20" style="margin-bottom: 15px;">
        <el-col :span="16">
          <el-input
            v-model="searchTerm"
            placeholder="输入应用名称搜索..."
            @keypress.enter="searchVersions"
            clearable
            size="large"
          >
            <template #prepend>搜索应用</template>
            <template #append>
              <el-button @click="searchVersions">搜索</el-button>
            </template>
          </el-input>
        </el-col>
        <el-col :span="8" style="text-align: right;">
          <el-button type="primary" size="large" @click="addVersion">新增</el-button>
        </el-col>
      </el-row>

      <!-- 版本列表 -->
      <el-table :data="sortedVersionList" v-loading="loading" stripe border size="large">
        <el-table-column prop="appCode" label="应用编码" width="220" />
        <el-table-column prop="appName" label="应用名称" min-width="250">
          <template #default="{ row }">
            <strong>{{ row.appName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本号" width="180">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="365" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" :disabled="row.status !== '0'" @click="buildApp(row)">构建</el-button>
              <el-button type="danger" size="small" :disabled="row.status !== '1'" @click="stopApp(row)">停止</el-button>
              <el-button size="small" @click="viewLogs(row)">日志</el-button>
              <el-button type="warning" size="small" @click="editVersion(row)">修改</el-button>
              <el-button type="danger" size="small" @click="deleteVersion(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 构建应用对话框 -->
    <el-dialog v-model="buildDialogVisible" :title="'应用构建 - ' + currentVersion.appName" width="450px">
      <el-form label-width="100px">
        <el-form-item label="当前版本号">
          <el-input :model-value="currentVersion.version" readonly />
        </el-form-item>
        <el-form-item label="目标版本号">
          <el-input v-model="buildForm.targetVersion" placeholder="请输入目标版本号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="buildDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBuild">确定构建</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑版本对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑版本' : '新增版本'" width="800px">
      <el-form :model="editForm" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="应用编码" required>
              <el-input v-model="editForm.appCode" :disabled="isEdit" placeholder="请输入应用编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用名称">
              <el-input v-model="editForm.appName" placeholder="请输入应用名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="版本号">
          <el-input v-model="editForm.version" placeholder="请输入版本号" />
        </el-form-item>
        <el-form-item label="Windows脚本">
          <el-input v-model="editForm.scriptCmd" type="textarea" :rows="8" placeholder="Windows构建脚本内容（.bat/.cmd）" style="font-family: monospace;" />
        </el-form-item>
        <el-form-item label="Linux脚本">
          <el-input v-model="editForm.scriptSh" type="textarea" :rows="8" placeholder="Linux构建脚本内容（.sh）" style="font-family: monospace;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveVersion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 日志模态框 -->
    <LogModal ref="logModal" />
  </div>
</template>

<script>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { verBuildApi } from '../api'
import LogModal from '../components/LogModal.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'VerMgt',
  components: { LogModal },
  setup() {
    const searchTerm = ref('')
    const versionList = ref([])
    const loading = ref(false)
    const currentVersion = ref({})
    const buildDialogVisible = ref(false)
    const buildForm = ref({ targetVersion: '' })
    const editDialogVisible = ref(false)
    const isEdit = ref(false)
    const editForm = ref({ appCode: '', appName: '', version: '', scriptCmd: '', scriptSh: '' })
    const logModal = ref(null)
    
    let autoRefreshInterval = null

    const sortedVersionList = computed(() => {
      return [...versionList.value].sort((a, b) => {
        const aIsNumber = !isNaN(a.appCode) && !isNaN(parseInt(a.appCode))
        const bIsNumber = !isNaN(b.appCode) && !isNaN(parseInt(b.appCode))
        if (aIsNumber && bIsNumber) return parseInt(a.appCode) - parseInt(b.appCode)
        if (aIsNumber && !bIsNumber) return -1
        if (!aIsNumber && bIsNumber) return 1
        return a.appCode.localeCompare(b.appCode)
      })
    })

    const searchVersions = async () => {
      loading.value = true
      try {
        const response = await verBuildApi.search(searchTerm.value)
        versionList.value = response.data
      } catch (error) {
        ElMessage.error('搜索失败: ' + error.message)
      } finally {
        loading.value = false
      }
    }

    const buildApp = (version) => {
      currentVersion.value = version
      buildForm.value.targetVersion = version.version
      buildDialogVisible.value = true
    }

    const confirmBuild = async () => {
      if (!buildForm.value.targetVersion.trim()) {
        ElMessage.warning('请输入目标版本号')
        return
      }
      try {
        await verBuildApi.build({
          appCode: currentVersion.value.appCode,
          appName: currentVersion.value.appName,
          targetVersion: buildForm.value.targetVersion
        })
        ElMessage.success('构建任务已启动')
        buildDialogVisible.value = false
        setTimeout(() => searchVersions(), 1000)
      } catch (error) {
        ElMessage.error('启动构建失败: ' + error.message)
      }
    }

    const stopApp = async (version) => {
      try {
        await verBuildApi.stop({ appCode: version.appCode })
        ElMessage.success('停止操作已执行')
        searchVersions()
      } catch (error) {
        ElMessage.error('停止操作失败: ' + error.message)
      }
    }

    const viewLogs = (version) => {
      if (!version.logFile) {
        ElMessage.warning('该应用没有日志文件')
        return
      }
      const fileName = version.logFile.split(/[/\\]/).pop()
      logModal.value.showLog(fileName)
    }

    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '-'
      return new Date(dateTimeStr).toLocaleString('zh-CN')
    }

    const addVersion = () => {
      isEdit.value = false
      editForm.value = { appCode: '', appName: '', version: '', scriptCmd: '', scriptSh: '' }
      editDialogVisible.value = true
    }

    const editVersion = (row) => {
      isEdit.value = true
      editForm.value = { 
        appCode: row.appCode, 
        appName: row.appName, 
        version: row.version, 
        scriptCmd: row.scriptCmd || '', 
        scriptSh: row.scriptSh || '' 
      }
      editDialogVisible.value = true
    }

    const saveVersion = async () => {
      if (!editForm.value.appCode.trim()) {
        ElMessage.warning('请输入应用编码')
        return
      }
      try {
        await verBuildApi.saveVersion(editForm.value)
        ElMessage.success('保存成功')
        editDialogVisible.value = false
        searchVersions()
      } catch (error) {
        ElMessage.error('保存失败: ' + error.message)
      }
    }

    const deleteVersion = async (row) => {
      try {
        await ElMessageBox.confirm(
          `确定要删除应用 "${row.appName || row.appCode}" 吗？`,
          '删除确认',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        await verBuildApi.deleteVersion({ appCode: row.appCode })
        ElMessage.success('删除成功')
        searchVersions()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败: ' + error.message)
        }
      }
    }

    const hasModalOpen = () => document.querySelectorAll('.el-dialog__wrapper:not([style*="display: none"])').length > 0

    const startAutoRefresh = () => {
      autoRefreshInterval = setInterval(() => {
        if (!hasModalOpen()) searchVersions()
      }, 15000)
    }

    onMounted(() => {
      searchVersions()
      startAutoRefresh()
    })

    onUnmounted(() => {
      if (autoRefreshInterval) clearInterval(autoRefreshInterval)
    })

    return {
      searchTerm, versionList, loading, currentVersion, buildForm,
      buildDialogVisible, sortedVersionList, editDialogVisible, isEdit, editForm,
      searchVersions, buildApp, confirmBuild, stopApp, viewLogs, formatDateTime, 
      addVersion, editVersion, saveVersion, deleteVersion, logModal
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
</style>
