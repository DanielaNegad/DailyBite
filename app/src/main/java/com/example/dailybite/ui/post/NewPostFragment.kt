package com.example.dailybite.ui.post

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dailybite.R
import com.example.dailybite.databinding.FragmentNewPostBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewPostFragment : Fragment() {

    private var _binding: FragmentNewPostBinding? = null
    private val binding get() = _binding!!
    private val vm: NewPostViewModel by viewModels()

    private var selectedImage: Uri? = null

    private val picker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImage = uri
            binding.imgPreview.setImageURI(uri)
        } else {
            Toast.makeText(requireContext(), "לא נבחרה תמונה", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ספינר סוגי ארוחה
        val meals = resources.getStringArray(R.array.meal_types).toList()
            .ifEmpty { listOf("בוקר", "צהריים", "ערב", "נשנוש") }
        binding.spMealType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            meals
        )

        // בחירת תמונה
        binding.btnPickImage.setOnClickListener {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // פרסום
        binding.btnPublish.setOnClickListener {
            val uri = selectedImage
            if (uri == null) {
                Toast.makeText(requireContext(), "נא לבחור תמונה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mealType = binding.spMealType.selectedItem?.toString() ?: "נשנוש"
            val desc = binding.etDescription.text?.toString()?.trim().orEmpty()
            val bytes = requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                Toast.makeText(requireContext(), "קריאת התמונה נכשלה", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.publish(mealType, desc, bytes)
        }

        // תצפית על ה-UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    // טעינה
                    binding.progress.isVisible = s.loading
                    binding.btnPublish.isEnabled = !s.loading
                    binding.btnPickImage.isEnabled = !s.loading
                    binding.spMealType.isEnabled = !s.loading
                    binding.etDescription.isEnabled = !s.loading

                    // שגיאה
                    s.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }

                    // הצלחה
                    if (s.successId != null) {
                        Toast.makeText(requireContext(), "הפוסט פורסם!", Toast.LENGTH_SHORT).show()
                        findNavController().popBackStack() // חזרה לפיד
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
