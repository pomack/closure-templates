package soyutil

// This is a direct port of the Java class
// com.google.template.soy.shared.restricted.EscapingConventions

import (
	"bytes"
	"fmt"
	"io"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

const (
	/**
	 * A string, used as the result of a filter when the filter pattern does not match the input, that
	 * is not a substring of any keyword or well-known identifier in HTML, JS, or CSS and that is a
	 * valid identifier part in all those languages, and which cannot terminate a string, comment, or
	 * other bracketed section.
	 * <p>
	 * This string is also longer than necessary so that developers can use grep when it starts
	 * showing up in their output.
	 * <p>
	 * If grep directed you here, then one of your Soy templates is using a filter directive that
	 * is receiving a potentially unsafe input.  Run your app in debug mode and you should get the
	 * name of the directive and the input deemed unsafe.
	 */
	INNOCUOUS_OUTPUT = "zSoyz"
)

var (
	HEX_DIGITS = []byte{
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
	}

	CSS_WORD = regexp.MustCompile(
		// See http://www.owasp.org/index.php/XSS_(Cross_Site_Scripting)_Prevention_Cheat_Sheet
		// #RULE_.234_-_CSS_Escape_Before_Inserting_Untrusted_Data_into_HTML_Style_Property_Values
		// for an explanation of why expression and moz-binding are bad.
		"^(?!-*(?:(expression|(?:moz-)?binding))(?:" +
			// A latin class name or ID, CSS identifier, hex color or unicode range.
			"[.#]?-?(?:[_a-zA-Z0-9-]+)(?:-[_a-zA-Z0-9-]+)*-?|" +
			// A quantity
			"-?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[a-zA-Z]{1,2}|%)?|" +
			// The special value !important.
			"!important|" +
			// Nothing.
			"" +
			")\\z/i",
	)

	/**
	 * Loose matcher for HTML tags, DOCTYPEs, and HTML comments.
	 * This will reliably find HTML tags (though not CDATA tags and not XML tags whose name or
	 * namespace starts with a non-latin character), and will do a good job with DOCTYPES (though
	 * will have trouble with complex doctypes that define their own entities) and does a decent job
	 * with simple HTML comments.
	 * <p>
	 * This should be food enough since HTML sanitizers do not typically output comments, or CDATA,
	 * or RCDATA content.
	 */
	HTML_TAG_CONTENT = regexp.MustCompile(
		// Matches a left angle bracket followed by either
		// (1) a "!" which indicates a doctype or comment, or
		// (2) an optional solidus (/, indicating an end tag) and an HTML tag name.
		// followed by any number of quoted strings (found in tags and doctypes) or other content
		// terminated by a right angle bracket.
		"<(?:!|/?[a-zA-Z])(?:[^>'\"]|\"[^\"]*\"|'[^']*')*>",
	)

	_BYTE_ARRAY_PERCENT = []byte{'%'}

	_FILTER_NORMALIZE_URI_RE = regexp.MustCompile(
		"^(?:(?:https?|mailto):|[^&:\\/?#]*(?:[\\/?#]|\\z))/i",
	)

	_FILTER_HTML_ATTRIBUTE_RE = regexp.MustCompile(
		"^" +
			// Disallow special attribute names
			"(?!style|on|action|archive|background|cite|classid|codebase|data|dsync|href" +
			"|longdesc|src|usemap)" +
			"(?:" +
			// Must match letters
			"[a-z0-9_$:-]*" +
			// Match until the end.
			")\\z/i",
	)

	_FILTER_HTML_ELEMENT_NAME_RE = regexp.MustCompile(
		"^" +
			// Disallow special element names.
			"(?!script|style|title|textarea|xmp|no)" +
			"[a-z0-9_$:-]*\\z/i",
	)
)

var (
	/** Implements the {@code |escapeHtml} directive. */
	EscapeHtmlInstance            = newEscapeHtmlEscaper()
	NormalizeHtmlInstance         = newNormalizeHtmlEscaper()
	EscapeHtmlNospaceInstance     = newEscapeHtmlNospaceEscaper()
	NormalizeHtmlNospaceInstance  = newNormalizeHtmlNospaceEscaper()
	EscapeJsStringInstance        = newEscapeJsStringEscaper()
	EscapeJsRegexInstance         = newEscapeJsRegexEscaper()
	EscapeCssStringInstance       = newEscapeCssStringEscaper()
	FilterCssValueInstance        = newFilterCssValueEscaper()
	NormalizeUriInstance          = newNormalizeUriEscaper()
	FilterNormalizeUriInstance    = newFilterNormalizeUriEscaper()
	EscapeUriInstance             = newEscapeUriEscaper()
	FilterHtmlAttributeInstance   = newFilterHtmlAttributeEscaper()
	FilterHtmlElementNameInstance = newFilterHtmlElementNameEscaper()
)

type stringer interface {
	String() string
}

/**
 * A mapping from a plain text character to the escaped text in the target language.
 * We define a character below as a code unit, not a codepoint as none of the target languages
 * treat supplementary codepoints as special.
 */
type Escape interface {
	/**
	 * A character in the input language.
	 */
	PlainText() rune
	/**
	 * A string in the output language that corresponds to {@link #getPlainText}
	 * in the input language.
	 */
	Escaped() string
	CompareTo(b Escape) int
}

type escape struct {
	plainText rune
	escaped   string
}

func NewEscape(plainText rune, escaped string) Escape {
	return &escape{
		plainText: plainText,
		escaped:   escaped,
	}
}

func (p *escape) PlainText() rune {
	return p.plainText
}

func (p *escape) Escaped() string {
	return p.escaped
}

func (p *escape) CompareTo(b Escape) int {
	if p == b {
		return 0
	}
	if b == nil {
		return 1
	}
	if p.plainText == b.PlainText() {
		return 0
	}
	if p.plainText > b.PlainText() {
		return 1
	}
	return -1
}

type appendableEscapedWriter struct {
	clsx *crossLanguageStringXform
	w    io.Writer
}

func newAppendableEscapedWriter(clsx *crossLanguageStringXform, w io.Writer) io.Writer {
	return &appendableEscapedWriter{
		clsx: clsx,
		w:    w,
	}
}

func (p *appendableEscapedWriter) WriteString(s string) (int, error) {
	_, err := p.clsx.maybeEscapeOnto(s, p.w)
	return len(s), err
}

func (p *appendableEscapedWriter) Write(b []byte) (int, error) {
	_, err := p.clsx.maybeEscapeOnto(string(b), p.w)
	return len(b), err
}

func (p *appendableEscapedWriter) Close() error {
	if cls, ok := p.w.(io.WriteCloser); ok {
		return cls.Close()
	}
	return nil
}

type defineEscapers interface {
	DefineEscapes() []Escape
}

type CrossLanguageStringXform interface {
	DirectiveName() string
	ValueFilter() *regexp.Regexp
	NonAsciiPrefix() string
	Escapes() []Escape
	Escape(s string) (string, error)
	EscapedWriter(w io.Writer) io.Writer
	DefineEscapes() []Escape
}

/**
 * A transformation on strings that preserves some correctness or safety properties.
 * Subclasses come in three varieties:
 * <dl>
 *   <dt>Escaper</dt>
 *     <dd>A mapping from strings in an input language to strings in an output language that
 *     preserves the content.
 *     E.g. the plain text string {@code 1 < 2} can be escaped to the equivalent HTML string
 *     {@code 1 &lt; 2}.</dd>
 *   <dt>Normalizer</dt>
 *     <dd>A mapping from strings in a language to equivalent strings in the same language but
 *     that can be more easily embedded in another language.
 *     E.g. the URI {@code http://www.google.com/search?q=O'Reilly} is equivalent to
 *     {@code http://www.google.com/search?q=O%27Reilly} but the latter can be safely
 *     embedded in a single quoted HTML attribute.</dd>
 *   <dt>Filter</dt>
 *     <dd>A mapping from strings in a language to the same value or to an innocuous value.
 *     E.g. the string {@code h1} might pass an html identifier filter but the string
 *     {@code ><script>alert('evil')</script>} should not and could be replaced by an innocuous
 *     value like {@code zzz}.</dd>
 * </dl>
 */
type crossLanguageStringXform struct {
	directiveName string
	valueFilter   *regexp.Regexp
	jsNames       []string
	escapes       []Escape

	/**
	 * A dense mapping mirroring escapes.
	 * I.e. for each element of {@link #escapes} {@code e} such that {@code e.plainText < 0x80},
	 * {@code escapesByCodeUnit[e.plainText] == e.escaped}.
	 */
	escapesByCodeUnit []string
	/** Keys in a sparse mapping for the non ASCII {@link #escapes}. */
	nonAsciiCodeUnits []int
	/** Values in a sparse mapping corresponding to {@link #nonAsciiCodeUnits}. */
	nonAsciiEscapes []string
	/** @see #getNonAsciiPrefix */
	nonAsciiPrefix string
}

/**
 * @param valueFilter {@code null} if the directive accepts all strings as inputs.  Otherwise
 *     a regular expression that accepts only strings that can be escaped by this directive.
 * @param jsNames The names of existing JavaScript builtins or Google Closure functions, if
 *     any exist, that implements this escaping convention.
 * @param nonAsciiPrefix An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to
 *     escape non-ASCII code units not in the sparse mapping.
 *     If null, then non-ASCII code units outside the sparse map can appear unescaped.
 */
func initCrossLanguageStringXform(clsx *crossLanguageStringXform, simpleName string, valueFilter *regexp.Regexp, jsNames []string, nonAsciiPrefix string, escapeDefiner defineEscapers) {
	// EscapeHtml -> |escapeHtml
	clsx.directiveName = "|" + strings.ToLower(simpleName[0:1]) + simpleName[1:]
	clsx.valueFilter = valueFilter
	if jsNames == nil {
		jsNames = make([]string, 0)
	}
	escapes := escapeDefiner.DefineEscapes()
	if escapes == nil {
		escapes = make([]Escape, 0)
	}
	newJsNames := make([]string, len(jsNames))
	copy(newJsNames, jsNames)
	clsx.jsNames = newJsNames
	clsx.escapes = escapes
	numEscapes := len(escapes)
	numAsciiEscapes := numEscapes
	// Now create the maps used by the escape methods.  The below depends on defineEscapes()
	// returning sorted escapes.  EscapeListBuilder.build() sorts its escapes.
	for numAsciiEscapes > 0 && escapes[numAsciiEscapes-1].PlainText() >= 0x80 {
		numAsciiEscapes--
	}
	// Create the dense ASCII map.
	if numAsciiEscapes != 0 {
		escapesByCodeUnit := make([]string, escapes[numAsciiEscapes-1].PlainText()+1)
		for _, escape := range escapes[0:numAsciiEscapes] {
			escapesByCodeUnit[int(escape.PlainText())] = escape.Escaped()
		}
		clsx.escapesByCodeUnit = escapesByCodeUnit
	} else {
		clsx.escapesByCodeUnit = make([]string, 0)
	}
	// Create the sparse non-ASCII map.
	if numEscapes != numAsciiEscapes {
		numNonAsciiEscapes := numEscapes - numAsciiEscapes
		nonAsciiCodeUnits := make([]int, numNonAsciiEscapes)
		nonAsciiEscapes := make([]string, numNonAsciiEscapes)
		for i := 0; i < numNonAsciiEscapes; i++ {
			esc := escapes[numAsciiEscapes+i]
			nonAsciiCodeUnits[i] = int(esc.PlainText())
			nonAsciiEscapes[i] = esc.Escaped()
		}
		clsx.nonAsciiCodeUnits = nonAsciiCodeUnits
		clsx.nonAsciiEscapes = nonAsciiEscapes
	} else {
		clsx.nonAsciiCodeUnits = make([]int, 0)
		clsx.nonAsciiEscapes = make([]string, 0)
	}

	// The fallback mode if neither the ASCII nor non-ASCII escaping maps contain a mapping.
	clsx.nonAsciiPrefix = nonAsciiPrefix
}

/**
 * The name of the directive associated with this escaping function.
 * @return E.g. {@code |escapeHtml}
 */
func (p *crossLanguageStringXform) DirectiveName() string {
	return p.directiveName
}

/**
 * An escaping prefix in {@code "%", "\\u", "\\"} which specifies how to escape non-ASCII code
 * units not in the sparse mapping.
 * If null, then non-ASCII code units outside the sparse map can appear unescaped.
 */
func (p *crossLanguageStringXform) NonAsciiPrefix() string {
	return p.nonAsciiPrefix
}

/**
 * Null if the escaper accepts all strings as inputs, or otherwise a regular expression
 * that accepts only strings that can be escaped by this escaper.
 */
func (p *crossLanguageStringXform) ValueFilter() *regexp.Regexp {
	return p.valueFilter
}

/**
 * The names of existing JavaScript builtins or Google Closure functions that implement
 * the escaping convention.
 * @return {@code null} if there is no such function.
 */
func (p *crossLanguageStringXform) JsFunctionNames() []string {
	return p.jsNames
}

/**
 * The escapes need to translate the input language to the output language.
 */
func (p *crossLanguageStringXform) Escapes() []Escape {
	return p.escapes
}

// Methods that satisfy the Escaper interface.
func (p *crossLanguageStringXform) Escape(s string) (string, error) {
	// We pass null so that we don't unnecessarily allocate (and zero) or copy char arrays.
	buf, err := p.maybeEscapeOnto(s, nil)
	if buf != nil {
		if sbuf, ok := buf.(stringer); ok {
			return sbuf.String(), err
		}
	}
	return s, err
}

func (p *crossLanguageStringXform) EscapedWriter(w io.Writer) io.Writer {
	return newAppendableEscapedWriter(p, w)
}

/**
 * Escapes the given char sequence onto the given buffer iff it contains characters that need to
 * be escaped.
 * @return null if no output buffer was passed in, and s contains no characters that need
 *    escaping.  Otherwise out, or a StringBuilder if one needed to be allocated.
 */
func (p *crossLanguageStringXform) maybeEscapeOnto(s string, out io.Writer) (io.Writer, error) {
	return p.maybeEscapeOntoSubstring(s, out, 0, len(s))
}

/**
 * Escapes the given range of the given sequence onto the given buffer iff it contains
 * characters that need to be escaped.
 * @return null if no output buffer was passed in, and s contains no characters that need
 *    escaping.  Otherwise out, or a StringBuilder if one needed to be allocated.
 */
func (p *crossLanguageStringXform) maybeEscapeOntoSubstring(s string, out io.Writer, start, end int) (io.Writer, error) {
	var err error
	pos := start
	escapesByCodeUnitLen := len(p.escapesByCodeUnit)
	for j, c := range s[start:end] {
		i := start + j
		if int(c) < escapesByCodeUnitLen { // Use the dense map.
			esc := p.escapesByCodeUnit[c]
			if esc != "" {
				if out == nil {
					// Create a new buffer if we need to escape a character in s.
					// We add 32 to the size to leave a decent amount of space for escape characters.
					out = bytes.NewBuffer(make([]byte, 0))
				}
				_, err = io.WriteString(out, s[pos:i])
				if err != nil {
					return out, err
				}
				_, err = io.WriteString(out, esc)
				if err != nil {
					return out, err
				}
				pos = i + 1
			}
		} else if c >= 0x80 { // Use the sparse map.
			index := sort.SearchInts(p.nonAsciiCodeUnits, int(c))
			if index >= 0 {
				if out == nil {
					out = bytes.NewBuffer(make([]byte, 0))
				}
				_, err = io.WriteString(out, s[pos:i])
				if err != nil {
					return out, err
				}
				_, err = io.WriteString(out, p.nonAsciiEscapes[index])
				if err != nil {
					return out, err
				}
				pos = i + 1
			} else if p.nonAsciiPrefix != "" { // Fallback to the prefix based escaping.
				if out == nil {
					out = bytes.NewBuffer(make([]byte, 0))
				}
				_, err = io.WriteString(out, s[pos:i])
				if err != nil {
					return out, err
				}
				err = p.escapeUsingPrefix(c, out)
				if err != nil {
					return out, err
				}
				pos = i + 1
			}
		}
	}
	if out != nil {
		_, err = io.WriteString(out, s[pos:end])
	}
	return out, err
}

/**
 * Appends a hex representation of the given code unit to out preceded by the
 * {@link #nonAsciiPrefix}.
 *
 * @param c A code unit greater than or equal to 0x80.
 * @param out written to.
 */
func (p *crossLanguageStringXform) escapeUsingPrefix(c rune, out io.Writer) (err error) {
	if "%" == p.nonAsciiPrefix { // Use a UTF-8
		if c < 0x800 {
			_, err = out.Write(_BYTE_ARRAY_PERCENT)
			if err != nil {
				return
			}
			err = appendHexPair(((c>>6)&0x1f)|0xc0, out)
			if err != nil {
				return
			}
		} else {
			_, err = out.Write(_BYTE_ARRAY_PERCENT)
			if err != nil {
				return
			}
			err = appendHexPair(((c>>12)&0xf)|0xe0, out)
			if err != nil {
				return
			}
			_, err = out.Write(_BYTE_ARRAY_PERCENT)
			if err != nil {
				return
			}
			err = appendHexPair(((c>>6)&0x3f)|0x80, out)
			if err != nil {
				return
			}
		}
		_, err = out.Write(_BYTE_ARRAY_PERCENT)
		if err != nil {
			return
		}
		err = appendHexPair((c&0x3f)|0x80, out)
		if err != nil {
			return
		}
	} else {
		_, err = io.WriteString(out, p.nonAsciiPrefix)
		if err != nil {
			return
		}
		err = appendHexPair((c>>8)&0xff, out)
		if err != nil {
			return
		}
		err = appendHexPair(c&0xff, out)
		if err != nil {
			return
		}
		if "\\" == p.nonAsciiPrefix {
			// Append with a space so that CSS escape doesn't pull in any hex digits following.
			_, err = out.Write([]byte{' '})
		}
	}
	return
}

/**
 * Given {@code 0x20} appends {@code "20"} to the given output buffer.
 */
func appendHexPair(b rune, out io.Writer) error {
	_, err := out.Write([]byte{HEX_DIGITS[b>>4]})
	if err != nil {
		return err
	}
	_, err = out.Write([]byte{HEX_DIGITS[b&0xf]})
	return err
}

type numericEscaperFor interface {
	/**
	 * Computes the numeric escape in the output language for the given codepoint in the input
	 * language.
	 * E.g. in C, the numeric escape for space is {@code \x20}.
	 */
	NumericEscapeFor(plainText rune) string
}

type escapeListBuilder struct {
	escapes []Escape
	escaper numericEscaperFor
}

func initEscapeListBuilder(elb *escapeListBuilder, escaper numericEscaperFor) {
	elb.escapes = make([]Escape, 0)
	elb.escaper = escaper
}

/**
 * Adds an escape for the given code unit in the input language to the given escaped text.
 */
func (p *escapeListBuilder) EscapeWithValue(plainText rune, escaped string) *escapeListBuilder {
	p.escapes = append(p.escapes, NewEscape(plainText, escaped))
	return p
}

/**
 * Adds an escape for the given code unit in the input language using the numeric escaping
 * scheme.
 */
func (p *escapeListBuilder) Escape(plainText rune) *escapeListBuilder {
	return p.EscapeWithValue(plainText, p.escaper.NumericEscapeFor(plainText))
}

/**
 * Adds a numeric escape for each code unit in the input string.
 */
func (p *escapeListBuilder) EscapeAll(plainTextCodeUnits string) *escapeListBuilder {
	for _, r := range plainTextCodeUnits {
		p.Escape(r)
	}
	return p
}

/**
 * Adds numeric escapes for each code unit in the given range not in the exclusion set.
 */
func (p *escapeListBuilder) EscapeAllInRangeExcept(startInclusive, endExclusive rune, notEscaped ...rune) *escapeListBuilder {
	notEscaped2 := make([]rune, len(notEscaped))
	copy(notEscaped2, notEscaped)
	//sort.Ints(notEscaped2)
	k := 0
	numNotEscaped2 := len(notEscaped2)
	for i := startInclusive; i < endExclusive; i++ {
		for k < numNotEscaped2 && notEscaped2[k] < i {
			k++
		}
		if k < numNotEscaped2 && notEscaped2[k] == i {
			continue
		}
		p.Escape(i)
	}
	return p
}

func (p *escapeListBuilder) Len() int {
	return len(p.escapes)
}

func (p *escapeListBuilder) Less(i, j int) bool {
	return p.escapes[i].CompareTo(p.escapes[j]) < 0
}

func (p *escapeListBuilder) Swap(i, j int) {
	p.escapes[i], p.escapes[j] = p.escapes[j], p.escapes[i]
}

func (p *escapeListBuilder) Build() []Escape {
	sort.Sort(p)
	escapes := make([]Escape, len(p.escapes))
	copy(escapes, p.escapes)
	return escapes
}

/**
 * Escapes using HTML/XML numeric entities : {@code 'A' -> "&#65;"}.
 */
type htmlEscapeListBuilder struct {
	escapeListBuilder
}

func newHtmlEscapeListBuilder() *htmlEscapeListBuilder {
	builder := new(htmlEscapeListBuilder)
	initEscapeListBuilder(&builder.escapeListBuilder, builder)
	return builder
}

func (p *htmlEscapeListBuilder) NumericEscapeFor(plainText rune) string {
	return "&#" + strconv.Itoa(int(plainText)) + ";"
}

// Implementations of particular escapers.
// These names follow the convention defined in Escaper's constructor above where
//    class EscapeFoo
// is the concrete definition for
//    |escapeFoo
// Each also provides a singleton INSTANCE member.

/**
 * Implements the {@code |escapeHtml} directive.
 */
type escapeHtmlEscaper struct {
	crossLanguageStringXform
}

func newEscapeHtmlEscaper() *escapeHtmlEscaper {
	p := new(escapeHtmlEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeHtml",
		nil,
		// TODO: enable goog.string.htmlEscape after it escapes single quotes.
		[]string{ /*"goog.string.htmlEscape"*/},
		"",
		p,
	)
	return p
}

func (p *escapeHtmlEscaper) DefineEscapes() []Escape {
	escapes := newHtmlEscapeListBuilder().
		EscapeWithValue('&', "&amp;").
		EscapeWithValue('<', "&lt;").
		EscapeWithValue('>', "&gt;").
		EscapeWithValue('"', "&quot;").
		EscapeAll(
		// It escapes ' to &#39; instead of &apos; which is not standardized in XML.
		"\000'",
	).Build()
	return escapes
}

/**
 * A directive that encodes any HTML special characters that can appear in RCDATA unescaped but
 * that can be escaped without changing semantics.
 * From <a href="http://www.w3.org/TR/html5/tokenization.html#rcdata-state">HTML 5</a>:
 * <blockquote>
 *   <h4>8.2.4.3 RCDATA state</h4>
 *   Consume the next input character:
 *   <ul>
 *     <li>U+0026 AMPERSAND (&)
 *       <br>Switch to the character reference in RCDATA state.
 *     <li>U+003C LESS-THAN SIGN (<)
 *       <br>Switch to the RCDATA less-than sign state.
 *     <li>EOF
 *       <br>Emit an end-of-file token.
 *     <li>Anything else
 *       <br>Emit the current input character as a character token.
 *   </ul>
 * </blockquote>
 * So all HTML special characters can be escaped, except ampersand, since escaping that would
 * lead to overescaping of legitimate HTML entities.
 */
type normalizeHtmlEscaper struct {
	crossLanguageStringXform
}

func newNormalizeHtmlEscaper() *normalizeHtmlEscaper {
	p := new(normalizeHtmlEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"NormalizeHtml",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *normalizeHtmlEscaper) DefineEscapes() []Escape {
	escapes := EscapeHtmlInstance.DefineEscapes()
	arr := make([]Escape, len(escapes))
	i := 0
	for _, esc := range escapes {
		if esc.PlainText() != '&' {
			arr[i] = esc
			i++
		}
	}
	return arr[0:i]
}

/**
 * Implements the {@code |escapeHtmlNoSpace} directive which allows arbitrary content
 * to be included in the value of an unquoted HTML attribute.
 */
type escapeHtmlNospaceEscaper struct {
	crossLanguageStringXform
}

func newEscapeHtmlNospaceEscaper() *escapeHtmlNospaceEscaper {
	p := new(escapeHtmlNospaceEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeHtmlNospace",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *escapeHtmlNospaceEscaper) DefineEscapes() []Escape {
	escapes := newHtmlEscapeListBuilder().EscapeWithValue(
		'&', "&amp;").EscapeWithValue(
		'<', "&lt;").EscapeWithValue(
		'>', "&gt;").EscapeWithValue(
		'"', "&quot;").EscapeAll(
		// The below list of characters are all those that need to be encode to prevent unquoted
		// value splitting.
		//
		// From the XML spec,
		//   [3]   S   ::=   (#x20 | #x9 | #xD | #xA)+
		// From section 2.4.1 of the HTML5 draft,
		//   The space characters, for the purposes of this specification, are
		//   U+0020 SPACE, U+0009 CHARACTER TABULATION (tab), U+000A LINE FEED (LF),
		//   U+000C FORM FEED (FF), and U+000D CARRIAGE RETURN (CR).
		//   The White_Space characters are those that have the Unicode property
		//   "White_Space" in the Unicode PropList.txt data file.
		// From XML processing notes:
		//   [XML1.1] also normalizes NEL (U+0085) and U+2028 LINE SEPARATOR, but
		//   U+2029 PARAGRAPH SEPARATOR is not treated that way.
		// Those newline characters are described at
		// http://unicode.org/reports/tr13/tr13-9.html
		//
		// Empirically, we need to quote
		//   U+0009 - U+000d, U+0020, double quote, single quote, '>', and back quote.
		// based on running
		//   <body>
		//   <div id=d></div>
		//   <script>
		//   var d = document.getElementById('d');
		//
		//   for (var i = 0x0; i <= 0xffff; ++i) {
		//     var unsafe = false;
		//
		//     var ch = String.fromCharCode(i);
		//
		//     d.innerHTML = '<input title=foo' + ch + 'checked>';
		//     var inp = d.getElementsByTagName('INPUT')[0];
		//     if (inp && (inp.getAttribute('title') === 'foo' || inp.checked)) {
		//       unsafe = true;
		//     } else {  // Try it as a quoting character.
		//       d.innerHTML = '<input title=' + ch + 'foo' + ch + 'checked>';
		//       inp = d.getElementsByTagName('INPUT')[0];
		//       unsafe = !!(inp && (inp.getAttribute('title') === 'foo' || inp.checked));
		//     }
		//     if (unsafe) {
		//       var fourhex = i.toString(16);
		//       fourhex = "0000".substring(fourhex.length) + fourhex;
		//       document.write('\\u' + fourhex + '<br>');
		//     }
		//   }
		//   </script>
		// in a variety of browsers.
		//
		// We supplement that set with the quotes and equal sign which have special
		// meanings in attributes, and with the XML normalized spaces.
		"\u0000\u0009\n\u000B\u000C\r '-/=\u0060\u0085\u00a0\u2028\u2029").Build()
	return escapes
}

/**
 * A directive that encodes any HTML special characters and unquoted attribute terminators that
 * can appear in RCDATA unescaped but that can be escaped without changing semantics.
 */
type normalizeHtmlNospaceEscaper struct {
	crossLanguageStringXform
}

func newNormalizeHtmlNospaceEscaper() *normalizeHtmlNospaceEscaper {
	p := new(normalizeHtmlNospaceEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"NormalizeHtmlNospace",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *normalizeHtmlNospaceEscaper) DefineEscapes() []Escape {
	escapes := EscapeHtmlNospaceInstance.DefineEscapes()
	arr := make([]Escape, len(escapes))
	i := 0
	for _, esc := range escapes {
		if esc.PlainText() != '&' {
			arr[i] = esc
			i++
		}
	}
	return arr[0:i]
}

/**
 * Escapes using hex escapes since octal are non-standard.  'A' -> "\\x41"
 */
type jsEscapeListBuilder struct {
	escapeListBuilder
}

func newJsEscapeListBuilder() *jsEscapeListBuilder {
	builder := new(jsEscapeListBuilder)
	initEscapeListBuilder(&builder.escapeListBuilder, builder)
	return builder
}

func (p *jsEscapeListBuilder) NumericEscapeFor(plainText rune) (s string) {
	if plainText < 0x100 {
		s = fmt.Sprintf("\\x%02x", plainText)
	} else {
		s = fmt.Sprintf("\\u%04x", plainText)
	}
	return
}

/**
 * Implements the {@code |escapeJsString} directive which allows arbitrary content
 * to be included inside a quoted JavaScript string.
 */
type escapeJsStringEscaper struct {
	crossLanguageStringXform
}

func newEscapeJsStringEscaper() *escapeJsStringEscaper {
	p := new(escapeJsStringEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeJsString",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *escapeJsStringEscaper) DefineEscapes() []Escape {
	escapes := newJsEscapeListBuilder().Escape(
		// Some control characters.
		'\u0000').Escape(
		// \\b means word-break inside RegExps
		'\b').EscapeWithValue(
		'\t', "\\t").EscapeWithValue(
		'\n', "\\n").Escape(
		// \\v not consistently supported on IE
		'\u000b').EscapeWithValue(
		'\f', "\\f").EscapeWithValue(
		'\r', "\\r").EscapeWithValue(
		'\\', "\\\\").Escape(
		// Quoting characters.  / is also instrumental in </script>.
		'"').Escape(
		'\'').EscapeWithValue(
		'/', "\\/").EscapeAll(
		// JavaScript newlines
		"\u2028\u2029").Escape(
		// A JavaScript newline according to at least one draft spec.
		'\u0085').EscapeAll(
		// HTML special characters.  Note, that this provides added protection against problems
		// with </script> <![CDATA[, ]]>, <!--, -->, etc.
		"<>&=").Build()
	return escapes
}

/**
* Implements the {@code |escapeJsRegex} directive which allows arbitrary content
* to be included inside a JavaScript regular expression.
 */
type escapeJsRegexEscaper struct {
	crossLanguageStringXform
}

func newEscapeJsRegexEscaper() *escapeJsRegexEscaper {
	p := new(escapeJsRegexEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeJsString",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *escapeJsRegexEscaper) DefineEscapes() []Escape {
	escapes := newJsEscapeListBuilder().Escape(
		// Some control characters.
		'\u0000').Escape(
		// \\b means word-break inside RegExps
		'\b').EscapeWithValue(
		'\t', "\\t").EscapeWithValue(
		'\n', "\\n").Escape(
		// \\v not consistently supported on IE
		'\u000b').EscapeWithValue(
		'\f', "\\f").EscapeWithValue(
		'\r', "\\r").EscapeWithValue(
		// Escape prefx
		'\\', "\\\\").EscapeAll(
		// JavaScript newlines
		"\u2028\u2029").Escape(
		// A JavaScript newline according to at least one draft spec.
		'\u0085').Escape(
		// Quoting characters.  / is also instrumental in </script>.
		'"').Escape(
		'\'').EscapeWithValue(
		'/', "\\/").EscapeAll(
		// HTML special characters.  Note, that this provides added protection against problems
		// with </script> <![CDATA[, ]]>, <!--, -->, etc.
		"<>&=").EscapeAll(
		// Special in regular expressions.  / is also special, but is escaped above.
		"$()*+-.:?[]^{|},").Build()
	return escapes
}

/**
 * Escapes using CSS hex escapes with a space at the end in case a hex digit is the next
 * character : {@code 'A' => "\41 "}
 */
type cssEscapeListBuilder struct {
	escapeListBuilder
}

func newCssEscapeListBuilder() *cssEscapeListBuilder {
	builder := new(cssEscapeListBuilder)
	initEscapeListBuilder(&builder.escapeListBuilder, builder)
	return builder
}

func (p *cssEscapeListBuilder) NumericEscapeFor(plainText rune) (s string) {
	return "\\" + strconv.FormatInt(int64(plainText), 16)
}

/**
 * Implements the {@code |escapeCssString} directive which allows arbitrary content to be
 * included in a CSS quoted string or identifier.
 */
type escapeCssStringEscaper struct {
	crossLanguageStringXform
}

func newEscapeCssStringEscaper() *escapeCssStringEscaper {
	p := new(escapeCssStringEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeCssString",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *escapeCssStringEscaper) DefineEscapes() []Escape {
	escapes := newCssEscapeListBuilder().EscapeAll(
		// Escape newlines and similar control characters, quotes, HTML special characters, and
		// CSS punctuation that might cause CSS error recovery code to restart parsing in the
		// middle of a string.
		// Semicolons, close curlies, and @ (which precedes top-level directives like @media),
		// and slashes in comment delimiters are all good places for CSS error recovery code to
		// skip to.
		// Quotes and parentheses are used as string and URL delimiters.
		// Angle brackets and slashes appear in escaping text spans allowed in HTML5 <style>
		// that might affect the parsing of subsequent content, and < appears in
		// </style> which could prematurely close a style element.
		// Newlines are disallowed in strings, so not escaping them can trigger CSS error
		// recovery.
		"\u0000\b\t\n\u000b\f\r\u0085\u00a0\u2028\u2029\"'\\<>&{};:()@/=*").Build()
	return escapes
}

/**
 * Implements the {@code |filterCssValue} directive which filters out strings that are not valid
 * CSS property names, keyword values, quantities, hex colors, or ID or class literals.
 */
type filterCssValueEscaper struct {
	crossLanguageStringXform
}

func newFilterCssValueEscaper() *filterCssValueEscaper {
	p := new(filterCssValueEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"FilterCssValue",
		CSS_WORD,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *filterCssValueEscaper) DefineEscapes() []Escape {
	return []Escape{}
}

/**
 * Escapes using URI percent encoding : {@code 'A' => "%41"}
 */
type uriEscapeListBuilder struct {
	escapeListBuilder
}

func newUriEscapeListBuilder() *uriEscapeListBuilder {
	builder := new(uriEscapeListBuilder)
	initEscapeListBuilder(&builder.escapeListBuilder, builder)
	return builder
}

func (p *uriEscapeListBuilder) NumericEscapeFor(plainText rune) (s string) {
	// URI encoding is different from the other escaping schemes.
	// The others are transformations on strings of UTF-16 code units, but URIs are composed of
	// strings of bytes.  We assume UTF-8 as the standard way to convert between bytes and code
	// units below.
	theBytes := []byte(string(plainText))
	numBytes := len(theBytes)
	buf := bytes.NewBuffer([]byte{})
	for i := 0; i < numBytes; i++ {
		// Use uppercase escapes for consistency with CharEscapers.uriEscaper().
		buf.WriteString(fmt.Sprintf("%%%02X", theBytes[i]))
	}
	return buf.String()
}

/**
 * Implements the {@code |normalizeUri} directive which allows arbitrary content to be included
 * in a URI regardless of the string delimiters of the the surrounding language.
 * This normalizes, but does not escape, so it does not affect URI special characters, but
 * instead escapes HTML, CSS, and JS delimiters.
 */
type normalizeUriEscaper struct {
	crossLanguageStringXform
}

func newNormalizeUriEscaper() *normalizeUriEscaper {
	p := new(normalizeUriEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"normalizeUri",
		nil,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *normalizeUriEscaper) DefineEscapes() []Escape {
	escapes := newUriEscapeListBuilder().EscapeAll(
		// Escape all ASCII control characters.
		"\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007").
		EscapeAll("\u0008\u0009\n\u000B\u000C\r\u000E\u000F").
		EscapeAll("\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017").
		EscapeAll("\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F").
		Escape('\u007f').
		EscapeAll(
		// Escape non-special URI characters that might prematurely close an unquoted CSS URI or
		// HTML attribute.
		// Parentheses and single quote are technically sub-delims, but not in HTTP or HTTPS,
		// only appearing in the obsolete mark rule in section D.2. of RFC 3986.
		// It is important to encode parentheses to prevent CSS URIs from being broken as in:
		//      background: {lb} background-image: url( /foo/{print $x}.png ) {rb}
		// It is important to encode both quote characters to prevent broken CSS URIs and HTML
		// attributes as in:
		//      background: {lb} background-image: url('/foo/{print $x}.png') {rb}
		// and
		//      <img src="/foo/{print $x}.png">
		" (){}\"'\\<>").
		EscapeAll(
		// More spaces and newlines.
		"\u0085\u00A0\u2028\u2029").
		EscapeAll(
		// Make sure that full-width versions of reserved characters are escaped.
		// Some user-agents treat full-width characters in URIs entered in the URL bar the same
		// as the ASCII version so that URLs copied and pasted from written Chinese work.
		// Each Latin printable character has a full-width equivalent in the U+FF00 code plane,
		// e.g. the full-width colon is \uFF1A.
		// http://www.cisco.com/en/US/products/products_security_response09186a008083f82e.html
		// says that it is possible to route malicious URLs through intervening layers to the
		// browser by using the full-width equivalents of special characters.
		toFullWidth(":/?#[]@!$&'()*+,;=")).
		Build()
	return escapes
}

/**
 * Like {@link NormalizeUri} but filters out dangerous protocols.
 */
type filterNormalizeUriEscaper struct {
	crossLanguageStringXform
}

func newFilterNormalizeUriEscaper() *filterNormalizeUriEscaper {
	p := new(filterNormalizeUriEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"FilterNormalizeUri",
		_FILTER_NORMALIZE_URI_RE,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *filterNormalizeUriEscaper) DefineEscapes() []Escape {
	return NormalizeUriInstance.DefineEscapes()
}

/**
 * Implements the {@code |escapeUri} directive which allows arbitrary content to be included in a
 * URI regardless of the string delimiters of the the surrounding language.
 */
type escapeUriEscaper struct {
	crossLanguageStringXform
}

func newEscapeUriEscaper() *escapeUriEscaper {
	p := new(escapeUriEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"EscapeUri",
		nil,
		[]string{"goog.string.urlEncode", "encodeURIComponent"},
		"%",
		p,
	)
	return p
}

func (p *escapeUriEscaper) DefineEscapes() []Escape {
	escapes := newUriEscapeListBuilder().
		EscapeAllInRangeExcept(
		0, 0x80,
		// From Appendix A of RFC 3986
		// unreserved := ALPHA / DIGIT / "-" / "." / "_" / "~"
		'-', '.',
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q',
		'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
		'_',
		'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q',
		'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
		'~',
	).
		// All non-ASCII codepoints escaped per the constructor above.
		Build()
	return escapes
}

/**
 * Implements the {@code |filterHtmlAttribute} directive which filters out identifiers that
 * can't appear as part of an HTML tag or attribute name.
 */
type filterHtmlAttributeEscaper struct {
	crossLanguageStringXform
}

func newFilterHtmlAttributeEscaper() *filterHtmlAttributeEscaper {
	p := new(filterHtmlAttributeEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"FilterHtmlAttribute",
		_FILTER_HTML_ATTRIBUTE_RE,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *filterHtmlAttributeEscaper) DefineEscapes() []Escape {
	return []Escape{}
}

/**
 * Implements the {@code |filterHtmlElementName} directive which filters out identifiers that
 * can't appear as part of an HTML tag or attribute name.
 */
type filterHtmlElementNameEscaper struct {
	crossLanguageStringXform
}

func newFilterHtmlElementNameEscaper() *filterHtmlElementNameEscaper {
	p := new(filterHtmlElementNameEscaper)
	initCrossLanguageStringXform(
		&p.crossLanguageStringXform,
		"FilterHtmlElementName",
		_FILTER_HTML_ELEMENT_NAME_RE,
		[]string{},
		"",
		p,
	)
	return p
}

func (p *filterHtmlElementNameEscaper) DefineEscapes() []Escape {
	return []Escape{}
}

/**
 * An accessor for all string transforms defined above.
 */
func AllEscapers() []CrossLanguageStringXform {
	return []CrossLanguageStringXform{
		EscapeHtmlInstance,
		NormalizeHtmlInstance,
		EscapeHtmlNospaceInstance,
		EscapeJsStringInstance,
		EscapeJsRegexInstance,
		EscapeCssStringInstance,
		FilterCssValueInstance,
		EscapeUriInstance,
		NormalizeUriInstance,
		FilterNormalizeUriInstance,
		FilterHtmlAttributeInstance,
		FilterHtmlElementNameInstance,
	}
}

/**
 * Convert an ASCII string to full-width.
 * Full-width characters are in Unicode page U+FFxx and are used to allow ASCII characters to be
 * embedded in written Chinese without breaking alignment -- so a sinograph which occupies two
 * columns can line up properly with a Latin letter or symbol which normally occupies only one
 * column.
 * <p>
 * See <a href="http://en.wikipedia.org/wiki/Duplicate_characters_in_Unicode#CJK_fullwidth_forms">
 * CJK fullwidth forms</a> and <a href="unicode.org/charts/PDF/UFF00.pdf">unicode.org</a>.
 */
func toFullWidth(ascii string) string {
	chars := []rune(ascii)
	for i, ch := range chars {
		if ch < 0x80 {
			chars[i] = ch + 0xff00 - 0x20
		}
	}
	return string(chars)
}
