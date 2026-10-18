package io.github.kintil555.bedrockpackloader.animation;

/** Receives the time values of the animation controller that is about to evaluate Molang ({@code q.state_time} and friends). */
public interface ControllerTimes {
    void setControllerTimes(double stateTime, double animTime, boolean anyAnimationFinished, boolean allAnimationsFinished);
}
