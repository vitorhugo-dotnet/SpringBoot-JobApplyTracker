package com.jobtracker.dto.gamification;

import com.jobtracker.entity.enums.GamificationEventType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aggregated result returned by the MCP gamification tool")
public record GamificationEventSummary(
        @Schema(description = "Processed event type")
        GamificationEventType eventType,
        @Schema(description = "XP awarded for this event")
        int xpGained,
        @Schema(description = "Whether the event caused a level up")
        boolean leveledUp,
        @Schema(description = "New level after the event")
        int newLevel,
        @Schema(description = "Current streak in days")
        int streakDays,
        @Schema(description = "Achievement codes newly unlocked during this event")
        List<String> newlyUnlockedAchievements,
        @Schema(description = "Human-readable feedback")
        String message,
        @Schema(description = "Full updated profile snapshot")
        GamificationProfileResponse profile
) {}
