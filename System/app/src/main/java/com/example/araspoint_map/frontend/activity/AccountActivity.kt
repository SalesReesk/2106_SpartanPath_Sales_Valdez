package com.example.araspoint_map.frontend.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.araspoint_map.R
import com.example.araspoint_map.backend.repository.AuthRepository
import com.example.araspoint_map.databinding.ActivityAccountBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.accountRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Profile editing feature coming soon", Toast.LENGTH_SHORT).show()
        }

        loadUserProfile()
    }

    private fun loadUserProfile() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            binding.profileName.text = "Guest User"
            binding.profileEmail.text = "Not logged in"
            return
        }

        binding.profileEmail.text = currentUser.email ?: "—"
        binding.profileUid.text = "UID: ${currentUser.uid.take(8)}..."

        lifecycleScope.launch {
            try {
                val user = authRepository.getFullUser()
                if (user != null) {
                    binding.profileName.text = user.name.ifEmpty { "Spartan User" }
                    binding.profileRole.text = user.role.name
                    binding.profileCampus.text = user.campus.ifEmpty { "BatStateU ARASOF-Nasugbu" }
                    binding.profileIdNumber.text = user.idNumber.ifEmpty { "—" }
                    binding.profileAccessLevel.text = user.role.name
                } else {
                    binding.profileName.text = currentUser.displayName ?: "Spartan User"
                    binding.profileRole.text = "STUDENT"
                    binding.profileCampus.text = "BatStateU ARASOF-Nasugbu"
                }

                if (user?.photo?.isNotEmpty() == true) {
                    Glide.with(this@AccountActivity)
                        .load(user.photo)
                        .placeholder(R.drawable.user_icon)
                        .error(R.drawable.user_icon)
                        .circleCrop()
                        .into(binding.profileImage)
                } else {
                    currentUser.photoUrl?.let { uri ->
                        Glide.with(this@AccountActivity)
                            .load(uri)
                            .placeholder(R.drawable.user_icon)
                            .error(R.drawable.user_icon)
                            .circleCrop()
                            .into(binding.profileImage)
                    }
                }
            } catch (_: Exception) {
                binding.profileName.text = currentUser.displayName ?: "Spartan User"
            }
        }
    }
}
