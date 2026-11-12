package com.hmgchallenge.pleosstarter.driving

enum class ParkingStyle(val label: String) { REVERSE("후진 주차"), FORWARD("전면 주차"), PARALLEL("평행 주차") }
data class ParkingStep(val title: String, val text: String, val lookAt: String)

/** Illustrations teach a sequence; they do not track geometry or prescribe fixed steering turns. */
object ParkingLesson {
    fun steps(style: ParkingStyle): List<ParkingStep> = listOf(
        ParkingStep("주변부터 살펴봐요", "사람과 장애물, 주차 공간의 여유를 직접 확인해 주세요.", "차량 주변 전체"),
        ParkingStep("시작 위치를 이해해요", when(style) {
            ParkingStyle.REVERSE -> "주차면과 차량의 위치 관계를 그림으로 살펴봐요. 실제 시작 위치는 공간과 차량에 따라 달라져요."
            ParkingStyle.FORWARD -> "앞으로 진입할 공간과 회전할 여유를 살펴봐요."
            ParkingStyle.PARALLEL -> "앞뒤 차량 사이의 공간과 옆 간격을 살펴봐요."
        }, "주차선과 차량의 위치"),
        ParkingStep("진입 흐름을 살펴봐요", when(style) {
            ParkingStyle.REVERSE -> "후진할 때 차체 방향이 어떻게 달라지는지 살펴봐요. 실제로 움직일 때는 직접 주변을 확인해 주세요."
            ParkingStyle.FORWARD -> "앞부분이 들어갈 때 바깥쪽과 안쪽 공간을 함께 확인해 주세요."
            ParkingStyle.PARALLEL -> "뒤쪽이 공간에 들어오는 흐름을 살펴봐요. 차량 모서리와 주변 간격은 직접 확인해 주세요."
        }, "진행 방향과 차량 모서리"),
        ParkingStep("다시 맞춰도 괜찮아요", "차체와 주차선의 관계를 확인해 주세요. 필요하면 멈추고 다시 조정해도 괜찮아요.", "차체 정렬과 양옆 여유"),
        ParkingStep("마지막 확인까지", "차량을 완전히 멈춘 뒤 기어 P, 주차 브레이크와 경고 표시를 직접 확인해 주세요.", "차량 계기판과 하차할 방향")
    )
}
