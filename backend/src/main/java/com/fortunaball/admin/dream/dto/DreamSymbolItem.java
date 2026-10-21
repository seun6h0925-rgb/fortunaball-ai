package com.fortunaball.admin.dream.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 꿈 상징 1건: 별칭·추천 번호·출처·최근 수정 이력(최대 10건, 최신순)을 함께 담는다. */
public record DreamSymbolItem(long id, String keyword, String category, String description, boolean active,
                              List<String> aliases, List<DreamNumberItem> numbers, List<DreamSourceItem> sources,
                              List<DreamHistoryItem> history, LocalDateTime updatedAt) {
}
