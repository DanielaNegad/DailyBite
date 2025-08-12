package com.example.dailybite.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.dailybite.R
import com.example.dailybite.databinding.FragmentProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val vm: ProfileViewModel by viewModels()

    private var pickedImage: Uri? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pickedImage = uri
            binding.ivAvatar.setImageURI(uri)
            binding.tilName.error = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // מפעיל האזנה לנתוני המשתמש
        vm.loadUserData()

        binding.btnPickImage.setOnClickListener { pickImage.launch("image/*") }

        // נקה שגיאת שדה כאשר המשתמש מקליד
        binding.etName.doAfterTextChanged {
            binding.tilName.error = null
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.tilName.error = getString(R.string.profile_error_name_required)
                return@setOnClickListener
            }
            vm.save(name, pickedImage)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { s ->
                binding.progress.isVisible = s.loading
                binding.btnSave.isEnabled = !s.loading
                binding.btnPickImage.isEnabled = !s.loading
                binding.tilName.isEnabled = !s.loading

                if (s.photoUrl != null && pickedImage == null) {
                    binding.ivAvatar.load(s.photoUrl) {
                        crossfade(true)
                        placeholder(R.drawable.ic_avatar_placeholder)
                        error(R.drawable.ic_avatar_placeholder)
                    }
                } else if (s.photoUrl == null && pickedImage == null) {
                    // פלייסהולדר אם אין תמונה בכלל
                    binding.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
                }

                if (binding.etName.text?.toString()?.trim().isNullOrEmpty() && s.name.isNotEmpty()) {
                    binding.etName.setText(s.name)
                }

                s.error?.let { msg ->
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    vm.consumeError()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
