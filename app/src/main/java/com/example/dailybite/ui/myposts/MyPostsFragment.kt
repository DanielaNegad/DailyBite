package com.example.dailybite.ui.myposts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailybite.databinding.FragmentFeedBinding
import com.example.dailybite.ui.feed.PostAdapter
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import com.example.dailybite.data.post.PostRepository

@AndroidEntryPoint
class MyPostsFragment : Fragment(com.example.dailybite.R.layout.fragment_feed) {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var storage: FirebaseStorage
    @Inject lateinit var repo: PostRepository

    private lateinit var adapter: PostAdapter
    private val vm: MyPostsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFeedBinding.bind(view)

        // אין צורך ב-FAB ובכפתור "הפוסטים שלי" במסך הזה
        binding.fabNewPost.visibility = View.GONE
        binding.btnMyPosts.visibility = View.GONE

        adapter = PostAdapter(
            storage = storage,
            onLike = { /* לא נדרש כאן */ },
            onLongPress = { postId, imagePath ->
                val item = vm.state.value.items.firstOrNull { it.id == postId } ?: return@PostAdapter
                // ניווט לעריכת פוסט עם SafeArgs
                val action = MyPostsFragmentDirections.actionMyPostsToPostEdit(
                    postId = postId,
                    imageStoragePath = imagePath,
                    mealType = item.mealType,
                    description = item.description
                )
                findNavController().navigate(action)
            }
        )

        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter

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
