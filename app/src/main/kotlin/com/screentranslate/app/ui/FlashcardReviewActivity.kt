package com.screentranslate.app.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.screentranslate.app.R
import com.screentranslate.app.data.Flashcard
import com.screentranslate.app.databinding.ActivityFlashcardReviewBinding
import com.screentranslate.app.util.appContainer
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class FlashcardReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFlashcardReviewBinding

    private val queue = mutableListOf<Flashcard>()
    private var currentIndex = 0
    private var totalReviewed = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlashcardReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.review_title)

        binding.btnShowAnswer.setOnClickListener { showAnswer() }
        binding.btnAgain.setOnClickListener { rateCard(Rating.AGAIN) }
        binding.btnHard.setOnClickListener { rateCard(Rating.HARD) }
        binding.btnGood.setOnClickListener { rateCard(Rating.GOOD) }
        binding.btnEasy.setOnClickListener { rateCard(Rating.EASY) }

        loadDueCards()
    }

    private fun loadDueCards() {
        lifecycleScope.launch {
            val due = application.appContainer.flashcardRepository.getDueCards().shuffled()
            if (due.isEmpty()) {
                showAllDone()
                return@launch
            }
            queue.clear()
            queue.addAll(due)
            currentIndex = 0
            showFront()
        }
    }

    private fun showFront() {
        if (currentIndex >= queue.size) {
            showAllDone()
            return
        }
        val card = queue[currentIndex]
        val remaining = queue.size - currentIndex
        binding.tvProgress.text = getString(R.string.review_progress, remaining, queue.size + totalReviewed)
        binding.tvFront.text = card.originalText
        binding.tvBack.visibility = View.GONE
        binding.tvDefinition.visibility = View.GONE
        binding.tvExamples.visibility = View.GONE
        binding.layoutRating.visibility = View.GONE
        binding.btnShowAnswer.visibility = View.VISIBLE
        binding.layoutDone.visibility = View.GONE
    }

    private fun showAnswer() {
        val card = queue[currentIndex]
        binding.tvBack.text = card.translatedText
        binding.tvBack.visibility = View.VISIBLE

        if (card.definition.isNotBlank()) {
            binding.tvDefinition.text = card.definition
            binding.tvDefinition.visibility = View.VISIBLE
        }
        if (card.examples.isNotEmpty()) {
            binding.tvExamples.text = card.examples
                .mapIndexed { i, ex -> "${i + 1}. $ex" }
                .joinToString("\n")
            binding.tvExamples.visibility = View.VISIBLE
        }

        binding.btnShowAnswer.visibility = View.GONE
        binding.layoutRating.visibility = View.VISIBLE
    }

    private fun rateCard(rating: Rating) {
        val card = queue[currentIndex]
        val updated = applyRating(card, rating)

        lifecycleScope.launch {
            application.appContainer.flashcardRepository.save(updated)
        }

        if (rating == Rating.AGAIN) {
            // Loop failed card back later in the queue
            queue.add(updated)
        } else {
            totalReviewed++
        }

        currentIndex++
        showFront()
    }

    private fun applyRating(card: Flashcard, rating: Rating): Flashcard {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L

        return when (rating) {
            Rating.AGAIN -> card.copy(
                intervalDays = 1,
                repetitions = 0,
                easeFactor = max(1.3f, card.easeFactor - 0.20f),
                nextReviewAt = now + dayMs
            )
            Rating.HARD -> card.copy(
                intervalDays = max(1, (card.intervalDays * 1.2f).roundToInt()),
                easeFactor = max(1.3f, card.easeFactor - 0.15f),
                repetitions = card.repetitions + 1,
                nextReviewAt = now + max(1, (card.intervalDays * 1.2f).roundToInt()) * dayMs
            )
            Rating.GOOD -> {
                val newInterval = max(1, (card.intervalDays * card.easeFactor).roundToInt())
                card.copy(
                    intervalDays = newInterval,
                    repetitions = card.repetitions + 1,
                    nextReviewAt = now + newInterval * dayMs
                )
            }
            Rating.EASY -> {
                val newInterval = max(1, (card.intervalDays * card.easeFactor * 1.3f).roundToInt())
                card.copy(
                    intervalDays = newInterval,
                    easeFactor = card.easeFactor + 0.15f,
                    repetitions = card.repetitions + 1,
                    nextReviewAt = now + newInterval * dayMs
                )
            }
        }
    }

    private fun showAllDone() {
        binding.layoutDone.visibility = View.VISIBLE
        binding.tvFront.visibility = View.GONE
        binding.tvBack.visibility = View.GONE
        binding.tvDefinition.visibility = View.GONE
        binding.tvExamples.visibility = View.GONE
        binding.btnShowAnswer.visibility = View.GONE
        binding.layoutRating.visibility = View.GONE
        binding.tvProgress.text = ""
        binding.tvDoneSummary.text = if (totalReviewed > 0) {
            getString(R.string.review_done_summary, totalReviewed)
        } else {
            getString(R.string.review_no_cards_due)
        }
        binding.btnDoneClose.setOnClickListener { finish() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private enum class Rating { AGAIN, HARD, GOOD, EASY }
}
