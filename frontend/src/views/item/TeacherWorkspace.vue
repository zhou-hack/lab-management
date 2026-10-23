<template>
  <div class="teacher_ws">
    <header class="teacher_ws__header">
      <div>
        <h1 class="teacher_ws__title">
          物料预约（教师端）
        </h1>
        <p class="teacher_ws__subtitle">
          发起本人课程的物料预约：创建即视为老师已审，提交后由管理员终审与实发
        </p>
      </div>
    </header>

    <!-- 与管理员端同一 RequestPanel：内容一致，无权限的操作（终审/实发/定额）按权限自动隐藏 -->
    <RequestPanel />
  </div>
</template>

<script setup>
import RequestPanel from './panels/RequestPanel.vue'
import './workspace.css'

/**
 * 教师端工作台：老师登录后的默认落地页，与管理员端共用同一 RequestPanel（内容一致）；
 * 无权限的操作（终审/终审拒绝/实发/定额页签）由 v-permission 与权限判断自动隐藏，
 * 不引入管理员权限（与运行库教师角色绑定 item:request:list / item:request:create 对齐）
 */
</script>

<style scoped>
.teacher_ws {
  max-width: 1680px;
  margin: 0 auto;
  padding: 20px 24px 32px;
}

.teacher_ws__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.teacher_ws__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.teacher_ws__subtitle {
  margin: 6px 0 0;
  font-size: 12px;
  color: #909399;
}
</style>
