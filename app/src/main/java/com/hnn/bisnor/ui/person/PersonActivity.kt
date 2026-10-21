package com.hnn.bisnor.ui.person

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import coil.load
import com.hnn.bisnor.R
import com.hnn.bisnor.data.model.RealMedia
import com.hnn.bisnor.data.model.RealPerson
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityPersonBinding
import com.hnn.bisnor.ui.adapter.PersonRowAdapter
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.util.MediaMetadataHelper
import com.hnn.bisnor.util.PersonPhotoResolver
import com.hnn.bisnor.util.ThemeHelper
import kotlinx.coroutines.launch

class PersonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonBinding
    private lateinit var adapter: RealMediaAdapter
    private lateinit var personName: String
    private lateinit var personRole: String

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        personName = intent.getStringExtra("person_name").orEmpty().trim()
        personRole = intent.getStringExtra("person_role").orEmpty()
        if (personName.isEmpty()) {
            finish()
            return
        }

        binding.toolbarPerson.setNavigationOnClickListener { finish() }
        binding.tvPersonName.text = personName
        binding.tvPersonRole.text = PersonRowAdapter.roleLabel(personRole)

        val passedPhoto = intent.getStringExtra("person_photo").orEmpty()
        if (passedPhoto.isNotBlank()) {
            showPhoto(passedPhoto)
        } else {
            lifecycleScope.launch {
                val photo = PersonPhotoResolver.resolve(personName)
                if (photo.isNotBlank() && !isFinishing && !isDestroyed) showPhoto(photo)
            }
        }

        adapter = RealMediaAdapter(emptyList(), onItemClick = { media ->
            startActivity(
                Intent(this, DetailActivity::class.java).apply {
                    putExtra("real_media", media)
                }
            )
        }, isGrid = true)
        binding.recyclerPersonWorks.layoutManager =
            GridLayoutManager(this, resources.getInteger(R.integer.grid_columns_count))
        binding.recyclerPersonWorks.adapter = adapter

        loadWorks()
    }

    private fun showPhoto(url: String) {
        binding.imgPersonAvatar.setPadding(0, 0, 0, 0)
        binding.imgPersonAvatar.imageTintList = null
        binding.imgPersonAvatar.load(url) {
            crossfade(true)
            placeholder(R.drawable.bg_skeleton)
            error(R.drawable.ic_person)
        }
    }

    private fun loadWorks() {
        binding.progressPerson.visibility = View.VISIBLE
        binding.layoutPersonEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val results = runCatching { RealMediaRepository.search(personName) }.getOrDefault(emptyList())
            val works = results.filter { item -> features(item, personName) }.distinctBy { it.id }
            if (isFinishing || isDestroyed) return@launch

            binding.progressPerson.visibility = View.GONE
            adapter.updateData(works)
            binding.layoutPersonEmpty.visibility = if (works.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun features(item: RealMedia, name: String): Boolean {
        if (item.people.any { it.name.equals(name, true) }) return true
        val meta = MediaMetadataHelper.parse(item.description, item.imdb, isSeries = item.isSeries)
        if (meta.director?.contains(name, ignoreCase = true) == true) return true
        if (meta.actors?.contains(name, ignoreCase = true) == true) return true
        return item.description.contains(name, ignoreCase = true)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ThemeHelper.applyTheme(this)
    }
}
