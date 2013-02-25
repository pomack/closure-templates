package soyutil_test

import (
	. "closure/template/soyutil"
	"testing"
)

func TestGetData(t *testing.T) {
	s := NewSoyMapDataFromArgs("name", "Albert Einstein", "occupation", NewStringData("Patent Clerk"), "birth_year", 1879)
	assertIntEquals(t, 3, s.Len(), "Size of SoyMapData incorrect")
	assertStringEquals(t, "Albert Einstein", GetData(s, "name").String(), "Invalid value for name in SoyMapData")
	assertStringEquals(t, "Patent Clerk", GetData(s, "occupation").String(), "Invalid value for occupation in SoyMapData")
	assertStringEquals(t, "1879", GetData(s, "birth_year").String(), "Invalid value for birth_year in SoyMapData")
	assertIntEquals(t, 1879, GetData(s, "birth_year").IntegerValue(), "Invalid value for birth_year in SoyMapData")

	m := NewSoyMapDataFromArgs("names", NewSoyListDataFromArgs("Albert Einstein", "Lawrence of Arabia", "Beetlejuice"))
	l, ok := GetData(m, "names").(SoyListData)
	if !ok {
		t.Errorf("GetData(m, \"names\") is of type %t: ", GetData(m, "names"))
	}
	assertIntEquals(t, 3, l.Len(), "GetData(m, \"names\").Len()")
	assertStringEquals(t, "Albert Einstein", l.At(0).StringValue(), "GetData(m, \"names\").At(0)")
	assertStringEquals(t, "Lawrence of Arabia", l.At(1).StringValue(), "GetData(m, \"names\").At(1)")
	assertStringEquals(t, "Beetlejuice", l.At(2).StringValue(), "GetData(m, \"names\").At(2)")
}

func TestRound2(t *testing.T) {
	assertFloat64Equals(t, 3.142, Round2(NewFloat64Data(3.14159), NewIntegerData(3)).Float64Value(), "")
	assertFloat64Equals(t, 3.14, Round2(NewFloat64Data(3.14159), NewIntegerData(2)).Float64Value(), "")
	assertFloat64Equals(t, 3.1, Round2(NewFloat64Data(3.14159), NewIntegerData(1)).Float64Value(), "")
	assertFloat64Equals(t, 3.0, Round2(NewFloat64Data(3.14159), NewIntegerData(0)).Float64Value(), "")
}
