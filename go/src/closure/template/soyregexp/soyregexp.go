package soyregexp

import "regexp"

type Matcher interface {
	MatchString(string) bool
}

type Regexp struct {
	MustMatch    bool
	RegexpString string
	re           *regexp.Regexp
}

func (self *Regexp) MustCompile() {
	self.re = regexp.MustCompile(self.RegexpString)
}

func (self *Regexp) Matches(s string) bool {
	if self.re == nil {
		self.re = regexp.MustCompile(self.RegexpString)
	}
	return self.re.MatchString(s) == self.MustMatch
}

type RegexpSlice struct {
	regExps []*Regexp
}

func (self *RegexpSlice) MatchString(s string) bool {
	for _, soyRegexp := range self.regExps {
		if !soyRegexp.Matches(s) {
			return false
		}
	}
	return true
}

func MustCompile(regExps []*Regexp) *RegexpSlice {
	for _, soyRegexp := range regExps {
		soyRegexp.MustCompile()
	}
	return &RegexpSlice{regExps}
}
