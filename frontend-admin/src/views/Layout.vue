<template>
  <el-container class="layout-container">
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo-area">
        <el-icon :size="28" color="#fff"><Reading /></el-icon>
        <span v-show="!isCollapse" class="logo-text">座位预约系统</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="isCollapse"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/seats">
          <el-icon><Grid /></el-icon>
          <template #title>座位列表</template>
        </el-menu-item>
        <el-menu-item index="/my-reservations">
          <el-icon><Tickets /></el-icon>
          <template #title>我的预约</template>
        </el-menu-item>
        <template v-if="userStore.isAdmin">
          <el-sub-menu index="admin">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/admin/seats">座位管理</el-menu-item>
            <el-menu-item index="/admin/reservations">预约管理</el-menu-item>
            <el-menu-item index="/admin/users">用户管理</el-menu-item>
            <el-menu-item index="/admin/check-records">打卡记录</el-menu-item>
            <el-menu-item index="/admin/logs">操作日志</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" /><Expand v-else />
          </el-icon>
          <span class="page-name">{{ route.meta.title || '首页' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background:#409EFF">
                {{ userStore.nickname?.charAt(0) || 'U' }}
              </el-avatar>
              <span class="username">{{ userStore.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <transition name="fade-slide" mode="out-in">
          <router-view />
        </transition>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'
import { resetRouter } from '../router'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

onMounted(() => {
  if (userStore.isLoggedIn) {
    userStore.fetchUserInfo()
  }
})

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    resetRouter()
    router.push('/login')
  }
}
</script>

<style lang="scss" scoped>
.layout-container {
  min-height: 100vh;
}
.layout-aside {
  background: #304156;
  transition: width 0.3s ease;
  overflow: hidden;
  border-right: 1px solid rgba(0, 0, 0, 0.12);
  .logo-area {
    height: 60px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    border-bottom: 1px solid rgba(255,255,255,0.1);
    .logo-text {
      color: #fff;
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }
  }
}
.layout-header {
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  border-bottom: 1px solid #ebeef5;
  z-index: 10;
  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
    .collapse-btn {
      font-size: 20px;
      cursor: pointer;
      color: #606266;
      padding: 4px;
      border-radius: 4px;
      transition: all 0.25s ease;
      &:hover {
        color: #409EFF;
        background: #ecf5ff;
      }
    }
    .page-name {
      font-size: 16px;
      font-weight: 500;
      color: #303133;
    }
  }
  .header-right {
    .user-info {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: 6px;
      transition: background-color 0.25s ease;
      &:hover {
        background: #f5f7fa;
      }
      .username {
        font-size: 14px;
        color: #606266;
      }
    }
  }
}
.layout-main {
  background: #f5f7fa;
  padding: 24px;
  height: calc(100vh - 60px);
  box-sizing: border-box;
}

// 二级菜单文字与一级菜单对齐
:deep(.el-sub-menu .el-menu-item) {
  padding-left: 50px !important;
}

// 路由切换过渡动画
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
