<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">打卡记录审核</h3>
      <el-table :data="list" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="id" label="ID" min-width="60" />
        <el-table-column prop="username" label="用户" min-width="100" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column label="打卡照片" min-width="90">
          <template #default="{ row }">
            <el-image :src="row.photoUrl" style="width:50px;height:50px;border-radius:4px" fit="cover" :preview-src-list="[row.photoUrl]" />
          </template>
        </el-table-column>
        <el-table-column label="清洁度评分" min-width="130">
          <template #default="{ row }">
            <el-progress
              v-if="row.autoCleanScore != null && row.autoCleanScore > 0"
              :percentage="row.autoCleanScore"
              :color="getScoreColor(row.autoCleanScore)"
              :stroke-width="10"
              style="width:90%"
            />
            <span v-else-if="row.autoCleanScore === 0 && row.autoCleanDetail" style="color:#E6A23C;font-size:12px">检测异常</span>
            <span v-else style="color:#909399;font-size:12px">未检测</span>
          </template>
        </el-table-column>
        <el-table-column label="自动检测" min-width="100">
          <template #default="{ row }">
            <el-tag v-if="row.autoCleanResult === 1" type="success" size="small">清洁</el-tag>
            <el-tag v-else-if="row.autoCleanResult === 2" type="danger" size="small">疑似不洁</el-tag>
            <el-tag v-else-if="row.autoCleanResult === 0 && row.photoUrl" type="warning" size="small">检测异常</el-tag>
            <el-tag v-else type="info" size="small">未检测</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最终审核" min-width="100">
          <template #default="{ row }">
            <el-tag :type="cleanStatusType[row.cleanPassed]" size="small">{{ cleanStatusMap[row.cleanPassed] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip min-width="100" />
        <el-table-column prop="createTime" label="打卡时间" min-width="160" />
        <el-table-column label="操作" fixed="right" width="200" align="left">
          <template #default="{ row }">
            <template v-if="row.cleanPassed === 0">
              <el-button type="success" size="small" @click="handleReview(row.id, 1)">通过</el-button>
              <el-button type="danger" size="small" @click="handleReview(row.id, 2)">不通过</el-button>
            </template>
            <span v-else style="color:#909399;font-size:12px">已审核</span>
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
import { getCheckRecordList, reviewCheckRecord } from '../../api'

const list = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)

const cleanStatusMap = { 0: '待审核', 1: '通过', 2: '未通过' }
const cleanStatusType = { 0: 'warning', 1: 'success', 2: 'danger' }

const getScoreColor = (score) => {
  if (score >= 80) return '#67C23A'
  if (score >= 60) return '#409EFF'
  if (score >= 40) return '#E6A23C'
  return '#F56C6C'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCheckRecordList({ page: page.value, size: pageSize.value })
    list.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

const handleReview = async (id, cleanPassed) => {
  const label = cleanPassed === 1 ? '通过' : '不通过'
  await ElMessageBox.confirm(`确认将此打卡记录标记为"${label}"？`, '审核确认', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  await reviewCheckRecord(id, cleanPassed)
  ElMessage.success('审核完成')
  loadData()
}

onMounted(loadData)
</script>
