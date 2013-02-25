package soyutil_test

import (
	. "closure/template/soyutil"
	"testing"
)

func assertBoolEquals(t *testing.T, expected, actual bool, errormsg string) {
	if expected != actual {
		if len(errormsg) > 0 {
			t.Errorf("%s\nExpected: %s but was: %d %v", errormsg, expected, actual, expected == actual)
		} else {
			t.Errorf("Expected: %s but was: %d %v", expected, actual, expected == actual)
		}
	}
}

func assertIntEquals(t *testing.T, expected, actual int, errormsg string) {
	if expected != actual {
		if len(errormsg) > 0 {
			t.Errorf("%s\nExpected: %d but was: %d %v", errormsg, expected, actual, expected == actual)
		} else {
			t.Errorf("Expected: %d but was: %d %v", expected, actual, expected == actual)
		}
	}
}

func assertFloat64Equals(t *testing.T, expected, actual float64, errormsg string) {
	if expected != actual {
		if len(errormsg) > 0 {
			t.Errorf("%s\nExpected: %g but was: %g %v", errormsg, expected, actual, expected == actual)
		} else {
			t.Errorf("Expected: %g but was: %g %v", expected, actual, expected == actual)
		}
	}
}

func assertStringEquals(t *testing.T, expected, actual, errormsg string) {
	if expected != actual {
		if len(errormsg) > 0 {
			t.Errorf("%s\nExpected: \"%s\"\n but was: \"%s\", %d %d %s", errormsg, expected, actual, len(expected), len(actual), expected == actual)
		} else {
			t.Errorf("Expected: \"%s\"\n but was: \"%s\" %d %d %s", expected, actual, len(expected), len(actual), expected == actual)
		}
	}
}

func assertSoyDataEquals(t *testing.T, expected, actual SoyData, errormsg string) {
	if expected != actual {
		if len(errormsg) > 0 {
			t.Errorf("%s\nExpected: %v\n but was: %v, %s", errormsg, expected, actual, expected.Equals(actual))
		} else {
			t.Errorf("Expected: %v\n but was: %v, %s", expected, actual, expected.Equals(actual))
		}
	}
}

func TestStringDataBool(t *testing.T) {
	assertBoolEquals(t, false, NewStringData("").Bool(), "Empty String")
	assertBoolEquals(t, true, NewStringData(" ").Bool(), "Whitespace String")
	assertBoolEquals(t, true, NewStringData("blah blah").Bool(), "String with value")
}

func TestNewSoyMapDataFromArgs(t *testing.T) {
	s := NewSoyMapDataFromArgs("name", "Albert Einstein", "occupation", NewStringData("Patent Clerk"), "birth_year", 1879)
	assertIntEquals(t, 3, s.Len(), "Size of SoyMapData incorrect")
	assertStringEquals(t, "Albert Einstein", s["name"].String(), "Invalid value for name in SoyMapData")
	assertStringEquals(t, "Patent Clerk", s["occupation"].String(), "Invalid value for occupation in SoyMapData")
	assertStringEquals(t, "1879", s["birth_year"].String(), "Invalid value for birth_year in SoyMapData")
	assertIntEquals(t, 1879, s["birth_year"].IntegerValue(), "Invalid value for birth_year in SoyMapData")
}

func TestToSoyData(t *testing.T) {
	m := map[string]SoyData{"name": NewStringData("John Doe"), "count": NewIntegerData(15)}
	m2, _ := ToSoyData(m)
	sm, _ := m2.(SoyMapData)
	assertIntEquals(t, 2, sm.Len(), "Invalid length for map")
	assertSoyDataEquals(t, NewStringData("John Doe"), sm["name"], "Invalid value in map")
	assertSoyDataEquals(t, NewIntegerData(15), sm["count"], "Invalid value in map")

	l := []interface{}{"name", NewStringData("John Doe"), "count", NewIntegerData(15)}
	l2, _ := ToSoyData(l)
	t.Logf("list: %#v\nas soy: %#v\n", l, l2)
	sl, _ := l2.(SoyListData)
	assertIntEquals(t, 4, sl.Len(), "Invalid length for list")
	e := sl.Front()
	t.Logf("Elem 0: %#v\n", e.Value)
	assertSoyDataEquals(t, NewStringData("name"), e.Value.(SoyData), "Invalid value in list")
	e = e.Next()
	t.Logf("Elem 1: %#v\n", e.Value)
	assertSoyDataEquals(t, NewStringData("John Doe"), e.Value.(SoyData), "Invalid value in list")
	e = e.Next()
	t.Logf("Elem 2: %#v\n", e.Value)
	assertSoyDataEquals(t, NewStringData("count"), e.Value.(SoyData), "Invalid value in list")
	e = e.Next()
	t.Logf("Elem 3: %#v\n", e.Value)
	assertSoyDataEquals(t, NewIntegerData(15), e.Value.(SoyData), "Invalid value in list")

	assertSoyDataEquals(t, NewStringData("name"), sl.At(0), "Invalid value in list")
	assertSoyDataEquals(t, NewStringData("John Doe"), sl.At(1), "Invalid value in list")
	assertSoyDataEquals(t, NewStringData("count"), sl.At(2), "Invalid value in list")
	assertSoyDataEquals(t, NewIntegerData(15), sl.At(3), "Invalid value in list")

}
