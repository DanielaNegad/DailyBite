package com.example.dailybite.ui.feed

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dailybite.R
import com.example.dailybite.data.auth.AuthRepository
import com.example.dailybite.data.post.PostRepository
import com.example.dailybite.databinding.FragmentFeedBinding
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!

    private val vm: FeedViewModel by viewModels()

    @Inject lateinit var storage: FirebaseStorage
    @Inject lateinit var postsRepo: PostRepository
    @Inject lateinit var authRepo: AuthRepository

    private lateinit var adapter: PostAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // הפעלת תפריט עליון
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // יצירת אדפטר לפוסטים
        adapter = PostAdapter(
            storage = storage,
            onLike = { postId ->
                val uid = authRepo.currentUidOrNull() ?: run {
                    Toast.makeText(requireContext(), "צריך להתחבר כדי לעשות לייק", Toast.LENGTH_SHORT).show()
                    return@PostAdapter
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    val res = postsRepo.like(postId, uid)
                    if (res.isFailure) {
                        Toast.makeText(requireContext(), "הפעולה נכשלה", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onLongPress = { _, _ -> /* אין מחיקה בפיד הכללי */ },
            onComments = { postId ->
                val action = FeedFragmentDirections.actionFeedToPostComments(postId)
                findNavController().navigate(action)
            }
        )

        binding.rvPosts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPosts.adapter = adapter

        // תצפית על הפיד
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { s ->
                binding.tvEmpty.visibility = if (s.items.isEmpty()) View.VISIBLE else View.GONE
                binding.tvEmpty.text = "הפיד ריק כרגע. לחץ על כפתור הפלוס כדי לפרסם פוסט ראשון."
                adapter.submitList(s.items)
            }
        }

        // כפתור לפרסום פוסט חדש
        binding.fabNewPost.setOnClickListener {
            findNavController().navigate(FeedFragmentDirections.actionFeedToNewPost())
        }


    }

    // תפריט עליון
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_feed, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                authRepo.signOut()
                findNavController().navigate(FeedFragmentDirections.actionFeedToLogin())
                true
            }
            R.id.action_profile -> {
                findNavController().navigate(FeedFragmentDirections.actionFeedToProfile())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
