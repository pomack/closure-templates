package soyutil;

import (
  "strings"
)


/**
 * Strips str of any HTML mark-up and escapes. Imprecise in several ways, but
 * precision is not very important, since the result is only meant to be used
 * for directionality detection.
 * @param {string} str The string to be stripped.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {string} The stripped string.
 * @private
 */
func BidiStripHtmlIfNecessary(str string, opt_isHtml bool) string {
  if opt_isHtml {
    return _BIDI_HTML_SKIP_RE.ReplaceAllString(str, " ")
  }
  return str
}


/**
 * Estimate the overall directionality of text. If opt_isHtml, makes sure to
 * ignore the LTR nature of the mark-up and escapes in text, making the logic
 * suitable for HTML and HTML-escaped text.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {number} 1 if text is LTR, -1 if it is RTL, and 0 if it is neutral.
 */
func BidiTextDir(text string, opt_isHtml bool) int {
  text = BidiStripHtmlIfNecessary(text, opt_isHtml);
  if len(text) == 0 {
    return 0
  }
  if BidiDetectRtlDirectionality(text) {
    return -1
  }
  return 1
};


/**
 * Returns "dir=ltr" or "dir=rtl", depending on text's estimated
 * directionality, if it is not the same as bidiGlobalDir.
 * Otherwise, returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} "dir=rtl" for RTL text in non-RTL context; "dir=ltr" for LTR
 *     text in non-LTR context; else, the empty string.
 */
func BidiDirAttr(bidiGlobalDir int, text string, opt_isHtml bool) string {
  dir := BidiTextDir(text, opt_isHtml)
  switch {
  case dir == bidiGlobalDir:
    return ""
  case dir < 0:
    return "dir=rtl"
  case dir > 0:
    return "dir=ltr"
  default:
    return ""
  }
  return ""
}

/**
 * Returns a Unicode BiDi mark matching bidiGlobalDir (LRM or RLM) if the
 * directionality or the exit directionality of text are opposite to
 * bidiGlobalDir. Otherwise returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or
 *     the empty string when text's overall and exit directionalities both match
 *     bidiGlobalDir.
 */
func BidiMarkAfter(bidiGlobalDir int, text string, opt_isHtml bool) string {
  dir := BidiTextDir(text, opt_isHtml)
  return BidiMarkAfterKnownDir(bidiGlobalDir, dir, text, opt_isHtml)
}


/**
 * Returns a Unicode BiDi mark matching bidiGlobalDir (LRM or RLM) if the
 * directionality or the exit directionality of text are opposite to
 * bidiGlobalDir. Otherwise returns the empty string.
 * If opt_isHtml, makes sure to ignore the LTR nature of the mark-up and escapes
 * in text, making the logic suitable for HTML and HTML-escaped text.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {number} dir text's directionality: 1 if ltr, -1 if rtl, 0 if unknown.
 * @param {string} text The text whose directionality is to be estimated.
 * @param {boolean=} opt_isHtml Whether text is HTML/HTML-escaped.
 *     Default: false.
 * @return {string} A Unicode bidi mark matching bidiGlobalDir, or
 *     the empty string when text's overall and exit directionalities both match
 *     bidiGlobalDir.
 */
func BidiMarkAfterKnownDir(bidiGlobalDir int, dir int, text string, opt_isHtml bool) string {
  switch {
  case bidiGlobalDir > 0 && (dir < 0 || BidiIsRtlExitText(text, opt_isHtml)):
    return "\u200E" // LRM
  case bidiGlobalDir < 0 && (dir > 0 || BidiIsLtrExitText(text, opt_isHtml)):
    return "\u200F" // RLM
  default:
    return ""
  }
  return ""
}


/**
 * Returns str wrapped in a <span dir=ltr|rtl> according to its directionality -
 * but only if that is neither neutral nor the same as the global context.
 * Otherwise, returns str unchanged.
 * Always treats str as HTML/HTML-escaped, i.e. ignores mark-up and escapes when
 * estimating str's directionality.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {*} str The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @param {bool} isHtml whether the text is HTML
 * @return {string} The wrapped string.
 */
func BidiSpanWrap(bidiGlobalDir int, str string, isHtml bool) string {
  var output string
  textDir := BidiTextDir(str, isHtml)
  reset := BidiMarkAfterKnownDir(bidiGlobalDir, textDir, str, isHtml)
  switch {
  case textDir > 0 && bidiGlobalDir <= 0:
    output = "<span dir=\"ltr\">" + str + "</span>"
  case textDir < 0 && bidiGlobalDir >= 0:
    output = "<span dir=\"rtl\">" + str + "</span>"
  default:
    output = str
  }
  return output + reset
}


/**
 * Returns str wrapped in Unicode BiDi formatting characters according to its
 * directionality, i.e. either LRE or RLE at the beginning and PDF at the end -
 * but only if str's directionality is neither neutral nor the same as the
 * global context. Otherwise, returns str unchanged.
 * Always treats str as HTML/HTML-escaped, i.e. ignores mark-up and escapes when
 * estimating str's directionality.
 * @param {number} bidiGlobalDir The global directionality context: 1 if ltr, -1
 *     if rtl, 0 if unknown.
 * @param {*} str The string to be wrapped. Can be other types, but the value
 *     will be coerced to a string.
 * @param {bool} isHtml whether the text is HTML
 * @return {string} The wrapped string.
 */
func BidiUnicodeWrap(bidiGlobalDir int, str string, isHtml bool) string {
  var output string
  textDir := BidiTextDir(str, isHtml)
  reset := BidiMarkAfterKnownDir(bidiGlobalDir, textDir, str, isHtml)
  switch {
  case textDir > 0 && bidiGlobalDir <= 0:
    output = "\u202A" + str + "\u202C"
  case textDir < 0 && bidiGlobalDir >= 0:
    output = "\u202B" + str + "\u202C"
  default:
    output = str
  }
  return output + reset
}


/**
 * Check the directionality of the a piece of text based on the first character
 * with strong directionality.
 * @param {string} str string being checked.
 * @return {boolean} return true if rtl directionality is being detected.
 * @private
 */
func BidiIsRtlText(str string) bool {
  return _BIDI_RTL_DIR_CHECK_RE.MatchString(str)
}


/**
 * Check the directionality of the a piece of text based on the first character
 * with strong directionality.
 * @param {string} str string being checked.
 * @return {boolean} true if all characters have neutral directionality.
 * @private
 */
func BidiIsNeutralText(str string) bool {
  return _BIDI_NEUTRAL_DIR_CHECK_RE.MatchString(str)
}


/**
 * Returns the RTL ratio based on word count.
 * @param {string} str the string that need to be checked.
 * @return {number} the ratio of RTL words among all words with directionality.
 * @private
 */
func BidiRtlWordRatio(str string) float64 {
  rtlCount := 0
  totalCount := 0
  tokens := strings.Split(str, " ", -1)
  for _, token := range tokens {
    if BidiIsRtlText(token) {
      rtlCount++
      totalCount++
    } else if BidiIsNeutralText(token) {
      totalCount++
    }
  }
  if totalCount == 0 {
    return 0
  }
  return float64(rtlCount) / float64(totalCount)
}


/**
 * Check the directionality of a piece of text, return true if the piece of
 * text should be laid out in RTL direction.
 * @param {string} str The piece of text that need to be detected.
 * @return {boolean} true if this piece of text should be laid out in RTL.
 * @private
 */
func BidiDetectRtlDirectionality(str string) bool {
  return BidiRtlWordRatio(str) > _BIDI_RTL_DETECTION_THRESHOLD
}


/**
 * Check if the exit directionality a piece of text is LTR, i.e. if the last
 * strongly-directional character in the string is LTR.
 * @param {string} str string being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether LTR exit directionality was detected.
 * @private
 */
func BidiIsLtrExitText(str string, opt_isHtml bool) bool {
  testString := BidiStripHtmlIfNecessary(str, opt_isHtml)
  return _BIDI_LTR_EXIT_DIR_CHECK_RE.MatchString(testString)
}


/**
 * Check if the exit directionality a piece of text is RTL, i.e. if the last
 * strongly-directional character in the string is RTL.
 * @param {string} str string being checked.
 * @param {boolean=} opt_isHtml Whether str is HTML / HTML-escaped.
 *     Default: false.
 * @return {boolean} Whether RTL exit directionality was detected.
 * @private
 */
func BidiIsRtlExitText(str string, opt_isHtml bool) bool {
  testString := BidiStripHtmlIfNecessary(str, opt_isHtml)
  return _BIDI_RTL_EXIT_DIR_CHECK_RE.MatchString(testString)
}



