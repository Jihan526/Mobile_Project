package com.example.mobileprogramming

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.mobileprogramming.databinding.FragmentHomeDashboardBinding
import com.google.android.material.button.MaterialButton

class HomeDashboardFragment : Fragment() {

    private var _binding: FragmentHomeDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Update Profile & Limits UI
        updateUserStats()

        // 2. Setup Create Certificate Button click
        binding.btnGoCreate.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .addToBackStack(null)
                .commit()
        }

        // 3. Logout action
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠습니까?")
                .setPositiveButton("로그아웃") { _, _ ->
                    SecurityHelper.logout(requireContext())
                    val intent = Intent(requireContext(), AuthActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // 4. Load Recent Certificates list
        loadRecentCertificates()
    }

    override fun onResume() {
        super.onResume()
        updateUserStats()
        loadRecentCertificates()
    }

    private fun updateUserStats() {
        val context = requireContext()
        val nickname = SecurityHelper.getNickname(context)
        val grade = SecurityHelper.getUserGrade(context)
        val currentCount = SecurityHelper.getCreationCount(context)
        val maxLimit = SecurityHelper.getMaxLimit(context)

        binding.tvDashboardGreeting.text = "안녕하세요, ${nickname}님! 👋"
        binding.tvDashboardGrade.text = "$grade 등급"

        if (grade == "프리미엄") {
            binding.tvDashboardUsage.text = "상장 제작 횟수: 무제한 (프리미엄 구독 중)"
            binding.pbDashboardUsage.visibility = View.GONE
        } else {
            binding.tvDashboardUsage.text = "남은 제작 횟수: ${maxLimit - currentCount} / $maxLimit 회"
            binding.pbDashboardUsage.visibility = View.VISIBLE
            binding.pbDashboardUsage.max = maxLimit
            binding.pbDashboardUsage.progress = currentCount
        }
    }

    private fun loadRecentCertificates() {
        val items = queryRecentCertificates()

        if (items.isEmpty()) {
            binding.rvRecentCertificates.visibility = View.GONE
            binding.layoutRecentEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutRecentEmpty.visibility = View.GONE
            binding.rvRecentCertificates.visibility = View.VISIBLE

            val adapter = RecentAdapter(items) { selectedItem ->
                showDetailDialog(selectedItem)
            }
            binding.rvRecentCertificates.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            binding.rvRecentCertificates.adapter = adapter
        }
    }

    private fun queryRecentCertificates(): List<StorageItem> {
        val items = mutableListOf<StorageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val username = SecurityHelper.getCurrentUser(requireContext())
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("certificate_${username}_%")
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

                var count = 0
                while (c.moveToNext() && count < 5) { // Limit to latest 5 items
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    items.add(StorageItem(contentUri, name))
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items
    }

    private fun showDetailDialog(item: StorageItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_certificate_detail, null)
        val ivDetail = dialogView.findViewById<ImageView>(R.id.iv_detail_image)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_close)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_share)

        ivDetail.load(item.uri) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_menu_report_image)
        }

        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            shareCertificateImage(item.uri)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareCertificateImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "상장 공유하기"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
