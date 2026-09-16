package com.fitmate.app.ui

import com.fitmate.app.R

internal data class ExerciseMotion(val id: String, val name: String, val description: String, val video: Int, val poster: Int)
internal val exerciseMotions = listOf(
    ExerciseMotion("30000000-0000-0000-0000-000000000001", "바벨 백 스쿼트", "바벨을 등 위쪽에 얹고 앉았다 일어나는 하체 복합 운동입니다. 허벅지와 엉덩이를 함께 사용하는 동작이에요.", R.raw.exercise_barbell_back_squat, R.drawable.exercise_barbell_back_squat),
    ExerciseMotion("30000000-0000-0000-0000-000000000002", "고블릿 스쿼트", "덤벨 하나를 가슴 앞에 들고 앉았다 일어나는 스쿼트입니다. 덤벨을 사용하는 하체 루틴에 구성할 수 있어요.", R.raw.exercise_goblet_squat, R.drawable.exercise_goblet_squat),
    ExerciseMotion("30000000-0000-0000-0000-000000000003", "레그 프레스", "등을 시트에 댄 상태에서 발판을 밀어내는 머신 운동입니다. 허벅지와 엉덩이가 주로 사용돼요.", R.raw.exercise_leg_press, R.drawable.exercise_leg_press),
    ExerciseMotion("30000000-0000-0000-0000-000000000004", "벤치 프레스", "벤치에 누워 바벨을 가슴 위로 밀어 올리는 운동입니다. 가슴을 중심으로 어깨와 팔 뒤쪽도 함께 사용해요.", R.raw.exercise_bench_press, R.drawable.exercise_bench_press),
    ExerciseMotion("30000000-0000-0000-0000-000000000005", "덤벨 벤치 프레스", "양손에 덤벨을 들고 벤치에 누워 밀어 올리는 운동입니다. 가슴을 중심으로 양팔이 각각 움직여요.", R.raw.exercise_dumbbell_bench_press, R.drawable.exercise_dumbbell_bench_press),
    ExerciseMotion("30000000-0000-0000-0000-000000000006", "푸시업", "바닥을 짚은 상태에서 팔을 굽혔다 펴는 맨몸 운동입니다. 가슴과 팔, 몸통을 함께 사용하는 동작이에요.", R.raw.exercise_push_up, R.drawable.exercise_push_up),
    ExerciseMotion("30000000-0000-0000-0000-000000000007", "랫 풀다운", "머리 위의 케이블 바를 몸 쪽으로 당기는 운동입니다. 등 근육을 중심으로 팔도 함께 사용해요.", R.raw.exercise_lat_pulldown, R.drawable.exercise_lat_pulldown),
    ExerciseMotion("30000000-0000-0000-0000-000000000008", "시티드 케이블 로우", "앉은 상태에서 케이블 손잡이를 몸통 쪽으로 당기는 운동입니다. 등 루틴에 구성할 수 있어요.", R.raw.exercise_seated_cable_row, R.drawable.exercise_seated_cable_row),
    ExerciseMotion("30000000-0000-0000-0000-000000000009", "원암 덤벨 로우", "한 손으로 덤벨을 몸 쪽으로 당기는 등 운동입니다. 좌우를 나누어 진행하는 동작이에요.", R.raw.exercise_one_arm_dumbbell_row, R.drawable.exercise_one_arm_dumbbell_row),
    ExerciseMotion("30000000-0000-0000-0000-000000000010", "오버헤드 프레스", "바벨을 어깨 높이에서 머리 위로 밀어 올리는 운동입니다. 어깨와 팔 뒤쪽을 함께 사용해요.", R.raw.exercise_overhead_press, R.drawable.exercise_overhead_press),
    ExerciseMotion("30000000-0000-0000-0000-000000000011", "사이드 레터럴 레이즈", "덤벨을 든 양팔을 옆으로 들어 올리는 운동입니다. 어깨 측면을 중심으로 사용하는 동작이에요.", R.raw.exercise_lateral_raise, R.drawable.exercise_lateral_raise),
    ExerciseMotion("30000000-0000-0000-0000-000000000012", "바벨 컬", "바벨을 잡고 팔꿈치를 굽혀 들어 올리는 운동입니다. 위팔 앞쪽을 사용하는 팔 운동이에요.", R.raw.exercise_barbell_curl, R.drawable.exercise_barbell_curl),
    ExerciseMotion("30000000-0000-0000-0000-000000000013", "케이블 트라이셉스 푸시다운", "높은 위치의 케이블 손잡이를 아래로 밀어내는 운동입니다. 팔 뒤쪽을 중심으로 사용해요.", R.raw.exercise_triceps_pushdown, R.drawable.exercise_triceps_pushdown),
    ExerciseMotion("30000000-0000-0000-0000-000000000014", "루마니안 데드리프트", "바벨을 든 상태에서 엉덩이를 뒤로 보내며 상체를 숙였다 펴는 동작입니다. 허벅지 뒤쪽과 엉덩이를 사용해요.", R.raw.exercise_romanian_deadlift, R.drawable.exercise_romanian_deadlift),
    ExerciseMotion("30000000-0000-0000-0000-000000000015", "힙 쓰러스트", "등 위쪽을 벤치에 지지하고 골반을 들어 올리는 운동입니다. 엉덩이를 중심으로 사용하는 동작이에요.", R.raw.exercise_hip_thrust, R.drawable.exercise_hip_thrust),
    ExerciseMotion("30000000-0000-0000-0000-000000000016", "레그 컬", "머신에서 무릎을 굽혀 다리를 접는 운동입니다. 허벅지 뒤쪽을 사용하는 하체 운동이에요.", R.raw.exercise_leg_curl, R.drawable.exercise_leg_curl),
    ExerciseMotion("30000000-0000-0000-0000-000000000017", "레그 익스텐션", "앉은 상태에서 무릎을 펴며 머신의 패드를 들어 올리는 운동입니다. 허벅지 앞쪽을 중심으로 사용해요.", R.raw.exercise_leg_extension, R.drawable.exercise_leg_extension),
    ExerciseMotion("30000000-0000-0000-0000-000000000018", "플랭크", "팔과 발로 몸을 지지하며 자세를 유지하는 운동입니다. 반복 횟수 대신 유지 시간을 기록할 수 있어요.", R.raw.exercise_plank, R.drawable.exercise_plank),
    ExerciseMotion("30000000-0000-0000-0000-000000000019", "크런치", "누운 상태에서 상체 윗부분을 들어 올리는 복부 운동입니다. 코어 루틴에 구성할 수 있어요.", R.raw.exercise_crunch, R.drawable.exercise_crunch),
    ExerciseMotion("30000000-0000-0000-0000-000000000020", "데드 버그", "등을 대고 누워 팔다리를 교차해 뻗는 코어 운동입니다. 좌우를 번갈아 움직이는 동작이에요.", R.raw.exercise_dead_bug, R.drawable.exercise_dead_bug),
    ExerciseMotion("30000000-0000-0000-0000-000000000021", "워킹 런지", "한 발씩 앞으로 내딛으며 무릎을 굽혔다 일어나는 운동입니다. 허벅지와 엉덩이를 함께 사용해요.", R.raw.exercise_walking_lunge, R.drawable.exercise_walking_lunge),
    ExerciseMotion("30000000-0000-0000-0000-000000000022", "체스트 프레스 머신", "등을 시트에 지지하고 손잡이를 앞으로 밀어내는 머신 운동입니다. 가슴을 중심으로 사용해요.", R.raw.exercise_chest_press_machine, R.drawable.exercise_chest_press_machine),
    ExerciseMotion("30000000-0000-0000-0000-000000000023", "어시스티드 풀업", "보조 패드를 이용해 몸을 끌어 올리는 풀업 동작입니다. 등과 팔을 함께 사용하는 운동이에요.", R.raw.exercise_assisted_pull_up, R.drawable.exercise_assisted_pull_up),
    ExerciseMotion("30000000-0000-0000-0000-000000000024", "페이스 풀", "케이블 로프를 얼굴 방향으로 당기는 운동입니다. 어깨 뒤쪽과 등 위쪽을 함께 사용해요.", R.raw.exercise_face_pull, R.drawable.exercise_face_pull),
)
internal fun exerciseMotion(id: String?, name: String): ExerciseMotion? =
    if (id != null) exerciseMotions.firstOrNull { it.id == id }
    else exerciseMotions.firstOrNull { it.name == name }
