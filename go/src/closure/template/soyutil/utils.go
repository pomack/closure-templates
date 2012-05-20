package soyutil;

import (
  "bytes"
  "math"
  "math/rand"
  "strconv"
  "strings"
)

type Lener interface {
  Len() int
}


func Conditional(cond bool, iftrue SoyData, iffalse SoyData) SoyData {
  if cond {
    return iftrue
  }
  return iffalse
}

func InsertWordBreaks(value string, maxCharsBetweenWordBreaks int) string {
  result := bytes.NewBuffer(make([]byte, 0, (len(value) + (len(value) / maxCharsBetweenWordBreaks) + 2)))

  // These variables keep track of important state while looping through the string below.
  isInTag := false  // whether we're inside an HTML tag
  isMaybeInEntity := false  // whether we might be inside an HTML entity
  numCharsWithoutBreak := 0  // number of characters since the last word break
  
  for _, codePoint := range value {
    // If hit maxCharsBetweenWordBreaks, and next char is not a space, then add <wbr>.
    if numCharsWithoutBreak >= maxCharsBetweenWordBreaks && codePoint != ' ' {
      result.WriteString("<wbr>")
      numCharsWithoutBreak = 0
    }
    if isInTag {
      // If inside an HTML tag and we see '>', it's the end of the tag.
      if codePoint == '>' {
        isInTag = false
      }
    } else if (isMaybeInEntity) {
      switch codePoint {
        // If maybe inside an entity and we see ';', it's the end of the entity. The entity
        // that just ended counts as one char, so increment numCharsWithoutBreak.
        case ';':
          isMaybeInEntity = false
          numCharsWithoutBreak++
          break
          // If maybe inside an entity and we see '<', we weren't actually in an entity. But
          // now we're inside and HTML tag.
        case '<':
          isMaybeInEntity = false
          isInTag = true
          break
          // If maybe inside an entity and we see ' ', we weren't actually in an entity. Just
          // correct the state and reset the numCharsWithoutBreak since we just saw a space.
        case ' ':
          isMaybeInEntity = false
          numCharsWithoutBreak = 0
          break
      }
    } else {  // !isInTag && !isInEntity
      switch codePoint {
        // When not within a tag or an entity and we see '<', we're now inside an HTML tag.
        case '<':
          isInTag = true
          break
          // When not within a tag or an entity and we see '&', we might be inside an entity.
        case '&':
          isMaybeInEntity = true
          break
          // When we see a space, reset the numCharsWithoutBreak count.
        case ' ':
          numCharsWithoutBreak = 0
          break
          // When we see a non-space, increment the numCharsWithoutBreak.
        default:
          numCharsWithoutBreak++
          break
      }
    }

    // In addition to adding <wbr>s, we still have to add the original characters.
    result.WriteRune(codePoint)
  }

  return result.String()
  
}

/**
 * Converts \r\n, \r, and \n to <br>s
 * @param {*} str The string in which to convert newlines.
 * @return {string} A copy of {@code str} with converted newlines.
 */
func ChangeNewlineToBr(str string) string {
  // This quick test helps in the case when there are no chars to replace, in
  // the worst case this makes barely a difference to the time taken.
  if !_CHANGE_NEWLINE_TO_BR_RE.MatchString(str) {
    return str
  }
  return _CHANGE_NEWLINE_TO_BR2_RE.ReplaceAllString(str, "<br/>")
}

func Negative(a SoyData) Float64Data {
  if a == nil {
    a = NilDataInstance
  }
  a1 := a.NumberValue();
  return NewFloat64Data(-a1);
}

func Plus(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewFloat64Data(a1 + b1)
}

func Divide(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewFloat64Data(a1 / b1)
}

func Minus(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewFloat64Data(a1 - b1)
}

func Times(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewFloat64Data(a1 * b1)
}

func LessThan(a, b SoyData) BooleanData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewBooleanData(a1 < b1)
}

func GreaterThan(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewBooleanData(a1 > b1)
}

func LessThanOrEqual(a, b SoyData) BooleanData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewBooleanData(a1 <= b1)
}

func GreaterThanOrEqual(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  return NewBooleanData(a1 >= b1)
}

func round(a float64) float64 {
  integral := math.Trunc(a)
  var output float64
  if math.Signbit(a) {
    // negative
    if integral - 0.5 >= a {
      output = integral - 1
    } else {
      output = integral
    }
  } else {
    // positive
    if integral + 0.5 <= a {
      output = integral + 1
    } else {
      output = integral
    }
  }
  return output
}

func Round(a SoyData) SoyData {
  if a == nil {
    return NewFloat64Data(defaultFloat64Value())
  }
  a1 := a.NumberValue()
  return NewFloat64Data(round(a1))
}

func Round2(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.IntegerValue()
  multiplier := math.Pow10(b1)
  return NewFloat64Data(round(a1 * multiplier) / multiplier)
}

func Min(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  if a1 < b1 {
    return a
  }
  return b
}

func Max(a, b SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  if b == nil {
    b = NilDataInstance
  }
  a1 := a.NumberValue()
  b1 := b.NumberValue()
  if a1 > b1 {
    return a
  }
  return b
}

func Floor(a float64) SoyData {
  //a1 := a.NumberValue()
  return NewFloat64Data(math.Floor(a))
}

func Ceiling(a float64) SoyData {
  //a1 := a.NumberValue()
  return NewFloat64Data(math.Ceil(a))
}

func Len(a SoyData) SoyData {
  if a == nil {
    a = NilDataInstance
  }
  output := 0
  if a1, ok := a.(Lener); ok {
    output = a1.Len()
  }
  return NewIntegerData(output)
}

func HasData() bool {
  return true
}

func RandomInt(a int) IntegerData {
  return IntegerData(rand.Intn(a))
}

func GetData(data SoyData, key string) SoyData {
  if data == nil {
    return NilDataInstance
  }
  dotIndex := strings.Index(key, ".")
  keypart := key
  keyleft := ""
  if dotIndex >= 0 {
    keypart = key[0:dotIndex]
    keyleft = key[dotIndex+1:]
  }
  switch d := data.(type) {
  case SoyListData:
    lindex, err := strconv.Atoi(keyleft)
    if err == nil {
      return NilDataInstance
    }
    v := d.At(lindex)
    if len(keyleft) == 0 {
      return v
    }
    return GetData(v, keyleft)
  case SoyMapData:
    v, found := d[keypart]
    if !found {
      return NilDataInstance
    }
    if len(keyleft) == 0 {
      return v
    }
    return GetData(v, keyleft)
  default:
    return NilDataInstance
  }
  return NilDataInstance
}

/**
 * Builds an augmented data object to be passed when a template calls another,
 * and needs to pass both original data and additional params. The returned
 * object will contain both the original data and the additional params. If the
 * same key appears in both, then the value from the additional params will be
 * visible, while the value from the original data will be hidden. The original
 * data object will be used, but not modified.
 *
 * @param {!Object} origData The original data to pass.
 * @param {Object} additionalParams The additional params to pass.
 * @return {Object} An augmented data object containing both the original data
 *     and the additional params.
 */
func AugmentData(a, b SoyMapData) SoyMapData {
  if a == nil {
    a = NewSoyMapData()
  }
  if b == nil {
    b = NewSoyMapData()
  }
  for k, v := range b {
    a[k] = v
  }
  return a
}

func BoolToInt(value bool) int {
  if value {
    return 1
  }
  return 0
}
