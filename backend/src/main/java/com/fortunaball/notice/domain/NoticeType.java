package com.fortunaball.notice.domain;

/** 공지 종류. DB(notice.notice_type)에는 글자 그대로(GENERAL / IMPORTANT / MAINTENANCE) 저장한다. */
public enum NoticeType {
    /** 일반 공지 */
    GENERAL,
    /** 중요 공지 (화면에서 강조) */
    IMPORTANT,
    /** 점검 공지 */
    MAINTENANCE
}
