package com.hmgchallenge.pleosstarter.driving

/** User-entered assumptions for the same intersection; never camera or vehicle observations. */
enum class DemoDriveStep(val title: String, val action: String, val instruction: String) {
    SIGNAL("방향지시등", "방향지시등 켜기", "오른쪽 방향지시등을 켰다고 입력해 보세요."),
    MIRROR("미러·사각지대", "미러·사각지대 확인 입력", "백미러·사이드미러와 사각지대를 확인했다고 입력해요. 카메라 자동 판정은 하지 않아요."),
    LANE_CHANGE("차로 변경", "차로 변경 입력", "주변 확인을 마친 상황을 가정하고 오른쪽 차로로 이동한 장면을 체험해요."),
    STOP("신호 앞 정지", "일시 정지 입력", "작은 진입로는 지나가고, 표시된 교차로에서 신호 앞에 멈춘 상황을 가정해요."),
    SURROUNDINGS("신호·보행자 확인", "신호·보행자 확인 입력", "신호·횡단보도·보행자·다른 차량을 직접 확인했다고 입력해요. 버튼은 통행 가능 판정이 아니에요."),
    TURN("우회전", "우회전 입력", "주변 확인을 마치고 우회전하는 장면을 체험해요. 실제 조향이나 이동을 제어하지 않아요."),
    EXIT("교차로 이탈", "교차로 빠져나오기", "우회전한 같은 교차로를 빠져나오는 장면을 입력해요."),
    FINISHED("체험 완료", "체험 완료", "입력한 행동은 체험 기록으로만 남아요. 실제 주행이나 퀘스트 완료로 인정하지 않아요.")
}
