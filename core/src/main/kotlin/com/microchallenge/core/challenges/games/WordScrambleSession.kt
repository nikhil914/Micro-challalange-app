package com.microchallenge.core.challenges.games

import com.microchallenge.core.challenges.ChallengeSession
import com.microchallenge.core.challenges.MAX_DIFFICULTY
import com.microchallenge.core.challenges.MIN_DIFFICULTY
import com.microchallenge.core.challenges.Scoring
import com.microchallenge.core.engine.Clock
import com.microchallenge.core.model.CHALLENGE_DURATION_MS
import com.microchallenge.core.model.ChallengeResult
import com.microchallenge.core.model.ChallengeType
import kotlin.random.Random

/**
 * Word Scramble (anagram): the letters of a common word are shuffled onto tiles.
 * The player taps tiles in order to spell the word. Any real anagram from the
 * word list counts. A wrong full spelling clears the slots and counts a mistake.
 *
 * Word length scales with difficulty (4 → 6 letters). Each solved word is followed
 * by a new one. A skip gives up the current word and counts as a mistake.
 */
class WordScrambleSession(
    difficulty: Int,
    private val random: Random,
    private val clock: Clock,
) : ChallengeSession {

    enum class Phase { PLAY, SOLVED, WRONG, DONE }

    enum class TapResult { PLACED, SOLVED, WRONG, IGNORED }

    /** One generated round: [letters] are the shuffled tiles of [target]. */
    data class Round(val number: Int, val target: String, val letters: List<Char>)

    override val type: ChallengeType = ChallengeType.WORD_SCRAMBLE
    override val difficulty: Int = difficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    private val startMs: Long = clock.monotonicMs()

    private val wordLength: Int = when {
        this.difficulty <= 4 -> 4
        this.difficulty <= 7 -> 5
        else -> 6
    }

    private val pool: List<String> = WordList.ofLength(wordLength)
    private val usedWords = mutableSetOf<String>()

    var round: Round = newRound(0)
        private set

    /** Tile indices placed into the answer slots, in order. */
    private val _picked = mutableListOf<Int>()
    val picked: List<Int> get() = _picked

    /** Session-relative time the current feedback started, or -1 while playing. */
    private var feedbackAtMs: Long = -1L
    private var feedbackSolved = false

    var wordsSolved: Int = 0
        private set
    var lettersSolved: Int = 0
        private set
    var mistakes: Int = 0
        private set
    var skips: Int = 0
        private set

    private var ended = false

    private fun elapsed(): Long = clock.monotonicMs() - startMs

    private fun newRound(number: Int): Round {
        val fresh = pool.filter { it !in usedWords }.ifEmpty { usedWords.clear(); pool }
        val word = fresh[random.nextInt(fresh.size)]
        usedWords += word
        val chars = word.toList()
        var shuffled = chars.shuffled(random)
        // Never show a scramble that already spells a valid answer.
        var tries = 0
        while (WordList.isAnagramOf(String(shuffled.toCharArray()), word) && tries < 20) {
            shuffled = chars.shuffled(random)
            tries++
        }
        if (WordList.isAnagramOf(String(shuffled.toCharArray()), word)) {
            shuffled = chars.drop(1) + chars.first()
        }
        return Round(number, word, shuffled)
    }

    /** Leaves feedback and deals the next tiles once feedback has shown long enough. */
    private fun advanceIfNeeded() {
        if (feedbackAtMs < 0) return
        val wait = if (feedbackSolved) SOLVED_MS else WRONG_MS
        if (elapsed() - feedbackAtMs < wait) return
        feedbackAtMs = -1L
        _picked.clear()
        if (feedbackSolved) round = newRound(round.number + 1)
    }

    val phase: Phase
        get() {
            if (ended || elapsed() >= CHALLENGE_DURATION_MS) return Phase.DONE
            advanceIfNeeded()
            return when {
                feedbackAtMs < 0 -> Phase.PLAY
                feedbackSolved -> Phase.SOLVED
                else -> Phase.WRONG
            }
        }

    /** 0..1 progress through the current feedback animation. */
    fun feedbackProgress(): Float {
        if (feedbackAtMs < 0) return 0f
        val wait = if (feedbackSolved) SOLVED_MS else WRONG_MS
        return ((elapsed() - feedbackAtMs).toFloat() / wait).coerceIn(0f, 1f)
    }

    /** The word currently spelled in the slots. */
    fun spelled(): String = String(_picked.map { round.letters[it] }.toCharArray())

    override val isComplete: Boolean get() = ended

    /** Places tile [index] into the next free slot. */
    fun tapLetter(index: Int): TapResult {
        if (phase != Phase.PLAY || index !in round.letters.indices || index in _picked) return TapResult.IGNORED
        _picked += index
        if (_picked.size < round.letters.size) return TapResult.PLACED
        feedbackAtMs = elapsed()
        return if (WordList.isAnagramOf(spelled(), round.target)) {
            feedbackSolved = true
            wordsSolved++
            lettersSolved += round.letters.size
            TapResult.SOLVED
        } else {
            feedbackSolved = false
            mistakes++
            TapResult.WRONG
        }
    }

    /** Sends the letter in answer slot [slot] (and every slot after it) back to the tray. */
    fun tapSlot(slot: Int): Boolean {
        if (phase != Phase.PLAY || slot !in _picked.indices) return false
        while (_picked.size > slot) _picked.removeAt(_picked.size - 1)
        return true
    }

    /** Gives up on the current word. Counts as a mistake. */
    fun skip(): Boolean {
        if (phase != Phase.PLAY) return false
        skips++
        mistakes++
        _picked.clear()
        round = newRound(round.number + 1)
        return true
    }

    override fun finish(): ChallengeResult {
        ended = true
        val duration = elapsed().coerceIn(0L, CHALLENGE_DURATION_MS)
        val raw = (lettersSolved * 32.0 + wordsSolved * 70.0 - mistakes * 25.0) * Scoring.difficultyBonus(difficulty)
        val acc = Scoring.accuracy(wordsSolved, mistakes)
        val performance = (0.5 * acc + 0.5 * (wordsSolved / 3.0).coerceAtMost(1.0)).coerceIn(0.0, 1.0)
        val detail = when {
            wordsSolved == 0 && mistakes == 0 -> "No words"
            wordsSolved == 1 -> "1 word"
            else -> "$wordsSolved words"
        }
        return ChallengeResult(
            type = type,
            score = Scoring.clampScore(raw),
            performance = performance,
            difficulty = difficulty,
            correct = wordsSolved,
            mistakes = mistakes,
            durationMs = duration,
            detail = detail,
        )
    }

    companion object {
        /** Celebration time after a solved word, ms. */
        const val SOLVED_MS = 700L

        /** Shake time after a wrong spelling, ms. */
        const val WRONG_MS = 450L
    }
}

/**
 * Small hand-picked list of common, family-friendly English words.
 * Written for this app; no third-party word list.
 */
object WordList {
    private val words: List<String> = """
        BEAR BIRD BOAT CAKE CAMP COIN CORN DUCK FARM FISH FROG GAME GOLD HAND HILL KING
        LAMP LEAF LION MILK MOON NEST PARK PINK RAIN ROAD ROCK ROSE SAND SHIP SNOW SOUP
        STAR SWIM TREE WIND WOLF YARD JUMP KITE MINT DRUM BELL GIFT HOME LAKE BLUE
        APPLE BEACH BREAD BRUSH CHAIR CLOUD CROWN DANCE EAGLE FLAME FRUIT GHOST GRAPE
        HEART HORSE HOUSE LEMON LIGHT MAGIC MOUSE MUSIC OCEAN PAINT PIANO PLANT QUEEN
        RIVER ROBOT SMILE SNAKE SPOON STORM SUGAR TABLE TIGER TRAIN WATER WHALE ZEBRA
        CANDY CLOCK DREAM FIELD GLOBE HONEY JUICE KOALA MANGO NIGHT PEARL TOAST
        ANCHOR BASKET BRIDGE BUTTER CAMERA CANDLE CASTLE CIRCLE COOKIE DRAGON FLOWER
        FOREST GARDEN GUITAR HAMMER ISLAND JACKET JUNGLE KITTEN LADDER MARKET MIRROR
        MONKEY ORANGE PENCIL PEPPER PLANET POCKET PUZZLE RABBIT ROCKET SCHOOL SILVER
        SPIDER SUMMER TOMATO TURTLE WINDOW WINTER YELLOW BANANA CARROT DONKEY PARROT
    """.trim().split(Regex("\\s+"))

    private val byLength: Map<Int, List<String>> = words.groupBy { it.length }
    private val keys: Set<String> = words.mapTo(mutableSetOf()) { key(it) }
    private val wordSet: Set<String> = words.toSet()

    private fun key(w: String): String = String(w.toCharArray().also { it.sort() })

    fun ofLength(n: Int): List<String> = byLength[n] ?: byLength.getValue(5)

    /** True if [candidate] is [target] itself or another listed word with the same letters. */
    fun isAnagramOf(candidate: String, target: String): Boolean =
        candidate == target || (candidate in wordSet && key(candidate) == key(target) && key(candidate) in keys)
}
