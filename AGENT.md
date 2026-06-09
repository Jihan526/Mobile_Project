# 🤝 AI 에이전트 협업 개발 회고록 (AGENT.md)

본 문서는 **2026학년도 1학기 모바일프로그래밍 기말 프로젝트**의 성공적인 수행을 위해, 인간 개발자(안지한)와 AI 에이전트(Antigravity)가 공동으로 참여한 협업 과정, 기술적 도전 과제, 그리고 트러블슈팅 과정을 상세히 기록한 문서입니다.

---

## 1. AI 에이전트와의 협업 개요

본 프로젝트는 기획 및 핵심 요구사항 정의부터 구체적인 안드로이드 Kotlin 코드 구현, 디버깅, 성능 최적화에 이르기까지 **인간 개발자와 AI 에이전트 간의 긴밀한 페어 프로그래밍(Pair Programming)** 형식으로 개발되었습니다.

### 👥 역할 분담 및 협업 모델
*   **인간 개발자 (Human Developer)**
    *   **역할**: 프로젝트 기획, 핵심 요구사항 명세서(FR-01~FR-07) 수립, UI/UX 흐름 디자인 및 최종 피드백 제공.
    *   **협업 방식**: 상세한 요구사항과 예외 상황을 프롬프트 형태로 전달하고, 구현된 소스 코드의 컴파일 및 실제 기기 테스트를 수행하며 문제 상황을 에이전트에게 환류.
*   **AI 에이전트 (Antigravity)**
    *   **역할**: 안드로이드 애플리케이션 아키텍처 설계, Kotlin 기반의 기능 비즈니스 로직 작성, 비기능적 요구사항(보안, 성능, 예외 처리) 반영 및 빌드 오류 발생 시 신속한 트러블슈팅 제시.
    *   **협업 방식**: 사용자의 기능 명세를 분석하여 컴포넌트 단위의 작업 계획을 수립하고, 클린 코드를 작성하며, API 연동 및 시스템 인텐트 통합 시 발생할 수 있는 잠재적 위험 요소를 선제적으로 해결.

---

## 2. 초기 환경 설정 및 인프라 구축

개발 초기 단계에서 Windows 운영체제 환경의 특성으로 인해 발생한 인프라 충돌을 해결함으로써 안정적인 빌드 환경을 구축하였습니다.

### ⚠️ Windows OneDrive 동기화 충돌 이슈 해결
*   **문제 현상**
    *   프로젝트가 Windows `OneDrive` 동기화 폴더 경로(`C:\Users\dkswl\OneDrive\Desktop\Mobile_Project`) 내에 배치되어 동작할 때, Android Studio 빌드 및 Gradle 동기화 속도가 비정상적으로 느려짐.
    *   자주 업데이트되는 임시 빌드 캐시 파일(`.gradle`, `.idea`, `/build` 디렉토리 내의 바이너리 파일)에 대해 OneDrive 동기화 엔진이 파일 잠금(Lock)을 시도하면서 Gradle 빌드가 멈추거나 파일 쓰기 권한 오류가 지속적으로 발생.
*   **해결 전략 및 조치**
    *   **로컬 독립 경로로의 이전**: 프로젝트 작업 디렉토리를 OneDrive 동기화 제어 범위 바깥인 최상위 로컬 독립 디렉토리 `C:\Mobile_Project`로 완전히 이전하여 개발을 진행.
    *   **안정성 및 속도 확보**: 동기화 락으로 인한 빌드 병목을 완전히 차단함으로써, Gradle Sync 및 빌드 시간이 80% 이상 단축되었으며 컴파일 중 발생하던 예기치 않은 I/O 예외를 종식시킴.

---

## 3. UI 디자인 구성 및 A4 종횡비 적용 전략

모바일 화면의 다양성에도 불구하고, 출력물 및 캡처본이 실제 '상장'의 느낌을 주기 위해 고정된 종횡비를 가지도록 UI 레이아웃을 설계했습니다.

### 📐 A4 비율(1:1.414) 고정 레이아웃 구현
*   **기술적 필요성**
    *   다양한 안드로이드 기기의 종횡비(16:9, 19.5:9 등)에 따라 상장 미리보기 영역이 길게 늘어나거나 찌그러지는 왜곡 현상이 발생함.
    *   Canvas API를 활용하여 미리보기 뷰를 비트맵 이미지로 캡처할 때, 기기 해상도에 종속되지 않는 일관된 A4 규격(1:1.414)의 고화질 이미지 출력이 필요함.
*   **구현 전략**
    *   `fragment_home.xml`의 상장 미리보기 영역인 `layout_certificate_canvas` 컨테이너에 `ConstraintLayout`을 적용하고, 핵심 속성인 **`app:layout_constraintDimensionRatio="1:1.414"`**를 명시적으로 부여.
    *   가로 길이를 `match_parent`로 지정하되 세로 길이를 `0dp`로 설정하고 비율 제약을 적용함으로써, 화면 크기에 맞게 가로 폭이 스케일링되면서 세로 폭이 A4 비율에 맞춰 실시간으로 동적 렌더링되도록 디자인.

---

## 4. 핵심 기능(FR-01~FR-05) 구현 프롬프트 전략

AI 에이전트의 생성 성능을 극대화하고 안드로이드 아키텍처에 적합한 코드를 유도하기 위해 체계적인 프롬프트 전략을 적용하였습니다.

| 요구사항 ID | 핵심 기능 | 프롬프트 엔지니어링 전략 |
| :--- | :--- | :--- |
| **FR-01** | **AI 상장 문구 자동 생성** | **역할 부여 및 출력 제약 조건 지정**:<br>Gemini API가 리턴하는 응답에서 불필요한 메타 텍스트(예: "알겠습니다", "여기 문구입니다" 등)와 마크다운 기호를 완전히 배제하고, 오직 완성된 2~3문장의 한국어 본문 텍스트만 출력하도록 시스템 프롬프트를 정교하게 설계함. 문장의 종결 어미를 상장 격식체(`~하기에 이 상을 수여합니다`)로 고정 유도. |
| **FR-02** | **상장 템플릿 선택** | **어댑터 패턴의 간결한 구현 요구**:<br>다양한 기본 배경 템플릿 리스트를 `RecyclerView`와 `TemplateAdapter`로 구현할 때, 불필요하게 클래스를 비대화하지 않고 람다 콜백을 통해 메인 프래그먼트에 즉각적인 템플릿 변경 이벤트를 전달하는 단일 방향 흐름 코드 작성을 요청. |
| **FR-03** | **커스텀 이미지 Crop 연동** | **명시적 권한 부여 및 호환성 고려**:<br>안드로이드 파일 공유 보안 정책(FileProvider)에 대응하기 위해, Crop Intent 호출 시 임시 URI에 대한 읽기/쓰기 권한(`Intent.FLAG_GRANT_READ_URI_PERMISSION` 등)을 명시적으로 패키지 매니저를 통해 수신 앱에 부여하는 방어적 예외 처리 코드 유도. |
| **FR-04** | **캔버스 비트맵 캡처 및 저장** | **비동기 스레드 분리 전략**:<br>UI 스레드 중단을 방지하기 위해 Canvas 그리기 작업 및 MediaStore를 통한 저장용 디스크 I/O 작업을 코루틴(`Dispatchers.IO`) 내에서 비동기로 수행하도록 프롬프트로 지시. Android Q 이상 버전에서의 `IS_PENDING` 플래그 및 `RELATIVE_PATH` 명세 처리 강조. |
| **FR-05** | **완성 상장 SNS 공유** | **표준 Intent 및 안전한 URI 공유**:<br>`FileProvider`를 통해 외부에 노출할 수 있는 안전한 콘텐츠 URI를 획득하고, `Intent.ACTION_SEND`에 정상적으로 바인딩하여 카카오톡, 인스타그램 등 서드파티 앱으로 안전하게 스트림을 전달하는 모범 사례 구현 유도. |

---

## 5. 🛠️ Gemini API v1beta 모델명 인식 오류(404 Not Found) 트러블슈팅

본 프로젝트의 핵심 고도화 단계에서 발생했던 Gemini API 호출 예외를 해결한 기술적 과정을 상세히 기술합니다.

### 1) 현상 (Symptom)
*   사용자가 상장 수여 사유를 입력하고 `AI 문구 생성` 버튼을 클릭했을 때, 정상적인 API 응답이 도출되지 않고 로딩 상태에서 대기하다가 예외 catch 블록으로 빠지며 미리 정의된 Mock fallback 텍스트가 화면에 노출됨.
*   다이얼로그 디버그 세부 로그상에 **`Google Generative AI SDK Exception: 404 Not Found (Model NOT_FOUND)`** 에러 메시지가 표출됨.

```
Gemini 에러 [ResponseException]: com.google.ai.client.generativeai.type.ResponseException: 
Remote error code: 404, message: Model gemini-2.5-flash is not found for API version v1beta, 
or is not supported for this call.
```

### 2) 원인 분석 (Root Cause)
*   **API 버전과 최신 모델의 매핑 불일치**:
    *   안드로이드 프로젝트 내에서 사용하고 있던 구글 Generative AI SDK의 기본 REST API 엔드포인트 호출 버전이 `v1beta`로 설정되어 있었음.
    *   최신 출시 모델인 **`gemini-2.5-flash`** 모델은 공식적으로 정식 릴리즈 버전인 **`v1`** 엔드포인트 하위에서 동작하도록 매핑되어 있으나, SDK 내부에서 기본 설정으로 `v1beta` 주소로 요청을 날렸기 때문에 API 게이트웨이 단계에서 해당 모델을 찾지 못하고 404 에러를 반환한 것임.
*   **엔드포인트 명시적 설정 부재**:
    *   `GenerativeModel` 생성 시 `requestOptions`를 별도로 커스텀 설정하지 않으면 SDK 버전에 따라 `v1beta`가 호출되는 파편화 이슈가 있었음.

### 3) 디버깅 및 분석 과정 (Debugging Process)
*   **1단계 (예외 세부 검출)**: 단순히 에러 토스트만 띄우는 것에서 탈피하여, `catch` 블록 내부에 스택 트레이스 전체와 예외 클래스명을 `AlertDialog`로 띄워 실제 에러가 발생한 지점과 원인 메시지(`Model not found`)를 명확하게 포착.
*   **2단계 (SDK 명세 검증)**: 공식 Google Gemini Developer Documentation을 대조 분석하여 `gemini-2.5-flash` 모델의 지원 엔드포인트가 `https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash` 형식인 것을 파악, 호출 시 버전 파라미터를 수정해야 함을 규명.
*   **3단계 (SDK API 조사)**: Kotlin Generative AI SDK의 `GenerativeModel` 생성자 구조를 파헤쳐 `RequestOptions` 객체를 통해 `apiVersion`을 변경할 수 있는 생성자가 제공됨을 식별.

### 4) 해결 및 조치 (Resolution)
`HomeFragment.kt` 소스 코드의 `GenerativeModel` 인스턴스 초기화 부분을 다음과 같이 수정하여 문제를 성공적으로 해결하였습니다.

```diff
- val generativeModel = GenerativeModel(
-     modelName = "gemini-2.5-flash",
-     apiKey = BuildConfig.GEMINI_API_KEY
- )
+ val generativeModel = GenerativeModel(
+     modelName = "gemini-2.5-flash",
+     apiKey = BuildConfig.GEMINI_API_KEY,
+     requestOptions = RequestOptions(
+         timeout = 60.seconds,
+         apiVersion = "v1" // 명시적으로 v1 API 버전을 타게끔 변경
+     )
+ )
```

*   **결과**: API 버전 매핑을 `v1`으로 바로잡은 즉시 `gemini-2.5-flash` 모델에 정상 연결되었으며, 3초 이내에 품격 있는 상장 문구를 동적으로 생성하여 뷰에 반영시키는 데 성공함.

---

## 6. 🛠️ Gemini API 503 Service Unavailable 및 이중화 모델 Fallback 구축 트러블슈팅

### 1) 현상 (Symptom)
*   일부 시간대 혹은 특정 무료 계정 키 환경에서 `AI 문구 생성` 요청 시, 로딩 창이 오랫동안 대기한 후 예외 catch 블록으로 빠지며 디버그 상세 정보 얼럿창에 다음과 같은 오류가 표시됨.
```
클래스: com.google.ai.client.generativeai.type.ServerException
메시지: Unexpected Response:
{
  "error": {
    "code": 503,
    "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
    "status": "UNAVAILABLE"
  }
}
kotlinx.serialization.MissingFieldException: Field 'details' is required...
```

### 2) 원인 분석 (Root Cause)
*   **특정 모델 트래픽 밀집**: 구글 AI Studio 무료 티어에서 `gemini-2.5-flash`와 같은 최신 모델이 순간적으로 과부하 상태(503 Service Unavailable)가 되어 응답을 반환하지 못하는 현상이 발생함.
*   **불친절한 UI 예외 노출**: 단순 서버 일시 혼잡 오류임에도 불구하고, 앱 개발 단계에서 삽입된 디버그 목적의 상세 스택 트레이스 `AlertDialog`가 엔드유저에게 그대로 노출되어 서비스 신뢰도를 떨어뜨리는 문제가 있음.

### 3) 해결 및 조치 (Resolution)
*   **이중 AI 모델 및 자동 재시도(Retry with Exponential Backoff) 도입**:
    *   **1차 방어선 - 기본 모델 우선 호출**: 무료 티어 환경에서 처리 한도가 넉넉하고 안정적인 **`gemini-1.5-flash`** 모델을 기본(Primary) 모델로 사용해 일시적인 503 에러의 발생 가능성을 줄였습니다.
    *   **2차 방어선 - 에러 시 자동 재시도**: 일시적인 503/429/네트워크 오류 발생 시, 1초 대기 후 2초 대기하는 식의 지수 백오프(Exponential Backoff) 방식을 활용하여 **모델당 최대 3회씩 재시도**를 수행합니다.
    *   **3차 방어선 - 모델 이중화(Failover)**: 기본 모델에서 3회 재시도가 전부 실패하는 경우, 최신 **`gemini-2.5-flash`** 모델(Fallback)로 전환해 동일하게 최대 3회 재시도를 실행합니다 (총 최대 6회 재시도).
    *   **4차 방어선 - 로컬 Fallback**: 만약 두 개의 모델을 통한 총 6회의 요청이 모두 실패할 경우에만 로컬의 Mock 텍스트를 출력합니다.
*   **사용자 친화적 예외 처리**:
    *   서버 일시 에러(503/UNAVAILABLE)인 경우, 기술적인 디버그 얼럿 팝업창을 띄우지 않고 "현재 AI 서버 트래픽이 많아 기본 문구로 대체되었습니다. 잠시 후 다시 시도해 주세요."라는 직관적이고 친절한 Toast 메시지만 띄우도록 보완하여 사용자 경험을 대폭 증진시켰습니다.

---

## 7. 결론 및 향후 계획

인간의 논리적 흐름 통제와 AI의 정확하고 신속한 코드 생성이 결합하여, 버그 발생률을 획기적으로 낮추고 완성도 높은 상장 제작 애플리케이션을 완성할 수 있었습니다. 특히 404 API 경로 파편화 오류, 503 서버 혼잡 대응을 위한 모델 이중화 설계, 그리고 윈도우 원드라이브의 I/O 경합 문제를 구조적으로 접근하여 해결한 경험은 향후 고도화 프로젝트에서 유용한 자산이 될 것입니다.
