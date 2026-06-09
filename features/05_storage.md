# 📂 상장 보관함 및 영속화 기능 (FR-06)

## 1. 기능 개요
사용자가 본 앱에서 제작하여 미디어 저장소에 성공적으로 저장한 상장 목록을 한눈에 격자형(Grid)으로 파악하고 관리할 수 있도록 보관함 화면을 제공합니다. 사용자는 보관함 리스트에서 자신이 저장한 특정 상장을 클릭하여 상세 뷰 팝업을 띄울 수 있고, 여기에서 바로 SNS 앱으로 재공유하거나 기기 내 저장소에서 완전히 삭제할 수 있습니다.

---

## 2. 미디어스토어 쿼리를 활용한 동적 목록 로드
본 앱은 상장 목록을 관리하기 위해 기기 내부 파일 경로를 별도 데이터베이스(DB)에 저장하는 번거로움과 비동기화 오차를 없애고, 기기 자체의 미디어 공급자인 `MediaStore`에 직접 쿼리를 전송해 실시간 파일 정보를 가져오는 아키텍처를 채택했습니다.

### 2.1 사용자 필터 매칭 쿼리 (`StorageFragment.kt`)
개인정보 격리 및 보관함을 멀티 유저로 제공하기 위해, 현재 로그인한 사용자 명칭을 획득하여 파일 이름의 프리픽스를 기반으로 LIKE 연산 조회를 수행합니다.

```kotlin
private fun queryCertificates(): List<StorageItem> {
    val items = mutableListOf<StorageItem>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    // 파일 이름이 "certificate_[로그인사용자명]_" 로 시작하는 항목만 조회하도록 조건 설정
    val username = SecurityHelper.getCurrentUser(requireContext())
    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
    val selectionArgs = arrayOf("certificate_${username}_%")
    // 최신 제작물이 상단에 위치하도록 정렬
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        val cursor = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val name = c.getString(nameColumn)
                // ID값을 매핑해 안전한 공유용 콘텐츠 URI 조립
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                items.add(StorageItem(contentUri, name))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return items
}
```

---

## 3. UI 바인딩 및 상세 인터랙션 (Detail Dialog)

### 3.1 Grid 레이아웃 구성
보관함 화면(`FragmentStorage`) 진입 시, 수집된 리스트 아이템이 2열 격자 형식으로 출력되도록 `GridLayoutManager`를 바인딩하고, 이미지가 없을 때는 빈 상태 UI(`layout_empty_state`)로 동적 토글되도록 처리했습니다.

```kotlin
binding.rvStorage.layoutManager = GridLayoutManager(requireContext(), 2)
binding.rvStorage.adapter = StorageAdapter(items) { selectedItem ->
    showDetailDialog(selectedItem) // 클릭 시 상세 창 노출
}
```

### 3.2 상세 작업 모달 창 구성
`showDetailDialog` 함수는 커스텀 레이아웃(`dialog_certificate_detail.xml`)을 인플레이트하여 `Coil` 이미지 로더를 통해 캡처된 상장 원본을 크게 띄워줍니다.
*   **SNS 공유 버튼 클릭**: `shareCertificateImage`를 호출해 동일한 `Intent.ACTION_SEND` 공유 다이어그램 트리거.
*   **상장 삭제 버튼 클릭**: `ContentResolver.delete`를 사용해 OS 레벨 미디어 공급자에서 파일 데이터를 완전히 삭제하고 목록을 리인덱싱 리프레시함.

```kotlin
private fun deleteCertificate(item: StorageItem) {
    try {
        val rowsDeleted = requireContext().contentResolver.delete(item.uri, null, null)
        if (rowsDeleted > 0) {
            Toast.makeText(requireContext(), "상장이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
            loadCertificates() // 삭제 완료 후 리스트 갱신
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
```
