<template>
  <el-drawer
    :model-value="store.drawerVisible"
    title="通知消息"
    size="420px"
    @close="store.closeDrawer()"
  >
    <div class="notification_drawer">
      <div class="notification_drawer__toolbar">
        <el-switch
          v-model="unreadOnly"
          active-text="仅看未读"
          inline-prompt
        />
        <el-button
          text
          :disabled="!store.unreadCount || store.readAllLoading"
          :loading="store.readAllLoading"
          @click="markAllRead"
        >
          全部已读
        </el-button>
      </div>

      <div
        v-loading="store.loading"
        class="notification_drawer__body"
      >
        <el-empty
          v-if="!store.notifications.length"
          :description="store.unreadOnly ? '暂无未读通知' : '暂无通知'"
          :image-size="72"
        />
        <article
          v-for="item in store.notifications"
          :key="item.id"
          class="notification_item"
          :class="{ 'notification_item--unread': !item.readAt }"
          @click="openNotification(item)"
        >
          <div class="notification_item__head">
            <span class="notification_item__title">{{ item.title }}</span>
            <span
              v-if="!item.readAt"
              class="notification_item__dot"
              aria-label="未读"
            />
          </div>
          <p class="notification_item__content">
            {{ item.content }}
          </p>
          <div class="notification_item__meta">
            <span>{{ getNotificationLabel(item) }}</span>
            <time>{{ formatInstant(item.createTime) }}</time>
          </div>
          <div class="notification_item__actions">
            <el-button
              text
              type="primary"
              @click.stop="openNotification(item)"
            >
              查看详情
            </el-button>
            <el-button
              v-if="!item.readAt && store.canRead"
              text
              :loading="store.readingId === String(item.id)"
              @click.stop="markRead(item.id)"
            >
              标记已读
            </el-button>
          </div>
        </article>
      </div>

      <el-pagination
        v-if="store.total > store.pageSize"
        class="notification_drawer__pager"
        small
        layout="prev, pager, next, total"
        :current-page="store.pageNum"
        :page-size="store.pageSize"
        :total="store.total"
        @current-change="store.changePage"
      />
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '@/store/notification'
import { useUserStore } from '@/store/user'
import { formatInstant } from '@/utils/datetime'
import { getNotificationLabel, getNotificationRoute } from './notification.rules'

const store = useNotificationStore()
const userStore = useUserStore()
const router = useRouter()

const unreadOnly = computed({
  get: () => store.unreadOnly,
  set: (value) => store.changeUnreadOnly(value)
})

async function markRead(id) {
  if (await store.read(id)) ElMessage.success('已标记为已读')
}

async function markAllRead() {
  if (await store.readAll()) ElMessage.success('全部通知已标记为已读')
}

async function openNotification(item) {
  if (!item.readAt && store.canRead) await store.read(item.id)
  await router.push(getNotificationRoute(item, userStore.permissions))
  store.closeDrawer()
}
</script>

<style scoped>
.notification_drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.notification_drawer__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: .5px solid var(--hair);
}

.notification_drawer__body {
  flex: 1;
  min-height: 180px;
  overflow-y: auto;
}

.notification_item {
  padding: 14px 2px;
  border-bottom: .5px solid var(--hair);
  cursor: pointer;
}

.notification_item--unread {
  background: var(--brand-a04, rgba(63, 134, 255, .04));
}

.notification_item__head,
.notification_item__meta,
.notification_item__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notification_item__title {
  font-weight: var(--fw-medium);
  color: var(--g1);
}

.notification_item__dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--brand);
}

.notification_item__content {
  margin: 8px 0;
  color: var(--g2);
  font-size: 13px;
  line-height: 1.6;
}

.notification_item__meta {
  color: var(--g3);
  font-size: 12px;
}

.notification_item__actions {
  justify-content: flex-end;
}

.notification_drawer__pager {
  justify-content: center;
  padding-top: 14px;
}
</style>
