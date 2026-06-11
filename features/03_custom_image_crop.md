# 📸 카메라/갤러리 연동 및 1:1 크롭 기능 (FR-03)

## 1. 기능 개요
사용자는 직접 모바일 카메라로 사진을 촬영하거나 기기 내부 갤러리에서 사진을 불러와 상장 본문의 커스텀 이미지로 삽입할 수 있습니다. 이미지를 가져오는 과정에서 안드로이드 시스템 내장 이미지 편집 시스템(Crop Intent)을 연동하여 이미지를 **1:1 비율**로 크롭한 뒤 상장 영역 내에 맞춤형 데코레이션으로 렌더링되도록 구현했습니다.

---

## 2. 권한 획득 및 뷰 런처 설계
Android API 24 이상 버전의 보안 가이드라인을 준수하기 위해 `ActivityResultContracts`를 활용한 런타임 권한 요구 및 단일 책임 계약(Contract) 방식을 채택했습니다.

### 2.1 카메라 권한 및 촬영 흐름
1.  **권한 검사**: `ContextCompat.checkSelfPermission`을 사용해 `Manifest.permission.CAMERA` 권한 존재 여부 판단.
2.  **임시 파일 생성 및 URI 획득**: 외부 저장소 충돌 및 보안 강화를 위해 `cacheDir` 내에 임시 파일(`camera_temp_[timestamp].jpg`)을 생성하고 `FileProvider`를 통해 격리된 `content://` URI를 발행.
3.  **카메라 앱 실행**: `ActivityResultContracts.TakePicture()` 런처를 트리거해 시스템 카메라 호출.

```kotlin
private val takePictureLauncher = registerForActivityResult(
    ActivityResultContracts.TakePicture()
) { success ->
    if (success) {
        tempCameraUri?.let { performCrop(it) }
    } else {
        Toast.makeText(requireContext(), "사진 촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
    }
}
```

### 2.2 갤러리 로드 흐름
`ActivityResultContracts.GetContent()`를 이용해 MimeType이 `image/*`인 원본 이미지를 불러옵니다. 단, 갤러리 앱에서 제공하는 가상 URI(`content://media/external/...`)의 권한 소실을 막기 위해 데이터를 앱 캐시 디렉토리로 안전하게 스트림 복사(copyUriToCache)한 뒤 크롭 단계로 넘겨줍니다.

---

## 3. 시스템 크롭 인텐트 (`com.android.camera.action.CROP`) 연동 명세
1:1 정밀 비율 컷을 구현하고 시스템 편집기에 안전하게 URI 접근 권한을 양도하기 위한 아키텍처입니다.

```kotlin
private fun performCrop(sourceUri: Uri) {
    try {
        val cropFile = File(requireContext().cacheDir, "crop_temp_${System.currentTimeMillis()}.jpg")
        cropResultUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            cropFile
        )

        val cropIntent = Intent("com.android.camera.action.CROP").apply {
            setDataAndType(sourceUri, "image/*")
            putExtra("crop", "true")
            putExtra("aspectX", 1)  // 가로 비율 고정 (1)
            putExtra("aspectY", 1)  // 세로 비율 고정 (1)
            putExtra("outputX", 500) // 최종 가로 크기 500px
            putExtra("outputY", 500) // 최종 세로 크기 500px
            putExtra("scale", true)
            putExtra(MediaStore.EXTRA_OUTPUT, cropResultUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

        // Modern Android 호환성 조치: ClipData 설정 및 임시 URI 권한 그랜트 부여
        cropIntent.clipData = android.content.ClipData.newRawUri("", sourceUri).apply {
            addItem(android.content.ClipData.Item(cropResultUri))
        }

        val list = requireContext().packageManager.queryIntentActivities(cropIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (list.isEmpty()) {
            // 시스템 크롭 지원 불가 기종 폴백
            Toast.makeText(requireContext(), "기기가 이미지 자르기를 지원하지 않아 원본 이미지를 적용합니다.", Toast.LENGTH_SHORT).show()
            applyImageToCertificate(sourceUri)
        } else {
            for (res in list) {
                val packageName = res.activityInfo.packageName
                requireContext().grantUriPermission(packageName, sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                requireContext().grantUriPermission(packageName, cropResultUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            cropResultLauncher.launch(cropIntent)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(requireContext(), "크롭 호출 오류: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
```

### 3.2 핵심 속성 요약
*   `aspectX` & `aspectY`: 크롭 박스의 종횡비를 1:1로 제한하는 설정.
*   `outputX` & `outputY`: 결과 해상도를 500x500 픽셀로 제한하여 앱 내 메모리 부족(OOM) 방지 및 UI 일관성 유지.
*   `ClipData` 및 `grantUriPermission`: 보안이 적용된 파일 시스템(`FileProvider`)상에서 시스템 편집기 앱이 에러 없이 파일 쓰기(Write) 작업을 수행할 수 있도록 명시적으로 권한을 허가하는 안드로이드 보안 표준을 준수.
*   `applyImageToCertificate(uri)`: 크롭 완료 성공 시, 로드 라이브러리인 `Coil`을 사용해 `ivCustomImage` 뷰를 표시하고 이미지를 렌더링. 또한 "사진 제거" 버튼(`btn_remove_sticker`)을 사용자 화면에 노출합니다.

---

## 4. UI/UX 개선: 스티커 추가 섹션 분리 및 제거 기능

기존 버전에서는 사진 가져오기 기능이 배경 템플릿 영역에 위치하고 있어, 사용자가 '템플릿 테두리를 추가하는 것'으로 쉽게 착각하는 UX 문제가 있었습니다. 이를 해소하기 위해 아래와 같이 UI를 리팩토링하였습니다.

1. **"3. 사진 스티커 추가" 전용 카드 신설**:
   - 템플릿 목록과 완전히 무관하게, 상장 내부에 스티커처럼 붙일 개인 사진을 불러오고 꾸밀 수 있도록 카드를 별도 그룹으로 분리하였습니다.
   - 버튼명 및 ID를 `btn_camera_sticker`와 `btn_gallery_sticker`로 개명하여 직관성을 높였습니다.
2. **"사진 제거" 기능 도입**:
   - 사진을 등록하면 우측에 붉은색 글씨의 **"사진 제거"**(`btn_remove_sticker`) 버튼이 동적으로 나타납니다.
   - 제거 버튼 클릭 시 상장 미리보기 내부의 이미지뷰(`iv_custom_image`)가 `GONE` 처리되고, 내부 이미지 리소스 및 크롭 캐시 데이터(`cropResultUri`)가 해제 및 리셋됩니다.

