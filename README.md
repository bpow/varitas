VARITAS - Variant Annotation with Rapid Incorporation of Tabular Annotation Sources
=======================================================================================

VARITAS is designed to help people who are evaluating genome sequence variants.


Design Goals
------------

VARITAS aims to be:
* Flexible: Incorporate new data sources easily, change a text file rather
  than recompile.
* Portable: What if you prefer Linux, but your boss uses Macintoshes and
  everyone else in the lab has to use Windows because that is what IT
  provides them? VARITAS runs on the Java(tm) virtual machine, and can be
  installed as a single .jar file (but it needs data sources, of course...)
* (Reasonably) small: Reference data sets are compressed (using bgzip) to
  minimize storage space and disk IO. The references can be stored on the
  laptop you bring to lab meeting.
* (Reasonably) fast: Reference data sets are indexed using Heng Li's `tabix`
  R-tree based interval indexing. Some trade-offs for speed were made to
  maintain portability.


Usage:
------

If you just run `java -jar varitas.jar`, you get a list of subcommands
(currently just `Varitas` and `XLifyVCF`). Help for command options for
subcommands can be obtained:

    java -jar Varitas -h

To annotate some variants:

    java -jar Varitas -c config.js -i input.vcf -o output.vcf

If no '-i' or '-o' options are given, then the standard input and standard
output streams are used, so this works:

    zcat input.vcf.gz | java -jar Varitas -c config.js | bgzip -c > output.vcf.gz

Varitas config files:
---------------------

Since version 6.0, the standard Java runtime environment (JRE) comes with a
javascript interpreter. Varitas uses this to process configuration files,
allowing for fluent flexibility.

**TODO:** discuss the exported commands


Database preparation:
---------------------

### Automatic ###

This part is not all that portable (yet). The `data` directory contains a
`Makefile` which can be used to download and prepare some useful data sets.
Requirements include `tabix`, `bgzip`, `zip`, `bzcat`, some standard GNU
utilities, and of course `make`.

I have tested on Ubuntu and CentOS, but this should also work in other
version of Linux and possibly MacOS X (provided the prerequisites are
installed).

If all the prerequisites are installed, just move to the data directory
and type:

    make

### Manual ###

Using information from a the info fields of a VCF file is easiest: the
file just needs to be compressed and indexed:

    bgzip reference.vcf # makes the file reference.vcf.gz
    tabix -p vcf reference.vcf.gz
