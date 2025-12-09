/**
 * API请求模块
 * 
 * 封装axios实例，提供统一的请求/响应拦截和错误处理
 */
import axios from 'axios'

/**
 * 创建axios实例
 * - baseURL: 后端API基础路径
 * - timeout: 请求超时时间（60秒，适应构建等耗时操作）
 */
const api = axios.create({
  baseURL: '/deploy',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 请求拦截器
 * 可在此添加token等认证信息
 */
api.interceptors.request.use(
  config => {
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

/**
 * 响应拦截器
 * - 统一处理业务错误（success: false）
 * - 统一处理HTTP错误，转换为友好的错误信息
 */
api.interceptors.response.use(
  response => {
    const data = response.data
    // 业务错误：后端返回 success: false
    if (data && data.success === false) {
      const error = new Error(data.message || '操作失败')
      error.response = response
      error.data = data
      return Promise.reject(error)
    }
    return data
  },
  error => {
    // HTTP错误处理
    let message = '网络请求失败'
    
    if (error.response) {
      const { status, data } = error.response
      
      // 优先使用后端返回的错误信息
      if (data && data.message) {
        message = data.message
      } else if (data && typeof data === 'string') {
        message = data
      } else {
        // 根据HTTP状态码映射友好提示
        const statusMessages = {
          400: '请求参数错误',
          401: '未授权，请重新登录',
          403: '拒绝访问',
          404: '请求的资源不存在',
          500: '服务器内部错误',
          502: '网关错误',
          503: '服务不可用',
          504: '网关超时'
        }
        message = statusMessages[status] || `请求失败 (${status})`
      }
    } else if (error.request) {
      message = '网络连接失败，请检查网络'
    } else {
      message = error.message || '请求配置出错'
    }
    
    // 创建增强的错误对象，保留原始信息便于调试
    const enhancedError = new Error(message)
    enhancedError.response = error.response
    enhancedError.request = error.request
    enhancedError.originalError = error
    
    return Promise.reject(enhancedError)
  }
)

/**
 * 应用管理API
 * 提供JAR应用的生命周期管理接口
 */
export const appMgtApi = {
  /**
   * 获取应用列表（含运行状态）
   * @param {string} appCode - 应用编码过滤条件（可选）
   */
  getAppList(appCode = '') {
    return api.get('/appMgt/list', { params: { appCode } })
  },
  
  /**
   * 启动应用
   * @param {Object} data - { appCode, version, params }
   */
  startApp(data) {
    return api.post('/appMgt/start', data)
  },
  
  /**
   * 停止应用
   * @param {Object} data - { appCode, pid }
   */
  stopApp(data) {
    return api.post('/appMgt/stop', data)
  },
  
  /**
   * 保存应用配置（新增/编辑）
   * @param {Object} data - 应用配置信息
   */
  saveApp(data) {
    return api.post('/appMgt/save', data)
  },
  
  /**
   * 删除应用配置
   * @param {Object} data - { appCode }
   */
  deleteApp(data) {
    return api.post('/appMgt/delete', data)
  }
}

/**
 * 版本构建API
 * 提供应用版本构建管理接口
 */
export const verBuildApi = {
  /**
   * 搜索版本配置列表
   * @param {string} appName - 应用名称过滤条件（可选）
   */
  search(appName = '') {
    return api.get('/verBuild/search', { params: { appName } })
  },
  
  /**
   * 启动构建任务
   * @param {Object} data - { appCode, branchOrTag }
   */
  build(data) {
    return api.post('/verBuild/build', data)
  },
  
  /**
   * 停止构建任务
   * @param {Object} data - { appCode }
   */
  stop(data) {
    return api.post('/verBuild/stop', data)
  },
  
  /**
   * 保存版本配置（新增/编辑）
   * @param {Object} data - 版本配置信息
   */
  saveVersion(data) {
    return api.post('/verBuild/save', data)
  },
  
  /**
   * 删除版本配置
   * @param {Object} data - { appCode }
   */
  deleteVersion(data) {
    return api.post('/verBuild/delete', data)
  }
}

/**
 * 日志API
 * 提供日志查询接口
 */
export const logApi = {

  /**
   * 增量读取日志
   * 通过序号实现增量拉取，避免重复获取已读日志
   * 
   * @param {string} appCode - 应用编码
   * @param {number} afterSeq - 上次读取的最后序号
   * @param {number} limit - 最大返回条数
   */
  incremental(appCode, afterSeq = 0, limit = 1000) {
    return api.get('/logs/buffer/incremental', { params: { appCode, afterSeq, limit } })
  }

}

export default api