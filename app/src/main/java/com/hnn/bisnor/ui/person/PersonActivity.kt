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
import com.hnn.bisnor.data.repository.PersonIndex
import com.hnn.bisnor.data.repository.RealMediaRepository
import com.hnn.bisnor.databinding.ActivityPersonBinding
import com.hnn.bisnor.ui.adapter.PersonRowAdapter
import com.hnn.bisnor.ui.adapter.RealMediaAdapter
import com.hnn.bisnor.ui.detail.DetailActivity
import com.hnn.bisnor.util.MediaMetadataHelper
import com.hnn.bisnor.util.PersonPhotoResolver
import com.hnn.bisnor.util.ThemeHelper
import com.hnn.bisnor.util.Tr
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PersonActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonBinding
    private lateinit var adapter: RealMediaAdapter
    private lateinit var personName: String
    private lateinit var personRole: String
    private var personNameFa: String = ""
    private var personCharacter: String = ""
    private var sourceMedia: RealMedia? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityPersonBinding.inflate(layoutInflater)
        setContentView(binding.root)

        personName = intent.getStringExtra("person_name").orEmpty().trim()
        personNameFa = intent.getStringExtra("person_name_fa").orEmpty().trim()
        personCharacter = intent.getStringExtra("person_character").orEmpty().trim()
        personRole = intent.getStringExtra("person_role").orEmpty()
        sourceMedia = intent.getSerializableExtra("source_media") as? RealMedia
        if (personName.isEmpty() && personNameFa.isEmpty()) {
            finish()
            return
        }
        if (personName.isEmpty()) personName = personNameFa

        binding.toolbarPerson.setNavigationOnClickListener { finish() }
        binding.tvPersonName.text = personName
        binding.tvPersonRole.text = subtitle()

        val passedPhoto = intent.getStringExtra("person_photo").orEmpty()
        val knownPhoto = passedPhoto.ifBlank { PersonIndex.photo(personName) }
        if (knownPhoto.isNotBlank()) {
            showPhoto(knownPhoto)
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
        binding.recyclerPersonWorks.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerPersonWorks.adapter = adapter
        binding.btnPersonRetry.setOnClickListener { loadWorks() }

        loadWorks()
    }

    private fun subtitle(): String {
        val role = PersonRowAdapter.roleLabel(personRole)
        return if (personCharacter.isNotBlank()) "$role  •  $personCharacter" else role
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

    /**
     * Local credits come first so a film opened a moment ago is never missing. The provider
     * search then adds anything the index has not seen yet, in both languages.
     */
    private fun loadWorks() {
        binding.skeletonPerson.visibility = View.VISIBLE
        binding.layoutPersonEmpty.visibility = View.GONE
        binding.layoutPersonError.visibility = View.GONE
        binding.recyclerPersonWorks.visibility = View.INVISIBLE

        val known = PersonIndex.works(personName).ifEmpty { PersonIndex.works(personNameFa) }
        val immediate = (listOfNotNull(sourceMedia) + known).distinctBy { it.id }
        if (immediate.isNotEmpty()) publish(immediate)

        lifecycleScope.launch {
            val outcome = runCatching {
                coroutineScope {
                    val fa = async {
                        if (personNameFa.isBlank() || personNameFa == personName) emptyList()
                        else RealMediaRepository.search(personNameFa)
                    }
                    val en = async { RealMediaRepository.search(personName) }
                    en.await() + fa.await()
                }
            }
            if (isFinishing || isDestroyed) return@launch

            val results = outcome.getOrNull()
            if (results == null && immediate.isEmpty()) {
                showError()
                return@launch
            }
            val names = listOf(personName, personNameFa).filter { it.isNotBlank() }
            val matched = results.orEmpty().filter { item -> names.any { features(item, it) } }
            PersonIndex.rememberAll(matched)
            publish((immediate + matched).distinctBy { it.id })
        }
    }

    private fun publish(works: List<RealMedia>) {
        if (isFinishing || isDestroyed) return
        binding.skeletonPerson.visibility = View.GONE
        binding.layoutPersonError.visibility = View.GONE
        binding.recyclerPersonWorks.visibility = View.VISIBLE
        adapter.updateData(works)
        binding.layoutPersonEmpty.visibility = if (works.isEmpty()) View.VISIBLE else View.GONE
        binding.tvPersonWorksCount.visibility = if (works.isEmpty()) View.GONE else View.VISIBLE
        binding.tvPersonWorksCount.text = Tr.s(R.string.person_works_count, works.size)
    }

    private fun showError() {
        binding.skeletonPerson.visibility = View.GONE
        binding.layoutPersonEmpty.visibility = View.GONE
        binding.recyclerPersonWorks.visibility = View.INVISIBLE
        binding.layoutPersonError.visibility = View.VISIBLE
    }

    private fun features(item: RealMedia, name: String): Boolean {
        if (item.people.any { it.name.equals(name, true) || it.nameFa.equals(name, true) }) return true
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
