# 🔒 이용 등급별 제작 횟수 제한 및 로컬 보안 (FR-07 & Security)

## 1. 기능 개요
사용자의 가입 및 이용 등급에 따라 상장을 최대로 제작할 수 있는 개수를 제한하고, 한도 초과 시 멤버십 결제 혹은 회원 가입 안내 다이얼로그를 통해 다음 단계로의 전화를 유도합니다. 이 과정에서 유저의 가입 세션 정보, 닉네임, 그리고 가장 중요한 누적 상장 제작 횟수 데이터가 로컬 환경에서 임의로 해킹되거나 조작되지 않도록 안드로이드 프레임워크의 고수준 암호화 저장 기술을 이용해 데이터를 완벽하게 보호합니다.

---

## 2. 이용 등급별 제한 명세

| 이용 등급 | 최대 제작 제한 횟수 | 한도 초과 시 처리 흐름 |
| :--- | :---: | :--- |
| **비회원 (게스트)** | **최대 2회** | 제작 불가 경고창 팝업 노출 및 마이페이지 회원가입 화면으로 전환 유도 |
| **일반 회원** | **최대 6회** | 제작 불가 경고창 팝업 노출 및 프리미엄 구독 멤버십 결제 페이지 유도 |
| **프리미엄 회원** | **무제한 (Unlimited)** | 제한 조건 없이 상장 무한 제작 가능 |

---

## 3. 암호화 기반 보안 영속성 (`EncryptedSharedPreferences`)
이용 등급(Grade) 정보와 누적 제작 횟수(Creation Count)가 SharedPreferences XML 파일의 조작을 통해 우회되는 것을 막기 위해 `SecurityHelper`를 설계했습니다. Android KeyStore 시스템에 의해 자동으로 생성 및 회전되는 마스터 키(Master Key)를 이용해 파일 키셋 및 밸류 전체를 **AES256 암호화 표준**으로 감싸서 저장합니다.

### 3.1 암호화 SharedPreference 인스턴스 획득
```kotlin
private fun getEncryptedPrefs(context: Context): android.content.SharedPreferences {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    return try {
        EncryptedSharedPreferences.create(
            PREF_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // 복호화 오류(키 유실 등) 발생 시 복구를 위해 기존 캐시 클리어 후 인스턴스 재발행
        e.printStackTrace()
        context.deleteSharedPreferences(PREF_FILE_NAME)
        EncryptedSharedPreferences.create(
            PREF_FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
```

### 3.2 제한 조건 판별 비즈니스 로직
매 제작 저장 및 공유 이벤트가 발생할 때마다, 사용자 ID에 매핑되어 암호화 저장된 누적 개수 정보를 불러와 조건 판별을 거칩니다.

```kotlin
fun isLimitExceeded(context: Context): Boolean {
    val grade = getUserGrade(context)
    val count = getCreationCount(context)
    return when (grade) {
        "비회원" -> count >= 2
        "일반회원" -> count >= 6
        else -> false // 프리미엄 등급은 한도 통과
    }
}
```

*   **동적 횟수 증가**: 갤러리 저장 및 SNS 공유 성공 콜백 수신 즉시 `incrementCreationCount(context)`가 트리거되어 암호화 카운트가 `+1` 증가하고 실시간 저장소 상태로 커밋됩니다.
