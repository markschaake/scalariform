package scalariform.formatter

import scalariform.lexer.Tokens._
import scalariform.lexer._
import scalariform.parser._
import scalariform.utils._

/** A class storing formatting instructions for a scalariform pass. */
case class FormatResult(
    predecessorFormatting: Map[Token, IntertokenFormatInstruction],
    inferredNewlineFormatting: Map[Token, IntertokenFormatInstruction],
    xmlRewrites: Map[Token, String]
) {

  def before(token: Token, formatInstruction: IntertokenFormatInstruction): FormatResult = {
    require(!token.isNewline, " cannot do 'before' formatting for NEWLINE* tokens: " + token + ", " + formatInstruction)
    copy(predecessorFormatting = predecessorFormatting + (token -> formatInstruction))
  }

  def formatNewline(token: Token, formatInstruction: IntertokenFormatInstruction) = {
    require(token.isNewline, " cannot do 'newline' formatting for non-NEWLINE tokens: " + token + ", " + formatInstruction)
    copy(inferredNewlineFormatting = inferredNewlineFormatting + (token -> formatInstruction))
  }

  def formatNewlineOrOrdinary(token: Token, formatInstruction: IntertokenFormatInstruction) =
    if (token.isNewline) formatNewline(token, formatInstruction)
    else before(token, formatInstruction)

  def tokenWillHaveNewline(token: Token): Boolean = {
    val hasNewlineInstruction = predecessorFormatting.get(token) map {
      PartialFunction.cond(_) { case newlineInstruction: EnsureNewlineAndIndent ⇒ true }
    }
    hasNewlineInstruction.getOrElse(false)
  }

  /** @return a copy of this FormatResult with a new instruction replacing the given XML token with
    * the given string
    */
  def replaceXml(token: Token, replacement: String): FormatResult = {
    require(token.tokenType.isXml)
    copy(xmlRewrites = xmlRewrites + (token -> replacement))
  }

  def mergeWith(other: FormatResult): FormatResult =
    FormatResult(
      this.predecessorFormatting ++ other.predecessorFormatting,
      this.inferredNewlineFormatting ++ other.inferredNewlineFormatting,
      this.xmlRewrites ++ other.xmlRewrites
    )

  def ++(other: FormatResult): FormatResult = mergeWith(other)
}

/** No-op result. */
object NoFormatResult extends FormatResult(Map(), Map(), Map())

abstract sealed class IntertokenFormatInstruction

/** Packs the comments together as compactly as possible, eliminating
  * as much non-comment whitespace as possible while ensuring that the
  * lexer produces the same tokens.
  */
case object Compact extends IntertokenFormatInstruction

/** Like "Compact", but ensures there is either some comment or a single space. */
case object CompactEnsuringGap extends IntertokenFormatInstruction

/** Like "Compact", but will keep at least a single space if there was whitespace before */
case object CompactPreservingGap extends IntertokenFormatInstruction

/** Ensures that the interttoken region ends with NEWLINE INDENT. */
case class EnsureNewlineAndIndent(indentLevel: Int, relativeTo: Option[Token] = None)
  extends IntertokenFormatInstruction

/** Places the token at spaces number of spaces after the indent level, padding with spaces if
  * necessary
  */
case class PlaceAtColumn(indentLevel: Int, spaces: Int, relativeTo: Option[Token] = None)
  extends IntertokenFormatInstruction
