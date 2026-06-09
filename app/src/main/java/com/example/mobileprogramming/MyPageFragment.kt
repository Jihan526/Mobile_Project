package com.example.mobileprogramming

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.mobileprogramming.databinding.FragmentMypageBinding

class MyPageFragment : Fragment() {

    private var _binding: FragmentMypageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMypageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUsageStats()

        binding.btnUpgrade.setOnClickListener {
            // Placeholder for Premium upgrade actions
            Toast.makeText(
                requireContext(),
                "결제/업그레이드 기능은 추가될 예정입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnWithdraw.setOnClickListener {
            val context = requireContext()
            val username = SecurityHelper.getCurrentUser(context)
            if (username == "guest") {
                Toast.makeText(
                    context,
                    "회원은 로그인 상태에서만 탈퇴할 수 있습니다",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                AlertDialog.Builder(context)
                    .setTitle("회원 탈퇴")
                    .setMessage("정말 탈퇴하시겠습니까? 탈퇴 시 저장된 모든 상장 내역 및 제작 데이터가 삭제됩니다.")
                    .setPositiveButton("탈퇴") { _, _ ->
                        deleteUserCertificatesFromMediaStore(context, username)
                        val success = SecurityHelper.withdrawUser(context, username)
                        if (success) {
                            Toast.makeText(context, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                            val intent = Intent(context, AuthActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        } else {
                            Toast.makeText(context, "회원탈퇴 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh values on return
        updateUsageStats()
    }

    private fun updateUsageStats() {
        val context = requireContext()
        val grade = SecurityHelper.getUserGrade(context)
        val currentCount = SecurityHelper.getCreationCount(context)
        val maxLimit = SecurityHelper.getMaxLimit(context)

        binding.tvUsername.text = SecurityHelper.getNickname(context)
        binding.tvUserGrade.text = "$grade 등급"

        if (grade == "프리미엄") {
            binding.tvMypageUsage.text = "상장 제작 횟수: 무제한 (프리미엄 구독 중)"
            binding.pbMypageUsage.visibility = android.view.View.GONE
        } else {
            binding.pbMypageUsage.visibility = android.view.View.VISIBLE
            val remaining = (maxLimit - currentCount).coerceAtLeast(0)
            binding.tvMypageUsage.text = "남은 제작 횟수: $remaining / $maxLimit 회"
            binding.pbMypageUsage.max = maxLimit
            binding.pbMypageUsage.progress = currentCount
        }
    }

    private fun deleteUserCertificatesFromMediaStore(context: Context, username: String) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("certificate_${username}_%")
        
        try {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    context.contentResolver.delete(uri, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
