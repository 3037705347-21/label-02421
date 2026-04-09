import request from './request'

// 认证相关
export const login = (data) => request.post('/api/auth/login', data)
export const register = (data) => request.post('/api/auth/register', data)
export const getUserInfo = () => request.get('/api/auth/info')

// 座位相关
export const getSeatList = (params) => request.get('/api/seat/list', { params })
export const addSeat = (data) => request.post('/api/seat', data)
export const updateSeat = (data) => request.put('/api/seat', data)
export const deleteSeat = (id) => request.delete(`/api/seat/${id}`)

// 预约相关
export const createReservation = (data) => request.post('/api/reservation', data)
export const getMyReservations = (params) => request.get('/api/reservation/my', { params })
export const cancelReservation = (id) => request.put(`/api/reservation/cancel/${id}`)
export const checkInReservation = (id) => request.put(`/api/reservation/checkin/${id}`)
export const getAllReservations = (params) => request.get('/api/reservation/list', { params })

// 打卡相关
export const uploadCheckPhoto = (data) => request.post('/api/check/upload', data, {
  headers: { 'Content-Type': 'multipart/form-data' }
})
export const getCheckRecord = (reservationId) => request.get(`/api/check/${reservationId}`)
export const getCheckRecordList = (params) => request.get('/api/check/list', { params })
export const reviewCheckRecord = (id, cleanPassed) => request.put(`/api/check/review/${id}`, null, { params: { cleanPassed } })

// 用户管理
export const getUserList = (params) => request.get('/api/user/list', { params })
export const toggleUserStatus = (id) => request.put(`/api/user/status/${id}`)

// 操作日志
export const getLogList = (params) => request.get('/api/log/list', { params })
