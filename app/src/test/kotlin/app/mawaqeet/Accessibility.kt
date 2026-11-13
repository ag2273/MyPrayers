package app.mawaqeet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals

/** What TalkBack would call a node: its text or its content description. */
internal fun SemanticsNode.label(): String =
    (config.getOrNull(SemanticsProperties.ContentDescription) ?: config.getOrNull(SemanticsProperties.Text)?.map { it.text })
        ?.joinToString() ?: ""

private fun ComposeTestRule.allNodes(): List<SemanticsNode> {
    fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
    return walk(onRoot(useUnmergedTree = true).fetchSemanticsNode())
}

/**
 * Problems a person with large text or a motor impairment would meet on the screen as shown: text cut off or
 * ellipsized (by its own width or by a parent's fixed size), and tap targets under 48×48dp. Off-screen nodes (scrolled
 * away) are skipped, so render with a tall window to check a whole screen. On a scrolled page, [parentClips] = false
 * skips the parent check, since a node half under the scroll edge would count as cut off; text that overflows its own
 * box still does.
 */
internal fun ComposeTestRule.accessibilityProblems(density: Float, parentClips: Boolean = true): List<String> {
    val problems = mutableListOf<String>()
    val root = onRoot(useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    for (node in allNodes()) {
        val bounds = node.boundsInRoot
        if (bounds.isEmpty) continue
        val name = node.label()
        if (node.config.contains(SemanticsActions.OnClick)) {
            val touch = node.touchBoundsInRoot
            // Half under the scroll edge, a target is smaller on screen than it is: measure the whole of it.
            val width = if (parentClips) touch.width else maxOf(touch.width, node.size.width.toFloat())
            val height = if (parentClips) touch.height else maxOf(touch.height, node.size.height.toFloat())
            if (width / density < 47.5f || height / density < 47.5f) {
                problems += "«$name» tap target is ${width / density}×${height / density}dp"
            }
        }
        val layout = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { get ->
            mutableListOf<TextLayoutResult>().also { get(it) }.firstOrNull()
        } ?: continue
        val lines = 0 until layout.lineCount
        val tooWide = lines.any { layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1 }
        val ellipsized = lines.any { layout.isLineEllipsized(it) }
        // Clipped by a parent: less of it shows than it needs, though it isn't at the window's edge.
        val cutByParent = parentClips && bounds.height < node.size.height - 1 && bounds.top > root.top && bounds.bottom < root.bottom
        if (layout.didOverflowHeight || tooWide || ellipsized || cutByParent) problems += "«$name» is cut off"
    }
    return problems
}

internal fun ComposeTestRule.assertAccessible(screen: String, density: Float, parentClips: Boolean = true) =
    assertEquals("$screen:", emptyList<String>(), accessibilityProblems(density, parentClips))
