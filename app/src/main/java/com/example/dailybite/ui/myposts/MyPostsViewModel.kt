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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    // זרם מצב המסך — אם אין UID, מחזיר רשימה ריקה ולא טוען מהשרת
    val state: StateFlow<MyPostsUiState> = (
            if (uid.isNullOrEmpty()) {
                emptyFlow<List<PostItem>>()
            } else {
                repo.myPostsLocalFlow(uid)
            }
            ).map { posts ->
            MyPostsUiState(loading = false, items = posts)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MyPostsUiState()
        )

    init {
        // התחלת סנכרון מהענן רק אם יש UID
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
        // ביטול הרשמת listener כדי למנוע דליפות זיכרון
        syncReg?.remove()
        super.onCleared()
    }
}
