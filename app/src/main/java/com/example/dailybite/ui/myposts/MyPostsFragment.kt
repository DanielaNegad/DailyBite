package com.example.dailybite.ui.myposts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailybite.R
import com.example.dailybite.databinding.FragmentFeedBinding
import com.example.dailybite.ui.feed.PostAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MyPostsFragment : Fragment(R.layout.fragment_feed) {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var storage: FirebaseStorage
    private val vm: MyPostsViewModel by viewModels()

    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedBinding.bind(view)

        // מסתירים כפתורים שלא רלוונטיים במסך "הפוסטים שלי"
        binding.fabNewPost.visibility = View.GONE

        adapter = PostAdapter(
            storage = storage,
            onLike = { /* לא נדרש במסך הזה */ },
            onLongPress = { postId, imagePath ->
                val item = vm.state.value.items.firstOrNull { it.id == postId } ?: return@PostAdapter
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("בחר פעולה")
                    .setItems(arrayOf("ערוך", "מחק")) { _, which ->
                        when (which) {
                            0 -> { // עריכה
                                val action = MyPostsFragmentDirections.actionMyPostsToPostEdit(
                                    postId = postId,
                                    imageStoragePath = imagePath,
                                    mealType = item.mealType,
                                    description = item.description
                                )
                                findNavController().navigate(action)
                            }
                            1 -> { // מחיקה
                                MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("מחיקת פוסט")
                                    .setMessage("האם למחוק את הפוסט לצמיתות?")
                                    .setPositiveButton("מחק") { _, _ ->
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            val res = vm.deletePost(postId, imagePath)
                                            val msg = if (res.isSuccess) "הפוסט נמחק" else "מחיקה נכשלה"
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .setNegativeButton("בטל", null)
                                    .show()
                            }
                        }
                    }
                    .show()
            },
            onProfile = {
                findNavController().navigate(R.id.action_myPosts_to_profile)
            }
        )

        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter



        // תצוגה ריקה אם אין פוסטים
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { s ->
                binding.tvEmpty.visibility = if (s.items.isEmpty()) View.VISIBLE else View.GONE
                adapter.submitList(s.items)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}