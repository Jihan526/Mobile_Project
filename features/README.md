# 📌 기말 프로젝트 핵심 기능 상세 명세서 (features/README.md)

본 디렉토리는 **나만의 상장 만들기** 애플리케이션의 핵심 기능적 요구사항(FR-01 ~ FR-07)과 비기능적 요구사항(NFR)의 구체적인 명세, 그리고 작동 아키텍처를 상세히 문서화한 공간입니다.

각 기능별 세부 설계 정보 및 구현 특징은 아래의 개별 문서를 참조해 주시기 바랍니다.

---

## 📂 기능별 세부 명세 문서 목록

### 1. [AI 상장 문구 자동 생성 (FR-01)](file:///c:/Mobile_Project/features/01_ai_text_generation.md)
*   **핵심 요약**: 사용자의 수여 사유를 바탕으로 Gemini API(기본: `gemini-1.5-flash`, 대체: `gemini-2.5-flash`)를 호출하여 격식 있는 한국어 상장 본문 텍스트를 실시간으로 자동 생성합니다.
*   **주요 기술**: Google Generative AI SDK, Coroutines Async Request, 이중화 모델 Fallback 설계, `RequestOptions` (v1 API 연동).

### 2. [상장 템플릿 선택 및 A4 비율 UI 구성 (FR-02 & NFR)](file:///c:/Mobile_Project/features/02_template_selection.md)
*   **핵심 요약**: 고전, 현대, 귀여운 스타일 등 3개 이상의 상장 배경 테두리 템플릿을 선택하여 즉시 미리보기에 반영하고, 화면 왜곡을 막기 위한 A4 규격 종횡비 레이아웃을 구현합니다.
*   **주요 기술**: RecyclerView, Custom Adapter, ConstraintLayout `layout_constraintDimensionRatio="1:1.414"`.

### 3. [카메라/갤러리 연동 및 1:1 크롭 기능 (FR-03)](file:///c:/Mobile_Project/features/03_custom_image_crop.md)
*   **핵심 요약**: 기기의 카메라로 사진을 찍거나 갤러리에서 불러와서, 시스템 내장 Crop 기능을 연동하여 1:1 비율로 자른 후 상장 내부 사진 영역에 매끄럽게 삽입합니다.
*   **주요 기술**: ActivityResultLauncher, System Intent (com.android.camera.action.CROP), FileProvider content URI, URI 권한 명시 부여.

### 4. [캔버스 비트맵 캡처 및 갤러리 저장 & SNS 공유 (FR-04 & FR-05)](file:///c:/Mobile_Project/features/04_canvas_save_share.md)
*   **핵심 요약**: 제작이 완료된 상장 미리보기 영역을 Android Canvas API를 활용해 고화질 비트맵 이미지로 렌더링하고, MediaStore API를 통해 기기 갤러리에 안전하게 저장한 뒤 Share Intent로 카카오톡 등 SNS에 즉시 공유합니다.
*   **주요 기술**: Android Canvas Drawing, MediaStore API (Scoped Storage 호환), `IS_PENDING` 제어, Share Intent.

### 5. [상장 보관함 및 영속화 기능 (FR-06)](file:///c:/Mobile_Project/features/05_storage.md)
*   **핵심 요약**: 사용자가 지금까지 제작하여 갤러리에 저장한 상장들을 SQLite 데이터베이스 대신 MediaStore 쿼리를 통해 효율적으로 검색하여 격자형 목록으로 출력하고, 재공유 및 삭제를 수행하는 보관함을 제공합니다.
*   **주요 기술**: MediaStore ContentResolver Query, GridLayoutManager, Detail Dialog.

### 6. [이용 등급별 제작 횟수 제한 및 로컬 데이터 보안 (FR-07 & Security)](file:///c:/Mobile_Project/features/06_limit_security.md)
*   **핵심 요약**: 유저의 로그인 상태와 가입 등급(비회원, 일반회원, 프리미엄)에 따라 각각 누적 제작 횟수 한도(2회, 6회, 무제한)를 제한하고, 관련 사용자 정보를 위변조할 수 없도록 암호화하여 저장합니다.
*   **주요 기술**: EncryptedSharedPreferences (AES256 암호화 키 생성 및 관리), Singleton Helper.
