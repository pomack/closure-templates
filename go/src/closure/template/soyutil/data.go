package soyutil

import (
	"container/list"
	"fmt"
	"reflect"
	"strconv"
)

var NilDataInstance = &NilData{}

type Equalser interface {
	Equals(other interface{}) bool
}

type Stringer interface {
	String() string
}

type SoyDataException struct {
	msg string
}

func NewSoyDataException(msg string) *SoyDataException {
	return &SoyDataException{msg: msg}
}

func (p *SoyDataException) String() string {
	return p.msg
}

func (p *SoyDataException) Error() string {
	return p.msg
}

type SoyData interface {
	/**
	 * Converts this data object into a string (e.g. when used in a string context).
	 * @return The value of this data object if coerced into a string.
	 */
	String() string

	/**
	 * Converts this data object into a boolean (e.g. when used in a boolean context). In other words,
	 * this method tells whether this object is truthy.
	 * @return The value of this data object if coerced into a boolean. I.e. true if this object is
	 *     truthy, false if this object is falsy.
	 */
	Bool() bool

	/**
	 * Precondition: Only call this method if you know that this SoyData object is a boolean.
	 * This method gets the boolean value of this boolean object.
	 * @return The boolean value of this boolean object.
	 * @throws SoyDataException If this object is not actually a boolean.
	 */
	BooleanValue() bool

	/**
	 * Precondition: Only call this method if you know that this SoyData object is an integer.
	 * This method gets the integer value of this integer object.
	 * @return The integer value of this integer object.
	 * @throws SoyDataException If this object is not actually an integer.
	 */
	IntegerValue() int

	/**
	 * Precondition: Only call this method if you know that this SoyData object is a float.
	 * This method gets the float value of this float object.
	 * @return The float value of this float object.
	 * @throws SoyDataException If this object is not actually a float.
	 */
	FloatValue() float32

	/**
	 * Precondition: Only call this method if you know that this SoyData object is a float64.
	 * This method gets the float value of this number object (converting integer to float if
	 * necessary).
	 * @return The float value of this number object.
	 * @throws SoyDataException If this object is not actually a number.
	 */
	Float64Value() float64

	/**
	 * Precondition: Only call this method if you know that this SoyData object is a number.
	 * This method gets the float value of this number object (converting integer to float if
	 * necessary).
	 * @return The float value of this number object.
	 * @throws SoyDataException If this object is not actually a number.
	 */
	NumberValue() float64

	/**
	 * Precondition: Only call this method if you know that this SoyData object is a string.
	 * This method gets the string value of this string object.
	 * @return The string value of this string object.
	 * @throws SoyDataException If this object is not actually a string.
	 */
	StringValue() string

	SoyData() SoyData

	/**
	 * Compares this data object against another for equality in the sense of the operator '==' for
	 * Soy expressions.
	 *
	 * @param other The other data object to compare against.
	 * @return True if the two objects are equal.
	 */
	Equals(other interface{}) bool
}

/**
 * Default function implementations for SoyData types
 */
type soyData struct{}

func defaultBooleanValue() bool {
	return false
}

func defaultIntegerValue() int {
	return 0
}

func defaultFloatValue() float32 {
	return 0.0
}

func defaultFloat64Value() float64 {
	return 0.0
}

func defaultNumberValue() float64 {
	return 0.0
}

func defaultStringValue() string {
	return ""
}

type NilData struct{}

func (p NilData) BooleanValue() bool {
	return false
}

func (p NilData) IntegerValue() int {
	return 0
}

func (p NilData) FloatValue() float32 {
	return 0.0
}

func (p NilData) Float64Value() float64 {
	return 0.0
}

func (p NilData) NumberValue() float64 {
	return 0.0
}

func (p NilData) StringValue() string {
	return "null"
}

func (p NilData) Value() interface{} {
	return nil
}

func (p NilData) String() string {
	return "null"
}

func (p NilData) Bool() bool {
	return false
}

func (p NilData) Equals(other interface{}) bool {
	return p == other || other == nil
}

func (p NilData) HashCode() int {
	return 827
}

func (p NilData) SoyData() SoyData {
	return p
}

func (p NilData) At(index int) SoyData {
	return p
}

func (p NilData) Back() *list.Element {
	return nil
}

func (p NilData) Front() *list.Element {
	return nil
}

func (p NilData) HasElements() bool {
	return false
}

func (p NilData) Init() SoyListData {
	return p
}

func (p NilData) InsertAfter(value SoyData, mark *list.Element) *list.Element {
	return nil
}

func (p NilData) InsertBefore(value SoyData, mark *list.Element) *list.Element {
	return nil
}

func (p NilData) IsEmpty() bool {
	return true
}

func (p NilData) Len() int {
	return 0
}

func (p NilData) MoveToBack(e *list.Element) {
}

func (p NilData) MoveToFront(e *list.Element) {
}

func (p NilData) PushBack(value SoyData) *list.Element {
	return nil
}

func (p NilData) PushBackList(ol SoyListData) {
}

func (p NilData) PushFront(value SoyData) *list.Element {
	return nil
}

func (p NilData) PushFrontList(ol SoyListData) {
}

func (p NilData) Remove(e *list.Element) SoyData {
	return p
}

type BooleanData bool

func NewBooleanData(value bool) BooleanData {
	return BooleanData(value)
}

func (p BooleanData) Value() bool {
	return bool(p)
}

func (p BooleanData) BooleanValue() bool {
	return bool(p)
}

func (p BooleanData) IntegerValue() int {
	if p {
		return 1
	}
	return 0
}

func (p BooleanData) FloatValue() float32 {
	if p {
		return 1
	}
	return 0
}

func (p BooleanData) Float64Value() float64 {
	if p {
		return 1
	}
	return 0
}

func (p BooleanData) NumberValue() float64 {
	if p {
		return 1
	}
	return 0
}

func (p BooleanData) StringValue() string {
	return p.String()
}

func (p BooleanData) String() string {
	if p {
		return "true"
	}
	return "false"
}

func (p BooleanData) Bool() bool {
	return bool(p)
}

func (p BooleanData) Equals(other interface{}) bool {
	if other == nil {
		return false
	}
	switch o := other.(type) {
	case *NilData:
		return false
	case bool:
		return bool(p) == o
	case SoyData:
		return bool(p) == o.Bool()
	}
	return false
}

func (p BooleanData) HashCode() int {
	if p {
		return 1
	}
	return 0
}

func (p BooleanData) SoyData() SoyData {
	return p
}

type IntegerData int

func NewIntegerData(value int) IntegerData {
	return IntegerData(value)
}

func (p IntegerData) Value() int {
	return int(p)
}

func (p IntegerData) BooleanValue() bool {
	return p.Value() != 0
}

func (p IntegerData) IntegerValue() int {
	return p.Value()
}

func (p IntegerData) FloatValue() float32 {
	return float32(p.Value())
}

func (p IntegerData) Float64Value() float64 {
	return float64(p.Value())
}

func (p IntegerData) NumberValue() float64 {
	return float64(p.Value())
}

func (p IntegerData) StringValue() string {
	return string(p.Value())
}

func (p IntegerData) String() string {
	return strconv.Itoa(p.Value())
}

func (p IntegerData) Bool() bool {
	return p.Value() != 0
}

func (p IntegerData) Equals(other interface{}) bool {
	if other == nil {
		return false
	}
	switch o := other.(type) {
	case *NilData:
		return false
	case int:
		return int(p) == o
	case int32:
		return int(p) == int(o)
	case int64:
		return int(p) == int(o)
	case float32:
		return float64(p) == float64(o)
	case float64:
		return float64(p) == o
	case SoyData:
		return int(p) == o.IntegerValue()
	}
	return false
}

func (p IntegerData) HashCode() int {
	return int(p)
}

func (p IntegerData) SoyData() SoyData {
	return p
}

type Float64Data float64

func NewFloat64Data(value float64) Float64Data {
	return Float64Data(value)
}

func (p Float64Data) BooleanValue() bool {
	return p != 0.0
}

func (p Float64Data) IntegerValue() int {
	return int(p)
}

func (p Float64Data) Value() float64 {
	return float64(p)
}

func (p Float64Data) FloatValue() float32 {
	return float32(p)
}

func (p Float64Data) Float64Value() float64 {
	return float64(p)
}

func (p Float64Data) NumberValue() float64 {
	return float64(p)
}

func (p Float64Data) StringValue() string {
	return strconv.FormatFloat(float64(p), 'g', -1, 64)
}

func (p Float64Data) String() string {
	return strconv.FormatFloat(float64(p), 'g', -1, 64)
}

func (p Float64Data) Bool() bool {
	return p != 0.0
}

func (p Float64Data) Equals(other interface{}) bool {
	if other == nil {
		return false
	}
	switch o := other.(type) {
	case *NilData:
		return false
	case int:
		return float64(p) == float64(o)
	case int32:
		return float64(p) == float64(o)
	case int64:
		return float64(p) == float64(o)
	case float32:
		return float64(p) == float64(o)
	case float64:
		return float64(p) == o
	case SoyData:
		return float64(p) == o.Float64Value()
	}
	return false
}

func (p Float64Data) HashCode() int {
	return int(p)
}

func (p Float64Data) SoyData() SoyData {
	return p
}

type StringData string

func NewStringData(value string) StringData {
	return StringData(value)
}

func (p StringData) Value() string {
	return string(p)
}

func (p StringData) BooleanValue() bool {
	return defaultBooleanValue()
}

func (p StringData) IntegerValue() int {
	return defaultIntegerValue()
}

func (p StringData) FloatValue() float32 {
	return defaultFloatValue()
}

func (p StringData) Float64Value() float64 {
	return defaultFloat64Value()
}

func (p StringData) NumberValue() float64 {
	return defaultNumberValue()
}

func (p StringData) StringValue() string {
	return string(p)
}

func (p StringData) String() string {
	return string(p)
}

func (p StringData) Bool() bool {
	return len(p) > 0
}

func (p StringData) Len() int {
	return len(p)
}

func (p StringData) Equals(other interface{}) bool {
	if other == nil {
		return false
	}
	switch o := other.(type) {
	case *NilData:
		return false
	case string:
		return string(p) == o
	case SoyData:
		return string(p) == o.StringValue()
	}
	return false
}

func (p StringData) HashCode() int {
	// todo create efficient string hashcode function
	return 123
}

func (p StringData) SoyData() SoyData {
	return p
}

type SoyListData interface {
	SoyData
	At(index int) SoyData
	Back() *list.Element
	Front() *list.Element
	HasElements() bool
	Init() SoyListData
	InsertAfter(value SoyData, mark *list.Element) *list.Element
	InsertBefore(value SoyData, mark *list.Element) *list.Element
	IsEmpty() bool
	Len() int
	MoveToBack(e *list.Element)
	MoveToFront(e *list.Element)
	PushBack(value SoyData) *list.Element
	PushBackList(ol SoyListData)
	PushFront(value SoyData) *list.Element
	PushFrontList(ol SoyListData)
	Remove(e *list.Element) SoyData
}

type soyListData struct {
	l *list.List
}

func NewSoyListData() SoyListData {
	return &soyListData{l: list.New()}
}

func NewSoyListDataFromArgs(args ...interface{}) SoyListData {
	l := list.New()
	for _, v := range args {
		s, _ := ToSoyData(v)
		l.PushBack(s)
	}
	o := &soyListData{l: l}
	return o
}

func NewSoyListDataFromSoyListData(o SoyListData) SoyListData {
	if o == nil {
		return &soyListData{l: list.New()}
	}
	a := &soyListData{l: list.New()}
	a.PushBackList(o)
	return a
}

func NewSoyListDataFromList(o *list.List) SoyListData {
	if o == nil {
		return &soyListData{l: list.New()}
	}
	l := list.New()
	l.PushBackList(o)
	a := &soyListData{l: l}
	return a
}

func NewSoyListDataFromVector(o []SoyData) SoyListData {
	if o == nil {
		return &soyListData{l: list.New()}
	}
	l := list.New()
	for i := 0; i < len(o); i++ {
		l.PushBack(o[i])
	}
	a := &soyListData{l: l}
	return a
}

func (p *soyListData) Bool() bool {
	return p.Len() > 0
}

func (p *soyListData) String() string {
	return fmt.Sprintf("[%#v]", p.l)
}

func (p *soyListData) BooleanValue() bool {
	return defaultBooleanValue()
}

func (p *soyListData) IntegerValue() int {
	return defaultIntegerValue()
}

func (p *soyListData) FloatValue() float32 {
	return defaultFloatValue()
}

func (p *soyListData) Float64Value() float64 {
	return defaultFloat64Value()
}

func (p *soyListData) NumberValue() float64 {
	return defaultNumberValue()
}

func (p *soyListData) StringValue() string {
	return p.String()
}

func (p *soyListData) Equals(other interface{}) bool {
	if p == other {
		return true
	}
	if other == nil {
		return false
	}
	if o, ok := other.(SoyListData); ok {
		if p.Len() != o.Len() {
			return false
		}
		for oe, pe := o.Front(), p.Front(); oe != nil && pe != nil; oe, pe = oe.Next(), pe.Next() {
			if oe.Value == pe.Value {
				continue
			}
			if oe.Value != nil {
				if e, ok := oe.Value.(Equalser); ok {
					if e.Equals(pe.Value) {
						continue
					}
				}
			}
			return false
		}
		return true
	}
	return false
}

func (p *soyListData) SoyData() SoyData {
	return p
}

func (p *soyListData) At(index int) SoyData {
	e := p.l.Front()
	for i := 0; i < index && e != nil; i++ {
		e = e.Next()
	}
	if e == nil {
		return NilDataInstance
	}
	return e.Value.(SoyData)
}

func (p *soyListData) Back() *list.Element {
	return p.l.Back()
}

func (p *soyListData) Front() *list.Element {
	return p.l.Front()
}

func (p *soyListData) HasElements() bool {
	return p.l.Len() > 0
}

func (p *soyListData) Init() SoyListData {
	p.l.Init()
	return p
}

func (p *soyListData) InsertAfter(value SoyData, mark *list.Element) *list.Element {
	return p.l.InsertAfter(value, mark)
}

func (p *soyListData) InsertBefore(value SoyData, mark *list.Element) *list.Element {
	return p.l.InsertBefore(value, mark)
}

func (p *soyListData) IsEmpty() bool {
	return p.l.Len() == 0
}

func (p *soyListData) Len() int {
	return p.l.Len()
}

func (p *soyListData) MoveToBack(e *list.Element) {
	p.l.MoveToBack(e)
}

func (p *soyListData) MoveToFront(e *list.Element) {
	p.l.MoveToFront(e)
}

func (p *soyListData) PushBack(value SoyData) *list.Element {
	return p.l.PushBack(value)
}

func (p *soyListData) PushBackList(ol SoyListData) {
	if ol == nil {
		return
	}
	if osld, ok := ol.(*soyListData); ok {
		p.l.PushBackList(osld.l)
	} else {
		for e := ol.Front(); e != nil; e = e.Next() {
			p.l.PushBack(e.Value)
		}
	}
}

func (p *soyListData) PushFront(value SoyData) *list.Element {
	return p.l.PushFront(value)
}

func (p *soyListData) PushFrontList(ol SoyListData) {
	if ol == nil {
		return
	}
	if osld, ok := ol.(*soyListData); ok {
		p.l.PushFrontList(osld.l)
	} else {
		for e := ol.Back(); e != nil; e = e.Prev() {
			p.l.PushFront(e.Value)
		}
	}
}

func (p *soyListData) Remove(e *list.Element) SoyData {
	return p.l.Remove(e).(SoyData)
}

type SoyMapData map[string]SoyData

func NewSoyMapData() SoyMapData {
	return make(SoyMapData)
}

func NewSoyMapDataFromArgs(args ...interface{}) SoyMapData {
	m := make(map[string]SoyData)
	isKey := true
	var key string
	for _, arg := range args {
		if isKey {
			sdk, err := ToSoyData(arg)
			if err != nil {
				return nil
			}
			key = sdk.String()
		} else {
			value, err := ToSoyData(arg)
			if err != nil {
				return nil
			}
			m[key] = value
		}
		isKey = !isKey
	}
	return SoyMapData(m)
}

func NewSoyMapDataFromGenericMap(o map[string]interface{}) SoyMapData {
	m := make(map[string]SoyData)
	for key, v := range o {
		value, err := ToSoyData(v)
		if err != nil {
			return nil
		}
		m[key] = value
	}
	return SoyMapData(m)
}

func NewSoyMapDataFromMap(o map[string]SoyData) SoyMapData {
	return SoyMapData(o)
}

func (p SoyMapData) BooleanValue() bool {
	return defaultBooleanValue()
}

func (p SoyMapData) IntegerValue() int {
	return defaultIntegerValue()
}

func (p SoyMapData) FloatValue() float32 {
	return defaultFloatValue()
}

func (p SoyMapData) Float64Value() float64 {
	return defaultFloat64Value()
}

func (p SoyMapData) NumberValue() float64 {
	return defaultNumberValue()
}

func (p SoyMapData) StringValue() string {
	return defaultStringValue()
}

func (p SoyMapData) Len() int {
	return len(p)
}

func (p SoyMapData) Get(key string) SoyData {
	value, ok := p[key]
	if !ok {
		return NilDataInstance
	}
	return value
}

func (p SoyMapData) Contains(key string) bool {
	_, ok := p[key]
	return ok
}

func (p SoyMapData) Keys() []string {
	arr := make([]string, len(p))
	i := 0
	for k := range p {
		arr[i] = k
		i++
	}
	return arr
}

func (p SoyMapData) Set(key string, value SoyData) {
	p[key] = value
}

func (p SoyMapData) Bool() bool {
	return len(p) > 0
}

func (p SoyMapData) String() string {
	return fmt.Sprintf("%#v", map[string]SoyData(p))
}

func (p SoyMapData) Equals(other interface{}) bool {
	if other == nil {
		return false
	}
	if o, ok := other.(SoyMapData); ok && &p == &o {
		return true
	}
	if o, ok := other.(SoyMapData); ok {
		if len(p) != len(o) {
			return false
		}
		// TODO check each element
		return true
	}
	return false
}

func (p SoyMapData) SoyData() SoyData {
	return p
}

func (p SoyMapData) HasElements() bool {
	return len(p) > 0
}

func (p SoyMapData) IsEmpty() bool {
	return len(p) == 0
}

func ToBooleanData(obj interface{}) BooleanData {
	if obj == nil || obj == NilDataInstance {
		return NewBooleanData(false)
	}
	if o, ok := obj.(BooleanData); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(BooleanData); ok {
		return o
	}
	return NewBooleanData(s.BooleanValue())
}

func ToIntegerData(obj interface{}) IntegerData {
	if obj == nil || obj == NilDataInstance {
		return NewIntegerData(0)
	}
	if o, ok := obj.(IntegerData); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(IntegerData); ok {
		return o
	}
	return NewIntegerData(s.IntegerValue())
}

func ToFloat64Data(obj interface{}) Float64Data {
	if obj == nil || obj == NilDataInstance {
		return NewFloat64Data(0.0)
	}
	if o, ok := obj.(Float64Data); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(Float64Data); ok {
		return o
	}
	return NewFloat64Data(s.Float64Value())
}

func ToStringData(obj interface{}) StringData {
	if obj == nil || obj == NilDataInstance {
		return NewStringData("")
	}
	if o, ok := obj.(StringData); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(StringData); ok {
		return o
	}
	return NewStringData(s.StringValue())
}

func ToSoyListData(obj interface{}) SoyListData {
	if obj == nil || obj == NilDataInstance {
		return NewSoyListData()
	}
	if o, ok := obj.(SoyListData); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(SoyListData); ok {
		return o
	}
	return NewSoyListData()
}

func ToSoyMapData(obj interface{}) SoyMapData {
	if obj == nil || obj == NilDataInstance {
		return NewSoyMapData()
	}
	if o, ok := obj.(SoyMapData); ok {
		return o
	}
	s := ToSoyDataNoErr(obj)
	if o, ok := s.(SoyMapData); ok {
		return o
	}
	return NewSoyMapData()
}

func ToSoyDataNoErr(obj interface{}) SoyData {
	s, _ := ToSoyData(obj)
	return s
}

/**
 * Creation function for creating a SoyData object out of any existing primitive, data object, or
 * data structure.
 *
 * <p> Important: Avoid using this function if you know the type of the object at compile time.
 * For example, if the object is a primitive, it can be passed directly to methods such as
 * {@code SoyMapData.put()} or {@code SoyListData.add()}. If the object is a Map or an Iterable,
 * you can directly create the equivalent SoyData object using the constructor of
 * {@code SoyMapData} or {@code SoyListData}. 
 *
 * <p> If the given object is already a SoyData object, then it is simply returned.
 * Otherwise a new SoyData object will be created that is equivalent to the given primitive, data
 * object, or data structure (even if the given object is null!).
 *
 * <p> Note that in order for the conversion process to succeed, the given data structure must
 * correspond to a valid SoyData tree. Some requirements include:
 * (a) all Maps within your data structure must have string keys that are identifiers,
 * (b) all non-leaf nodes must be Maps or Lists,
 * (c) all leaf nodes must be null, boolean, int, double, or String (corresponding to Soy
 *     primitive data types null, boolean, integer, float, string).
 *
 * @param obj The existing object or data structure to convert.
 * @return A SoyData object or tree that corresponds to the given object.
 * @throws SoyDataException If the given object cannot be converted to SoyData.
 */
func ToSoyData(obj interface{}) (SoyData, error) {
	if obj == nil {
		return NilDataInstance, nil
	}
	if o, ok := obj.(SoyData); ok && o != nil {
		return o, nil
	}
	switch o := obj.(type) {
	case nil:
		return NilDataInstance, nil
	case SoyData:
		return o, nil
	case string:
		return NewStringData(o), nil
	case bool:
		return NewBooleanData(o), nil
	case uint:
		return NewIntegerData(int(o)), nil
	case int:
		return NewIntegerData(o), nil
	case int32:
		return NewIntegerData(int(o)), nil
	case int64:
		return NewIntegerData(int(o)), nil
	case float32:
		return NewFloat64Data(float64(o)), nil
	case float64:
		return NewFloat64Data(o), nil
	case *list.List:
		return NewSoyListDataFromList(o), nil
	case []SoyData:
		return NewSoyListDataFromVector(o), nil
	}
	rv := reflect.ValueOf(obj)
	switch rv.Kind() {
	case reflect.Array, reflect.Slice:
		l := NewSoyListData()
		for i := 0; i < rv.Len(); i++ {
			v := rv.Index(i)
			var sv SoyData
			if v.Interface() == nil {
				sv = NilDataInstance
			} else {
				sv, _ = ToSoyData(v.Interface())
			}
			l.PushBack(sv)
		}
		return l, nil
	case reflect.Map:
		m := NewSoyMapData()
		if !rv.IsNil() {
			for _, key := range rv.MapKeys() {
				var k string
				var sv SoyData
				if key.Interface() == nil {
					k = "null"
				} else if st, ok := key.Interface().(Stringer); ok {
					k = st.String()
				} else if k, ok = key.Interface().(string); ok {
				} else {
					s, _ := ToSoyData(key.Interface())
					k = s.StringValue()
				}
				av := rv.MapIndex(key)
				if av.Interface() == nil {
					sv = NilDataInstance
				} else {
					sv, _ = ToSoyData(av.Interface())
				}
				m.Set(k, sv)
			}
		}
		return m, nil
	case reflect.Struct:
		m := NewSoyMapData()
		rt := rv.Type()
		for i := 0; i < rt.NumField(); i++ {
			f := rt.Field(i)
			k := f.Name
			v, _ := ToSoyData(rv.Field(i).Interface())
			m.Set(k, v)
		}
		return m, nil
	}
	str := fmt.Sprintf("Attempting to convert unrecognized object to Soy data (object type %t).", obj)
	return NilDataInstance, NewSoyDataException(str)
}
