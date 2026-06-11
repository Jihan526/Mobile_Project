# 🎨 상장 템플릿 선택 및 A4 비율 UI 구성 (FR-02 & NFR)

## 1. 기능 개요
사용자는 앱 내에서 기본으로 제공하는 다양한 테마의 상장 배경 디자인 템플릿(Classic, Modern, Cute 등) 중 하나를 실시간으로 선택하여 상장 배경으로 적용할 수 있으며, 이 모든 과정에서 상장의 종횡비는 실제 종이 문서 규격인 A4(1:1.414) 비율을 엄격하게 유지합니다.

---

## 2. A4 비율 (1:1.414) 레이아웃 적용 원리
기기마다 다양한 화면 해상도(3:4, 9:16, 9:19.5 등)를 가진 안드로이드 에코시스템 내에서, 상장 저장 시 비율이 찌그러지거나 콘텐츠 배치가 밀리지 않도록 고안된 레이아웃 디자인 시스템입니다.

### 2.1 XML 설정 구조
`fragment_home.xml` 내의 `layout_certificate_canvas`는 `ConstraintLayout`을 부모로 하여 종횡비 락을 획득합니다.

```xml
<!-- ConstraintLayout 부모 내에서 A4 비율을 적용하는 컨테이너 -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/layout_certificate_canvas"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:background="@color/white"
    android:elevation="4dp"
    app:layout_constraintDimensionRatio="1:1.414"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintBottom_toBottomOf="parent">

    <!-- 템플릿 배경 이미지 -->
    <ImageView
        android:id="@+id/iv_certificate_background"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitXY" />

    <!-- 텍스트/이미지 배치 레이어 -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="24dp">
        
        <!-- 상장 내부 컴포넌트(제목, 이름, 내용, 날짜 등)가 상대 배치됨 -->
        
    </androidx.constraintlayout.widget.ConstraintLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

*   **`android:layout_width="match_parent"`**: 화면 가로 길이에 꽉 차게 자동으로 늘어납니다.
*   **`android:layout_height="0dp"`**: 고정 치수 대신 제약조건을 따르게 설계합니다.
*   **`app:layout_constraintDimensionRatio="1:1.414"`**: 가로 대비 세로의 길이를 A4 용지 규격인 1 대 1.414로 자동 환산하여 세로 높이를 동적으로 정의합니다.

---

## 3. RecyclerView 기반 템플릿 선택 명세

### 3.1 템플릿 데이터 모델 (`TemplateItem.kt`)
각 템플릿 정보는 ID, 드로어블 리소스 ID, 템플릿 명칭으로 구성됩니다.
```kotlin
data class TemplateItem(
    val id: Int,
    val drawableResId: Int,
    val name: String
)
```

### 3.2 템플릿 선택 및 뷰 바인딩 (`HomeFragment.kt`)
`TemplateAdapter`의 생성자 매개변수로 클릭 리스너 람다 함수 `(TemplateItem) -> Unit`을 제공하여 어댑터 내부의 클릭 이벤트가 프래그먼트 내부의 상장 배경 `ImageView`로 직접 전파되도록 설계했습니다.

```kotlin
    private fun setupTemplatesRecyclerView() {
    val templates = listOf(
        TemplateItem(1, R.drawable.template_border_classic, "Classic"),
        TemplateItem(2, R.drawable.template_border_modern, "Modern"),
        TemplateItem(3, R.drawable.template_border_cute, "Cute"),
        TemplateItem(4, R.drawable.template_border_traditional, "Traditional")
    )

    // 최초 실행 시 기본 클래식 배경 적용
    binding.ivCertificateBackground.setImageResource(templates[0].drawableResId)

    val adapter = TemplateAdapter(templates) { selectedItem ->
        // 아이템 클릭 시 실시간 템플릿 변경 적용
        binding.ivCertificateBackground.setImageResource(selectedItem.drawableResId)
    }

    binding.rvTemplates.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    binding.rvTemplates.adapter = adapter
}
```

---

## 4. 고품격 전통 상장 서체(Fonts) 시스템 적용

상장의 심미성과 전문성을 크게 향상시키기 위해 기본 산세리프 글꼴에서 벗어나 정식 붓글씨 및 명조 계열의 한국어 웹 폰트를 리소스에 탑재하여 상장에 일괄 매핑하였습니다.

### 4.1 서체 매핑 구조
- **송명체 (`@font/font_song_myung`)**: 전통 서예의 힘차고 뾰족한 붓끝 질감을 재현한 서체로, 상장의 핵심 제목인 **"상 장"** (`tv_cert_title`) 텍스트에 적용하여 엄숙한 분위기를 선사합니다.
- **고운바탕체 (`@font/font_gowun_batang`)**: 단정하고 정갈하게 정돈된 세리프/명조 스타일 서체로, 상장 본문 (`tv_cert_content`), 이름 (`tv_cert_recipient`), 수여 날짜 (`tv_cert_date`), 수여인 (`tv_cert_issuer`)에 일관되게 입혀 가독성과 격식을 충족합니다.

### 4.2 테두리 디자인의 고도화 (Vector Drawables)
기존의 밋밋한 단선 shape 테두리에서 한 차원 진보하여 해상도 변화에도 번지지 않는 고화질 Vector Drawable (`<vector>`) 프레임을 적용했습니다.
1. **Classic (`template_border_classic.xml`)**: 화려한 금박 모서리 문양과 조화로운 2중 테두리선을 그리는 전통 서양식 금박 레이아웃.
2. **Modern (`template_border_modern.xml`)**: 실제 상견례나 현대식 임명장 양식을 모티브 삼아 신뢰감을 주는 딥 네이비 면과 얇은 골드 크로스 라인이 어우러진 현대적인 비즈니스 템플릿.
3. **Traditional (`template_border_traditional.xml`)**: 한글 고유 문서를 흉내 낸 한지 느낌의 미색 배경지 위에 전통 기하학 코너 무양과 은은한 학 워터마크가 투영된 고급 한식 프레임.

