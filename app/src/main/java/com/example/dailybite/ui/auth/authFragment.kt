package com.example.dailybite.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.dailybite.R
import com.example.dailybite.databinding.FragmentAuthBinding
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private val vm: AuthViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            findNavController().navigate(R.id.action_auth_to_feed)
            return
        }

        binding.btnLogin.setOnClickListener {
            vm.loginEmail(
                email = binding.etEmail.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty()
            )
        }

        binding.btnRegister.setOnClickListener {
            vm.register(
                email = binding.etEmail.text?.toString().orEmpty(),
                password = binding.etPassword.text?.toString().orEmpty()
            )
        }

        binding.tvForgot.setOnClickListener {
            vm.resetPassword(binding.etEmail.text?.toString().orEmpty())
        }

        binding.btnGuest.setOnClickListener {
            vm.loginGuest()
        }

        // תצפית בטוחה ללא launchWhenStarted (מוחלף ב-repeatOnLifecycle)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collectLatest { s ->
                    val loading = s.loading
                    binding.btnLogin.isEnabled = !loading
                    binding.btnRegister.isEnabled = !loading
                    binding.btnGuest.isEnabled = !loading
                    binding.tilEmail.isEnabled = !loading
                    binding.tilPassword.isEnabled = !loading
                    binding.progress.isVisible = loading

                    s.error?.let {
                        android.widget.Toast.makeText(
                            requireContext(),
                            it,
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        vm.consumeError()
                    }
                    if (s.loggedIn) {
                        findNavController().navigate(R.id.action_auth_to_feed)
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
