import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const publicRoutes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue')
  }
]

const userRoutes = [
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/seats',
    children: [
      {
        path: 'seats',
        name: 'Seats',
        component: () => import('../views/user/SeatList.vue'),
        meta: { title: '座位列表' }
      },
      {
        path: 'my-reservations',
        name: 'MyReservations',
        component: () => import('../views/user/MyReservations.vue'),
        meta: { title: '我的预约' }
      }
    ]
  }
]

const adminRoutes = [
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    children: [
      {
        path: 'admin/seats',
        name: 'AdminSeats',
        component: () => import('../views/admin/SeatManage.vue'),
        meta: { title: '座位管理', admin: true }
      },
      {
        path: 'admin/reservations',
        name: 'AdminReservations',
        component: () => import('../views/admin/ReservationManage.vue'),
        meta: { title: '预约管理', admin: true }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('../views/admin/UserManage.vue'),
        meta: { title: '用户管理', admin: true }
      },
      {
        path: 'admin/check-records',
        name: 'AdminCheckRecords',
        component: () => import('../views/admin/CheckRecordManage.vue'),
        meta: { title: '打卡记录', admin: true }
      },
      {
        path: 'admin/logs',
        name: 'AdminLogs',
        component: () => import('../views/admin/LogManage.vue'),
        meta: { title: '操作日志', admin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: publicRoutes
})

let isRoutesAdded = false

export function resetRouter() {
  const newRouter = createRouter({
    history: createWebHistory(),
    routes: publicRoutes
  })
  router.matcher = newRouter.matcher
  isRoutesAdded = false
}

export function addRoutesByRole(isAdmin) {
  if (isRoutesAdded) return

  userRoutes.forEach(route => {
    router.addRoute(route)
  })

  if (isAdmin) {
    adminRoutes.forEach(route => {
      router.addRoute(route)
    })
  }

  isRoutesAdded = true
}

router.beforeEach(async (to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.path === '/login') {
    if (token) {
      next('/seats')
    } else {
      next()
    }
    return
  }

  if (!token) {
    next('/login')
    return
  }

  if (!isRoutesAdded) {
    const userStore = useUserStore()

    if (!userStore.userInfo || !userStore.userInfo.role) {
      try {
        await userStore.fetchUserInfo()
      } catch (e) {
        console.error('获取用户信息失败', e)
      }
    }

    addRoutesByRole(userStore.isAdmin)

    next({ ...to, replace: true })
    return
  }

  next()
})

export default router
