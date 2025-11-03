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
  }
}

// 日志文件 API
export const logFileApi = {
  // 获取日志文件列表
  getFileList() {
    return api.get('/logs/file/log-files')
  },
  // 读取文件最后N行
  readFileLastLines(fileName, lastLines = 3000) {
    return api.get('/logs/file/read-file-last-lines', { 
      params: { fileName, lastLines } 
    })
  },
  // 增量读取文件
  readFileIncremental(fileName, fromLine) {
    return api.get('/logs/file/read-file-incremental', { 
      params: { fileName, fromLine } 
    })
  },
  // 下载文件
  downloadFile(fileName) {
    return `/deploy/logs/file/download-file?fileName=${encodeURIComponent(fileName)}`
  }
}

export default api

