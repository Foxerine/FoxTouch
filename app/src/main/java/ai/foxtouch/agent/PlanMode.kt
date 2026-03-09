package ai.foxtouch.agent

import com.tinder.StateMachine

sealed class PlanState {
    data object Idle : PlanState()
    data object Exploring : PlanState()
    data class Reviewing(val plan: List<String>) : PlanState()
    data class Executing(val plan: List<String>, val currentStep: Int = 0) : PlanState()
}

sealed class PlanEvent {
    data object EnablePlanMode : PlanEvent()
    data object DisablePlanMode : PlanEvent()
    data class PlanReady(val plan: List<String>) : PlanEvent()
    data object Approve : PlanEvent()
    data object Reject : PlanEvent()
    data object StepComplete : PlanEvent()
    data object AllComplete : PlanEvent()
}

sealed class PlanSideEffect {
    data object RestrictTools : PlanSideEffect()
    data object UnlockTools : PlanSideEffect()
    data object ResetTools : PlanSideEffect()
}

fun createPlanStateMachine() = StateMachine.create<PlanState, PlanEvent, PlanSideEffect> {
    initialState(PlanState.Idle)

    state<PlanState.Idle> {
        on<PlanEvent.EnablePlanMode> {
            transitionTo(PlanState.Exploring, PlanSideEffect.RestrictTools)
        }
    }

    state<PlanState.Exploring> {
        on<PlanEvent.PlanReady> {
            transitionTo(PlanState.Reviewing(it.plan))
        }
        on<PlanEvent.DisablePlanMode> {
            transitionTo(PlanState.Idle, PlanSideEffect.ResetTools)
        }
    }

    state<PlanState.Reviewing> {
        on<PlanEvent.Approve> {
            transitionTo(PlanState.Executing(plan), PlanSideEffect.UnlockTools)
        }
        on<PlanEvent.Reject> {
            transitionTo(PlanState.Idle, PlanSideEffect.ResetTools)
        }
    }

    state<PlanState.Executing> {
        on<PlanEvent.StepComplete> {
            val next = currentStep + 1
            if (next >= plan.size) {
                transitionTo(PlanState.Idle, PlanSideEffect.ResetTools)
            } else {
                transitionTo(PlanState.Executing(plan, next))
            }
        }
        on<PlanEvent.AllComplete> {
            transitionTo(PlanState.Idle, PlanSideEffect.ResetTools)
        }
    }
}
