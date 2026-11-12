package com.hmgchallenge.pleosstarter.driving

/** Unknown values remain unknown. A brake pedal is not a parking brake. */
data class VehicleCoachState(val gear: String?=null,val belt: Boolean?=null,val parkingBrake: Boolean?=null) {
    fun summary()="기어 ${gear ?: "미확인"}  ·  앞좌석 왼쪽 벨트 ${state(belt,"착용","미착용")}  ·  주차 브레이크 ${state(parkingBrake,"체결","해제") }"
    fun cue(stopped: Boolean): String = when {
        !stopped -> "주행 중에는 도로에 집중해요. 자세한 기록은 정차 후 확인해요."
        gear=="R" -> "후진 준비 · 미러와 뒤쪽 주변을 직접 확인해 주세요."
        gear=="P" && parkingBrake==true -> "주차 마무리 · 내리기 전 뒤에서 오는 차량과 사람을 확인해요."
        gear=="P" -> "출발 전 벨트와 주차 브레이크 상태를 확인해요."
        belt==false -> "출발 전 안전벨트를 착용해 주세요."
        else -> "차량 상태를 확인하며 준비해요."
    }
    private fun state(value: Boolean?,yes: String,no: String)=when(value) {true->yes;false->no;null->"미확인"}
}
