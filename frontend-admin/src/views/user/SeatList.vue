<template>
  <div class="page-container">
    <div class="card">
      <h3 class="page-title">可预约座位</h3>
      <div class="filter-bar">
        <el-select v-model="filters.area" placeholder="选择区域" clearable style="width:140px" @change="loadSeats">
          <el-option label="A区" value="A区" />
          <el-option label="B区" value="B区" />
        </el-select>
        <el-select v-model="filters.hasCharger" placeholder="充电孔" clearable style="width:140px" @change="loadSeats">
          <el-option label="有充电孔" :value="1" />
          <el-option label="无充电孔" :value="0" />
        </el-select>
        <el-select v-model="filters.nearWindow" placeholder="靠窗" clearable style="width:140px" @change="loadSeats">
          <el-option label="靠窗" :value="1" />
          <el-option label="不靠窗" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadSeats">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </div>
    </div>

    <div class="seat-grid" v-loading="seatLoading" element-loading-text="加载中...">
      <div v-for="seat in seatList" :key="seat.id" class="seat-card" :class="{ 'seat-unavailable': seat.status !== 0 }">
        <div class="seat-header">
          <span class="seat-no">{{ seat.seatNo }}</span>
          <el-tag :type="seat.status === 0 ? 'success' : seat.status === 1 ? 'warning' : 'info'" size="small">
            {{ statusMap[seat.status] }}
          </el-tag>
        </div>
        <div class="seat-body">
          <div class="info-row">
            <el-icon><Location /></el-icon>
            <span>{{ seat.area }} · {{ seat.floor }}楼</span>
          </div>
          <div class="info-row">
            <el-icon><OfficeBuilding /></el-icon>
            <span>座位编号：{{ seat.seatNo }}</span>
          </div>
          <div class="seat-features">
            <div class="feature-item" :class="{ active: seat.hasCharger === 1 }">
              <el-icon><Lightning /></el-icon>
              <span>{{ seat.hasCharger === 1 ? '有充电孔' : '无充电孔' }}</span>
            </div>
            <div class="feature-item" :class="{ active: seat.nearWindow === 1 }">
              <el-icon><Sunny /></el-icon>
              <span>{{ seat.nearWindow === 1 ? '靠窗位' : '非靠窗' }}</span>
            </div>
          </div>
        </div>
        <div class="seat-footer">
          <el-button
            v-if="seat.status === 0"
            type="primary"
            size="default"
            style="width:100%"
            @click="openReserveDialog(seat)"
          >立即预约</el-button>
          <el-button v-else type="info" size="default" style="width:100%" disabled>不可预约</el-button>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="pagination-wrap" style="margin-top:0">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadSeats"
        />
      </div>
    </div>

    <!-- 预约弹窗 -->
    <el-dialog v-model="dialogVisible" title="预约座位" width="440px" :close-on-click-modal="false">
      <el-form :model="reserveForm" label-width="80px">
        <el-form-item label="座位">
          <el-input :value="currentSeat?.seatNo + ' (' + currentSeat?.area + ')'" disabled />
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker
            v-model="reserveForm.reserveDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            :disabled-date="disablePastDate"
            style="width:100%"
            @change="reserveForm.timeSlot = ''"
          />
        </el-form-item>
        <el-form-item label="时间段">
          <el-select v-model="reserveForm.timeSlot" placeholder="选择时间段" style="width:100%">
            <el-option
              v-for="slot in availableTimeSlots"
              :key="slot.value"
              :label="slot.label"
              :value="slot.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitReserve">确认预约</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getSeatList, createReservation } from '../../api'

const allTimeSlots = [
  { label: '08:00 - 10:00', value: '08:00-10:00', start: '08:00', end: '10:00' },
  { label: '10:00 - 12:00', value: '10:00-12:00', start: '10:00', end: '12:00' },
  { label: '13:00 - 15:00', value: '13:00-15:00', start: '13:00', end: '15:00' },
  { label: '15:00 - 17:00', value: '15:00-17:00', start: '15:00', end: '17:00' },
  { label: '18:00 - 20:00', value: '18:00-20:00', start: '18:00', end: '20:00' },
  { label: '20:00 - 22:00', value: '20:00-22:00', start: '20:00', end: '22:00' }
]

const seatList = ref([])
const page = ref(1)
const pageSize = ref(12)
const total = ref(0)
const filters = ref({ area: '', hasCharger: '', nearWindow: '' })
const dialogVisible = ref(false)
const currentSeat = ref(null)
const submitLoading = ref(false)
const reserveForm = ref({ reserveDate: '', timeSlot: '' })
const seatLoading = ref(false)

const statusMap = { 0: '可用', 1: '已预约', 2: '维护中' }

// 选择当天时，过滤掉已完全过去的时间段（当前时间在时段内的仍可选）；选择未来日期则全部可选
const availableTimeSlots = computed(() => {
  if (!reserveForm.value.reserveDate) return allTimeSlots
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  if (reserveForm.value.reserveDate !== todayStr) return allTimeSlots
  const nowMinutes = today.getHours() * 60 + today.getMinutes()
  return allTimeSlots.filter(slot => {
    // 用结束时间判断：只要当前时间还没超过时段结束时间，就可以预约
    const [h, m] = slot.end.split(':').map(Number)
    return h * 60 + m > nowMinutes
  })
})

const disablePastDate = (date) => date.getTime() < Date.now() - 86400000

const loadSeats = async () => {
  const params = { page: page.value, size: pageSize.value, status: 0 }
  if (filters.value.area) params.area = filters.value.area
  if (filters.value.hasCharger !== '') params.hasCharger = filters.value.hasCharger
  if (filters.value.nearWindow !== '') params.nearWindow = filters.value.nearWindow
  seatLoading.value = true
  try {
    const res = await getSeatList(params)
    seatList.value = res.data.records
    total.value = res.data.total
  } finally {
    seatLoading.value = false
  }
}

const openReserveDialog = (seat) => {
  currentSeat.value = seat
  reserveForm.value = { reserveDate: '', timeSlot: '' }
  dialogVisible.value = true
}

const submitReserve = async () => {
  if (!reserveForm.value.reserveDate || !reserveForm.value.timeSlot) {
    ElMessage.warning('请选择日期和时间段')
    return
  }
  submitLoading.value = true
  try {
    await createReservation({
      seatId: currentSeat.value.id,
      reserveDate: reserveForm.value.reserveDate,
      timeSlot: reserveForm.value.timeSlot
    })
    ElMessage.success('预约成功')
    dialogVisible.value = false
    loadSeats()
  } finally {
    submitLoading.value = false
  }
}

onMounted(loadSeats)
</script>

<style lang="scss" scoped>
.seat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  min-height: 200px;
}
.seat-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
  border: 1px solid #ebeef5;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  display: flex;
  flex-direction: column;
  min-height: 230px;
  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 6px 20px rgba(64, 158, 255, 0.12);
    border-color: #d9ecff;
  }
  &.seat-unavailable {
    opacity: 0.55;
    &:hover {
      transform: none;
      box-shadow: 0 2px 12px rgba(0,0,0,0.06);
      border-color: #ebeef5;
    }
  }
}
.seat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f2f5;
  .seat-no {
    font-size: 18px;
    font-weight: 700;
    color: #303133;
  }
}
.seat-body {
  flex: 1;
  .info-row {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #606266;
    font-size: 13px;
    margin-bottom: 8px;
    .el-icon {
      color: #909399;
      font-size: 15px;
    }
  }
  .seat-features {
    display: flex;
    gap: 8px;
    margin-top: 12px;
    .feature-item {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 4px 10px;
      border-radius: 6px;
      font-size: 12px;
      background: #f5f7fa;
      color: #909399;
      border: 1px solid #ebeef5;
      .el-icon { font-size: 14px; }
      &.active {
        background: #ecf5ff;
        color: #409EFF;
        border-color: #d9ecff;
      }
    }
  }
}
.seat-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f5f7fa;
}
</style>
