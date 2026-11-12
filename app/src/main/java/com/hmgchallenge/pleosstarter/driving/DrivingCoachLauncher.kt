package com.hmgchallenge.pleosstarter.driving

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.hmgchallenge.pleosstarter.quests.CoachCommand

/** Quest screen entry into the actual coach Activity, replacing its old placeholder controls. */
class DrivingCoachLauncher : DrivingGuidePort {
    private var model: GuideUiModel?=null
    private var label: TextView?=null
    override fun createView(context: Context, host: DrivingGuideHost): View = LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL
        label=TextView(context).apply {setTextColor(Color.WHITE);textSize=17f};addView(label)
        addView(Button(context).apply {
            text="현재 목표로 주행 안내 열기";tag="open_driving_coach";isAllCaps=false;minHeight=(56*resources.displayMetrics.density).toInt()
            setOnClickListener {
                val current=model ?: return@setOnClickListener
                context.startActivity(Intent(context,DrivingCoachActivity::class.java)
                    .putExtra("live_mode",!current.demo)
                    .putExtra("quest_trip_id",current.tripId.takeIf { !current.tripFinished }))
            }
        })
        model?.let(::render)
    }
    override fun render(model: GuideUiModel) {
        this.model=model
        label?.text="퀘스트와 주행 안내 연결됨\n${if(model.demo) "행동 버튼 체험 → 시연 프로필에만 기록" else "실제 Pleos 연결 → 관측·미확인 기록 분리"}\n목표와 영역별 안내 설정을 주행 코치에 전달해요."
    }
    override fun onCommand(command: CoachCommand) {
        // The parked quest screen does not play speech. Controls live in the coach Activity.
        label?.append("\n다시 듣기·음성 중단은 주행 안내 화면에서 사용할 수 있어요.")
    }
    override fun release() {label=null}
}
