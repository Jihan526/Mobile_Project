package com.example.mobileprogramming

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.mobileprogramming.databinding.FragmentHomeBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // URIs for Image Source (Camera) & Crop Result
    private var tempCameraUri: Uri? = null
    private var cropResultUri: Uri? = null

    // Activity Result Launchers
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { performCrop(it) }
        } else {
            Toast.makeText(requireContext(), "사진 촬영이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            val cachedUri = copyUriToCache(it)
            if (cachedUri != null) {
                performCrop(cachedUri)
            } else {
                Toast.makeText(requireContext(), "이미지를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyUriToCache(uri: Uri): Uri? {
        return try {
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(requireContext().cacheDir, "gallery_temp_${System.currentTimeMillis()}.jpg")
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val cropResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cropResultUri?.let { applyImageToCertificate(it) }
        } else {
            Toast.makeText(requireContext(), "이미지 편집이 취소되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(requireContext(), "카메라 촬영을 위해 카메라 권한 허용이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Register BackPress Callback to show Exit Confirmation Dialog
        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)

        // Setup Toolbar back navigation to trigger onBackPressed
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Set today's date on the preview
        val currentDate = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault()).format(Date())
        binding.tvCertDate.text = currentDate

        // Set default issuer name and stamp on preview
        val defaultIssuer = SecurityHelper.getNickname(requireContext())
        binding.tvCertIssuer.text = "수여인 : $defaultIssuer"
        updateStampText()

        // Real-time preview binding for Names
        binding.etRecipientName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tvCertRecipient.text = "이름 : ${s?.toString() ?: ""}"
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etIssuerName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: ""
                binding.tvCertIssuer.text = "수여인 : $text"
                updateStampText()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etStampText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateStampText()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // FR-02: Template List Setup & Recycler Binding
        setupTemplatesRecyclerView()

        // FR-01: AI Award Text Generation Button Event
        binding.btnGenerateText.setOnClickListener {
            if (!binding.btnGenerateText.isEnabled) return@setOnClickListener
            val reason = binding.etAwardReason.text.toString().trim()
            if (reason.isEmpty()) {
                Toast.makeText(requireContext(), "수여 사유를 짧게 입력해주세요!", Toast.LENGTH_SHORT).show()
            } else {
                generateAwardText(reason)
            }
        }

        // FR-03: Camera & Gallery Action Events (Separated Sticker Buttons)
        binding.btnCameraSticker.setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        binding.btnGallerySticker.setOnClickListener {
            openGallery()
        }

        binding.btnRemoveSticker.setOnClickListener {
            binding.ivCustomImage.visibility = View.GONE
            binding.btnRemoveSticker.visibility = View.GONE
            binding.ivCustomImage.setImageDrawable(null)
            cropResultUri = null
        }

        // FR-08: Stamp Switch Control
        binding.switchIssuerStamp.setOnCheckedChangeListener { _, _ ->
            updateStampText()
        }

        // FR-04: Save Certificate to Device Gallery
        binding.btnSaveGallery.setOnClickListener {
            saveCertificateWorkflow(shouldShare = false)
        }

        // FR-05: Share Certificate to SNS Apps
        binding.btnShareSns.setOnClickListener {
            saveCertificateWorkflow(shouldShare = true)
        }

        binding.etAwardDate.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim() ?: ""
                if (text.isEmpty()) {
                    val today = SimpleDateFormat("yyyy년 MM월 dd일", Locale.getDefault()).format(Date())
                    binding.tvCertDate.text = today
                } else {
                    binding.tvCertDate.text = text
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnSaveStorage.setOnClickListener {
            val recipient = binding.etRecipientName.text.toString().trim()
            val reason = binding.etAwardReason.text.toString().trim()
            val issuer = binding.etIssuerName.text.toString().trim()

            if (recipient.isEmpty() || reason.isEmpty() || issuer.isEmpty()) {
                Toast.makeText(requireContext(), "받는 사람 이름, 상장 수여 사유, 주는 사람 이름을 모두 작성해 주세요!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("보관함 저장")
                .setMessage("이 상장을 보관함에 저장하시겠습니까?")
                .setPositiveButton("저장") { _, _ ->
                    saveCertificateWorkflow(shouldShare = false, andNavigateToStorage = true)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    /**
     * FR-02: Template Selection Logic
     */
    private fun setupTemplatesRecyclerView() {
        val templates = listOf(
            TemplateItem(1, R.drawable.template_border_classic, "Classic"),
            TemplateItem(2, R.drawable.template_border_modern, "Modern"),
            TemplateItem(3, R.drawable.template_border_cute, "Cute"),
            TemplateItem(4, R.drawable.template_border_traditional, "Traditional")
        )

        // Setup default background preview
        binding.ivCertificateBackground.setImageResource(templates[0].drawableResId)

        val adapter = TemplateAdapter(templates) { selectedItem ->
            binding.ivCertificateBackground.setImageResource(selectedItem.drawableResId)
        }

        binding.rvTemplates.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTemplates.adapter = adapter
    }

    private suspend fun tryGenerateWithModel(modelName: String, reason: String): String? {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY,
            requestOptions = RequestOptions(
                timeout = 60.seconds,
                apiVersion = "v1"
            )
        )
        val prompt = """
            다음은 상장을 수여하려는 사유입니다: "$reason"
            이 사유를 바탕으로 정중하고 격려와 약간의 유머가 섞인 품격 있는 상장 본문(Certificate text)으로 자연스럽게 생성해줘.
            조건:
            1. 오직 상장 본문에 해당하는 2~3문장의 텍스트만 출력하고, 다른 설명이나 제목, 인사말, 기호 등은 절대 포함하지 마라.
            2. 한국어로 작성하고, 문장 끝은 "~하기에 이 상장을 수여합니다." 또는 "~하여 이 상을 드립니다." 등으로 격식 있게 맺어라.
        """.trimIndent()
        
        var lastException: Exception? = null
        var delayMs = 1000L
        for (attempt in 1..3) {
            try {
                val response = generativeModel.generateContent(prompt)
                val text = response.text?.trim()
                if (!text.isNullOrEmpty()) {
                    return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lastException = e
                
                // Do not retry for non-retryable errors (e.g. invalid API key, prompt blocked, local settings, model not found 404)
                val isNonRetryable = e is com.google.ai.client.generativeai.type.InvalidAPIKeyException ||
                                     e is com.google.ai.client.generativeai.type.PromptBlockedException ||
                                     (e.message?.contains("API key") == true) ||
                                     (e.message?.contains("404") == true) ||
                                     (e.message?.contains("not found") == true)
                
                if (isNonRetryable || attempt == 3) {
                    throw e
                }
            }
            // Wait before next attempt with exponential backoff
            kotlinx.coroutines.delay(delayMs)
            delayMs *= 2
        }
        if (lastException != null) {
            throw lastException
        }
        return null
    }

    /**
     * FR-01: Gemini API call to generate award body texts
     */
    private fun generateAwardText(reason: String) {
        binding.btnGenerateText.isEnabled = false
        binding.btnGenerateText.text = "생성 중..."
        binding.tvCertContent.text = "AI가 상장 문구를 생성하고 있어요..."

        viewLifecycleOwner.lifecycleScope.launch {
            var resultText: String? = null
            var lastException: Exception? = null

            // 1. Try with gemini-2.5-flash directly (gemini-1.5-flash returns 404 for this API key)
            try {
                if (BuildConfig.GEMINI_API_KEY.isBlank() || BuildConfig.GEMINI_API_KEY.startsWith("YOUR_")) {
                    throw IllegalStateException("API key is not configured")
                }
                resultText = tryGenerateWithModel("gemini-2.5-flash", reason)
            } catch (e: Exception) {
                e.printStackTrace()
                lastException = e
            }

            try {
                if (!resultText.isNullOrEmpty()) {
                    binding.tvCertContent.text = resultText
                } else {
                    // Both failed, fall back to mock
                    val fallbackText = getMockAwardText(reason)
                    binding.tvCertContent.text = fallbackText

                    val errorMsg = lastException?.localizedMessage ?: lastException?.message ?: ""
                    val is503 = errorMsg.contains("503") || errorMsg.contains("UNAVAILABLE") || errorMsg.contains("demand")
                    
                    if (is503) {
                        Toast.makeText(
                            requireContext(),
                            "현재 AI 서버 트래픽이 많아 기본 문구로 대체되었습니다. 잠시 후 다시 시도해 주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val userMsg = "AI 서비스 오류로 기본 문구로 대체되었습니다. (${lastException?.javaClass?.simpleName})"
                        Toast.makeText(requireContext(), userMsg, Toast.LENGTH_LONG).show()

                        lastException?.let { ex ->
                            AlertDialog.Builder(requireContext())
                                .setTitle("Gemini API 디버그 상세 정보")
                                .setMessage("소스 위치: C드라이브 (C:/Mobile_Project)\n클래스: ${ex.javaClass.name}\n메시지: ${ex.message}\n\n상세 정보:\n${ex.stackTraceToString()}")
                                .setPositiveButton("닫기", null)
                                .show()
                        }
                    }
                }
            } catch (dialogEx: Exception) {
                dialogEx.printStackTrace()
            } finally {
                _binding?.let {
                    it.btnGenerateText.isEnabled = true
                    it.btnGenerateText.text = "AI 문구 생성"
                }
            }
        }
    }

    private fun getMockAwardText(reason: String): String {
        return "위 사람은 평소 \"$reason\" 활동을 통해 주변 사람들에게 큰 기쁨과 긍정적인 에너지를 전파하였으며, 그 헌신적인 노력이 타의 모범이 되므로 이 상장을 수여하여 칭찬하고 격려합니다."
    }

    /**
     * FR-03: Camera Permission Check & Intent Trigger
     */
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        try {
            val photoFile = File(requireContext().cacheDir, "camera_temp_${System.currentTimeMillis()}.jpg")
            tempCameraUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "카메라 열기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openGallery() {
        try {
            pickImageLauncher.launch("image/*")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "갤러리 열기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * FR-03: System Crop Intent for 1:1 Aspect Ratio Cropping
     */
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
                putExtra("aspectX", 1)
                putExtra("aspectY", 1)
                putExtra("outputX", 500)
                putExtra("outputY", 500)
                putExtra("scale", true)
                putExtra(MediaStore.EXTRA_OUTPUT, cropResultUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            // Modern Android: set ClipData and grant permissions explicitly
            cropIntent.clipData = android.content.ClipData.newRawUri("", sourceUri).apply {
                addItem(android.content.ClipData.Item(cropResultUri))
            }

            val list = requireContext().packageManager.queryIntentActivities(cropIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (list.isEmpty()) {
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

    private fun applyImageToCertificate(uri: Uri) {
        binding.ivCustomImage.visibility = View.VISIBLE
        binding.btnRemoveSticker.visibility = View.VISIBLE
        binding.ivCustomImage.load(uri) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_report_image)
        }
    }

    /**
     * FR-08: Format and update square red stamp text dynamically (2x2 grid)
     * Fallbacks to issuer name or user nickname if custom stamp text is empty.
     */
    private fun updateStampText() {
        val isStampEnabled = binding.switchIssuerStamp.isChecked
        if (!isStampEnabled) {
            binding.tvStampText.visibility = View.GONE
            return
        }

        val customText = binding.etStampText.text.toString().replace(" ", "")
        val issuerText = binding.etIssuerName.text.toString().replace(" ", "")

        val targetText = if (customText.isNotEmpty()) {
            customText
        } else if (issuerText.isNotEmpty()) {
            issuerText
        } else {
            SecurityHelper.getNickname(requireContext())
        }

        val formattedText = when (targetText.length) {
            1 -> "$targetText\n인"
            2 -> "${targetText[0]}\n${targetText[1]}"
            3 -> "${targetText.substring(0, 2)}\n${targetText[2]}인"
            4 -> "${targetText.substring(0, 2)}\n${targetText.substring(2, 4)}"
            else -> "${targetText.substring(0, 2)}\n직인"
        }

        binding.tvStampText.text = formattedText
        binding.tvStampText.visibility = View.VISIBLE
    }

    /**
     * FR-04, FR-05 & FR-07: Workflow with Limit Check
     */
    private fun saveCertificateWorkflow(shouldShare: Boolean, andNavigateToStorage: Boolean = false) {
        val context = requireContext()

        // Verify mandatory inputs are filled
        val recipient = binding.etRecipientName.text.toString().trim()
        val reason = binding.etAwardReason.text.toString().trim()
        val issuer = binding.etIssuerName.text.toString().trim()

        if (recipient.isEmpty() || reason.isEmpty() || issuer.isEmpty()) {
            Toast.makeText(context, "받는 사람 이름, 상장 수여 사유, 주는 사람 이름을 모두 작성해 주세요!", Toast.LENGTH_LONG).show()
            return
        }

        // FR-07: Creation Limit Check before saving/sharing
        if (SecurityHelper.isLimitExceeded(context)) {
            showLimitExceededDialog()
            return
        }

        val canvasView = binding.layoutCertificateCanvas
        if (canvasView.width <= 0 || canvasView.height <= 0) {
            Toast.makeText(context, "상장 캔버스를 렌더링하는 중입니다. 잠시만 기다려주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val capturedBitmap = captureView(canvasView)
        setOperationButtonsEnabled(false)

        viewLifecycleOwner.lifecycleScope.launch {
            val savedUri = saveBitmapToGallery(capturedBitmap)
            
            withContext(Dispatchers.Main) {
                setOperationButtonsEnabled(true)
                if (savedUri != null) {
                    // Increment count on successful creation
                    SecurityHelper.incrementCreationCount(context)
                    
                    if (shouldShare) {
                        shareCertificateImage(savedUri)
                    } else {
                        if (andNavigateToStorage) {
                            Toast.makeText(context, "상장이 보관함에 저장되었습니다! 🎉", Toast.LENGTH_LONG).show()
                            (activity as? MainActivity)?.navigateToTab(R.id.action_storage)
                        } else {
                            Toast.makeText(context, "상장이 갤러리에 저장되었습니다! 🎉", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "저장 중 오류가 발생했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * FR-07: Show Upgrade dialog when limit is exceeded
     */
    private fun showLimitExceededDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("상장 제작 횟수 초과! ⚠️")
            .setMessage("비회원(게스트)은 누적 2회까지만 상장을 제작할 수 있습니다.\n\n회원가입 시 6회까지 제작 가능하며, 프리미엄 업그레이드 시 무제한 제작이 가능합니다!")
            .setPositiveButton("회원가입/업그레이드") { _, _ ->
                // Transition to MyPage Tab
                (activity as? MainActivity)?.navigateToTab(R.id.action_mypage)
            }
            .setNegativeButton("나중에", null)
            .show()
    }

    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private suspend fun saveBitmapToGallery(bitmap: Bitmap): Uri? {
        val context = requireContext()
        val username = SecurityHelper.getCurrentUser(context)
        return withContext(Dispatchers.IO) {
            val filename = "certificate_${username}_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null
            var imageUri: Uri? = null
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyCertificates")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            try {
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = resolver.openOutputStream(imageUri)
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
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

    private fun shareCertificateImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "상장 공유하기"))
    }

    private fun setOperationButtonsEnabled(enabled: Boolean) {
        _binding?.let {
            it.btnSaveStorage.isEnabled = enabled
            it.btnSaveGallery.isEnabled = enabled
            it.btnShareSns.isEnabled = enabled
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun showExitConfirmationDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("상장 제작 중단 ⚠️")
            .setMessage("지금 페이지를 나가시면 작성 중인 상장 내용이 모두 사라집니다. 정말 나가시겠습니까?")
            .setPositiveButton("나가기") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("계속 작성", null)
            .show()
    }
}
