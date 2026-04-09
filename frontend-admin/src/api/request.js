import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '',
  timeout: 15000
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 将后端 "field: msg; field: msg" 格式转为友好提示
function formatErrorMessage(message) {
  if (!message) return '请求失败'
  // 检测是否包含 "field: msg" 格式
  if (message.includes(':') && message.includes(';')) {
    const parts = message.split(';').map(s => {
      const colonIdx = s.indexOf(':')
      return colonIdx > -1 ? s.substring(colonIdx + 1).trim() : s.trim()
    }).filter(Boolean)
    // 去重（同一字段可能有多条规则）
    return [...new Set(parts)].join('；')
  }
  // 单条 "field: msg" 格式
  if (/^\w+:\s/.test(message)) {
    return message.substring(message.indexOf(':') + 1).trim()
  }
  return message
}

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 200) {
      const friendlyMsg = formatErrorMessage(res.message)
      ElMessage.error(friendlyMsg)
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        router.push('/login')
      }
      return Promise.reject(new Error(friendlyMsg))
    }
    return res
  },
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
      ElMessage.error('登录已过期，请重新登录')
    } else if (error.response && error.response.status === 400) {
      // 400 校验错误交给业务层处理，不在拦截器弹提示
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
