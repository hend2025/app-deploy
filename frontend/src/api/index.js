import axios from 'axios'

const api = axios.create({
  baseURL: '/deploy',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    const data = response.data
    // 如果后端返回 success: false，视为业务错误
    if (data && data.success === false) {
      const error = new Error(data.message || '操作失败')
      error.response = response
      error.data = data
      return Promise.reject(error)
    }
    return data
  },
  error => {
    // 处理HTTP错误
    let message = '网络请求失败'
    
    if (error.response) {
      const { status, data } = error.response
      
      // 尝试从响应中提取错误信息
      if (data && data.message) {
        message = data.message
      } else if (data && typeof data === 'string') {
        message = data
      } else {
        // 根据HTTP状态码显示友好的错误信息
        switch (status) {
          case 400:
            message = '请求参数错误'
            break
          case 401:
            message = '未授权，请重新登录'
            break
          case 403:
            message = '拒绝访问'
            break
          case 404:
            message = '请求的资源不存在'
            break
          case 500:
            message = '服务器内部错误'
            break
          case 502:
            message = '网关错误'
            break
          case 503:
            message = '服务不可用'
            break
          case 504:
            message = '网关超时'
            break
          default:
            message = `请求失败 (${status})`
        }
      }
    } else if (error.request) {
      message = '网络连接失败，请检查网络'
    } else {
      message = error.message || '请求配置出错'
    }
    
    // 创建增强的错误对象
    const enhancedError = new Error(message)
    enhancedError.response = error.response
    enhancedError.request = error.request
    enhancedError.originalError = error
    
    return Promise.reject(enhancedError)
  }
)

// 应用管理 API
export const appMgtApi = {
  // 获取应用列表
  getAppList(appCode = '') {
    return api.get('/appMgt/list', { params: { appCode } })
  },
  // 启动应用
  startApp(data) {
    return api.post('/appMgt/start', data)
  },
  // 停止应用
  stopApp(data) {
    return api.post('/appMgt/stop', data)
  },
  // 保存应用（新增/编辑）
  saveApp(data) {
    return api.post('/appMgt/save', data)
  },
  // 删除应用
  deleteApp(data) {
    return api.post('/appMgt/delete', data)
  }
}

// 版本构建 API
export const verBuildApi = {
  // 搜索版本
  search(appName = '') {
    return api.get('/verBuild/search', { params: { appName } })
  },
  // 构建应用
  build(data) {
    return api.post('/verBuild/build', data)
  },
  // 停止构建
  stop(data) {
    return api.post('/verBuild/stop', data)
  },
  // 保存版本（新增/编辑）
  saveVersion(data) {
    return api.post('/verBuild/save', data)
  },
  // 删除版本
  deleteVersion(data) {
    return api.post('/verBuild/delete', data)
  }
}

// 日志 API（数据库存储）
export const logApi = {
  // 查询日志
  query(params = {}) {
    return api.get('/logs/db/query', { params })
  },
  // 查询最新日志（从数据库）
  latest(appCode, limit = 500) {
    return api.get('/logs/db/latest', { params: { appCode, limit } })
  },
  // 从缓冲区读取实时日志（用于应用管理和构建页面）
  bufferLogs(appCode, limit = 1000) {
    return api.get('/logs/db/buffer/logs', { params: { appCode, limit } })
  },
  // 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
  incremental(appCode, afterSeq = 0, limit = 1000) {
    return api.get('/logs/db/buffer/incremental', { params: { appCode, afterSeq, limit } })
  },
  // 分页查询日志
  page(params = {}) {
    return api.get('/logs/db/page', { params })
  },
  // 获取缓冲区状态
  bufferStatus() {
    return api.get('/logs/db/buffer/status')
  },
  // 刷新缓冲区
  flushBuffer() {
    return api.post('/logs/db/buffer/flush')
  },
  // 清理过期日志
  cleanup() {
    return api.post('/logs/db/cleanup')
  }
}

export default api