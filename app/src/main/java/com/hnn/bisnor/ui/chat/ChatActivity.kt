package com.hnn.bisnor.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.ChatMessage
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.remote.AuthManager
import com.hnn.bisnor.data.remote.SupabaseManager
import com.hnn.bisnor.databinding.ActivityChatBinding
import com.hnn.bisnor.databinding.ItemChatBubbleBinding
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.util.ThemeHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var authManager: AuthManager
    private var targetFriendUsername: String = ""
    private var pendingMediaToShare: RealMedia? = null
    private val messagesList = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatMessagesAdapter
    private var pollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        targetFriendUsername = intent.getStringExtra("target_username") ?: ""
        pendingMediaToShare = intent.getSerializableExtra("share_media") as? RealMedia

        if (!authManager.isLoggedIn) {
            Toast.makeText(this, "برای ارسال پیام ابتدا وارد حساب کاربری خود شوید.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (targetFriendUsername.isEmpty()) {
            Toast.makeText(this, "کاربر مقصد مشخص نیست.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.toolbarChat.title = "گفتگو با $targetFriendUsername"
        binding.toolbarChat.setNavigationOnClickListener { finish() }

        chatAdapter = ChatMessagesAdapter()
        binding.recyclerChatMessages.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }

        // If launched to share a movie, auto-populate or send
        pendingMediaToShare?.let { media ->
            binding.etChatInput.setText("فیلم «${media.title}» رو بهت پیشنهاد می‌کنم!")
        }

        binding.btnSendChat.setOnClickListener {
            sendMessage()
        }

        startPollingMessages()
    }

    private fun startPollingMessages() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                loadMessages()
                delay(4000) // Poll every 4 seconds
            }
        }
    }

    private suspend fun loadMessages() {
        val msgs = SupabaseManager.getMessagesBetween(authManager.currentUsername, targetFriendUsername)
        if (msgs != messagesList) {
            messagesList.clear()
            messagesList.addAll(msgs)
            chatAdapter.notifyDataSetChanged()
            if (messagesList.isNotEmpty()) {
                binding.recyclerChatMessages.scrollToPosition(messagesList.size - 1)
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etChatInput.text.toString().trim()
        val mediaToSend = pendingMediaToShare
        if (text.isEmpty() && mediaToSend == null) return

        binding.btnSendChat.isEnabled = false
        lifecycleScope.launch {
            val success = SupabaseManager.sendMessage(
                sender = authManager.currentUsername,
                receiver = targetFriendUsername,
                messageText = text,
                media = mediaToSend
            )
            binding.btnSendChat.isEnabled = true
            if (success) {
                binding.etChatInput.setText("")
                pendingMediaToShare = null
                loadMessages()
            } else {
                Toast.makeText(this@ChatActivity, "خطا در ارسال پیام. لطفاً اینترنت را چک کنید.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }

    inner class ChatMessagesAdapter : RecyclerView.Adapter<ChatMessagesAdapter.MsgViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MsgViewHolder {
            val b = ItemChatBubbleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return MsgViewHolder(b)
        }

        override fun getItemCount(): Int = messagesList.size

        override fun onBindViewHolder(holder: MsgViewHolder, position: Int) {
            holder.bind(messagesList[position])
        }

        inner class MsgViewHolder(val b: ItemChatBubbleBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(msg: ChatMessage) {
                val isMe = msg.sender == authManager.currentUsername
                val params = b.layoutMessageBubble.layoutParams as LinearLayout.LayoutParams
                if (isMe) {
                    params.gravity = Gravity.END
                    b.layoutMessageBubble.setBackgroundResource(R.drawable.badge_background)
                    b.tvChatSenderLabel.text = "شما"
                    b.tvChatSenderLabel.setTextColor(getColor(R.color.primary))
                } else {
                    params.gravity = Gravity.START
                    b.layoutMessageBubble.setBackgroundResource(R.drawable.badge_background)
                    b.tvChatSenderLabel.text = msg.sender
                    b.tvChatSenderLabel.setTextColor(getColor(R.color.secondary))
                }
                b.layoutMessageBubble.layoutParams = params

                b.tvChatText.text = msg.messageText
                b.tvChatText.visibility = if (msg.messageText.isNotEmpty()) View.VISIBLE else View.GONE

                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                b.tvChatTime.text = timeStr

                // Shared Film Preview
                val sharedMedia = msg.sharedMedia
                if (sharedMedia != null) {
                    b.cardSharedFilm.visibility = View.VISIBLE
                    val banner = if (sharedMedia.cover.isNotEmpty()) sharedMedia.cover else sharedMedia.image
                    b.imgSharedFilmPoster.load(banner) { crossfade(true) }
                    b.tvSharedFilmTitle.text = sharedMedia.title

                    b.cardSharedFilm.setOnClickListener {
                        val intent = Intent(this@ChatActivity, DetailActivity::class.java).apply {
                            putExtra("real_media", sharedMedia)
                        }
                        startActivity(intent)
                    }
                } else {
                    b.cardSharedFilm.visibility = View.GONE
                }
            }
        }
    }
}
