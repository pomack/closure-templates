package soyutil

import (
	"regexp"
)

const (
	/**
	 * A practical pattern to identify strong LTR character. This pattern is not
	 * theoretically correct according to unicode standard. It is simplified for
	 * performance and small code size.
	 * @type {string}
	 * @private
	 */
	_BIDI_LTR_CHARS = "A-Za-z\u00C0-\u00D6\u00D8-\u00F6\u00F8-\u02B8\u0300-\u0590\u0800-\u1FFF\u2C00-\uFB1C\uFDFE-\uFE6F\uFEFD-\uFFFF"

	/**
	 * A practical pattern to identify strong neutral and weak character. This
	 * pattern is not theoretically correct according to unicode standard. It is
	 * simplified for performance and small code size.
	 * @type {string}
	 * @private
	 */
	_BIDI_NEUTRAL_CHARS = "\u0000-\u0020!-@[-`{-\u00BF\u00D7\u00F7\u02B9-\u02FF\u2000-\u2BFF"

	/**
	 * A practical pattern to identify strong RTL character. This pattern is not
	 * theoretically correct according to unicode standard. It is simplified for
	 * performance and small code size.
	 * @type {string}
	 * @private
	 */
	_BIDI_RTL_CHARS = "\u0591-\u07FF\uFB1D-\uFDFD\uFE70-\uFEFC"

	/**
	 * This constant controls threshold of rtl directionality.
	 * @type {number}
	 * @private
	 */
	_BIDI_RTL_DETECTION_THRESHOLD = 0.40
)

type ContentKind int

const (

	/**
	 * A snippet of HTML that does not start or end inside a tag, comment, entity, or DOCTYPE; and
	 * that does not contain any executable code (JS, {@code <object>}s, etc.) from a different
	 * trust domain.
	 */
	CONTENT_KIND_HTML ContentKind = iota + 1

	/**
	 * A sequence of code units that can appear between quotes (either single or double) in a JS
	 * program without causing a parse error, and without causing any side effects.
	 * <p>
	 * The content should not contain unescaped quotes, newlines, or anything else that would
	 * cause parsing to fail or to cause a JS parser to finish the string it is parsing inside
	 * the content.
	 * <p>
	 * The content must also not end inside an escape sequence ; no partial octal escape sequences
	 * or odd number of '{@code \}'s at the end.
	 */
	CONTENT_KIND_JS_STR_CHARS

	/** A properly encoded portion of a URI. */
	CONTENT_KIND_URI

	/** An attribute name and value, such as {@code dir="ltr"}. */
	CONTENT_KIND_HTML_ATTRIBUTE
)

func (p ContentKind) String() string {
	switch p {
	case CONTENT_KIND_HTML:
		return "HTML"
	case CONTENT_KIND_JS_STR_CHARS:
		return "JS_STR_CHARS"
	case CONTENT_KIND_HTML_ATTRIBUTE:
		return "HTML_ATTRIBUTE"
	}
	return "UNKNOWN_CONTENT_KIND"
}

var (
	/**
	 * Simplified regular expression for am HTML tag (opening or closing) or an HTML
	 * escape - the things we want to skip over in order to ignore their ltr
	 * characters.
	 * @type {RegExp}
	 * @private
	 */
	_BIDI_HTML_SKIP_RE *regexp.Regexp

	/**
	 * Regular expressions to check if a piece of text is of RTL directionality
	 * on first character with strong directionality.
	 * @type {RegExp}
	 * @private
	 */
	_BIDI_RTL_DIR_CHECK_RE *regexp.Regexp

	/**
	 * Regular expressions to check if a piece of text is of neutral directionality.
	 * Url are considered as neutral.
	 * @type {RegExp}
	 * @private
	 */
	_BIDI_NEUTRAL_DIR_CHECK_RE *regexp.Regexp

	/**
	 * Regular expressions to check if the last strongly-directional character in a
	 * piece of text is LTR.
	 * @type {RegExp}
	 * @private
	 */
	_BIDI_LTR_EXIT_DIR_CHECK_RE *regexp.Regexp

	/**
	 * Regular expressions to check if the last strongly-directional character in a
	 * piece of text is RTL.
	 * @type {RegExp}
	 * @private
	 */
	_BIDI_RTL_EXIT_DIR_CHECK_RE *regexp.Regexp

	/**
	 * Regular expression used within $$changeNewlineToBr().
	 * @type {RegExp}
	 * @private
	 */
	_CHANGE_NEWLINE_TO_BR_RE *regexp.Regexp

	_CHANGE_NEWLINE_TO_BR2_RE *regexp.Regexp

	/**
	 * Regular expression used for determining if a string needs to be encoded.
	 * @type {RegExp}
	 * @private
	 */
	_ENCODE_URI_RE *regexp.Regexp

	/**
	 * Character mappings used internally for soy.$$escapeJs
	 * @private
	 * @type {Object}
	 */
	_EscapeCharJs map[string]string
)

func init() {
	_BIDI_HTML_SKIP_RE, _ = regexp.Compile("<[^>]*>|&[^;]+;")
	_BIDI_RTL_DIR_CHECK_RE, _ = regexp.Compile("^[^" + _BIDI_LTR_CHARS + "]*[" + _BIDI_RTL_CHARS + "]")
	_BIDI_NEUTRAL_DIR_CHECK_RE, _ = regexp.Compile("^[" + _BIDI_NEUTRAL_CHARS + "]*$|^http://")
	_BIDI_LTR_EXIT_DIR_CHECK_RE, _ = regexp.Compile("[" + _BIDI_LTR_CHARS + "][^" + _BIDI_RTL_CHARS + "]*$")
	_BIDI_RTL_EXIT_DIR_CHECK_RE, _ = regexp.Compile("[" + _BIDI_RTL_CHARS + "][^" + _BIDI_LTR_CHARS + "]*$")
	_CHANGE_NEWLINE_TO_BR_RE, _ = regexp.Compile("[\r\n]")
	_CHANGE_NEWLINE_TO_BR2_RE, _ = regexp.Compile("(\r\n|\r|\n)")
	_ENCODE_URI_RE, _ = regexp.Compile("^[a-zA-Z0-9\\-_.!~*'()]*$")
	_EscapeCharJs = map[string]string{
		"\b":   "\\b",
		"\f":   "\\f",
		"\n":   "\\n",
		"\r":   "\\r",
		"\t":   "\\t",
		"\x0B": "\\x0B", // '\v' is not supported in JScript
		"\"":   "\\\"",
		"'":    "\\'",
		"\\":   "\\\\",
	}
}
