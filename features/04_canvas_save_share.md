# 💾 캔버스 비트맵 캡처 및 저장/공유 (FR-04 & FR-05)

## 1. 기능 개요
완성된 상장의 미리보기 뷰 전체를 안드로이드 2D 그래픽 렌더링 엔진인 **Canvas API**를 활용해 가상 비트맵으로 캡처하고, 이를 기기의 미디어 저장소(MediaStore)에 이미지 파일로 저장(FR-04)합니다. 저장에 성공하면 즉시 안드로이드의 표준 **Share Intent**를 트리거해 사용자가 카카오톡, 라인, 인스타그램 등 외부 SNS 앱으로 완성된 상장 이미지를 신속하게 전송할 수 있는 공유 환경(FR-05)을 제공합니다.

---

## 2. 뷰 캡처 아키텍처 (Android Canvas API)
화면에 그려진 UI 레이아웃의 형상을 그대로 비트맵 데이터로 추출하기 위해 `captureView` 메소드를 설계했습니다.

```kotlin
private fun captureView(view: View): Bitmap {
    // 1. 뷰의 물리적 크기만큼 ARGB_8888 포맷의 메모리상 빈 비트맵 객체 생성
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    // 2. 비트맵을 대상으로 그릴 가상 캔버스 객체 생성
    val canvas = Canvas(bitmap)
    // 3. 대상 뷰의 draw() 메서드를 호출하여 가상 캔버스 위에 UI 형상을 투영하여 드로잉 처리
    view.draw(canvas)
    return bitmap
}
```

---

## 3. Scoped Storage 호환 미디어 저장 (MediaStore API)
Android 10(API 29, Q) 이상의 스코프드 스토리지 보안 정책과 완벽히 호환되도록 저장 코드를 작성했으며, 대용량 이미지 저장 시 메인 스레드가 락업(Lock-up)되지 않도록 **Kotlin Coroutines의 `Dispatchers.IO` 비동기 워커 스레드**를 사용하였습니다.

```kotlin
private suspend fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
    val context = requireContext()
    val username = SecurityHelper.getCurrentUser(context)
    return withContext(Dispatchers.IO) { // 디스크 I/O를 비동기 백그라운드 스레드로 분리
        val filename = "certificate_${username}_${System.currentTimeMillis()}.png"
        var outputStream: OutputStream? = null
        var imageUri: Uri? = null
        val resolver = context.contentResolver

        // MediaStore 메타데이터 선언
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 저장될 디렉토리 정의 (/Pictures/MyCertificates)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyCertificates")
                // 쓰기 작업 진행 중 타 앱에 노출되지 않도록 잠금 설정
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        try {
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (imageUri != null) {
                outputStream = resolver.openOutputStream(imageUri)
                if (outputStream != null) {
                    // 비트맵 압축 및 파일 쓰기 수행
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    // 파일 쓰기가 정상 완료되었으므로 잠금 해제 (타 미디어 앱 노출 허용)
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null) {
                resolver.delete(imageUri, null, null)
                imageUri = null
            }
        } finally {
            outputStream?.flush()
            outputStream?.close()
        }
        imageUri
    }
}
```

### 🔑 주요 조치 요소
1.  **`IS_PENDING` 제어**: 쓰기가 완료되기 전까지 시스템 미디어 스캔 대상에서 물리적으로 격리하여 타 뷰어 앱에서 미완성 파일로 불려오지 않도록 함.
2.  **`RELATIVE_PATH`**: 외부 저장소의 범용 디렉토리인 `Pictures` 하위에 전용 앨범 폴더명(`/MyCertificates`)을 생성하여 깔끔한 파일 정리 기여.

---

## 4. SNS 공유 기능 (Android Share Intent)
저장 후 획득한 미디어 콘텐츠 URI를 이용해 서드파티 모바일 메신저 및 SNS 앱으로 공유할 수 있는 시스템 런처를 트리거합니다.

```kotlin
private fun shareCertificateImage(uri: Uri) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        // 수신하는 서드파티 앱에게 해당 임시 URI 콘텐츠에 대한 읽기 권한을 명시 부여
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // 공유가 가능한 앱 목록 다이얼로그(Chooser) 구성 및 노출
    startActivity(Intent.createChooser(shareIntent, "상장 공유하기"))
}
```
