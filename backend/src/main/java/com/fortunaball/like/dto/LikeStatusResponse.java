package com.fortunaball.like.dto;

/**
 * 좋아요 현황. total: 서비스 전체 좋아요 수, todayCount: 이 사람이 오늘 누른 횟수,
 * dailyLimit: 하루에 누를 수 있는 최대 횟수, remaining: 오늘 더 누를 수 있는 횟수.
 */
public record LikeStatusResponse(long total, int todayCount, int dailyLimit, int remaining) {
}
