if [ "$1" ]
then
	jarname=$1
else
	jarname=bsh.jar
fi

rm -f bsh/util/AWT*class

jar cvfm $jarname Manifest.console bsh/lib/* bsh/util/*.class bsh/util/lib/* bsh/commands/*.class bsh/commands/*.bsh bsh/*.class bsh/classpath/*.class


