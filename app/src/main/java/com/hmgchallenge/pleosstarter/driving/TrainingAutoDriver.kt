package com.hmgchallenge.pleosstarter.driving

/** Authored playback for the virtual model only. It follows the same path that is drawn. */
class TrainingAutoDriver {
    private var priorStage: SimStage?=null
    private var elapsed=0.0
    fun update(model: DrivingSimulator,seconds: Double) {
        if(!seconds.isFinite() || seconds<=0)return
        if(model.stage!=priorStage) {priorStage=model.stage;elapsed=0.0}
        elapsed+=seconds.coerceAtMost(0.1)
        when(model.stage) {
            SimStage.PREPARE->{
                model.pedals(0.0,80.0)
                if(elapsed>=0.6)model.belt=true
                if(elapsed>=1.2 && model.rain)model.wiper=2
                if(elapsed>=1.8)model.setParkingBrake(false)
                if(elapsed>=2.4)model.selectGear(SimGear.D)
            }
            SimStage.PARKING_EXIT->{model.steer(0.0);if(model.stageTravel>=3)model.pedals(0.0,100.0)else cruise(model)}
            SimStage.EXIT_PREPARE->{
                model.pedals(0.0,80.0);model.selectGear(SimGear.D)
                if(elapsed>=0.6)model.setSignal(model.expectedSignal)
                if(elapsed>=1.4)model.checkMirrors()
            }
            SimStage.EXIT_APPROACH,SimStage.APPROACH->{
                model.steer(model.targetSteering);model.setSignal(model.expectedSignal)
                val remaining=if(model.stage==SimStage.APPROACH)model.approachRemaining else model.exitApproachRemaining
                if(remaining<=model.speed*model.speed/(2*5.2)+0.15)model.pedals(0.0,100.0)else cruise(model)
            }
            SimStage.EXIT_CHECK,SimStage.TURN_CHECK->{
                model.pedals(0.0,80.0);model.setSignal(model.expectedSignal)
                // Authored signals and pedestrians, never camera recognition.
                if(elapsed>=1.2)model.green=true
                if(elapsed>=2.4)model.pedestrian=false
                if(elapsed>=3.2)model.checkMirrors()
            }
            SimStage.EXIT_TURN,SimStage.LANE_OUT,SimStage.LANE_RETURN,SimStage.TURN,
            SimStage.EXIT_ALIGN,SimStage.TURN_ALIGN->{model.steer(model.targetSteering);cruise(model)}
            SimStage.LANE_PREPARE->{
                model.steer(model.targetSteering);cruise(model)
                if(elapsed>=0.4)model.setSignal(model.expectedSignal)
                if(elapsed>=1.2)model.checkMirrors()
            }
            SimStage.FINAL_STOP->{
                model.steer(model.targetSteering)
                if(model.stageTravel>=6)model.pedals(0.0,100.0)else cruise(model)
            }
            SimStage.SECURE->{
                model.pedals(0.0,80.0);model.selectGear(SimGear.P)
                if(elapsed>=0.6)model.setParkingBrake(true)
                if(elapsed>=1.2)model.setSignal(SimSignal.OFF)
                if(elapsed>=1.8)model.checkMirrors()
            }
            SimStage.COMPLETE->model.freeze()
        }
    }
    private fun cruise(model: DrivingSimulator) {
        when {
            model.speed>1.9->model.pedals(0.0,20.0)
            model.speed>=1.8->model.pedals(12.5,0.0)
            else->model.pedals(35.0,0.0)
        }
    }
}
