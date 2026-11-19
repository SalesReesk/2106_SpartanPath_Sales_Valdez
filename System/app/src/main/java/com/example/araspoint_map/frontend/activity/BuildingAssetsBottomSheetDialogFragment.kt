package com.example.araspoint_map.frontend.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.araspoint_map.BuildConfig
import com.example.araspoint_map.R
import com.example.araspoint_map.backend.model.Asset
import com.example.araspoint_map.backend.model.Constants
import com.example.araspoint_map.backend.model.MaintenanceRecord
import com.example.araspoint_map.backend.model.enums.PriorityStatus
import com.example.araspoint_map.backend.model.enums.ReportStatus
import com.example.araspoint_map.backend.repository.AssetRepository
import com.example.araspoint_map.backend.repository.MaintenanceRepository
import com.example.araspoint_map.databinding.DialogAssetDetailsBinding
import com.example.araspoint_map.databinding.DialogBuildingAssetsBinding
import com.example.araspoint_map.databinding.DialogSubmitReportBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BuildingAssetsBottomSheetDialogFragment(
    private val buildingTitle: String,
    private val buildingSubtitle: String,
    private val onNavigateClickListener: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private var _binding: DialogBuildingAssetsBinding? = null
    private val binding get() = _binding!!
    private val assetRepository = AssetRepository()
    private val maintenanceRepository = MaintenanceRepository()

    private var capturedPhotoUrl = ""
    private var activePhotoStatusText: TextView? = null
    private var tempCameraUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            startCrop(tempCameraUri!!)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File(requireContext().cacheDir, "camera_bs_report_${System.currentTimeMillis()}.jpg")
            tempCameraUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", photoFile)
            takePictureLauncher.launch(tempCameraUri!!)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { startCrop(it) }
    }

    private val cropActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                uploadEvidenceToCloudinary(resultUri)
            }
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(File(requireContext().cacheDir, "cropped_bs_report_${System.currentTimeMillis()}.jpg"))
        val intent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(4f, 3f)
            .withMaxResultSize(1200, 900)
            .getIntent(requireContext())
        cropActivityResultLauncher.launch(intent)
    }

    private fun uploadEvidenceToCloudinary(imageUri: Uri) {
        activePhotoStatusText?.text = "Uploading evidence photo..."
        activePhotoStatusText?.setTextColor(Color.parseColor("#EF6C00"))
        Toast.makeText(requireContext(), "Uploading evidence to Cloudinary...", Toast.LENGTH_SHORT).show()

        MediaManager.get().upload(imageUri)
            .unsigned(BuildConfig.CLOUDINARY_UPLOAD_PRESET)
            .callback(object : UploadCallback {
                override fun onStart(requestId: String) {}
                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val secureUrl = resultData["secure_url"] as? String ?: ""
                    capturedPhotoUrl = secureUrl
                    activity?.runOnUiThread {
                        activePhotoStatusText?.text = "Evidence photo verified ✓"
                        activePhotoStatusText?.setTextColor(Color.parseColor("#2E7D32"))
                        Toast.makeText(requireContext(), "Evidence photo verified successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(requestId: String, error: ErrorInfo) {
                    activity?.runOnUiThread {
                        activePhotoStatusText?.text = "Upload failed"
                        activePhotoStatusText?.setTextColor(Color.parseColor("#C62828"))
                        Toast.makeText(requireContext(), "Upload failed: ${error.description}", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onReschedule(requestId: String, error: ErrorInfo) {}
            })
            .dispatch()
    }

    private fun showPhotoSourceDialog() {
        val options = arrayOf("Take Photo with Camera", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Capture Evidence Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            launchCamera()
                        } else {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                    1 -> {
                        try {
                            pickImageLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Could not open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBuildingAssetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.txtSheetBuildingTitle.text = buildingTitle
        binding.txtSheetBuildingSubtitle.text = buildingSubtitle

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        binding.btnNavigateToBuilding.setOnClickListener {
            onNavigateClickListener?.invoke()
            dismiss()
        }

        binding.rvBuildingAssets.layoutManager = LinearLayoutManager(requireContext())

        loadAndGroupAssetsByFloorAndRoom()
    }

    private fun loadAndGroupAssetsByFloorAndRoom() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = assetRepository.getAssets()
            val allAssets = if (result.isSuccess) result.getOrNull() ?: emptyList() else emptyList()

            val keywords = when {
                buildingTitle.contains("ADMIN", ignoreCase = true) -> listOf("admin", "registrar", "executive")
                buildingTitle.contains("CICS", ignoreCase = true) -> listOf("cics", "lab", "computer")
                buildingTitle.contains("HEB", ignoreCase = true) || buildingTitle.contains("LIBRARY", ignoreCase = true) -> listOf("heb", "library", "audio")
                buildingTitle.contains("CTEB", ignoreCase = true) -> listOf("cteb", "registrar", "cte")
                buildingTitle.contains("GYM", ignoreCase = true) -> listOf("gym", "grandstand", "athletics")
                buildingTitle.contains("TAAL", ignoreCase = true) -> listOf("taal")
                buildingTitle.contains("LEMERY", ignoreCase = true) -> listOf("lemery")
                buildingTitle.contains("IBAAN", ignoreCase = true) -> listOf("ibaan")
                buildingTitle.contains("HOSTEL", ignoreCase = true) -> listOf("hostel", "nasugbu hostel")
                buildingTitle.contains("IMT", ignoreCase = true) -> listOf("imt")
                buildingTitle.contains("LIAN", ignoreCase = true) -> listOf("lian", "cafeteria")
                buildingTitle.contains("ROSARIO", ignoreCase = true) -> listOf("rosario", "canteen")
                buildingTitle.contains("INFIRMARY", ignoreCase = true) -> listOf("infirmary", "clinic")
                buildingTitle.contains("APACIBLE", ignoreCase = true) -> listOf("apacible", "museum")
                buildingTitle.contains("CAS", ignoreCase = true) -> listOf("cas")
                buildingTitle.contains("ERMITA", ignoreCase = true) -> listOf("ermita")
                else -> listOf(buildingTitle.lowercase())
            }

            val filteredAssets = allAssets.filter { asset ->
                keywords.any { kw ->
                    asset.location.contains(kw, ignoreCase = true) ||
                    asset.assetName.contains(kw, ignoreCase = true) ||
                    asset.remarks.contains(kw, ignoreCase = true) ||
                    asset.assetType.contains(kw, ignoreCase = true)
                }
            }

            val matchedBuildingKey = Constants.BUILDING_FLOORS_MAP.keys.firstOrNull { key ->
                buildingTitle.contains(key, ignoreCase = true)
            }
            val isSingleFloorBuilding = matchedBuildingKey != null && (Constants.BUILDING_FLOORS_MAP[matchedBuildingKey]?.size == 1)

            val displayItems = mutableListOf<BuildingDisplayItem>()
            if (isSingleFloorBuilding) {
                for (asset in filteredAssets) {
                    displayItems.add(BuildingDisplayItem.AssetRow(asset))
                }
            } else {
                val floorRoomMap = mutableMapOf<String, MutableMap<String, MutableList<Asset>>>()

                for (asset in filteredAssets) {
                    val loc = asset.location
                    val locLower = loc.lowercase()

                    val floorName = when {
                        locLower.contains("3rd") || locLower.contains("third") || locLower.contains(" floor 3") -> "Third Floor"
                        locLower.contains("2nd") || locLower.contains("second") || locLower.contains(" floor 2") -> "Second Floor"
                        else -> "Ground Floor"
                    }

                    val roomName = when {
                        locLower.contains("room") -> {
                            val idx = locLower.indexOf("room")
                            loc.substring(idx).trim()
                        }
                        locLower.contains("lab") -> {
                            val idx = locLower.indexOf("lab")
                            loc.substring(idx).trim()
                        }
                        else -> "General Room"
                    }

                    floorRoomMap.getOrPut(floorName) { mutableMapOf() }
                        .getOrPut(roomName) { mutableListOf() }
                        .add(asset)
                }

                val floorsOrder = listOf("Ground Floor", "Second Floor", "Third Floor")

                for (floor in floorsOrder) {
                    val roomMap = floorRoomMap[floor]
                    if (!roomMap.isNullOrEmpty()) {
                        displayItems.add(BuildingDisplayItem.FloorHeader(floor))
                        for ((roomName, assetsInRoom) in roomMap) {
                            displayItems.add(BuildingDisplayItem.RoomHeader(roomName))
                            for (asset in assetsInRoom) {
                                displayItems.add(BuildingDisplayItem.AssetRow(asset))
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (_binding != null) {
                    if (displayItems.isEmpty()) {
                        binding.txtEmptyBuildingAssets.visibility = View.VISIBLE
                        binding.rvBuildingAssets.visibility = View.GONE
                    } else {
                        binding.txtEmptyBuildingAssets.visibility = View.GONE
                        binding.rvBuildingAssets.visibility = View.VISIBLE
                        binding.rvBuildingAssets.adapter = BuildingAssetsAdapter(displayItems) { asset ->
                            showAssetDetailsDialog(asset)
                        }
                    }
                }
            }
        }
    }

    private fun showAssetDetailsDialog(asset: Asset) {
        val dialogBinding = DialogAssetDetailsBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.detailAssetName.text = asset.assetName.ifBlank { asset.assetType }
        dialogBinding.detailAssetId.text = "Tag: ${asset.getFormattedMapTag()}"
        dialogBinding.detailAssetType.text = "Type: ${asset.assetType}"
        dialogBinding.detailAssetLocation.text = "Location: ${asset.location}"
        if (asset.remarks.isNotBlank()) {
            dialogBinding.detailAssetRemarks.visibility = View.VISIBLE
            dialogBinding.detailAssetRemarks.text = "Remarks: ${asset.remarks}"
        } else {
            dialogBinding.detailAssetRemarks.visibility = View.GONE
        }
        dialogBinding.detailAssetStatus.text = "Status: ${asset.status.name.replace("_", " ")}"
        dialogBinding.detailAssetCondition.text = "Condition: ${asset.condition}"

        dialogBinding.detailAssetImage.visibility = View.VISIBLE
        Glide.with(requireContext())
            .load(asset.photo.ifEmpty { null })
            .placeholder(R.drawable.ic_inventory_box)
            .error(R.drawable.ic_inventory_box)
            .into(dialogBinding.detailAssetImage)

        dialogBinding.btnReportThisAsset.setOnClickListener {
            dialog.dismiss()
            showReportIssueDialog(asset)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showReportIssueDialog(asset: Asset) {
        capturedPhotoUrl = ""
        val dialogBinding = DialogSubmitReportBinding.inflate(layoutInflater)
        activePhotoStatusText = dialogBinding.dialogTxtPhotoStatus

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.dialogReportTitle.setText("Issue: ${asset.assetName.ifBlank { asset.assetType }}")
        dialogBinding.dialogReportLocation.setText(asset.location)
        dialogBinding.dialogReportLocation.isEnabled = false
        dialogBinding.dialogReportLocation.isFocusable = false

        val priorities = arrayOf("LOW", "MEDIUM", "HIGH", "CRITICAL")
        val priorityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)
        dialogBinding.dialogReportPriority.adapter = priorityAdapter
        dialogBinding.dialogReportPriority.setSelection(1)

        dialogBinding.dialogBtnCapturePhoto.setOnClickListener {
            showPhotoSourceDialog()
        }

        dialogBinding.dialogBtnSubmitReport.setOnClickListener {
            val title = dialogBinding.dialogReportTitle.text.toString().trim()
            val location = dialogBinding.dialogReportLocation.text.toString().trim()
            val description = dialogBinding.dialogReportDescription.text.toString().trim()
            val priorityStr = dialogBinding.dialogReportPriority.selectedItem.toString()

            if (title.isEmpty()) {
                dialogBinding.dialogReportTitle.error = "Title required"
                return@setOnClickListener
            }
            if (location.isEmpty()) {
                dialogBinding.dialogReportLocation.error = "Location required"
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                dialogBinding.dialogReportDescription.error = "Description required"
                return@setOnClickListener
            }
            if (capturedPhotoUrl.isEmpty()) {
                Toast.makeText(requireContext(), "An evidence photo captured via camera is required to submit a report.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val priority = try {
                PriorityStatus.valueOf(priorityStr)
            } catch (_: Exception) {
                PriorityStatus.MEDIUM
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            val userEmail = currentUser?.email ?: "Anonymous"
            val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

            val generatedId = "REP-${System.currentTimeMillis()}-${(1000..9999).random()}"
            val record = MaintenanceRecord(
                reportId = generatedId,
                assetId = asset.assetId,
                title = title,
                description = description,
                location = location,
                assignedTo = "Unassigned",
                dateReported = nowStr,
                dateUpdated = "Pending Admin Review",
                status = ReportStatus.PENDING,
                priority = priority,
                reporterEmail = userEmail,
                photo = capturedPhotoUrl
            )

            val ctx = context
            CoroutineScope(Dispatchers.IO).launch {
                val result = maintenanceRepository.addReport(record)
                withContext(Dispatchers.Main) {
                    if (ctx != null) {
                        if (result.isSuccess) {
                            Toast.makeText(ctx, "Report ticket submitted successfully!", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        } else {
                            Toast.makeText(ctx, "Failed to submit report ticket", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
