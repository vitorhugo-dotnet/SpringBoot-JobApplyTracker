package com.jobtracker.gamification;

/**
 * Callback fired by GamificationService at key milestones during event processing.
 * The MCP layer implements this to translate milestones into protocol progress notifications.
 */
@FunctionalInterface
public interface GamificationProgressCallback {

    /**
     * @param step    1-based step number
     * @param total   total expected steps
     * @param message human-readable description of the milestone
     */
    void onStep(int step, int total, String message);
}
