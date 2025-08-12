package com.example.dailybite.ui.myposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dailybite.data.auth.AuthRepository
import com.example.dailybite.data.post.PostItem
import com.example.dailybite.data.post.PostRepository
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyPostsUiState(
    val loading: Boolean = true,
    val items: List<PostItem> = emptyList()
)

@HiltViewModel
class MyPostsViewModel @Inject constructor(
    private val repo: PostRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    private var syncReg: ListenerRegistration? = null
    private val uid: String? = authRepo.currentUidOrNull()

    val state: StateFlow<MyPostsUiState> =
        if (uid.isNullOrEmpty()) {
            flowOf(emptyList<PostItem>())
                .map { MyPostsUiState(loading = false, items = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPostsUiState())
        } else {
            repo.myPostsLocalFlow(uid)
                .map { MyPostsUiState(loading = false, items = it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyPostsUiState())
        }

    init {
        uid?.let { u ->
            syncReg = repo.startMyPostsSync(u)
        }
    }

    /**
     * מחיקת פוסט לפי מזהה + נתיב תמונה (אם יש)
     */
    suspend fun deletePost(postId: String, imagePath: String?): Result<Unit> {
        return repo.deletePost(postId, imagePath ?: "")
    }

    override fun onCleared() {
        syncReg?.remove()
        super.onCleared()
    }
}
