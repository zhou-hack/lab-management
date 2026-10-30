package com.labmanage.module.item.dto;

import com.labmanage.common.core.BasePageQuery;

public class NotificationPageQuery extends BasePageQuery {

    private Boolean unread;

    public Boolean getUnread() { return unread; }
    public void setUnread(Boolean unread) { this.unread = unread; }
}
