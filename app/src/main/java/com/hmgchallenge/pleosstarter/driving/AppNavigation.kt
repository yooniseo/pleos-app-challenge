package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.hmgchallenge.pleosstarter.quests.QuestActivity
import java.lang.ref.WeakReference

/** One app, four destinations. Existing screen colours/content stay owned by each screen. */
object AppNavigation {
    private var installed=false
    private val handler=Handler(Looper.getMainLooper())
    private val resumed=mutableSetOf<Activity>()
    private var owner=WeakReference<DrivingSimulatorActivity>(null)
    private var transitionUntil=0L
    val internalTransition get()=SystemClock.elapsedRealtime()<transitionUntil
    private val suspendApp=Runnable {if(resumed.isEmpty())owner.get()?.suspendInternalSession()}
    fun diagnostics()=resumed.joinToString {it.localClassName}

    private fun install(activity: Activity) {
        if(installed)return
        installed=true
        activity.application.registerActivityLifecycleCallbacks(object: Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                resumed.add(a);transitionUntil=0;handler.removeCallbacks(suspendApp)
                owner.get()?.takeUnless {it.isFinishing || it.isDestroyed}?.resumeInternalSession()
            }
            override fun onActivityPaused(a: Activity) {
                resumed.remove(a)
                if(resumed.isEmpty())handler.postDelayed(suspendApp,650)
            }
            override fun onActivityStopped(a: Activity) {
                resumed.remove(a)
                if(resumed.isEmpty())handler.postDelayed(suspendApp,650)
            }
            override fun onActivityDestroyed(a: Activity) {
                resumed.remove(a)
                if(owner.get()===a)owner.clear()
            }
            override fun onActivityCreated(a: Activity,s: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity,s: Bundle) {}
        })
    }
    fun own(activity: DrivingSimulatorActivity) {install(activity);owner=WeakReference(activity)}
    fun forget(activity: DrivingSimulatorActivity) {if(owner.get()===activity)owner.clear()}
    fun content(activity: Activity,view: View,selected: String,dark: Boolean=false) {
        install(activity)
        val scale=activity.resources.displayMetrics.density
        fun dp(v: Int)=(v*scale).toInt()
        val root=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL}
        root.addView(view,LinearLayout.LayoutParams(-1,0,1f))
        val bar=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL;setPadding(dp(8),dp(4),dp(8),dp(4))
            setBackgroundColor(if(dark)Color.rgb(13,25,34) else Color.WHITE)
            tag="app_navigation"
        }
        listOf("drive" to "주행", "quests" to "퀘스트", "records" to "기록", "more" to "더보기").forEach {(key,title)->
            bar.addView(Button(activity).apply {
                text=title;tag="app_tab_$key";contentDescription="$title 화면 열기"
                isAllCaps=false;textSize=16f;minHeight=dp(48);minimumHeight=dp(48)
                setTextColor(if(key==selected)Color.rgb(108,76,247) else if(dark)Color.WHITE else Color.rgb(65,63,78))
                backgroundTintList=android.content.res.ColorStateList.valueOf(if(key==selected)Color.rgb(236,230,255) else if(dark)Color.rgb(25,43,54) else Color.rgb(247,247,250))
                setOnClickListener {when(key){"drive"->drive(activity);"quests"->quests(activity);"records"->records(activity);else->more(activity)}}
            },LinearLayout.LayoutParams(0,dp(52),1f).apply {marginEnd=dp(5)})
        }
        root.addView(bar,LinearLayout.LayoutParams(-1,-2));activity.setContentView(root)
    }
    private fun launch(activity: Activity,intent: Intent) {
        transitionUntil=SystemClock.elapsedRealtime()+2500
        activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
    fun drive(activity: Activity,controls: Boolean=false) {
        if(activity is DrivingSimulatorActivity) {activity.openInternalView(controls);return}
        launch(activity,Intent(activity,DrivingSimulatorActivity::class.java).putExtra("app_view",if(controls)"controls" else "map").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    fun quests(activity: Activity,tab: Int=0) {
        launch(activity,Intent(activity,QuestActivity::class.java).putExtra("quest_demo",true).putExtra("quest_tab",tab))
    }
    fun records(activity: Activity) {launch(activity,Intent(activity,PracticeRecordsActivity::class.java))}
    private fun more(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("차근차근 · 더보기")
            .setItems(arrayOf("튜토리얼 · 주차와 운전 순서 익히기","코치 설정 · 안내량과 도움 선택","운전 조작 · 핸들·페달 직접 체험","앱 사용법 · 어디서 무엇을 하나요?")) {_,which->
                when(which) {
                    0->launch(activity,Intent(activity,DrivingCoachActivity::class.java).putExtra("app_page","parking"))
                    1->quests(activity,1)
                    2->drive(activity,true)
                    else->AlertDialog.Builder(activity).setTitle("이렇게 사용해요")
                        .setMessage("주행: 지도에서 연습 시작을 눌러요.\n퀘스트: 지금까지의 연습과 도움 단계를 봐요.\n기록: 주행 후 메모와 피드백을 돌아봐요.\n더보기: 튜토리얼·코치 설정·운전 조작을 열어요.\n\n앱 안에서 화면을 옮겨도 진행 중인 연습과 기록은 이어져요. 앱을 벗어나면 연습을 잠시 멈춰요.")
                        .setPositiveButton("알겠어요",null).show()
                }
            }.setNegativeButton("닫기",null).show()
    }
}
