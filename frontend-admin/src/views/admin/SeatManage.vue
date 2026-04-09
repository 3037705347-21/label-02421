<template>
  <div class="page-container">
    <div class="card">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px">
        <h3 class="page-title" style="margin-bottom:0">座位管理</h3>
        <el-button type="primary" @click="openDialog(null)"><el-icon><Plus /></el-icon> 新增座位</el-button>
      </div>
      <el-table :data="seatList" stripe v-loading="loading" style="width:100%">
        <el-table-column prop="seatNo" label="座位编号" min-width="120" />
        <el-table-column prop="area" label="区域" min-width="100" />
        <el-table-column prop="floor" label="楼层" min-width="80" />
        <el-table-column label="充电孔" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.hasCharger ? 'success' : 'info'" size="small">{{ row.hasCharger ? '有' : '无' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="靠窗" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.nearWindow ? 'success' : 'info'" size="small">{{ row.nearWindow ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'danger'" size="small">
              {{ statusMap[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="180" align="left">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openDialog(row)">编辑</el-button>
            <el-button type="danger" size="small" link @click="handleDelete(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadData" />
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑座位' : '新增座位'" width="480px" :close-on-click-modal="false">
      <el-form :model="form" label-width="80px">
        <el-form-item label="座位编号"><el-input v-model="form.seatNo" placeholder="如 A-001" /></el-form-item>
        <el-form-item label="区域"><el-input v-model="form.area" placeholder="如 A区" /></el-form-item>
        <el-form-item label="楼层"><el-input-number v-model="form.floor" :min="1" :max="10" /></el-form-item>
        <el-form-item label="充电孔"><el-switch v-model="form.hasCharger" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="靠窗"><el-switch v-model="form.nearWindow" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item v-if="isEdit" label="状态">
          <el-select v-model="form.status">
            <el-option label="可用" :value="0" /><el-option label="已预约" :value="1" /><el-option label="维护中" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSeatList, addSeat, updateSeat, deleteSeat } from '../../api'

const seatList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const form = ref({})
const statusMap = { 0: '可用', 1: '已预约', 2: '维护中' }

const loadData = async () => {
  loading.value = true
  try {
    const res = await getSeatList({ page: page.value, size: pageSize.value })
    seatList.value = res.data.records
    total.value = res.data.total
  } finally { loading.value = false }
}

const openDialog = (row) => {
  isEdit.value = !!row
  form.value = row ? { ...row } : { seatNo: '', area: '', floor: 1, hasCharger: 0, nearWindow: 0 }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.seatNo) { ElMessage.warning('请输入座位编号'); return }
  submitLoading.value = true
  try {
    if (isEdit.value) { await updateSeat(form.value) } else { await addSeat(form.value) }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

const handleDelete = async (id) => {
  await ElMessageBox.confirm('确认删除此座位？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  await deleteSeat(id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
:deep(.el-table) {
  .el-button--primary.is-link:hover {
    opacity: 0.8;
  }
  .el-button--danger.is-link:hover {
    opacity: 0.8;
  }
}
</style>
