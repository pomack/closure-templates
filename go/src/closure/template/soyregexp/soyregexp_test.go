package soyregexp

import "testing"

func TestRegexp(t *testing.T) {
	rs := MustCompile([]*Regexp{
		&Regexp{MustMatch: false, RegexpString: "^(-*(?:(expression|(?:moz-)?binding)))"},
		&Regexp{MustMatch: true, RegexpString: "!important"},
	})
	if rs.MatchString("-moz-binding: none !important;") {
		t.Errorf("Expected soy regexp slice to return false as string starts with -moz-binding, but got true")
	}
	if rs.MatchString("color: red !important;") == false {
		t.Errorf("Expected soy regexp slice to return true, but got false")
	}
	if rs.MatchString("color: red;") {
		t.Errorf("Expected soy regexp slice to return false, but got true")
	}
}
