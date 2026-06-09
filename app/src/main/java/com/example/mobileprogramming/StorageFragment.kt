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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.example.mobileprogramming.databinding.FragmentStorageBinding
import com.google.android.material.button.MaterialButton

class StorageFragment : Fragment() {

    private var _binding: FragmentStorageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCertificates()

        binding.btnDeleteAll.setOnClickListener {
            val items = queryCertificates()
            if (items.isEmpty()) {
                android.widget.Toast.makeText(requireContext(), "삭제할 상장이 없습니다", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setTitle("전체 삭제")
                    .setMessage("보관함에 저장된 모든 상장을 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        deleteAllCertificates()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload list on resume to capture new saved items
        loadCertificates()
    }

    private fun loadCertificates() {
        val items = queryCertificates()
        
        if (items.isEmpty()) {
            binding.rvStorage.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvStorage.visibility = View.VISIBLE

            val adapter = StorageAdapter(items) { selectedItem ->
                showDetailDialog(selectedItem)
            }
            binding.rvStorage.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.rvStorage.adapter = adapter
        }
    }

    /**
     * FR-06: Query MediaStore for saved certificate images
     */
    private fun queryCertificates(): List<StorageItem> {
        val items = mutableListOf<StorageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        // Query files that start with our prefix "certificate_[username]_"
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

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
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

    private fun deleteCertificate(item: StorageItem) {
        try {
            val rowsDeleted = requireContext().contentResolver.delete(item.uri, null, null)
            if (rowsDeleted > 0) {
                android.widget.Toast.makeText(requireContext(), "상장이 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                loadCertificates()
            } else {
                android.widget.Toast.makeText(requireContext(), "삭제에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(requireContext(), "오류 발생: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllCertificates() {
        val username = SecurityHelper.getCurrentUser(requireContext())
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("certificate_${username}_%")
        
        try {
            val cursor = requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )
            var count = 0
            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                    requireContext().contentResolver.delete(uri, null, null)
                    count++
                }
            }
            android.widget.Toast.makeText(requireContext(), "총 ${count}개의 상장이 삭제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
            loadCertificates()
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(requireContext(), "오류 발생: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * FR-06: Large View Dialog with Sharing
     */
    private fun showDetailDialog(item: StorageItem) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_certificate_detail, null)
        val ivDetail = dialogView.findViewById<ImageView>(R.id.iv_detail_image)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_close)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btn_dialog_delete)
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

        btnDelete.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("상장 삭제")
                .setMessage("이 상장을 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    deleteCertificate(item)
                    dialog.dismiss()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        btnShare.setOnClickListener {
            // FR-05: share via intent
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
