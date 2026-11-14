package com.hmgchallenge.pleosstarter.ui

import android.content.Context
import android.graphics.*
import com.hmgchallenge.pleosstarter.driving.*
import com.hmgchallenge.pleosstarter.session.ReplaySessionController

class ParkingCanvas(context: Context,assets: ProductIllustrations,private val session: ReplaySessionController): SceneCanvas(context,assets) {
    private val preview=SimpleRoadScenario(session.course,RoadLesson.PARKING)
    private val wheel=SteeringWheelRenderer()
    override fun drawScene(c: Canvas) {
        c.drawColor(Color.rgb(174,174,174))
        val s=session.scenario.takeIf {it.lesson==RoadLesson.PARKING} ?: preview
        val step=when {s.complete->4;s.distance<5->1;s.parkingWaitMs<2000->2;s.distance<s.endDistance-3->3;else->4}
        val motion=if(s.started)s.progress.toFloat() else 0f
        assets.car(c,RectF(425f+40*motion,223f+70*motion,1233f+40*motion,995f+70*motion),true)
        assets.car(c,RectF(-373f,121f,435f,893f),true);assets.car(c,RectF(1263f,121f,2071f,893f),true)
        paint.shader=LinearGradient(0f,660f,0f,1184f,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP)
        c.drawRect(0f,660f,1700f,1184f,paint);paint.shader=null
        // Keep the functional coaching wheel in full view, rather than clipping a static ring.
        wheel.draw(c,850f,855f,470f,s.steeringTarget,true)
        assets.steeringArrow(c,674f,620f,352f,68f,s.steeringTarget)
        box(c,55f,156f,330f,698f,Color.argb(240,20,25,31),28f)
        text(c,"주차 진행 단계  $step / 4",87f,173f,27f,Color.WHITE,true)
        val ys=listOf(248f,334f,575f,660f)
        listOf("① 공간 확인","② 핸들 조향","③ 천천히 후진","④ 정렬 완료").forEachIndexed {i,title->
            box(c,83f,ys[i],245f,59f,if(i+1==step)Palette.lime else if(i+1<step)Color.rgb(84,115,104) else Color.rgb(52,63,77),28f)
            text(c,title,97f,ys[i]+12,25f,if(i+1==step)Color.rgb(28,40,20) else Color.WHITE,i+1==step)
        }
        text(c,"STEP 0$step",97f,407f,26f,Palette.lime,true)
        text(c,listOf("주차칸 옆에\n천천히 맞춰요","멈춘 뒤 뒤쪽과\n양옆을 확인해요","차량 후면을\n주차선과 나란하게","핸들을 풀고\n정렬을 마무리해요")[step-1],97f,450f,25f,Color.WHITE,true)
        box(c,1210f,40f,355f,84f,Color.argb(242,20,25,31),42f)
        text(c,"후진 주차  STEP 0$step",1240f,60f,29f,Palette.lime,true)
        text(c,"가이드에 맞춰 천천히 움직여 주세요",490f,73f,28f,Color.WHITE)
        box(c,1210f,365f,435f,360f,Color.argb(235,20,25,31),24f)
        text(c,when(step){1->"주차칸 옆에\n위치를 맞춰요";2->"뒤쪽 공간을\n확인하세요";4->"핸들을 풀어\n가운데로!";else->"천천히 후진하며\n그림을 확인해요"},1240f,405f,36f,Color.WHITE,true)
        text(c,"천천히 확인해요",1240f,580f,29f,Color.rgb(174,186,203))
        text(c,"시연용 핸들 · 실제 조향량 아님",1240f,650f,21f,Color.rgb(174,186,203))
        box(c,577f,1018f,546f,60f,Color.argb(220,20,25,31),24f)
        listOf("R","N","D").forEachIndexed {i,gear->text(c,gear,673f+145*i,1024f,34f,if(s.parkingGear==gear)Palette.lime else Color.rgb(174,186,203),true)}
        if(s.parkingGear=="P")text(c,"P",1135f,1024f,34f,Palette.lime,true)
        text(c,"DEMO · 가상 주차 연습 · 실제 차량 제어 없음",60f,1045f,22f,Color.WHITE)
    }
}
