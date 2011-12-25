package soyutil;

type SanitizedContent struct {
  content string
  contentKind ContentKind
}

func NewSanitizedContent(content string, contentKind ContentKind) *SanitizedContent {
  return &SanitizedContent{
    content: content,
    contentKind: contentKind,
  }
}

func (p *SanitizedContent) Content() string {
  return p.content
}

func (p *SanitizedContent) ContentKind() ContentKind {
  return p.contentKind
}

func (p *SanitizedContent) Bool() bool {
  return len(p.content) != 0
}

func (p *SanitizedContent) BooleanValue() bool {
  return len(p.content) != 0
}

func (p *SanitizedContent) String() string {
  return p.content
}

func (p *SanitizedContent) StringValue() string {
  return p.content
}

func (p *SanitizedContent) Equals(other interface{}) bool {
  if other == nil {
    return false
  }
  if o, ok := other.(*SanitizedContent); ok {
    if o == nil {
      return false
    }
    return o.content == p.content && o.contentKind == p.contentKind
  }
  if o, ok := other.(SanitizedContent); ok {
    return o.content == p.content && o.contentKind == p.contentKind
  }
  return false
}

