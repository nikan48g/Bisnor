package com.hnn.bisnor.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.hnn.bisnor.R
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.remote.SupabaseManager
import com.hnn.bisnor.databinding.FragmentChatListBinding
import com.hnn.bisnor.databinding.ItemChatRecentUserBinding
import com.hnn.bisnor.util.QrCodeHelper
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!

    private lateinit var authManager: AuthManager
    private val recentUsers = mutableListOf<String>()
    private lateinit var adapter: RecentUsersAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())

        adapter = RecentUsersAdapter(recentUsers) { targetUser ->
            openChatWith(targetUser)
        }
        binding.recyclerRecentChats.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecentChats.adapter = adapter

        binding.btnChatMyQr.setOnClickListener {
            if (authManager.isLoggedIn) {
                showQrCodeDialog()
            } else {
                Toast.makeText(requireContext(), "ابتدا وارد حساب کاربری شوید.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNewChat.setOnClickListener {
            if (authManager.isLoggedIn) {
                showOpenChatDialog()
            } else {
                Toast.makeText(requireContext(), "ابتدا وارد حساب کاربری شوید.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShareInviteLink.setOnClickListener {
            if (authManager.isLoggedIn) {
                shareMyLink()
            } else {
                Toast.makeText(requireContext(), "ابتدا وارد حساب کاربری شوید.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRefreshChats.setOnClickListener {
            loadRecentChats()
        }

        binding.btnChatLogin.setOnClickListener {
            Toast.makeText(requireContext(), "از تب تنظیمات وارد حساب شوید.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun updateUIState() {
        if (!authManager.isLoggedIn) {
            binding.cardChatGuestPrompt.visibility = View.VISIBLE
            binding.layoutChatUserContent.visibility = View.GONE
        } else {
            binding.cardChatGuestPrompt.visibility = View.GONE
            binding.layoutChatUserContent.visibility = View.VISIBLE
            loadRecentChats()
        }
    }

    private fun loadRecentChats() {
        val user = authManager.currentUsername
        if (user.isEmpty()) return

        lifecycleScope.launch {
            val list = SupabaseManager.getRecentChatUsers(user)
            recentUsers.clear()
            recentUsers.addAll(list)
            adapter.notifyDataSetChanged()

            if (recentUsers.isEmpty()) {
                binding.layoutEmptyChats.visibility = View.VISIBLE
                binding.recyclerRecentChats.visibility = View.GONE
            } else {
                binding.layoutEmptyChats.visibility = View.GONE
                binding.recyclerRecentChats.visibility = View.VISIBLE
            }
        }
    }

    private fun openChatWith(targetUser: String) {
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("target_username", targetUser)
        }
        startActivity(intent)
    }

    private fun showOpenChatDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = "نام کاربری دوست خود را وارد کنید"
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val til = TextInputLayout(requireContext()).apply {
            setPadding(48, 16, 48, 8)
            addView(input)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("شروع گفتگوی جدید")
            .setMessage("نام کاربری دوستتان در بیسنور را وارد کنید:")
            .setView(til)
            .setPositiveButton("ورود به چت") { _, _ ->
                val target = input.text?.toString()?.trim()?.lowercase() ?: ""
                if (target.isNotEmpty()) {
                    if (target == authManager.currentUsername.lowercase()) {
                        Toast.makeText(requireContext(), "نمی‌توانید به خودتان پیام دهید!", Toast.LENGTH_SHORT).show()
                    } else {
                        openChatWith(target)
                    }
                }
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun shareMyLink() {
        val username = authManager.currentUsername
        val link = "bisnor://app/chat?user=$username"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "بیسنور - ارتباط با من")
            putExtra(
                Intent.EXTRA_TEXT,
                "سلام! در اپلیکیشن بیسنور به من پیام بده یا فیلم به اشتراک بذار:\n$link"
            )
        }
        startActivity(Intent.createChooser(shareIntent, "اشتراک با..."))
    }

    private fun showQrCodeDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_qr_code, null)
        val tvUser = dialogView.findViewById<TextView>(R.id.tv_qr_username)
        val imgQr = dialogView.findViewById<ImageView>(R.id.img_qr_code)
        val btnShare = dialogView.findViewById<View>(R.id.btn_share_my_code)

        val username = authManager.currentUsername
        tvUser.text = "@$username"

        val link = "bisnor://app/chat?user=$username"
        val qrBitmap = QrCodeHelper.generateQrCode(link, 512)
        if (qrBitmap != null) {
            imgQr.setImageBitmap(qrBitmap)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        btnShare.setOnClickListener {
            shareMyLink()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inner class RecentUsersAdapter(
        private val users: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<RecentUsersAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val b = ItemChatRecentUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return Holder(b)
        }

        override fun getItemCount(): Int = users.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(users[position])
        }

        inner class Holder(val b: ItemChatRecentUserBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(user: String) {
                b.tvChatUsername.text = "@$user"
                b.root.setOnClickListener { onClick(user) }
            }
        }
    }
}
