<template>
  <div class="role_page">
    <el-card class="role_page__card">
      <p class="role_page__hint t-hint">
        角色与菜单绑定由系统预置维护，不开放页面增删；运维管理员可调整除 OPS_ADMIN 外角色的权限绑定（保存后该角色用户需重新登录生效）；ASSISTANT 角色由有效助理授权动态计算，不做持久化绑定。
      </p>
    </el-card>

    <el-card class="role_page__card">
      <el-table
        v-loading="loading"
        :data="roles"
      >
        <el-table-column
          prop="roleCode"
          label="角色码"
          width="130"
        />
        <el-table-column
          prop="roleName"
          label="角色名称"
          min-width="120"
        />
        <el-table-column
          label="状态"
          width="90"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled ? 'success' : 'warning'"
              size="small"
            >
              {{ row.enabled ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="userCount"
          label="绑定用户"
          width="100"
        />
        <el-table-column
          label="权限数"
          width="90"
        >
          <template #default="{ row }">
            {{ row.permissions.length }}
          </template>
        </el-table-column>
        <el-table-column
          label="菜单数"
          width="90"
        >
          <template #default="{ row }">
            {{ row.menus.length }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="190"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openDetail(row)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="row.roleCode !== 'OPS_ADMIN'"
              v-permission="'system:role:assign'"
              link
              type="warning"
              @click="openAssign(row)"
            >
              编辑权限
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色" />
        </template>
      </el-table>
    </el-card>

    <!-- 角色详情 -->
    <el-dialog
      v-model="detailVisible"
      :title="detail ? `角色详情：${detail.roleName}（${detail.roleCode}）` : '角色详情'"
      width="640px"
      destroy-on-close
    >
      <template v-if="detail">
        <h4 class="role_page__section">
          权限标识（{{ detail.permissions.length }}）
        </h4>
        <el-empty
          v-if="!detail.permissions.length"
          description="未绑定权限"
          :image-size="60"
        />
        <div
          v-for="(items, module) in groupedPermissions"
          :key="module"
          class="role_page__module"
        >
          <p class="role_page__module_name">
            {{ module }}
          </p>
          <el-tag
            v-for="p in items"
            :key="p.permissionCode"
            class="role_page__perm"
            size="small"
            :title="p.permissionName"
          >
            {{ p.permissionCode }}
          </el-tag>
        </div>

        <h4 class="role_page__section">
          菜单绑定（{{ detail.menus.length }}）
        </h4>
        <el-empty
          v-if="!detail.menus.length"
          description="未绑定菜单"
          :image-size="60"
        />
        <el-table
          v-else
          :data="detail.menus"
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
      </template>
    </el-dialog>

    <!-- 编辑角色权限 -->
    <el-dialog
      v-model="assignVisible"
      :title="assignRow ? `编辑权限：${assignRow.roleName}（${assignRow.roleCode}）` : '编辑权限'"
      width="560px"
      destroy-on-close
    >
      <p class="role_page__assign_hint">
        勾选该角色拥有的权限。保存后持有该角色的用户将被强制退出，重新登录后生效；该操作需二次密码确认。
      </p>
      <el-tree
        ref="assignTreeRef"
        v-loading="assignLoading"
        :data="assignTree"
        node-key="key"
        show-checkbox
        default-expand-all
        :props="{ label: 'label', children: 'children' }"
        class="role_page__assign_tree"
      />
      <template #footer>
        <el-button @click="assignVisible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="assignSaving"
          @click="submitAssign"
        >
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignRolePermissions, listPermissionOptions, listRoles } from '@/api/system/role'

const roles = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    roles.value = await listRoles()
  } finally {
    loading.value = false
  }
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref(null)

const groupedPermissions = computed(() => {
  const groups = {}
  for (const p of detail.value?.permissions || []) {
    const key = p.moduleCode || '其他'
    if (!groups[key]) groups[key] = []
    groups[key].push(p)
  }
  return groups
})

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

// ---------- 编辑权限 ----------
const assignVisible = ref(false)
const assignLoading = ref(false)
const assignSaving = ref(false)
const assignTreeRef = ref(null)
const assignRow = ref(null)
const allPermissions = ref([])

const assignTree = computed(() => {
  const groups = {}
  for (const p of allPermissions.value) {
    const key = p.moduleCode || '其他'
    if (!groups[key]) groups[key] = []
    groups[key].push({ key: `p:${p.id}`, label: `${p.permissionCode}（${p.permissionName}）` })
  }
  return Object.entries(groups).map(([module, children]) => ({
    key: `m:${module}`,
    label: module,
    children
  }))
})

async function openAssign(row) {
  assignRow.value = row
  assignVisible.value = true
  assignLoading.value = true
  try {
    if (!allPermissions.value.length) {
      allPermissions.value = await listPermissionOptions()
    }
    await nextTick()
    assignTreeRef.value?.setCheckedKeys(row.permissions.map(p => `p:${p.id}`))
  } finally {
    assignLoading.value = false
  }
}

async function submitAssign() {
  const keys = assignTreeRef.value?.getCheckedKeys(true) || []
  const permissionIds = keys
    .filter(k => String(k).startsWith('p:'))
    .map(k => String(k).slice(2))
  assignSaving.value = true
  try {
    await assignRolePermissions(assignRow.value.id, permissionIds)
    ElMessage.success('已保存，持有该角色的用户需重新登录生效')
    assignVisible.value = false
    load()
  } finally {
    assignSaving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.role_page__card + .role_page__card {
  margin-top: 14px;
}

.role_page__hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
}

.role_page__section {
  margin: 6px 0 10px;
  font-size: 14px;
}

.role_page__section + .el-empty {
  padding: 10px 0;
}

.role_page__module {
  margin-bottom: 10px;
}

.role_page__module_name {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.role_page__perm {
  margin: 0 6px 6px 0;
}

.role_page__assign_hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--el-text-color-secondary);
}

.role_page__assign_tree {
  max-height: 46vh;
  overflow: auto;
}
</style>
