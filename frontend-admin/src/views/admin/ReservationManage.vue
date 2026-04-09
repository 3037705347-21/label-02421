<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">预约管理</h3>
      <div class="filter-bar">
        <el-select v-model="statusFilter" placeholder="预约状态" clearable style="width:140px" @change="loadData">
          <el-option label="待使用" :value="0" />
          <el-option label="使用中" :value="1" />
          <el-option label="已完成" :value="2" />
          <el-option label="已取消" :value="3" />
          <el-option label="超时未到" :value="4" />
        </el-select>
      </div>
      <el-table :data="list" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="username" label="用户" min-width="100" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column prop="seatNo" label="座位" min-width="100" />
        <el-table-column prop="area" label="区域" min-width="80" />
        <el-table-column prop="reserveDate" label="预约日期" min-width="120" />
        <el-table-column prop="timeSlot" label="时间段" min-width="140" />
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status]" size="small">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkInTime" label="签到时间" min-width="170" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getAllReservations } from '../../api'

const list = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const statusFilter = ref('')
const statusMap = { 0: '待使用', 1: '使用中', 2: '已完成', 3: '已取消', 4: '超时未到' }
const statusType = { 0: 'primary', 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (statusFilter.value !== '') params.status = statusFilter.value
    const res = await getAllReservations(params)
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
