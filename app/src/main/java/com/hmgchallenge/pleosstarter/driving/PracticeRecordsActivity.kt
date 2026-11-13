package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import com.hmgchallenge.pleosstarter.core.data.SharedCoachRepository
import com.hmgchallenge.pleosstarter.quests.QuestDrivingGuard
import org.json.JSONArray

/** A view of the shared records; opening it never starts another driving session. */
class PracticeRecordsActivity: Activity() {
    private val database by lazy {SharedCoachRepository.get(this)}
    private var guard: QuestDrivingGuard?=null
    private var subscription: java.io.Closeable?=null
    private var restricted=false
    private var selectedId: String?=null
    override fun onCreate(state: Bundle?) {super.onCreate(state);selectedId=state?.getString("trip");render()}
    override fun onStart() {
        super.onStart();subscription=database.observe {if(!isFinishing)render()}
        if(packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            restricted=true;guard=QuestDrivingGuard(this){restricted=it;render()}
        }
        render()
    }
    private fun render() {
        val body=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(28,18,28,20);setBackgroundColor(Color.rgb(13,25,34))}
        fun label(text: String,size: Float=17f)=TextView(this).apply {
            this.text=text;textSize=size;setTextColor(Color.WHITE);setPadding(0,10,0,10);body.addView(this)
        }
        fun button(text: String,tag: String,action: ()->Unit)=Button(this).apply {
            this.text=text;this.tag=tag;textSize=16f;isAllCaps=false;minHeight=56;setOnClickListener {action()};body.addView(this)
        }
        label("연습 돌아보기",26f)
        if(restricted)label("기록과 메모는 안전하게 정차한 뒤 확인해 주세요.")
        else {
            val trips=database.trips()
            if(trips.isEmpty()) {
                label("아직 저장된 연습이 없어요. 지도에서 연습을 시작해 보세요.")
                button("주행 연습 시작","records_start"){AppNavigation.drive(this)}
            } else if(selectedId!=null) {
                val id=selectedId!!;val trip=trips.firstOrNull {it.tripId==id}
                label(if(trip?.endedAt==null)"진행 중인 연습" else "저장된 연습",20f)
                val report=database.report(id)
                fun lines(raw: String): String {val a=JSONArray(raw);return (0 until a.length()).joinToString("\n") {"• ${a.getString(it)}"}}
                if(report!=null){label(report.headline,21f);label("잘한 점\n${lines(report.goodPoints)}");label("다음에 연습할 점\n${lines(report.practicePoints)}")}
                else label("연습 결과를 모으고 있어요. 주행을 마치면 피드백을 볼 수 있어요.")
                val notes=database.notes(id)
                label("기억한 순간 · ${notes.size}개",20f)
                notes.forEach {label(it.rawText)}
                label("체험 기록과 실제 운전 평가는 구분해요.",14f)
                button("전체 기록 보기","records_list"){selectedId=null;render()}
                button("퀘스트에서 확인","records_quests"){AppNavigation.quests(this)}
            } else {
                label("주행 중 쌓인 기록을 여기서 돌아봐요. 기록을 눌러 메모와 피드백을 볼 수 있어요.")
                trips.forEachIndexed {index,trip->
                    val date=java.text.SimpleDateFormat("MM/dd HH:mm",java.util.Locale.KOREA).format(java.util.Date(trip.startedAt))
                    button("$date · ${if(trip.endedAt==null)"진행 중" else "연습 기록"}","record_$index") {
                        database.previewReport(trip.tripId);selectedId=trip.tripId;render()
                    }
                }
            }
        }
        AppNavigation.content(this,ScrollView(this).apply {addView(body)},"records",true)
    }
    override fun onStop() {subscription?.close();subscription=null;guard?.stop();guard=null;super.onStop()}
    override fun onSaveInstanceState(out: Bundle) {out.putString("trip",selectedId);super.onSaveInstanceState(out)}
}
