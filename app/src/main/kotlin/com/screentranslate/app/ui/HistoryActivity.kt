package com.screentranslate.app.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.screentranslate.app.R
import com.screentranslate.app.data.Flashcard
import com.screentranslate.app.data.HistoryEntry
import com.screentranslate.app.databinding.ActivityHistoryBinding
import com.screentranslate.app.databinding.ItemHistoryBinding
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val adapter = HistoryAdapter()
    private var allEntries = listOf<HistoryEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.history_title)

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            allEntries = application.appContainer.historyRepository.getAll()
            updateList(allEntries)
        }
    }

    private fun updateList(entries: List<HistoryEntry>) {
        adapter.submitList(entries)
        binding.tvHistoryEmpty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                val q = newText.orEmpty().lowercase()
                updateList(if (q.isBlank()) allEntries else allEntries.filter {
                    it.originalText.lowercase().contains(q) || it.translatedText.lowercase().contains(q)
                })
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_clear_history -> {
                AlertDialog.Builder(this)
                    .setTitle(R.string.history_clear_title)
                    .setMessage(R.string.history_clear_confirm)
                    .setPositiveButton(R.string.clear) { _, _ ->
                        lifecycleScope.launch {
                            application.appContainer.historyRepository.clear()
                            allEntries = emptyList()
                            updateList(emptyList())
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var items = listOf<HistoryEntry>()

        fun submitList(list: List<HistoryEntry>) {
            items = list
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemHistoryBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            holder.binding.tvHistoryTimestamp.text = fmt.format(Date(entry.timestamp))
            holder.binding.tvHistoryOriginal.text = entry.originalText
            holder.binding.tvHistoryTranslated.text = entry.translatedText
            holder.binding.tvHistoryLangs.text = "${entry.sourceLang.uppercase()} → ${entry.targetLang.uppercase()}"

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@HistoryActivity)
                    .setItems(arrayOf(
                        getString(R.string.save_flashcard),
                        getString(R.string.delete)
                    )) { _, which ->
                        when (which) {
                            0 -> saveAsFlashcard(entry)
                            1 -> deleteEntry(entry)
                        }
                    }
                    .show()
                true
            }
        }

        override fun getItemCount() = items.size

        private fun saveAsFlashcard(entry: HistoryEntry) {
            lifecycleScope.launch {
                application.appContainer.flashcardRepository.save(
                    Flashcard(
                        id = UUID.randomUUID().toString(),
                        originalText = entry.originalText,
                        translatedText = entry.translatedText,
                        sourceLang = entry.sourceLang,
                        targetLang = entry.targetLang
                    )
                )
                Toast.makeText(this@HistoryActivity, R.string.flashcard_saved, Toast.LENGTH_SHORT).show()
            }
        }

        private fun deleteEntry(entry: HistoryEntry) {
            lifecycleScope.launch {
                application.appContainer.historyRepository.delete(entry.id)
                allEntries = allEntries.filter { it.id != entry.id }
                updateList(allEntries)
            }
        }
    }
}
