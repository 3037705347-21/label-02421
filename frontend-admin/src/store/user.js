import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserInfo } from '../api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value.role === 1)
  const nickname = computed(() => {
    const n = userInfo.value.nickname || ''
    // 如果昵称包含乱码字符（非常见字符），回退显示用户名
    if (n && !/^[\u4e00-\u9fa5a-zA-Z0-9_\-\s]+$/.test(n)) {
      return userInfo.value.username || ''
    }
    return n || userInfo.value.username || ''
  })

  function setLogin(data) {
    token.value = data.token
    userInfo.value = { userId: data.userId, username: data.username, nickname: data.nickname, role: data.role }
    localStorage.setItem('token', data.token)
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  async function fetchUserInfo() {
    try {
      const res = await getUserInfo()
      userInfo.value = res.data
      localStorage.setItem('userInfo', JSON.stringify(res.data))
    } catch (e) {
      console.error('获取用户信息失败', e)
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, isLoggedIn, isAdmin, nickname, setLogin, fetchUserInfo, logout }
})
