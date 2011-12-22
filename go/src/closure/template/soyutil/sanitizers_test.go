package soyutil_test;

import (
  . "closure/template/soyutil"
  "testing"
)


func TestEscapeHtml(t *testing.T) {
  unescapedHtml := []string{"", "eat & be merry", "1 < 2", "1 < 2 < 3 > 0", "gutenberg"}
  escapedHtml := []string{"", "eat &amp; be merry", "1 &lt; 2", "1 &lt; 2 &lt; 3 &gt; 0", "gutenberg"}
  for i := 0; i < len(unescapedHtml); i++ {
    s := EscapeHtml(unescapedHtml[i])
    if s != escapedHtml[i] {
      t.Error("EscapeHtml(\"", unescapedHtml[i], "\") -> \"", s, "\" expected: \"", escapedHtml[i], "\"")
    }
  }
}

