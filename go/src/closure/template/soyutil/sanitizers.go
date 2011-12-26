package soyutil;

import (
  "bytes"
  "io"
  "json"
  "strconv"
  "strings"
  "url"
)

/**
 * Converts the input to HTML by entity escaping.
 */
func EscapeHtml(s string) string {
  value, _ := EscapeHtmlInstance.Escape(s)
  return value
}


/**
 * Converts the input to HTML by entity escaping.
 */
func EscapeHtmlSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_HTML {
    return v.String()
  }
  return EscapeHtml(s.String())
}

/**
 * Converts the input to HTML suitable for use inside {@code <textarea>} by entity escaping.
 */
func EscapeHtmlRcdata(s string) string {
  value, _ := EscapeHtmlInstance.Escape(s)
  return value
}

/**
 * Converts the input to HTML suitable for use inside {@code <textarea>} by entity escaping.
 */
func EscapeHtmlRcdataSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_HTML {
    return NormalizeHtml(v.String())
  }
  return EscapeHtmlRcdata(s.String())
}


/**
 * Normalizes HTML to HTML making sure quotes and other specials are entity encoded.
 */
func NormalizeHtml(s string) string {
  value, _ := NormalizeHtmlInstance.Escape(s)
  return value
}

/**
 * Normalizes HTML to HTML making sure quotes and other specials are entity encoded.
 */
func NormalizeHtmlSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return NormalizeHtml(s.String())
}


/**
 * Normalizes HTML to HTML making sure quotes, spaces and other specials are entity encoded
 * so that the result can be safely embedded in a valueless attribute.
 */
func NormalizeHtmlNospace(s string) string {
  value, _ := NormalizeHtmlNospaceInstance.Escape(s)
  return value
}

/**
 * Normalizes HTML to HTML making sure quotes, spaces and other specials are entity encoded
 * so that the result can be safely embedded in a valueless attribute.
 */
func NormalizeHtmlNospaceSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return NormalizeHtmlNospace(s.String())
}


/**
 * Converts the input to HTML by entity escaping, stripping tags in sanitized content so the
 * result can safely be embedded in an HTML attribute value.
 */
func EscapeHtmlAttribute(s string) string {
  value, _ := EscapeHtmlInstance.Escape(s)
  return value
}

/**
 * Converts the input to HTML by entity escaping, stripping tags in sanitized content so the
 * result can safely be embedded in an HTML attribute value.
 */
func EscapeHtmlAttributeSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_HTML {
    // |escapeHtmlAttribute should only be used on attribute values that cannot have tags.
    return StripHtmlTags(v.String(), true);
  }
  return EscapeHtmlAttribute(s.String())
}

/**
 * Converts plain text to HTML by entity escaping, stripping tags in sanitized content so the
 * result can safely be embedded in an unquoted HTML attribute value.
 */
func EscapeHtmlAttributeNospace(s string) string {
  value, _ := EscapeHtmlNospaceInstance.Escape(s)
  return value
}

/**
 * Converts plain text to HTML by entity escaping, stripping tags in sanitized content so the
 * result can safely be embedded in an unquoted HTML attribute value.
 */
func EscapeHtmlAttributeNospaceSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_HTML {
    // |escapeHtmlAttributeNospace should only be used on attribute values that cannot have tags.
    return StripHtmlTags(v.String(), false);
  }
  return EscapeHtmlAttributeNospace(s.String())
}

/**
 * Converts the input to the body of a JavaScript string by using {@code \n} style escapes.
 */
func EscapeJsString(s string) string {
  value, _ := EscapeJsStringInstance.Escape(s)
  return value
}

/**
 * Converts the input to the body of a JavaScript string by using {@code \n} style escapes.
 */
func EscapeJsStringSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_JS_STR_CHARS {
    return v.String();
  }
  return EscapeJsString(s.String())
}

/**
 * Converts the input to a JavaScript expression.  The resulting expression can be a boolean,
 * number, string literal, or {@code null}.
 */
func EscapeJsValue(s string) string {
  return "'" + EscapeJsString(s) + "'"
}

/**
 * Converts the input to a JavaScript expression.  The resulting expression can be a boolean,
 * number, string literal, or {@code null}.
 */
func EscapeJsValueSoyData(s SoyData) string {
  // We surround values with spaces so that they can't be interpolated into identifiers
  // by accident.  We could use parentheses but those might be interpreted as a function call.
  if s == nil {
    return " null "
  }
  if _, ok := s.(NilData); ok {
    return " null "
  } else if v, ok := s.(IntegerData); ok {
    return " " + strconv.Itoa(v.IntegerValue()) + " "
  } else if v, ok := s.(Float64Data); ok {
    return " " + strconv.Ftoa64(v.Float64Value(), 'g', -1) + " "
  } else if v, ok := s.(BooleanData); ok {
    if v.BooleanValue() {
      return " true "
    }
    return " false "
  }
  return EscapeJsValue(s.String())
}

/**
 * Converts plain text to the body of a JavaScript regular expression literal.
 */
func EscapeJsRegex(s string) string {
  value, _ := EscapeJsRegexInstance.Escape(s)
  return value
}

/**
 * Converts plain text to the body of a JavaScript regular expression literal.
 */
func EscapeJsRegexSoyData(s SoyData) string {
  if s == nil {
    return " null "
  }
  return EscapeJsRegex(s.String())
}

/**
 * Converts the input to the body of a CSS string literal.
 */
func EscapeCssString(s string) string {
  value, _ := EscapeCssStringInstance.Escape(s)
  return value
}

/**
 * Converts the input to the body of a CSS string literal.
 */
func EscapeCssStringSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return EscapeCssString(s.String())
}

/**
 * Makes sure that the input is a valid CSS identifier part, CLASS or ID part, quantity, or
 * CSS keyword part.
 */
func FilterCssValue(s string) string {
  if FilterCssValueInstance.ValueFilter().MatchString(s) {
    return s
  }
  return INNOCUOUS_OUTPUT
}

/**
 * Makes sure that the input is a valid CSS identifier part, CLASS or ID part, quantity, or
 * CSS keyword part.
 */
func FilterCssValueSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if _, ok := s.(NilData); ok {
    return ""
  }
  return FilterCssValue(s.String())
}


/**
 * Escapes a string so that it can be safely included in a URI.
 *
 * @param {*} str The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
func EscapeUri(s string) string {
  // Checking if the search matches before calling encodeURIComponent avoids an
  // extra allocation in IE6. This adds about 10us time in FF and a similiar
  // over head in IE6 for lower working set apps, but for large working set
  // apps, it saves about 70us per call.
  if !_ENCODE_URI_RE.MatchString(s) {
    return url.QueryEscape(s)
  }
  return s
}

/**
 * Converts the input to a piece of a URI by percent encoding assuming a UTF-8 encoding.
 */
func EscapeUriSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  if _, ok := s.(NilData); ok {
    return ""
  } else if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_URI {
    return FilterNormalizeUriSoyData(v)
  }
  return EscapeUri(s.String())
}


/**
 * Converts a piece of URI content to a piece of URI content that can be safely embedded
 * in an HTML attribute by percent encoding.
 */
func NormalizeUri(s string) string {
  value, _ := NormalizeUriInstance.Escape(s)
  return value
}


/**
 * Converts a piece of URI content to a piece of URI content that can be safely embedded
 * in an HTML attribute by percent encoding.
 */
func NormalizeUriSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return NormalizeUri(s.String())
}

/**
 * Makes sure that the given input doesn't specify a dangerous protocol and also
 * {@link #normalizeUri normalizes} it.
 */
func FilterNormalizeUri(s string) string {
  if FilterNormalizeUriInstance.ValueFilter().MatchString(s) {
    return s
  }
  return "#" + INNOCUOUS_OUTPUT
}

/**
 * Makes sure that the given input doesn't specify a dangerous protocol and also
 * {@link #normalizeUri normalizes} it.
 */
func FilterNormalizeUriSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return FilterNormalizeUri(s.String())
}

/**
 * Checks that the input is a valid HTML attribute name with normal keyword or textual content.
 */
func FilterHtmlAttribute(s string) string {
  if FilterHtmlAttributeInstance.ValueFilter().MatchString(s) {
    return s
  }
  return INNOCUOUS_OUTPUT
}

/**
 * Checks that the input is a valid HTML attribute name with normal keyword or textual content
 * or known safe attribute content.
 */
func FilterHtmlAttributeSoyData(s SoyData) string {
  if v, ok := s.(*SanitizedContent); ok && v.contentKind == CONTENT_KIND_HTML_ATTRIBUTE {
    content := s.String()
    eqIndex := strings.Index(content, "=")
    if eqIndex != -1 {
      contentLen := len(content)
      ch := content[contentLen-1]
      if ch != '"' && ch != '\'' {
        // Quote any attribute values so that a contextually autoescaped whole attribute
        // does not end up having a following value associated with it.
        // The contextual autoescaper, since it propagates context left to right, is unable to
        // distinguish
        //    <div {$x}>
        // from
        //    <div {$x}={$y}>.
        // If {$x} is "dir=ltr", and y is "foo" make sure the parser does not see the attribute
        // "dir=ltr=foo".
        return content[0:eqIndex] + "=\"" + content[eqIndex + 1:] + "\""
      }
    }
  }
  return FilterHtmlAttribute(s.String())
}

/**
 * Checks that the input is part of the name of an innocuous element.
 */
func FilterHtmlElementName(s string) string {
  if FilterHtmlElementNameInstance.ValueFilter().MatchString(s) {
    return s
  }
  return INNOCUOUS_OUTPUT
}

/**
 * Checks that the input is part of the name of an innocuous element.
 */
func FilterHtmlElementNameSoyData(s SoyData) string {
  if s == nil {
    return ""
  }
  return FilterHtmlElementName(s.String())
}

func StripHtmlTags(value string, inQuotedAttribute bool) string {
  var normalizer CrossLanguageStringXform
  if inQuotedAttribute {
    normalizer = NormalizeHtmlInstance
  } else {
    normalizer = NormalizeHtmlNospaceInstance
  }
  if !HTML_TAG_CONTENT.MatchString(value) {
    // Normalize so that the output can be embedded in an HTML attribute.
    v, _ := normalizer.Escape(value)
    return v
  }
  buf := bytes.NewBuffer([]byte{})
  normalizedOut := normalizer.EscapedWriter(buf)
  pos := 0
  match := HTML_TAG_CONTENT.FindStringIndex(value)
  for match != nil {
    io.WriteString(normalizedOut, value[pos:match[0]])
    pos = match[1]
    match = HTML_TAG_CONTENT.FindStringIndex(value[pos:])
  }
  if pos < len(value) {
    io.WriteString(normalizedOut, value[pos:])
  }
  return buf.String()
}

/**
 * Escapes characters in the string to make it a valid content for a JS string literal.
 *
 * @param {*} s The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
func EscapeJs(s string) string {
  output, _ := json.Marshal(s)
  return string(output)
}


/**
 * Takes a character and returns the escaped string for that character. For
 * example escapeChar(String.fromCharCode(15)) -> "\\x0E".
 * @param {string} c The character to escape.
 * @return {string} An escaped string representing {@code c}.
 */
func EscapeChar(c string) string {
  if v, ok := _EscapeCharJs[c]; ok {
    return v
  }
  var rv string
  var cc int
  for _, cc = range rv {
    switch {
    case cc > 31 && cc < 127:
      rv = c
    case cc < 16:
      // tab is 9 but handled above
      rv = "\\x0" + strings.ToUpper(string(cc))
    case cc < 256:
      rv = "\\x" + strings.ToUpper(string(cc))
    case cc < 4096:
      rv = "\\u0" + strings.ToUpper(string(cc))
    case cc >= 4096:
      rv = "\\u" + strings.ToUpper(string(cc))
    default:
      rv = c
    }
    break
  }
  _EscapeCharJs[c] = rv, true
  return rv
}


