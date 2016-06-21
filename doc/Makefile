OUTPUT = guide-toc.md guide.html example-operations.html

all: $(OUTPUT)

%.html: %.md
	rm -f $@
	pandoc -f markdown_github-hard_line_breaks $? > $@
	chmod ugo-w $@

guide-toc.md: guide.md
	rm -f $@
	./md2toc -l 2 -h 3 $? > $@
	chmod ugo-w $@

clean:
	rm -f $(OUTPUT)

