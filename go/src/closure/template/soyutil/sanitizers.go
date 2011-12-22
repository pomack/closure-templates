package soyutil;

import (
    "html"
    "json"
    "strings"
    "url"
)

/**
 * Escapes HTML special characters in a string. Escapes double quote '"' in
 * addition to '&', '<', and '>' so that a string can be included in an HTML
 * tag attribute value within double quotes.
 *
 * @param {*} str The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
func EscapeHtml(s string) string {
  return html.EscapeString(s)
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


/**
 * Escapes a string so that it can be safely included in a URI.
 *
 * @param {*} str The string to be escaped. Can be other types, but the value
 *     will be coerced to a string.
 * @return {string} An escaped copy of the string.
*/
func EscapeUri(str string) string {
  // Checking if the search matches before calling encodeURIComponent avoids an
  // extra allocation in IE6. This adds about 10us time in FF and a similiar
  // over head in IE6 for lower working set apps, but for large working set
  // apps, it saves about 70us per call.
  if !_ENCODE_URI_RE.MatchString(str) {
    return url.QueryEscape(str)
  }
  return str
}


