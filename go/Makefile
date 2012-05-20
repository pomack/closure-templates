
all: install

GOPATH:=$(GOPATH):`pwd`

#

clean:
	GOPATH=$(GOPATH) go clean closure/template/soyutil

install:
	GOPATH=$(GOPATH) go install closure/template/soyutil

nuke:
	GOPATH=$(GOPATH) go clean -i closure/template/soyutil
	/bin/rm -rf pkg bin

test:
	GOPATH=$(GOPATH) go test closure/template/soyutil

check:
	GOPATH=$(GOPATH) go build closure/template/soyutil
