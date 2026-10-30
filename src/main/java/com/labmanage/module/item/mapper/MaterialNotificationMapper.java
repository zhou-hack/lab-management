package com.labmanage.module.item.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmanage.module.item.entity.MaterialNotification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface MaterialNotificationMapper extends BaseMapper<MaterialNotification> {

    @Insert("""
            INSERT INTO material_notification (
                id, dedupe_key, recipient_user_id, notification_type, notification_stage,
                reminder_date, claim_id, material_document_id, biz_document_id,
                title, content, read_at, create_time, update_time, deleted,
                create_user_id, update_user_id
            ) VALUES (
                #{id}, #{dedupeKey}, #{recipientUserId}, #{notificationType}, #{notificationStage},
                #{reminderDate}, #{claimId}, #{materialDocumentId}, #{bizDocumentId},
                #{title}, #{content}, #{readAt}, #{createTime}, #{updateTime}, 0, NULL, NULL
            )
            """)
    int insertSystemNotification(MaterialNotification notification);

    @Update("""
            UPDATE material_notification SET read_at = #{readAt}
            WHERE id = #{id} AND recipient_user_id = #{recipientUserId}
              AND read_at IS NULL AND deleted = 0
            """)
    int markRead(@Param("id") Long id,
                 @Param("recipientUserId") Long recipientUserId,
                 @Param("readAt") Instant readAt);

    @Update("""
            UPDATE material_notification SET read_at = #{readAt}
            WHERE recipient_user_id = #{recipientUserId} AND read_at IS NULL AND deleted = 0
            """)
    int markAllRead(@Param("recipientUserId") Long recipientUserId,
                    @Param("readAt") Instant readAt);
}
