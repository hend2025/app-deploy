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
    return response.data
  },
  error => {
    return Promise.reject(error)
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
    return api.get('/logs/file/list')
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

