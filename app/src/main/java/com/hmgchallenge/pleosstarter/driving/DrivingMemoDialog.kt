package com.hmgchallenge.pleosstarter.driving

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.widget.*
import com.hmgchallenge.pleosstarter.SttProbe

/** STT final text stays editable; only an explicit Save persists a memo. Buttons work without STT. */
class DrivingMemoDialog(private val activity: Activity,private val saved: (String,String,Boolean)->Boolean,
                        private val closed: ()->Unit) {
    private val text=EditText(activity).apply {hint="어려웠거나 기억하고 싶은 순간";minLines=2;tag="memo_text"}
    private val status=TextView(activity)
    private val difficult=CheckBox(activity).apply {this.text="이번 상황이 어려웠어요";tag="memo_difficult"}
    private val categories=listOf("차로 변경" to "LANE_CHANGE","우회전" to "RIGHT_TURN","주차" to "PARKING","속도 조절" to "SPEED_CONTROL","기타" to "OTHER")
    private val category=Spinner(activity).apply {adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,categories.map {it.first})}
    private var probe: SttProbe?=null
    private var dialog: AlertDialog?=null
    fun show(defaultCategory: String) {
        category.setSelection(categories.indexOfFirst {it.second==defaultCategory}.coerceAtLeast(0))
        val view=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL;setPadding(30,16,30,16)
            addView(category);addView(text);addView(difficult)
            addView(Button(activity).apply {this.text="음성으로 입력";tag="memo_stt";setOnClickListener {listen()}})
            addView(status)
        }
        dialog=AlertDialog.Builder(activity).setTitle("기억할 순간 남기기").setView(view)
            .setNegativeButton("닫기",null).setPositiveButton("저장",null).create().also {d->
                d.setOnShowListener {d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value=text.text.toString().trim()
                    if(value.isBlank()){text.error="내용을 입력하거나 음성으로 남겨주세요";return@setOnClickListener}
                    if(saved(value,categories[category.selectedItemPosition].second,difficult.isChecked))d.dismiss() else status.text="저장하지 못했어요. 다시 시도해 주세요"
                }}
                d.setOnDismissListener {probe?.release();probe=null;closed()};d.show()
            }
    }
    private fun listen() {
        val info=runCatching {activity.packageManager.getPermissionInfo(PERMISSION,0)}.getOrNull()
        if(info==null){status.text="이 환경은 음성 입력을 지원하지 않아요. 글로 메모할 수 있어요";return}
        if(activity.checkSelfPermission(PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
            if(info.protection==android.content.pm.PermissionInfo.PROTECTION_DANGEROUS)activity.requestPermissions(arrayOf(PERMISSION),REQUEST)
            else status.text="음성 입력 권한이 없어요. 글로 메모할 수 있어요"
            return
        }
        if(probe!=null){probe?.request();return}
        probe=SttProbe(activity,{status.text=it},{value->text.setText(value);status.text="인식한 내용을 확인한 뒤 저장해 주세요"},{probe?.request()}).also {it.start()}
    }
    fun onPermissionResult(request: Int,granted: Boolean) {if(request==REQUEST) {if(granted)listen() else status.text="음성 권한 없이도 글로 메모할 수 있어요"}}
    fun dismiss() {dialog?.dismiss();dialog=null}
    companion object {const val REQUEST=831;private const val PERMISSION="pleos.car.permission.STT_SERVICE"}
}
