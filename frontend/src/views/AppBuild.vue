<!--
  应用构建页面
  功能：
  - 版本配置的增删改查
  - 启动/停止构建任务
  - 查看构建日志
-->
<template>
  <div class="page-container">
    <el-card>
      <!-- 搜索和操作按钮区域 -->
      <el-row :gutter="20" style="margin-bottom: 15px;">
        <el-col :span="12">
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
        <el-col :span="12" style="text-align: right;">
          <el-button type="primary" size="large" @click="addVersion">新增</el-button>
          <el-button type="warning" size="large" :disabled="!selectedRow" @click="editVersion(selectedRow)">修改</el-button>
          <el-button type="danger" size="large" :disabled="!selectedRow" @click="deleteVersion(selectedRow)">删除</el-button>
        </el-col>
      </el-row>

      <!-- 版本列表 -->
      <el-table 
        :data="sortedVersionList" 
        v-loading="loading" 
        stripe 
        border 
        size="large"
        highlight-current-row
        @current-change="handleCurrentChange"
      >
        <el-table-column type="index" width="70" label="序号" align="center" />
        <el-table-column prop="appCode" label="应用编码">
          <template #default="{ row }">
            <strong>{{ row.appCode }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="appName" label="应用名称">
          <template #default="{ row }">
            <strong>{{ row.appName }}</strong>
          </template>
        </el-table-column>
        <el-table-column prop="appType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.appType === '1'" type="success" size="small">Java</el-tag>
            <el-tag v-else-if="row.appType === '2'" type="warning" size="small">Vue</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本号">
          <template #default="{ row }">
            <el-tag type="primary">{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="168" align="center">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" :disabled="row.status !== '0'" @click="buildApp(row)">构建</el-button>
              <el-button type="danger" size="small" :loading="stoppingApps[row.appCode]" :disabled="row.status !== '1'" @click="stopApp(row)">停止</el-button>
              <el-button size="small" @click="viewLogs(row)">日志</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 构建应用对话框 -->
    <el-dialog v-model="buildDialogVisible" :title="'应用构建 - ' + currentVersion.appName" width="450px">
      <el-form label-width="100px">
        <el-form-item label="当前版本">
          <el-input :model-value="currentVersion.version" readonly />
        </el-form-item>
        <el-form-item label="分支/Tag" required>
          <el-input v-model="buildForm.branchOrTag" placeholder="请输入分支名或Tag，如：master, v1.0.0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="buildingApps[currentVersion.appCode]" @click="buildDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="buildingApps[currentVersion.appCode]" @click="confirmBuild">确定构建</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑版本对话框 -->
    <el-dialog v-model="editDialogVisible" :title="isEdit ? '编辑版本' : '新增版本'" width="1200px" align-center>
      <el-form :model="editForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="11">
            <el-form-item label="应用编码" required>
              <el-input v-model="editForm.appCode" :disabled="isEdit" placeholder="请输入应用编码" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="应用名称">
              <el-input v-model="editForm.appName" placeholder="请输入应用名称" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="应用类型">
              <el-select v-model="editForm.appType" placeholder="请选择应用类型" style="width: 100%;">
                <el-option label="Java" value="1" />
                <el-option label="Vue" value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="11">
            <el-form-item label="Git地址">
              <el-input v-model="editForm.gitUrl" placeholder="请输入Git仓库地址" />
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="Git账号">
              <el-input v-model="editForm.gitAcct" placeholder="请输入Git账号" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="Git密码">
              <el-input v-model="editForm.gitPwd" type="password" show-password placeholder="请输入Git密码" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="归档文件">
              <el-input v-model="editForm.archiveFiles" placeholder="如：target/*.jar,dist/" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="构建脚本">
          <el-input v-model="editForm.buildScript" type="textarea" :rows="15" placeholder="构建脚本内容，支持变量：$BRANCH_OR_TAG, $APP_CODE, $WORKSPACE_DIR" style="font-family: monospace;" />
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
/**
 * 应用构建页面组件
 * 提供版本配置管理和构建任务控制功能
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { verBuildApi, logApi } from '../api'
import LogModal from '../components/LogModal.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'AppDeploy',
  components: { LogModal },
  setup() {
    // ========== 响应式状态 ==========
    const searchTerm = ref('')             // 搜索关键词
    const versionList = ref([])            // 版本列表数据
    const loading = ref(false)             // 加载状态
    const currentVersion = ref({})         // 当前操作的版本
    const buildDialogVisible = ref(false)  // 构建对话框显示状态
    const buildForm = ref({ branchOrTag: '' }) // 构建表单
    const editDialogVisible = ref(false)   // 编辑对话框显示状态
    const isEdit = ref(false)              // 是否为编辑模式
    const editForm = ref({                 // 编辑表单
      appCode: '', appName: '', appType: '', 
      gitUrl: '', gitAcct: '', gitPwd: '', 
      buildScript: '', archiveFiles: ''
    })
    const logModal = ref(null)             // 日志模态框引用
    const selectedRow = ref(null)          // 当前选中的行
    const buildingApps = ref({})           // 正在构建的应用（按钮loading状态）
    const stoppingApps = ref({})           // 正在停止的应用（按钮loading状态）

    /**
     * 表格行选中事件处理
     */
    const handleCurrentChange = (row) => {
      selectedRow.value = row
    }

    /**
     * 排序比较函数（提取为独立函数避免重复创建）
     */
    const sortByAppCode = (a, b) => {
      const aIsNumber = !isNaN(a.appCode) && !isNaN(parseInt(a.appCode))
      const bIsNumber = !isNaN(b.appCode) && !isNaN(parseInt(b.appCode))
      if (aIsNumber && bIsNumber) return parseInt(a.appCode) - parseInt(b.appCode)
      if (aIsNumber && !bIsNumber) return -1
      if (!aIsNumber && bIsNumber) return 1
      return a.appCode.localeCompare(b.appCode)
    }

    /**
     * 计算属性：排序后的版本列表
     * 数字编码优先，按数值排序；字符串编码按字母排序
     */
    const sortedVersionList = computed(() => {
      return [...versionList.value].sort(sortByAppCode)
    })

    /**
     * 搜索版本列表
     */
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

    /**
     * 打开构建对话框
     */
    const buildApp = (version) => {
      currentVersion.value = version
      buildForm.value.branchOrTag = version.version || 'master'
      buildDialogVisible.value = true
    }

    /**
     * 确认启动构建
     */
    const confirmBuild = async () => {
      if (!buildForm.value.branchOrTag.trim()) {
        ElMessage.warning('请输入分支名或Tag')
        return
      }
      const appCode = currentVersion.value.appCode
      buildingApps.value[appCode] = true
      
      try {
        await verBuildApi.build({
          appCode: appCode,
          appName: currentVersion.value.appName,
          branchOrTag: buildForm.value.branchOrTag
        })
        ElMessage.success('构建任务已启动')
        buildDialogVisible.value = false

        // 立即更新当前行的状态为构建中
        const targetRow = versionList.value.find(v => v.appCode === currentVersion.value.appCode)
        if (targetRow) {
          targetRow.status = '1'  // 构建中
        }

        // 延迟刷新列表获取最新状态
        setTimeout(() => searchVersions(), 5000)
      } catch (error) {
        ElMessage.error('启动构建失败: ' + error.message)
      } finally {
        buildingApps.value[appCode] = false
        // 刷新列表获取最新状态
        setTimeout(() => searchVersions(), 200)
      }
    }

    /**
     * 停止构建任务
     */
    const stopApp = async (version) => {
      try {
        await ElMessageBox.confirm(
          `确定要停止应用 "${version.appName || version.appCode}" 的构建吗？`,
          '停止确认',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        const appCode = version.appCode
        stoppingApps.value[appCode] = true
        try {
          await verBuildApi.stop({ appCode })
          ElMessage.success('停止操作已执行，日志已保存')
        } finally {
          stoppingApps.value[appCode] = false
          searchVersions()
        }
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('停止操作失败: ' + error.message)
        }
      }
    }

    /**
     * 查看构建日志
     */
    const viewLogs = (version) => {
      logModal.value.showLog(version.appCode)
    }

    /**
     * 格式化日期时间
     */
    const formatDateTime = (dateTimeStr) => {
      if (!dateTimeStr) return '-'
      return new Date(dateTimeStr).toLocaleString('zh-CN')
    }

    /**
     * 打开新增版本对话框
     */
    const addVersion = () => {
      isEdit.value = false
      editForm.value = { 
        appCode: '', appName: '', appType: '', 
        gitUrl: '', gitAcct: '', gitPwd: '', 
        buildScript: '', archiveFiles: ''
      }
      editDialogVisible.value = true
    }

    /**
     * 打开编辑版本对话框
     */
    const editVersion = (row) => {
      if (!row) {
        ElMessage.warning('请先选择要修改的记录')
        return
      }
      isEdit.value = true
      editForm.value = { 
        appCode: row.appCode, 
        appName: row.appName,
        appType: row.appType || '',
        gitUrl: row.gitUrl || '',
        gitAcct: row.gitAcct || '',
        gitPwd: row.gitPwd || '',
        buildScript: row.buildScript || '',
        archiveFiles: row.archiveFiles || ''
      }
      editDialogVisible.value = true
    }

    /**
     * 保存版本配置
     */
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

    /**
     * 删除版本配置
     */
    const deleteVersion = async (row) => {
      if (!row) {
        ElMessage.warning('请先选择要删除的记录')
        return
      }
      try {
        await ElMessageBox.confirm(
          `确定要删除应用 "${row.appName || row.appCode}" 吗？`,
          '删除确认',
          { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
        )
        await verBuildApi.deleteVersion({ appCode: row.appCode })
        ElMessage.success('删除成功')
        selectedRow.value = null
        searchVersions()
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败: ' + error.message)
        }
      }
    }

    onMounted(() => {
      searchVersions()
    })

    return {
      searchTerm, versionList, loading, currentVersion, buildForm,
      buildDialogVisible, sortedVersionList, editDialogVisible, isEdit, editForm,
      selectedRow, handleCurrentChange, buildingApps, stoppingApps,
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
  height: 28px;
  min-width: 60px;
}
:deep(.el-table__body tr.current-row > td.el-table__cell) {
  background-color: #d9ecff !important;
}
:deep(.el-table__body tr.current-row) {
  font-weight: 500;
}
</style>
