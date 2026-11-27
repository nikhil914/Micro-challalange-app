package com.microchallenge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.microchallenge.app.ui.design.GameCard
import com.microchallenge.app.ui.design.Medal
import com.microchallenge.app.ui.design.MedalBadge
import com.microchallenge.app.ui.design.Palette
import com.microchallenge.app.ui.design.SkillChip
import com.microchallenge.app.ui.design.SkillStyle
import com.microchallenge.app.ui.design.staggerIn
import com.microchallenge.app.ui.design.style
import com.microchallenge.core.model.ChallengeType
import com.microchallenge.core.model.Skill

private val AllStyle = SkillStyle("All", Palette.Primary, Color(0xFFEC4899))

/** Games tab: every game as a card, filterable by skill. */
@Composable
fun LibraryScreen(
    bests: Map<ChallengeType, Int>,
    plays: Map<ChallengeType, Int>,
    filter: Skill?,
    onFilter: (Skill?) -> Unit,
    onGame: (ChallengeType) -> Unit,
) {
    val games = ChallengeType.entries.filter { filter == null || it.skill == filter }
    val golds = ChallengeType.entries.count { Medal.forScore(bests[it] ?: 0) == Medal.GOLD }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Games", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text("${ChallengeType.entries.size} quick games, 10 seconds each",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
            MedalBadge(Medal.GOLD, size = 22.dp)
            Text("  $golds / ${ChallengeType.entries.size}", style = MaterialTheme.typography.titleMedium)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            item { SkillChip(AllStyle, selected = filter == null, onClick = { onFilter(null) }) }
            items(Skill.entries) { s -> SkillChip(s.style(), selected = filter == s, onClick = { onFilter(s) }) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(games, key = { _, t -> t.name }) { i, t ->
                GameCard(t, bests[t] ?: 0, plays[t] ?: 0, onClick = { onGame(t) }, modifier = Modifier.staggerIn(i))
            }
        }
    }
}
