JCFLAGS = -Xlint:all -Xmaxerrs 1 -Xmaxwarns 1 -Werror
TCP = /usr/share/java/junit4.jar

all: BTree.class BTreeTest.class

check: all
	java -ea -cp .:$(TCP) org.junit.runner.JUnitCore BTreeTest

%.class: %.java
	javac $(JCFLAGS) $<

%Test.class: %Test.java
	javac $(JCFLAGS) -cp .:$(TCP) $<

clean:
	rm -f *.class

.PHONY: all check clean
