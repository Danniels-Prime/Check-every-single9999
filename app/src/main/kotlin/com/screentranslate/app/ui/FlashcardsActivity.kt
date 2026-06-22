package com.screentranslate.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.screentranslate.app.R
import com.screentranslate.app.data.Flashcard
import com.screentranslate.app.databinding.ActivityFlashcardsBinding
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.launch

class FlashcardsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlashcardsBinding
    private val adapter = FlashcardAdapter(
        onDelete = { card -> deleteCard(card) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.flashcards_title)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        loadCards()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        if (item.itemId == R.id.action_export) {
            exportCsv()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_flashcards, menu)
        return true
    }

    private fun loadCards() {
        lifecycleScope.launch {
            val cards = application.appContainer.flashcardRepository.getAll()
            adapter.submitList(cards)
            binding.tvEmpty.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (cards.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun deleteCard(card: Flashcard) {
        lifecycleScope.launch {
            application.appContainer.flashcardRepository.delete(card.id)
            loadCards()
        }
    }

    private fun exportCsv() {
        lifecycleScope.launch {
            val csv = application.appContainer.flashcardRepository.exportCsv()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_TEXT, csv)
                putExtra(Intent.EXTRA_SUBJECT, "Screen Translate Flashcards")
            }
            startActivity(Intent.createChooser(intent, getString(R.string.export_flashcards)))
        }
    }

    class FlashcardAdapter(
        private val onDelete: (Flashcard) -> Unit
    ) : RecyclerView.Adapter<FlashcardAdapter.ViewHolder>() {

        private var items: List<Flashcard> = emptyList()

        fun submitList(cards: List<Flashcard>) {
            items = cards
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_flashcard, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvOriginal: TextView = itemView.findViewById(R.id.tvOriginal)
            private val tvTranslated: TextView = itemView.findViewById(R.id.tvTranslated)
            private val tvDefinition: TextView = itemView.findViewById(R.id.tvDefinition)
            private val tvExamples: TextView = itemView.findViewById(R.id.tvExamples)
            private val tvLang: TextView = itemView.findViewById(R.id.tvLang)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            fun bind(card: Flashcard) {
                tvOriginal.text = card.originalText
                tvTranslated.text = card.translatedText
                tvLang.text = "${card.sourceLang.uppercase()} → ${card.targetLang.uppercase()}"

                if (card.definition.isNotBlank()) {
                    tvDefinition.text = card.definition
                    tvDefinition.visibility = View.VISIBLE
                } else {
                    tvDefinition.visibility = View.GONE
                }

                if (card.examples.isNotEmpty()) {
                    tvExamples.text = card.examples.mapIndexed { i, ex -> "${i + 1}. $ex" }.joinToString("\n")
                    tvExamples.visibility = View.VISIBLE
                } else {
                    tvExamples.visibility = View.GONE
                }

                btnDelete.setOnClickListener { onDelete(card) }
            }
        }
    }
}
