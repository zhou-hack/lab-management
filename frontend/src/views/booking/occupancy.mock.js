              /**
 * 场地占用总览 mock 数据（仅本地调试用，不请求后端、不写数据库）。
 *
 * 启用方式（二选一）：
 *   1. 页面 URL 追加查询参数 ?mockOccupancy=1
 *   2. 浏览器控制台执行 localStorage.setItem('mockOccupancy', '1') 后刷新
 *
 * 场景覆盖：
 *   - 空闲：大部分场地任意时段空闲（可点选）
 *   - 单时段占用：C703 仅第 3~4 节（10:00-11:40）被占用
 *   - 跨时段占用：A703 整个上午第 1~4 节连续占用（验证窗口重叠判定）
 *   - 下午占用：教学楼201 仅第 6 节（14:55-15:40）被占用
 *   - 非可用状态：K楼523 维护中、G楼211 已关闭（不参与占用判断）
 * 切换节次/区间/日期时，占用结果随窗口变化，便于验证筛选逻辑。
 */

/** 节次时刻表（与 class_period 官方作息一致，id 即节次号） */
const PERIOD_CLOCKS = {
  1: ['08:25', '09:10'],
  2: ['09:15', '10:00'],
  3: ['10:10', '10:55'],
  4: ['11:00', '11:45'],
  5: ['13:50', '14:35'],
  6: ['14:40', '15:25'],
  7: ['15:35', '16:20'],
  8: ['16:25', '17:10'],
  9: ['18:30', '19:15'],
  10: ['19:20', '20:05'],
  11: ['20:10', '20:55'],
  12: ['21:00', '21:45']
}

const toMin = (clock) => Number(clock.slice(0, 2)) * 60 + Number(clock.slice(3, 5))

/** 占用场景：occupyPeriods 为 [起, 止] 节次号（闭区间），窗口与之重叠即占用 */
const MOCK_LABS = [
  {
    spaceId: 'mock-1', labName: 'A703 网络实训室', labTypeName: '实训室', capacity: 50,
    locationDetail: 'A 楼七层 A703', occupyPeriods: [1, 4],
    teacherName: '李慧敏', courseName: '网络综合布线实训（跨时段占用样例）'
  },
  {
    spaceId: 'mock-2', labName: 'C703 电子电工实训室', labTypeName: '实训室', capacity: 45,
    locationDetail: 'C 楼七层 C703', occupyPeriods: [3, 4],
    teacherName: '王建国', courseName: 'Python 程序设计实训课'
  },
  {
    spaceId: 'mock-3', labName: '教学楼201多媒体教室', labTypeName: '教室', capacity: 60,
    locationDetail: '教学楼二层 201', occupyPeriods: [6, 6],
    teacherName: '张立', courseName: '多媒体课件制作'
  },
  { spaceId: 'mock-4', labName: 'C503', labTypeName: '教室', capacity: 40, locationDetail: 'C 楼503' },
  { spaceId: 'mock-5', labName: 'C楼809 智慧教室', labTypeName: '教室', capacity: 62, locationDetail: 'C 楼8层 809室' },
  { spaceId: 'mock-6', labName: 'J楼214 大数据实训室', labTypeName: '实训室', capacity: 93, locationDetail: 'J 楼2层 214室' },
  { spaceId: 'mock-7', labName: 'A楼830 普通教室', labTypeName: '教室', capacity: 75, locationDetail: 'A 楼8层 830室' },
  { spaceId: 'mock-8', labName: 'J楼626 录播教室', labTypeName: '教室', capacity: 87, locationDetail: 'J 楼6层 626室' },
  {
    spaceId: 'mock-9', labName: 'K楼523 阶梯教室', labTypeName: '教室', capacity: 88,
    locationDetail: 'K 楼5层 523室', status: 'MAINTAINING', statusName: '维护中'
  },
  {
    spaceId: 'mock-10', labName: 'G楼211 普通教室', labTypeName: '教室', capacity: 87,
    locationDetail: 'G 楼2层 211室', status: 'CLOSED', statusName: '关闭'
  },
  { spaceId: 'mock-11', labName: 'B楼715 云计算实训室', labTypeName: '实训室', capacity: 51, locationDetail: 'B 楼7层 715室' },
  { spaceId: 'mock-12', labName: 'K楼725 录播教室', labTypeName: '教室', capacity: 101, locationDetail: 'K 楼7层 725室' }
]

/** 是否启用 mock（URL 参数优先，其次 localStorage 标记） */
export function isOccupancyMockEnabled() {
  if (typeof window === 'undefined') return false
  const query = new URLSearchParams(window.location.search)
  return query.get('mockOccupancy') === '1' || window.localStorage.getItem('mockOccupancy') === '1'
}

/** 本地时刻 + 日期 → UTC ISO（与后端 Instant 序列化口径一致） */
function toUtcIso(date, clock) {
  return new Date(`${date}T${clock}:00+08:00`).toISOString()
}

/**
 * 按请求参数生成占用总览 mock 行。
 * params: { date, startPeriodId, endPeriodId }（与 getLabOccupancy 入参一致）
 */
export function buildMockOccupancy(params) {
  const { date } = params
  const startId = Number(params.startPeriodId)
  const endId = Number(params.endPeriodId)
  const winStart = toMin(PERIOD_CLOCKS[startId]?.[0] || '00:00')
  const winEnd = toMin(PERIOD_CLOCKS[endId]?.[1] || '23:59')

  return MOCK_LABS.map((lab) => {
    const status = lab.status || 'AVAILABLE'
    let occupied = false
    let occupyStartAt = null
    let occupyEndAt = null
    let slots = []
    if (status === 'AVAILABLE' && lab.occupyPeriods) {
      const segStart = toMin(PERIOD_CLOCKS[lab.occupyPeriods[0]][0])
      const segEnd = toMin(PERIOD_CLOCKS[lab.occupyPeriods[1]][1])
      // 半开区间重叠判定，与后端口径一致
      occupied = segEnd > winStart && segStart < winEnd
      if (occupied) {
        occupyStartAt = toUtcIso(date, PERIOD_CLOCKS[lab.occupyPeriods[0]][0])
        occupyEndAt = toUtcIso(date, PERIOD_CLOCKS[lab.occupyPeriods[1]][1])
        slots = [{
          bookingId: `${lab.spaceId}-booking`,
          startAt: occupyStartAt,
          endAt: occupyEndAt,
          courseName: lab.courseName,
          teacherName: lab.teacherName,
          applicantName: lab.teacherName
        }]
      }
    }
    return {
      spaceId: lab.spaceId,
      labName: lab.labName,
      labType: lab.labTypeName === '教室' ? 'CLASSROOM' : 'TRAINING_ROOM',
      labTypeName: lab.labTypeName,
      locationDetail: lab.locationDetail,
      capacity: lab.capacity,
      openStart: '08:00',
      openEnd: '21:00',
      status,
      statusName: lab.statusName || '可用',
      occupied,
      bookingId: occupied ? `${lab.spaceId}-booking` : null,
      courseName: occupied ? lab.courseName : null,
      teacherName: occupied ? lab.teacherName : null,
      applicantName: occupied ? lab.teacherName : null,
      occupyStartAt,
      occupyEndAt,
      slots
    }
  })
}
