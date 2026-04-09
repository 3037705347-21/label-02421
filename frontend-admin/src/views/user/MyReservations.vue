<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">我的预约</h3>
      <el-table :data="reservations" stripe style="width:100%" v-loading="loading">
        <el-table-column prop="seatNo" label="座位" min-width="100" />
        <el-table-column prop="area" label="区域" min-width="80" />
        <el-table-column prop="reserveDate" label="预约日期" min-width="120" />
        <el-table-column prop="timeSlot" label="时间段" min-width="140" />
        <el-table-column label="座位属性" min-width="160">
          <template #default="{ row }">
            <span v-if="row.hasCharger === 1" class="tag-charger">⚡ 充电</span>
            <span v-if="row.nearWindow === 1" class="tag-window">🪟 靠窗</span>
            <span v-if="row.hasCharger !== 1 && row.nearWindow !== 1" style="color:#909399">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status]" size="small">{{ statusMap[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="预约时间" min-width="170" />
        <el-table-column label="操作" fixed="right" width="280" align="left">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="primary" size="small" @click="handleCheckIn(row.id)">签到</el-button>
            <el-button v-if="row.status === 0" type="warning" size="small" @click="handleCancel(row.id)">取消</el-button>
            <el-button v-if="row.status === 1" type="success" size="small" @click="openCheckDialog(row)">拍照打卡</el-button>
            <el-button v-if="row.status === 2" type="info" size="small" @click="viewCheckRecord(row.id)">查看打卡</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 拍照打卡弹窗 -->
    <el-dialog v-model="checkDialogVisible" title="清洁拍照打卡" width="480px" :close-on-click-modal="false" class="check-dialog">
      <div class="dialog-scroll-body">
        <el-alert title="自习结束后，请拍照确认座位已清理干净" type="info" :closable="false" style="margin-bottom:16px" />
        <el-form label-width="80px">
          <el-form-item label="座位">
            <el-input :value="currentRow?.seatNo" disabled />
          </el-form-item>
          <el-form-item label="上传照片">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              accept="image/*"
              :on-change="handleFileChange"
              list-type="picture-card"
            >
              <el-icon><Plus /></el-icon>
            </el-upload>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="checkRemark" type="textarea" :rows="2" placeholder="可选备注" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="checkDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="checkLoading" @click="submitCheck">提交打卡</el-button>
      </template>
    </el-dialog>

    <!-- 查看打卡记录弹窗 -->
    <el-dialog v-model="viewDialogVisible" title="打卡记录" width="520px" class="check-dialog">
      <div class="dialog-scroll-body">
        <div v-if="checkRecord">
          <el-image :src="checkRecord.photoUrl" style="width:100%;border-radius:8px" fit="contain" />

          <!-- 自动清洁度检测结果 -->
          <div class="detection-result" v-if="checkRecord.autoCleanResult || checkRecord.autoCleanDetail">
            <div class="detection-header">
              <span class="detection-title">清洁度自动检测</span>
              <el-tag
                v-if="checkRecord.autoCleanResult === 1"
                type="success"
                size="small"
              >检测通过</el-tag>
              <el-tag
                v-else-if="checkRecord.autoCleanResult === 2"
                type="danger"
                size="small"
              >疑似未清洁</el-tag>
              <el-tag
                v-else
                type="warning"
                size="small"
              >检测异常</el-tag>
            </div>
            <div class="score-bar">
              <div class="score-label">清洁度评分</div>
              <el-progress
                :percentage="checkRecord.autoCleanScore || 0"
                :color="getScoreColor(checkRecord.autoCleanScore)"
                :stroke-width="12"
                style="flex:1"
              />
            </div>
            <div class="detection-detail" v-if="checkRecord.autoCleanDetail">
              {{ checkRecord.autoCleanDetail }}
            </div>
          </div>

          <!-- 审核状态 -->
          <div style="text-align:center;margin-top:12px">
            <el-tag
              :type="checkRecord.cleanPassed === 1 ? 'success' : checkRecord.cleanPassed === 2 ? 'danger' : 'warning'"
              size="default"
            >{{ cleanPassedMap[checkRecord.cleanPassed] }}</el-tag>
          </div>

          <p style="margin-top:8px;color:#606266;text-align:center">{{ checkRecord.remark || '无备注' }}</p>
          <p style="color:#909399;font-size:12px;text-align:center">打卡时间: {{ checkRecord.createTime }}</p>
        </div>
        <el-empty v-else description="暂无打卡记录" />
      </div>
    </el-dialog>

    <!-- 检测结果弹窗（提交打卡后展示） -->
    <el-dialog v-model="detectionResultVisible" title="清洁度检测结果" width="420px" :close-on-click-modal="false">
      <div style="text-align:center;padding:10px 0">
        <el-icon :size="48" :color="detectionResult.autoCleanResult === 1 ? '#67C23A' : '#E6A23C'">
          <component :is="detectionResult.autoCleanResult === 1 ? 'CircleCheck' : 'Warning'" />
        </el-icon>
        <h3 style="margin:12px 0 4px;color:#303133">
          {{ detectionResult.autoCleanResult === 1 ? '清洁度检测通过' : '清洁度检测未通过' }}
        </h3>
        <p style="color:#909399;font-size:13px;margin-bottom:16px">
          {{ detectionResult.autoCleanResult === 1 ? '系统自动判定座位已清理干净，打卡已通过。' : '系统检测到座位可能未清理干净，已提交人工复审。' }}
        </p>
        <el-progress
          type="dashboard"
          :percentage="detectionResult.autoCleanScore || 0"
          :color="getScoreColor(detectionResult.autoCleanScore)"
          :width="120"
        >
          <template #default="{ percentage }">
            <span style="font-size:24px;font-weight:700;color:#303133">{{ percentage }}</span>
            <span style="font-size:12px;color:#909399;display:block">清洁度评分</span>
          </template>
        </el-progress>
        <p style="color:#606266;font-size:12px;margin-top:12px;text-align:left;line-height:1.8;padding:0 10px">
          {{ detectionResult.autoCleanDetail }}
        </p>
      </div>
      <template #footer>
        <el-button type="primary" @click="detectionResultVisible = false">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyReservations, cancelReservation, checkInReservation, uploadCheckPhoto, getCheckRecord } from '../../api'

const reservations = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const checkDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentRow = ref(null)
const checkFile = ref(null)
const checkRemark = ref('')
const checkLoading = ref(false)
const checkRecord = ref(null)
const detectionResultVisible = ref(false)
const detectionResult = ref({})

const statusMap = { 0: '待使用', 1: '使用中', 2: '已完成', 3: '已取消', 4: '超时未到' }
const statusType = { 0: 'primary', 1: 'warning', 2: 'success', 3: 'info', 4: 'danger' }
const cleanPassedMap = { 0: '待审核', 1: '审核通过', 2: '审核未通过' }

const getScoreColor = (score) => {
  if (score >= 80) return '#67C23A'
  if (score >= 60) return '#409EFF'
  if (score >= 40) return '#E6A23C'
  return '#F56C6C'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getMyReservations({ page: page.value, size: pageSize.value })
    reservations.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

const handleCheckIn = async (id) => {
  await ElMessageBox.confirm('确认签到？', '提示', { type: 'info', confirmButtonText: '确定', cancelButtonText: '取消' })
  await checkInReservation(id)
  ElMessage.success('签到成功')
  loadData()
}

const handleCancel = async (id) => {
  await ElMessageBox.confirm('确认取消此预约？', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' })
  await cancelReservation(id)
  ElMessage.success('已取消')
  loadData()
}

const openCheckDialog = (row) => {
  currentRow.value = row
  checkFile.value = null
  checkRemark.value = ''
  checkDialogVisible.value = true
}

const handleFileChange = (file) => {
  checkFile.value = file.raw
}

const submitCheck = async () => {
  if (!checkFile.value) {
    ElMessage.warning('请上传清洁照片')
    return
  }
  checkLoading.value = true
  try {
    const formData = new FormData()
    formData.append('reservationId', currentRow.value.id)
    formData.append('file', checkFile.value)
    if (checkRemark.value) formData.append('remark', checkRemark.value)
    const res = await uploadCheckPhoto(formData)
    checkDialogVisible.value = false
    // 展示自动检测结果
    detectionResult.value = res.data || {}
    detectionResultVisible.value = true
    loadData()
  } finally {
    checkLoading.value = false
  }
}

const viewCheckRecord = async (reservationId) => {
  const res = await getCheckRecord(reservationId)
  checkRecord.value = res.data
  viewDialogVisible.value = true
}

onMounted(loadData)
</script>

<style lang="scss" scoped>
// 打卡弹窗：固定高度，内容滚动，header和footer固定
:deep(.check-dialog) {
  .el-dialog__body {
    padding: 0;
  }
}
.dialog-scroll-body {
  max-height: 50vh;
  overflow-y: auto;
  padding: 20px;
}
// 检测结果区域
.detection-result {
  margin-top: 16px;
  padding: 14px;
  background: #f8f9fb;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  .detection-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
    .detection-title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
  }
  .score-bar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 8px;
    .score-label {
      font-size: 12px;
      color: #909399;
      white-space: nowrap;
    }
  }
  .detection-detail {
    font-size: 12px;
    color: #606266;
    line-height: 1.8;
    margin-top: 6px;
  }
}
</style>
