#!/bin/sh
file=$1
cat new-header-lic.txt $file > "${file}.newlic"
mv "${file}.newlic" $file

