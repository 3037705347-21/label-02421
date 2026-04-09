<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">用户管理</h3>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索用户名/昵称" clearable style="width:240px" @keyup.enter="loadData">
          <template #append><el-button @click="loadData"><el-icon><Search /></el-icon></el-button></template>
        </el-input>
      </div>
      <el-table :data="userList" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="角色" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'danger' : ''" size="small">{{ row.role === 1 ? '管理员' : '普通用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.deleted === 0 ? 'success' : 'info'" size="small">{{ row.deleted === 0 ? '正常' : '已禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="150" align="left">
          <template #default="{ row }">
            <el-button
              v-if="row.role !== 1"
              :type="row.deleted === 0 ? 'warning' : 'success'"
              size="small"
              @click="handleToggleStatus(row)"
            >{{ row.deleted === 0 ? '禁用' : '启用' }}</el-button>
            <span v-else style="color:#909399;font-size:12px">-</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, toggleUserStatus } from '../../api'

const userList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const keyword = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (keyword.value) params.keyword = keyword.value
    const res = await getUserList(params)
    userList.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

const handleToggleStatus = async (row) => {
  const action = row.deleted === 0 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确认${action}用户「${row.username}」？`, '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  await toggleUserStatus(row.id)
  ElMessage.success(`${action}成功`)
  loadData()
}

onMounted(loadData)
</script>
