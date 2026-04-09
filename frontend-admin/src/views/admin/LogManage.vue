<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">操作日志</h3>
      <el-table :data="logList" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" min-width="80" />
        <el-table-column prop="userId" label="用户ID" min-width="80" />
        <el-table-column prop="module" label="模块" min-width="120" />
        <el-table-column prop="action" label="操作" min-width="100" />
        <el-table-column prop="detail" label="详情" show-overflow-tooltip min-width="200" />
        <el-table-column prop="ip" label="IP" min-width="140" />
        <el-table-column prop="createTime" label="时间" min-width="170" />
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getLogList } from '../../api'

const logList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getLogList({ page: page.value, size: pageSize.value })
    logList.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

onMounted(loadData)
</script>
