<template>
  <div class="role_page page">
    <!-- 左：角色列表 -->
    <aside class="role_page__side">
      <div class="role_side__header">
        角色列表
      </div>
      <div
        v-loading="loading"
        class="role_side__list"
      >
        <div
          v-for="role in roles"
          :key="role.roleCode"
          class="role_side__item"
          :class="{ 'role_side__item--active': current?.roleCode === role.roleCode }"
          @click="selectRole(role)"
        >
          <div class="role_side__name">
            {{ role.roleName }}
          </div>
          <div class="role_side__meta">
            <span class="role_side__code">{{ role.roleCode }}</span>
            <span class="role_side__count">{{ role.userCount }} 人</span>
          </div>
        </div>
      </div>
      <p class="role_side__hint">
        角色由系统预置，不开放页面增删
      </p>
    </aside>

    <!-- 右：角色配置 -->
    <section
      v-if="current"
      class="role_page__main"
    >
      <header class="role_main__header">
        <div>
          <div class="role_main__title">
            {{ current.roleName }}
            <el-tag
              size="small"
              effect="plain"
              class="role_main__code_tag"
            >
              {{ current.roleCode }}
            </el-tag>
            <el-tag
              v-if="current.roleCode === 'OPS_ADMIN'"
              size="small"
              type="danger"
              effect="plain"
            >
              绑定锁定
            </el-tag>
          </div>
          <div class="role_main__meta">
            绑定用户 {{ current.userCount }} 人 · 权限 {{ checkedIds.length }} 项 · 菜单 {{ current.menus.length }} 项
          </div>
        </div>
        <div
          v-if="editable"
          class="role_main__actions"
        >
          <el-button
            :disabled="!dirty || saving"
            @click="resetChecked"
          >
            重置
          </el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!dirty"
            @click="save"
          >
            保存修改
          </el-button>
        </div>
      </header>

      <el-alert
        v-if="current.roleCode === 'OPS_ADMIN'"
        type="warning"
        :closable="false"
        title="OPS_ADMIN 为最高级运维角色，权限绑定由 SQL 预置锁定，页面仅可查看"
        class="role_main__alert"
      />
      <el-alert
        v-else-if="current.roleCode === 'ASSISTANT'"
        type="info"
        :closable="false"
        title="ASSISTANT 角色不绑定到用户，由有效助理授权动态计算；此处调整其权限内容"
        class="role_main__alert"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        title="保存后持有该角色的用户将被强制退出，重新登录后生效；操作需二次密码确认"
        class="role_main__alert"
      />

      <el-tabs v-model="activeTab">
        <el-tab-pane
          label="功能权限"
          name="perms"
        >
          <div
            v-loading="permLoading"
            class="role_main__groups"
          >
            <section
              v-for="group in permissionGroups"
              :key="group.moduleKey"
              class="perm_group"
            >
              <header class="perm_group__header">
                <el-checkbox
                  :model-value="group.allChecked"
                  :indeterminate="group.partial"
                  :disabled="!editable"
                  @change="toggleModule(group, $event)"
                >
                  <span class="perm_group__title">{{ group.label }}</span>
                </el-checkbox>
                <span class="perm_group__count">{{ group.checkedCount }}/{{ group.total }}</span>
              </header>
              <div class="perm_group__body">
                <div
                  v-for="res in group.resources"
                  :key="res.resourceKey"
                  class="perm_row"
                >
                  <div class="perm_row__name">
                    {{ res.label }}
                  </div>
                  <div class="perm_row__actions">
                    <el-checkbox
                      v-for="p in res.perms"
                      :key="p.id"
                      :model-value="checkedIds.includes(p.id)"
                      :disabled="!editable"
                      @change="toggleOne(p.id, $event)"
                    >
                      {{ p.permissionName }}
                    </el-checkbox>
                  </div>
                </div>
              </div>
            </section>
            <el-empty
              v-if="!permissionGroups.length && !permLoading"
              description="暂无权限选项"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane
          :label="`菜单绑定（${current.menus.length}）`"
          name="menus"
        >
          <el-table
            :data="current.menus"
            size="small"
          >
            <el-table-column
              prop="menuName"
              label="菜单"
              min-width="140"
            />
            <el-table-column
              label="类型"
              width="90"
            >
              <template #default="{ row }">
                <el-tag
                  :type="{ DIRECTORY: 'warning', PAGE: 'primary', ACTION: 'info' }[row.menuType] || 'info'"
                  size="small"
                >
                  {{ { DIRECTORY: '目录', PAGE: '页面', ACTION: '操作' }[row.menuType] || row.menuType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="routePath"
              label="路由"
              min-width="160"
              show-overflow-tooltip
            />
          </el-table>
          <p class="role_main__menu_hint">
            菜单绑定由系统预置维护，页面不开放调整
          </p>
        </el-tab-pane>

        <el-tab-pane
          label="角色成员"
          name="members"
        >
          <el-empty
            v-if="current.roleCode === 'ASSISTANT'"
            description="助理成员由有效授权动态产生，请前往「授权审计」查看"
          />
          <template v-else>
            <div class="role_members__bar">
              <span class="role_members__hint">{{ memberHint }}</span>
              <div class="role_members__actions">
                <el-button
                  v-if="current.roleCode === 'ADMIN' && canBindRole"
                  type="primary"
                  size="small"
                  @click="openAddMembers"
                >
                  添加成员
                </el-button>
                <el-button
                  size="small"
                  :loading="memberExporting"
                  @click="exportMembers"
                >
                  导出
                </el-button>
              </div>
            </div>
            <el-table
              v-loading="memberLoading"
              :data="memberRows"
              size="small"
            >
              <el-table-column
                label="姓名"
                min-width="130"
              >
                <template #default="{ row }">
                  <div class="role_members__person">
                    <span
                      class="role_members__avatar"
                      :style="{ backgroundColor: avatarColor(row.realName) }"
                    >{{ avatarText(row.realName) }}</span>
                    <span>{{ row.realName || '—' }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column
                label="学工号"
                min-width="100"
              >
                <template #default="{ row }">
                  {{ row.personNo || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="登录账号"
                min-width="100"
              >
                <template #default="{ row }">
                  {{ row.accountName || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="部门"
                min-width="110"
              >
                <template #default="{ row }">
                  {{ row.departmentName || '—' }}
                </template>
              </el-table-column>
              <el-table-column
                label="身份"
                width="80"
              >
                <template #default="{ row }">
                  {{ PERSON_TYPE_LABELS[row.personType] || row.personType }}
                </template>
              </el-table-column>
              <el-table-column
                label="状态"
                width="90"
              >
                <template #default="{ row }">
                  <el-tag
                    :type="row.status === 'NORMAL' ? 'success' : 'info'"
                    size="small"
                  >
                    {{ STATUS_LABELS[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column
                v-if="current.roleCode === 'ADMIN' && canBindRole"
                label="操作"
                width="80"
                fixed="right"
              >
                <template #default="{ row }">
                  <el-button
                    v-if="row.personType === 'TEACHER'"
                    link
                    type="danger"
                    @click="removeMember(row)"
                  >
                    移出
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无成员" />
              </template>
            </el-table>
            <div class="role_members__pager">
              <el-pagination
                v-model:current-page="memberPage.pageNum"
                v-model:page-size="memberPage.pageSize"
                :total="memberTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @size-change="loadMembers(1)"
                @current-change="loadMembers()"
              />
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </section>

    <!-- 添加成员（ADMIN：从本组织老师账号中勾选绑定） -->
    <el-dialog
      v-model="addVisible"
      title="添加成员"
      width="560px"
      destroy-on-close
    >
      <p class="role_add__tip">
        从本组织内的老师账号中选择，确认后为其绑定管理员角色；被绑定者当前会话将退出，重新登录后生效。
      </p>
      <div class="role_add__bar">
        <el-input
          v-model="addKeyword"
          placeholder="姓名 / 学工号 / 账号"
          clearable
          size="small"
          style="width: 240px"
          @keyup.enter="loadCandidates(1)"
          @clear="loadCandidates(1)"
        />
        <el-button
          size="small"
          @click="loadCandidates(1)"
        >
          搜索
        </el-button>
      </div>
      <el-table
        v-loading="addLoading"
        :data="addRows"
        row-key="userId"
        size="small"
        height="320"
        @selection-change="addSelection = $event"
      >
        <el-table-column
          type="selection"
          width="40"
          reserve-selection
          :selectable="candidateSelectable"
        />
        <el-table-column
          label="姓名"
          min-width="110"
        >
          <template #default="{ row }">
            <div class="role_members__person">
              <span
                class="role_members__avatar"
                :style="{ backgroundColor: avatarColor(row.realName) }"
              >{{ avatarText(row.realName) }}</span>
              <span>{{ row.realName || '—' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="学工号"
          min-width="100"
        >
          <template #default="{ row }">
            {{ row.personNo || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="部门"
          min-width="110"
        >
          <template #default="{ row }">
            {{ row.departmentName || '—' }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              v-if="roleCodesOf(row).includes('ADMIN')"
              size="small"
              type="warning"
              effect="plain"
            >
              已是管理员
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="role_add__pager">
        <el-pagination
          v-model:current-page="addPage.pageNum"
          :page-size="addPage.pageSize"
          :total="addTotal"
          layout="total, prev, pager, next"
          @current-change="loadCandidates()"
        />
      </div>
      <template #footer>
        <el-button @click="addVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="addSubmitting"
          :disabled="!addSelection.length"
          @click="submitAddMembers"
        >
          确认（{{ addSelection.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignRolePermissions, listPermissionOptions, listRoles } from '@/api/system/role'
import { pagePersons } from '@/api/system/person'
import { updateUserAdminRole } from '@/api/system/user'
import { withSafeAuth } from '@/utils/safe_auth'
import { hasPermission } from '@/utils/permission'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const roles = ref([])
const loading = ref(false)
const current = ref(null)
const activeTab = ref('perms')
const saving = ref(false)
const permLoading = ref(false)
const allPermissions = ref([])
// 勾选态与基线（角色权限 id 列表）
const checkedIds = ref([])
const baselineIds = ref([])

// 模块/资源中文名映射；未登记的新模块或资源原样显示编码段，不阻塞展示
const MODULE_LABELS = { system: '系统管理', auth: '认证', grant: '助理授权' }
const RESOURCE_LABELS = {
  user: '用户管理',
  roster: '名单管理',
  department: '组织管理',
  role: '角色权限',
  policy: '参数配置',
  log: '操作日志',
  grant: '助理授权'
}

/** 是否可编辑：非 OPS_ADMIN 且持有 system:role:assign */
const editable = computed(() =>
  current.value
  && current.value.roleCode !== 'OPS_ADMIN'
  && hasPermission('system:role:assign', userStore.permissions)
)

const dirty = computed(() =>
  JSON.stringify([...checkedIds.value].sort()) !== JSON.stringify([...baselineIds.value].sort())
)

/** 权限按 模块 → 资源 分组，行内展示操作勾选 */
const permissionGroups = computed(() => {
  const modules = {}
  for (const p of allPermissions.value) {
    const moduleKey = p.moduleCode || 'other'
    const resourceKey = p.permissionCode?.split(':')[1] || 'other'
    if (!modules[moduleKey]) modules[moduleKey] = {}
    if (!modules[moduleKey][resourceKey]) modules[moduleKey][resourceKey] = []
    modules[moduleKey][resourceKey].push(p)
  }
  return Object.entries(modules).map(([moduleKey, resources]) => {
    const resList = Object.entries(resources).map(([resourceKey, perms]) => ({
      resourceKey,
      label: RESOURCE_LABELS[resourceKey] || resourceKey,
      perms
    }))
    const all = resList.flatMap((r) => r.perms)
    const checkedCount = all.filter((p) => checkedIds.value.includes(p.id)).length
    return {
      moduleKey,
      label: MODULE_LABELS[moduleKey] || moduleKey,
      resources: resList,
      perms: all,
      total: all.length,
      checkedCount,
      allChecked: checkedCount === all.length && all.length > 0,
      partial: checkedCount > 0 && checkedCount < all.length
    }
  })
})

async function load(selectCode) {
  loading.value = true
  try {
    roles.value = await listRoles()
    const target = roles.value.find((r) => r.roleCode === selectCode) || roles.value[0]
    if (target) applyRole(target)
  } finally {
    loading.value = false
  }
}

function applyRole(role) {
  current.value = role
  checkedIds.value = role.permissions.map((p) => p.id)
  baselineIds.value = [...checkedIds.value]
  // 切换角色时重置成员页签状态；当前停留在成员页签则立即刷新
  memberPage.pageNum = 1
  memberRows.value = []
  memberTotal.value = 0
  if (activeTab.value === 'members') {
    loadMembers()
  }
}

// 确认弹窗打开期间忽略重复的角色切换点击，避免弹窗叠加
let switchConfirming = false

async function selectRole(role) {
  if (saving.value || switchConfirming) return
  if (current.value?.roleCode === role.roleCode) return
  if (dirty.value) {
    switchConfirming = true
    try {
      await ElMessageBox.confirm('当前修改尚未保存，切换角色将丢弃修改，是否继续？', '未保存的修改', {
        confirmButtonText: '丢弃并切换',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    } finally {
      switchConfirming = false
    }
  }
  applyRole(role)
}

function toggleOne(id, checked) {
  checkedIds.value = checked
    ? [...checkedIds.value, id]
    : checkedIds.value.filter((x) => x !== id)
}

function toggleModule(group, checked) {
  const ids = group.perms.map((p) => p.id)
  const rest = checkedIds.value.filter((id) => !ids.includes(id))
  checkedIds.value = checked ? [...rest, ...ids] : rest
}

function resetChecked() {
  checkedIds.value = [...baselineIds.value]
}

async function save() {
  saving.value = true
  try {
    const res = await withSafeAuth(() =>
      assignRolePermissions(current.value.id, [...checkedIds.value])
    )
    if (!res.ok) return
    ElMessage.success('已保存，持有该角色的用户需重新登录生效')
    await load(current.value.roleCode)
  } finally {
    saving.value = false
  }
}

// ---------- 角色成员（统一人员视图按 roleCode 筛选；仅 ADMIN 支持添加/移出） ----------
const PERSON_TYPE_LABELS = { STUDENT: '学生', TEACHER: '老师', ADMIN: '管理员' }
const STATUS_LABELS = { UNFILED: '未建档', NORMAL: '正常', ROSTER_DISABLED: '名单停用', USER_DISABLED: '账号停用' }
const AVATAR_COLORS = ['#7c6cf2', '#4f8ef7', '#2bb673', '#e8a13d', '#e06262', '#3aa7a3', '#b75fd6', '#78909c']

const canBindRole = computed(() => hasPermission('system:user:bind_role', userStore.permissions))
const memberRows = ref([])
const memberTotal = ref(0)
const memberPage = reactive({ pageNum: 1, pageSize: 10 })
const memberLoading = ref(false)
const memberExporting = ref(false)

const memberHint = computed(() => {
  const code = current.value?.roleCode
  if (code === 'ADMIN') return '管理员账号由建档产生；老师可经「添加成员」绑定管理员角色，移出仅对绑定老师生效'
  if (code === 'OPS_ADMIN') return '成员仅 SQL 预置账号，页面仅可查看'
  return '成员由预录名单首登自动建档产生，页面仅可查看'
})

function roleCodesOf(row) {
  return (row?.roles || '').split(',').filter(Boolean)
}

function avatarText(name) {
  return (name || '?').trim().charAt(0) || '?'
}

function avatarColor(name) {
  const s = name || '?'
  let h = 0
  for (const ch of s) {
    h = (h * 31 + ch.codePointAt(0)) >>> 0
  }
  return AVATAR_COLORS[h % AVATAR_COLORS.length]
}

async function loadMembers(resetPage) {
  if (!current.value || current.value.roleCode === 'ASSISTANT') return
  if (resetPage) memberPage.pageNum = 1
  memberLoading.value = true
  try {
    const data = await pagePersons({
      roleCode: current.value.roleCode,
      pageNum: memberPage.pageNum,
      pageSize: memberPage.pageSize
    })
    memberRows.value = data.records
    memberTotal.value = data.total
  } finally {
    memberLoading.value = false
  }
}

watch(activeTab, (v) => {
  if (v === 'members') loadMembers()
})

/** 只刷新左侧角色列表的绑定人数，不重置权限编辑态 */
async function refreshUserCount() {
  const list = await listRoles()
  roles.value = list
  const updated = list.find((r) => r.roleCode === current.value?.roleCode)
  if (updated) current.value.userCount = updated.userCount
}

async function removeMember(row) {
  try {
    await ElMessageBox.confirm('解绑后该老师当前登录会话将立即退出，确定移出吗？', '移出角色成员', { type: 'warning' })
  } catch {
    return
  }
  await updateUserAdminRole(row.userId, false)
  ElMessage.success('已移出')
  loadMembers()
  refreshUserCount()
}

// 添加成员（候选为本组织内已建档老师账号；已持 ADMIN 者禁选标注）
const addVisible = ref(false)
const addKeyword = ref('')
const addRows = ref([])
const addTotal = ref(0)
const addPage = reactive({ pageNum: 1, pageSize: 10 })
const addLoading = ref(false)
const addSubmitting = ref(false)
const addSelection = ref([])

function candidateSelectable(row) {
  return !!row.userId && !roleCodesOf(row).includes('ADMIN')
}

function openAddMembers() {
  addKeyword.value = ''
  addSelection.value = []
  addPage.pageNum = 1
  addVisible.value = true
  loadCandidates(1)
}

async function loadCandidates(resetPage) {
  if (resetPage) addPage.pageNum = 1
  addLoading.value = true
  try {
    const data = await pagePersons({
      personType: 'TEACHER',
      keyword: addKeyword.value || undefined,
      pageNum: addPage.pageNum,
      pageSize: addPage.pageSize
    })
    // 未建档名单无账号可绑，不进入候选
    addRows.value = data.records.filter((r) => r.userId)
    addTotal.value = data.total
  } finally {
    addLoading.value = false
  }
}

async function submitAddMembers() {
  addSubmitting.value = true
  let ok = 0
  let fail = 0
  try {
    for (const row of addSelection.value) {
      try {
        await updateUserAdminRole(row.userId, true)
        ok++
      } catch {
        fail++
      }
    }
  } finally {
    addSubmitting.value = false
  }
  if (fail) {
    ElMessage.warning(`已绑定 ${ok} 人，${fail} 人失败`)
  } else {
    ElMessage.success(`已绑定 ${ok} 人`)
  }
  addVisible.value = false
  addSelection.value = []
  loadMembers()
  refreshUserCount()
}

function saveCsv(blob, prefix) {
  const url = URL.createObjectURL(blob)
  const t = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const a = document.createElement('a')
  a.href = url
  a.download = `${prefix}_${t.getFullYear()}${pad(t.getMonth() + 1)}${pad(t.getDate())}_${pad(t.getHours())}${pad(t.getMinutes())}${pad(t.getSeconds())}.csv`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

async function exportMembers() {
  memberExporting.value = true
  try {
    const all = []
    let page = 1
    for (;;) {
      const data = await pagePersons({ roleCode: current.value.roleCode, pageNum: page, pageSize: 100 })
      all.push(...data.records)
      if (all.length >= data.total || !data.records.length) break
      page++
    }
    const esc = (v) => `"${String(v ?? '').replaceAll('"', '""')}"`
    const lines = all.map((r) => [
      r.realName,
      r.personNo,
      r.accountName,
      r.departmentName,
      PERSON_TYPE_LABELS[r.personType] || r.personType,
      STATUS_LABELS[r.status] || r.status
    ].map(esc).join(','))
    const csv = '\uFEFF' + ['姓名,学工号,登录账号,部门,身份,状态', ...lines].join('\r\n')
    saveCsv(new Blob([csv], { type: 'text/csv;charset=utf-8' }), `${current.value.roleName}成员导出`)
  } finally {
    memberExporting.value = false
  }
}

onMounted(async () => {
  permLoading.value = true
  try {
    allPermissions.value = await listPermissionOptions()
  } finally {
    permLoading.value = false
  }
  await load()
})
</script>

<style scoped>
.role_page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

/* 左侧角色列表 */
.role_page__side {
  width: 240px;
  flex-shrink: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.role_side__header {
  padding: 14px 16px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.role_side__list {
  min-height: 120px;
}

.role_side__item {
  padding: 12px 16px;
  cursor: pointer;
  border-left: 3px solid transparent;
  transition: background 0.15s;
}

.role_side__item:hover {
  background: var(--el-fill-color-light);
}

.role_side__item--active {
  background: var(--el-color-primary-light-9);
  border-left-color: var(--el-color-primary);
}

.role_side__name {
  font-size: 14px;
  font-weight: 500;
}

.role_side__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role_side__hint {
  margin: 0;
  padding: 10px 16px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 右侧配置 */
.role_page__main {
  flex: 1;
  min-width: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px 20px;
}

.role_main__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.role_main__title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.role_main__meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role_main__alert {
  margin: 12px 0 0;
}

/* 权限分组 */
.perm_group {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 12px;
}

.perm_group__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
  border-radius: 8px 8px 0 0;
}

.perm_group__title {
  font-size: 14px;
  font-weight: 600;
}

.perm_group__count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.perm_group__body {
  padding: 0 16px;
}

.perm_row {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.perm_row:last-child {
  border-bottom: none;
}

.perm_row__name {
  width: 110px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.perm_row__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 20px;
}

.role_main__menu_hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 角色成员页签 */
.role_members__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.role_members__hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role_members__actions {
  display: flex;
  gap: 10px;
}

.role_members__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.role_members__person {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role_members__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
}

.role_members__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 添加成员对话框 */
.role_add__tip {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.role_add__bar {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}

.role_add__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
